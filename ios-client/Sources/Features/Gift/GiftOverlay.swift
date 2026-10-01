import SwiftUI
import UIKit

/// Gifts landing on the broadcast, for host and viewers alike. Two bounded
/// surfaces: banners (two slots, top-left, coalesced by sender+gift with a
/// counter) and the animation lane (one at a time, a queue of at most 30,
/// coalesced by sender+gift so a lucky streak plays once with a counter).
///
/// Playback never waits on the network: an SVGA not decoded within 600 ms shows
/// its listing image with a pop, so every viewer sees the gift at the same
/// moment. The sender's own gift renders from the optimistic frame on the tap;
/// the server's echo (same clientId) is recognised and skipped.
@MainActor
final class GiftOverlayModel: ObservableObject {
    final class Banner: ObservableObject, Identifiable {
        let id: Int64
        let sender: LivePersonData
        let receiver: LivePersonData
        let gift: GiftData
        @Published var count: Int
        @Published var bump = 0
        var lastAt = Date()
        var expiry: Task<Void, Never>?
        init(id: Int64, sender: LivePersonData, receiver: LivePersonData, gift: GiftData, count: Int) {
            self.id = id; self.sender = sender; self.receiver = receiver; self.gift = gift; self.count = count
        }
    }

    struct Playing: Identifiable {
        let id = UUID()
        let ev: LiveGiftEvent
        let movie: SvgaMovie?
    }

    @Published private(set) var banners: [Banner] = []
    @Published private(set) var playing: Playing?
    @Published private(set) var repeats = 1

    /// The one player view on screen; set by the representable.
    var player: SvgaPlayerView?

    private var queue: [LiveGiftEvent] = []
    private var queueRepeats: [Int64: Int] = [:]
    private var seenClientIds: [String] = []
    private var seenClientSet = Set<String>()
    private var lane: Task<Void, Never>?
    private var wake: CheckedContinuation<Void, Never>?
    private var token: Int?
    private let roomId: Int64

    private static let queueCap = 30
    private static let bannerSlots = 2
    private static let bannerMs = 3000
    private static let readyMs = 1400
    private static let stillMs = 1800
    private static let minMs = 900
    private static let maxMs = 12000

    init(roomId: Int64) {
        self.roomId = roomId
    }

    func start() {
        guard token == nil else { return }
        token = LiveSession.shared.listenGifts { [weak self] ev in
            guard let self, ev.roomId == self.roomId || self.roomId == 0 else { return }
            self.onEvent(ev)
        }
        lane = Task { [weak self] in await self?.run() }
    }

    func stop() {
        if let t = token { LiveSession.shared.stopListening(t) }
        token = nil
        lane?.cancel(); lane = nil
        banners.forEach { $0.expiry?.cancel() }
        banners.removeAll()
        queue.removeAll(); queueRepeats.removeAll()
        player?.stop()
        playing = nil
        GiftSound.shared.stop()
    }

    private func key(_ ev: LiveGiftEvent) -> Int64 { (ev.sender.id << 20) ^ ev.gift.id }

    func onEvent(_ ev: LiveGiftEvent) {
        guard ev.gift.id != 0 else { return }
        if !ev.clientId.isEmpty {
            if seenClientSet.contains(ev.clientId) { return }
            seenClientSet.insert(ev.clientId); seenClientIds.append(ev.clientId)
            if seenClientIds.count > 256 { seenClientSet.remove(seenClientIds.removeFirst()) }
        }
        if ev.gift.isSvga { SvgaLoader.shared.prefetch(ev.gift.animationUrl) }
        if !ev.gift.soundUrl.isEmpty { GiftSound.shared.prefetch(ev.gift.soundUrl) }
        banner(ev)
        enqueue(ev)
    }

    private func banner(_ ev: LiveGiftEvent) {
        let k = key(ev)
        let b: Banner
        if let existing = banners.first(where: { $0.id == k }) {
            existing.count = ev.combo > existing.count ? ev.combo : existing.count + ev.count
            existing.bump += 1
            existing.lastAt = Date()
            b = existing
        } else {
            if banners.count >= Self.bannerSlots, let oldest = banners.min(by: { $0.lastAt < $1.lastAt }) {
                oldest.expiry?.cancel()
                banners.removeAll { $0 === oldest }
            }
            b = Banner(id: k, sender: ev.sender, receiver: ev.receiver, gift: ev.gift, count: ev.combo > 1 ? ev.combo : ev.count)
            banners.append(b)
        }
        b.expiry?.cancel()
        b.expiry = Task { [weak self, weak b] in
            try? await Task.sleep(nanoseconds: UInt64(Self.bannerMs) * 1_000_000)
            guard !Task.isCancelled, let self, let b else { return }
            withAnimation(.easeOut(duration: 0.25)) { self.banners.removeAll { $0 === b } }
        }
    }

    private func enqueue(_ ev: LiveGiftEvent) {
        let k = key(ev)
        if let cur = playing, key(cur.ev) == k { repeats += 1; return }
        if let tail = queue.last, key(tail) == k { queueRepeats[k, default: 1] += 1; return }
        if queue.count >= Self.queueCap {
            let dropped = queue.removeFirst()
            queueRepeats[key(dropped)] = nil
        }
        queue.append(ev)
        if let w = wake { wake = nil; w.resume() }
    }

    private func run() async {
        while !Task.isCancelled {
            guard !queue.isEmpty else {
                await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in wake = c }
                continue
            }
            let ev = queue.removeFirst()
            let n = queueRepeats.removeValue(forKey: key(ev)) ?? 1
            await play(ev, repeats: n)
        }
    }

    private func play(_ ev: LiveGiftEvent, repeats n: Int) async {
        let gift = ev.gift
        var movie: SvgaMovie?
        if gift.isSvga, player != nil {
            // Already decoded (prefetched on selection / banner) costs nothing;
            // a first-ever play waits a short beat so the room sees the
            // animation rather than its poster.
            movie = SvgaLoader.shared.cached(gift.animationUrl)
                ?? (await withTimeout(ms: Self.readyMs) { await SvgaLoader.shared.load(gift.animationUrl) })
        }
        repeats = n
        let p = Playing(ev: ev, movie: movie)
        playing = p
        var hold = gift.durationMs > 0 ? gift.durationMs : (movie?.durationMs ?? Self.stillMs)
        hold = min(Self.maxMs, max(Self.minMs, hold))
        if !gift.soundUrl.isEmpty { GiftSound.shared.play(gift.soundUrl, maxMs: hold) }
        if let movie, let v = player {
            let loops = movie.durationMs <= 0 ? 1 : max(1, (hold + movie.durationMs / 2) / movie.durationMs)
            await withDeadline(ms: hold + 800) {
                await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                    v.play(movie, loops: loops) { c.resume() }
                }
            }
            v.stop()
        } else {
            try? await Task.sleep(nanoseconds: UInt64(hold) * 1_000_000)
        }
        playing = nil
        try? await Task.sleep(nanoseconds: 120_000_000)
    }

    private func withTimeout<T>(ms: Int, _ op: @escaping @MainActor () async -> T?) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask { await op() }
            group.addTask { try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000); return nil }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private func withDeadline(ms: Int, _ op: @escaping @MainActor () async -> Void) async {
        let _: Bool? = await withTimeout(ms: ms) { () async -> Bool? in await op(); return true }
    }
}

/// Catalog URLs are absolute; a bare storage key falls back to the media base.
@MainActor
func giftImageURL(_ s: String) -> URL? {
    if s.isEmpty { return nil }
    if s.hasPrefix("http") { return URL(string: s) }
    return MediaConfig.shared.url(s)
}

struct GiftOverlay: View {
    @ObservedObject var model: GiftOverlayModel
    @Environment(\.uiScale) private var scale

    var body: some View {
        ZStack(alignment: .topLeading) {
            SvgaPlayerRepresentable(model: model)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            if let p = model.playing, p.movie == nil {
                StillGift(playing: p)
            }
            if model.playing != nil, model.repeats > 1 {
                HStack {
                    Spacer()
                    ComboBadge(n: model.repeats)
                        .padding(.trailing, 18 * scale)
                }
                .frame(maxHeight: .infinity)
            }
            VStack(alignment: .leading, spacing: 8 * scale) {
                ForEach(model.banners) { b in
                    GiftBannerView(banner: b)
                        .transition(.asymmetric(insertion: .move(edge: .leading).combined(with: .opacity), removal: .opacity))
                }
            }
            .padding(.top, 150 * scale)
            .padding(.leading, 12 * scale)
            .frame(maxWidth: UIScreen.main.bounds.width * 0.72, alignment: .leading)
            .animation(.easeOut(duration: 0.26), value: model.banners.map(\.id))
        }
        .allowsHitTesting(false)
        .onAppear {
            SvgaLoader.shared.screenWidthPx = UIScreen.main.bounds.width * UIScreen.main.scale
            model.start()
        }
        .onDisappear { model.stop() }
    }
}

private struct SvgaPlayerRepresentable: UIViewRepresentable {
    let model: GiftOverlayModel
    func makeUIView(context: Context) -> SvgaPlayerView {
        let v = SvgaPlayerView(frame: .zero)
        model.player = v
        return v
    }
    func updateUIView(_ uiView: SvgaPlayerView, context: Context) {}
    static func dismantleUIView(_ uiView: SvgaPlayerView, coordinator: ()) { uiView.stop() }
}

private struct StillGift: View {
    let playing: GiftOverlayModel.Playing
    @Environment(\.uiScale) private var scale
    @State private var shown = false

    var body: some View {
        RemoteImage(url: giftImageURL(playing.ev.gift.listingUrl), contentMode: .fit, fallback: .clear)
            .frame(width: 180 * scale, height: 180 * scale)
            .scaleEffect(shown ? 1 : 0.4)
            .opacity(shown ? 1 : 0)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onAppear { withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) { shown = true } }
    }
}

private struct ComboBadge: View {
    let n: Int
    @State private var pop: CGFloat = 1
    var body: some View {
        AppText("×\(n)", style: Type.labelBold,
                gradient: LinearGradient(colors: [Color(rgb: 0xFDE68A), Color(rgb: 0xF59E0B)], startPoint: .top, endPoint: .bottom),
                sizeOverride: 44)
            .scaleEffect(pop)
            .onChange(of: n) { _ in
                pop = 1.35
                withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) { pop = 1 }
            }
    }
}

private struct GiftBannerView: View {
    @ObservedObject var banner: GiftOverlayModel.Banner
    @Environment(\.uiScale) private var scale
    @State private var pop: CGFloat = 1

    var body: some View {
        let senderName = banner.sender.displayName.isEmpty ? "…" : banner.sender.displayName
        let toName: String = {
            if banner.receiver.role == "host" { return Copy.t(.liveGiftHost) }
            if !banner.receiver.fullName.isEmpty { return banner.receiver.fullName }
            if !banner.receiver.username.isEmpty { return "@" + banner.receiver.username }
            return Copy.t(.liveGiftHost)
        }()
        HStack(spacing: 8 * scale) {
            PersonAvatar(id: banner.sender.id, name: senderName, avatarUrl: banner.sender.avatarUrl, size: 34 * scale, frameUrl: banner.sender.frameUrl, frameMime: banner.sender.frameMime)
            VStack(alignment: .leading, spacing: 1) {
                AppText(senderName, style: Type.labelBold, color: .white, lineLimit: 1, sizeOverride: 12)
                AppText(Copy.t(.liveGiftSentLine, ["gift": banner.gift.title, "to": toName]), style: Type.caption,
                        color: Color.white.opacity(0.85), lineLimit: 1, sizeOverride: 11)
            }
            RemoteImage(url: giftImageURL(banner.gift.listingUrl), contentMode: .fit, fallback: .clear)
                .frame(width: 36 * scale, height: 36 * scale)
            if banner.count > 1 {
                AppText("×\(banner.count)", style: Type.labelBold,
                        gradient: LinearGradient(colors: [Color(rgb: 0xFDE68A), Color(rgb: 0xF59E0B)], startPoint: .top, endPoint: .bottom),
                        sizeOverride: 20)
                    .scaleEffect(pop)
            }
        }
        .padding(.leading, 4 * scale)
        .padding(.trailing, 14 * scale)
        .padding(.vertical, 4 * scale)
        .background(
            LinearGradient(colors: [Color(rgb: 0x7C3AED).opacity(0.8), Color(rgb: 0xEC4899).opacity(0.4), Color(rgb: 0x07070F).opacity(0)],
                           startPoint: .leading, endPoint: .trailing)
        )
        .clipShape(Capsule())
        .onChange(of: banner.bump) { _ in
            pop = 1.3
            withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) { pop = 1 }
        }
    }
}
