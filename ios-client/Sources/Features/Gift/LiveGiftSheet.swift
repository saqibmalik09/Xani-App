import SwiftUI

/// The gift catalog every sheet reads: loaded once, refreshed when older than a
/// minute, served stale while a refresh is in flight. Listing images are warmed
/// on load; animations and sounds only on selection or when one lands.
@MainActor
final class GiftCatalogStore: ObservableObject {
    static let shared = GiftCatalogStore()
    @Published private(set) var catalog = GiftCatalogData.empty
    private var loadedAt: TimeInterval = 0
    private var failedAt: TimeInterval = 0
    private var loading: Task<GiftCatalogData, Never>?

    private init() {}

    func load() async -> GiftCatalogData {
        let now = Date().timeIntervalSince1970
        if !catalog.categories.isEmpty, now - loadedAt < 60 { return catalog }
        if catalog.categories.isEmpty, now - failedAt < 5 { return catalog }
        if let running = loading { return await running.value }
        let task = Task<GiftCatalogData, Never> { [weak self] in
            let r = await Xani.gifts.catalog()
            guard let self else { return .empty }
            if r.ok, let data = r.value {
                self.loadedAt = Date().timeIntervalSince1970
                self.catalog = data
                var n = 0
                for c in data.categories { for g in c.gifts where n < 48 { n += 1; if let u = giftImageURL(g.listingUrl) { ImageLoader.shared.prefetch(u) } } }
                return data
            }
            self.failedAt = Date().timeIntervalSince1970
            return self.catalog
        }
        loading = task
        let out = await task.value
        loading = nil
        return out
    }

    func warm(_ gift: GiftData) {
        if gift.isSvga { SvgaLoader.shared.prefetch(gift.animationUrl) }
        if !gift.soundUrl.isEmpty { GiftSound.shared.prefetch(gift.soundUrl) }
    }
}

/// The gift sheet: receivers (host first, selected), categories, the grid, and
/// a bar with the sender's coins bottom-left and Send / combo bottom-right.
struct LiveGiftSheet: View {
    let roomId: Int64
    let hostId: Int64
    var onClose: () -> Void
    var onRecharge: (() -> Void)? = nil

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @ObservedObject private var store = GiftCatalogStore.shared
    @ObservedObject private var profile = ProfileStore.shared
    @State private var people: [LivePersonData] = []
    @State private var receiverId: Int64 = 0
    @State private var categoryId: Int64 = 0
    /// Sub-category inside the open root; 0 = all of it.
    @State private var subId: Int64 = 0
    @State private var selectedId: Int64 = 0
    @State private var loading = true
    /// The engine's tree for this room + receiver: sub-categories and lock
    /// states. Nil until it lands (or while the engine is away), when the
    /// flat catalog stands in without lock states.
    @State private var tree: GiftTreeData?
    @State private var treeSeq = 0
    /// A refusal is a modal over the sheet, never a line inside it.
    @State private var dialog: GiftDialog?
    /// Finger-following drag on the sheet body; > 0 pulls it down.
    @State private var dragY: CGFloat = 0
    @State private var pending: [String: Int64] = [:]
    @State private var combo = 0
    @State private var comboGift: Int64 = 0
    @State private var ring: CGFloat = 0
    @State private var comboTask: Task<Void, Never>?
    @State private var tokens: [Int] = []

    private var catalog: GiftCatalogData { store.catalog }

    /// What the sheet lists: the engine tree, else the flat catalog as roots.
    private var roots: [GiftCategoryNode] {
        if let t = tree, !t.categories.isEmpty { return t.categories }
        return catalog.categories.map { c in
            GiftCategoryNode(id: c.id, title: c.title, lucky: c.lucky, gifts: c.gifts.map { GiftLeafData(gift: $0) })
        }
    }
    private var leaves: [GiftLeafData] {
        var seen = Set<Int64>()
        return roots.flatMap(\.allGifts).filter { seen.insert($0.id).inserted }
    }
    private var selectedLeaf: GiftLeafData? { leaves.first { $0.id == selectedId } }
    private var selected: GiftData? { selectedLeaf?.gift }
    private var coins: Int64 { max(0, profile.coins - pending.values.reduce(0, +)) }
    private var me: Int64 { Xani.currentUserId }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.35 * (1 - min(1, Double(max(0, dragY)) / 320)))
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
            VStack(spacing: 0) {
                Capsule().fill(Color.white.opacity(0.2)).frame(width: 36 * scale, height: 4 * scale).padding(.top, 10 * scale)
                receiverRow
                if !roots.isEmpty { categoryRow }
                if let root = roots.first(where: { $0.id == categoryId }), !root.children.isEmpty { subRow(root) }
                grid.frame(height: 268 * scale)
                Spacer().frame(height: 8 * scale)
                bottomBar
            }
            .padding(.bottom, max(insets.bottom, 8 * scale))
            .frame(maxWidth: .infinity)
            .background(
                LinearGradient(colors: [Color(rgb: 0x1B1430), Color(rgb: 0x110C21)], startPoint: .top, endPoint: .bottom)
            )
            .clipShape(RoundedCorners(radius: 24 * scale))
            .overlay(RoundedCorners(radius: 24 * scale).stroke(Color(rgb: 0x8B5CF6).opacity(0.25), lineWidth: 1))
            .offset(y: max(0, dragY) + min(0, dragY) * 0.25)
            // Whole-surface drag: the handle, header, bar — the grid keeps its
            // own vertical scroll and wins when it moves first.
            .simultaneousGesture(
                DragGesture(minimumDistance: 12, coordinateSpace: .local)
                    .onChanged { v in dragY = v.translation.height }
                    .onEnded { v in
                        let fling = v.predictedEndTranslation.height - v.translation.height
                        if dragY > 120 || (fling > 260 && dragY > 20) {
                            withAnimation(.easeIn(duration: 0.2)) { dragY = 600 }
                            onClose()
                        } else {
                            withAnimation(.spring(response: 0.36, dampingFraction: 0.82)) { dragY = 0 }
                        }
                    }
            )

            if let d = dialog {
                GiftModal(
                    dialog: d, coins: coins,
                    onDismiss: { dialog = nil },
                    onRecharge: onRecharge.map { r in { dialog = nil; r() } },
                    onRetry: { dialog = nil; send() }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.92)))
                .zIndex(3)
            }
        }
        .animation(.spring(response: 0.32, dampingFraction: 0.8), value: dialog != nil)
        .ignoresSafeArea(edges: .bottom)
        .task(id: roomId) { await loadAll() }
        // The tree is per receiver: switching who you send to re-asks the rules.
        .task(id: "\(roomId)/\(receiverId)/\(treeSeq)") { await loadTree() }
        .onChange(of: catalog.rev) { _ in autoSelect() }
        .onChange(of: selectedId) { _ in if let g = selected { store.warm(g) } }
        .onChange(of: profile.coins) { _ in pending.removeAll() }
        .onAppear {
            // Open on the ledger's number, not on what this process last heard:
            // a recharge from the dashboard is visible at once.
            ProfileStore.shared.refresh()
            tokens.append(LiveSession.shared.listenGifts { ev in
                if ev.id != 0, !ev.clientId.isEmpty { pending[ev.clientId] = nil }
            })
            tokens.append(LiveSession.shared.listenGiftErrors { f in
                pending[f.clientId] = nil
                combo = 0
                let g = selected
                switch f.code {
                case "insufficient_funds":
                    ProfileStore.shared.refresh()
                    dialog = .notEnough(gift: g?.title ?? "", price: g?.coinPrice ?? 0)
                case "too_fast":
                    dialog = .failed(message: Copy.t(.liveGiftTooFast), retry: true)
                case let code where code.hasPrefix("gift_"):
                    // A rule refused it: say why in the server's words and
                    // re-read the tree so the grid shows the lock.
                    treeSeq += 1
                    dialog = .locked(message: f.message.isEmpty ? Copy.t(.liveGiftFailed) : f.message)
                default:
                    dialog = .failed(message: f.message.isEmpty ? Copy.t(.liveGiftFailed) : f.message, retry: true)
                }
            })
        }
        .onDisappear { tokens.forEach { LiveSession.shared.stopListening($0) }; tokens.removeAll() }
    }

    private func loadAll() async {
        receiverId = hostId
        // People and catalog in parallel; both land on the main actor.
        // Yourself included — a self-gift converts coins into diamonds.
        let peopleTask = Task { @MainActor in
            let r = await Xani.gifts.people(roomId)
            let items = r.value?.items ?? []
            let host = items.first { $0.role == "host" } ?? (hostId > 0 ? LivePersonData(id: hostId, role: "host") : nil)
            let rest = items.filter { $0.role != "host" }.sorted { ($0.role == "guest" ? 0 : 1) < ($1.role == "guest" ? 0 : 1) }
            let list = [host].compactMap { $0 } + rest
            people = list
            if receiverId == 0 || !list.contains(where: { $0.id == receiverId }) { receiverId = list.first?.id ?? 0 }
        }
        _ = await store.load()
        loading = false
        autoSelect()
        _ = await peopleTask.value
    }

    /// The rule-aware tree for this receiver. Engine away or offline: keep
    /// whatever we had, or the flat catalog.
    private func loadTree() async {
        guard roomId != 0 else { return }
        let r = await Xani.gifting.catalog(roomId: roomId, receiverId: receiverId)
        guard !Task.isCancelled, r.ok, let got = r.value else { return }
        tree = got
        loading = false
        autoSelect()
        var n = 0
        for leaf in got.all where n < 48 { n += 1; if let u = giftImageURL(leaf.gift.listingUrl) { ImageLoader.shared.prefetch(u) } }
    }

    /// Keeps a root, a sub-category and a gift selected as the lists change;
    /// prefers a gift this sender may actually send.
    private func autoSelect() {
        let roots = self.roots
        guard !roots.isEmpty else { return }
        if !roots.contains(where: { $0.id == categoryId }) { categoryId = roots[0].id; subId = 0 }
        let root = roots.first { $0.id == categoryId }!
        if subId != 0, !root.children.contains(where: { $0.id == subId }) { subId = 0 }
        let visible = Self.node(in: root, sub: subId).allGifts
        if !visible.contains(where: { $0.id == selectedId }) {
            selectedId = visible.first { $0.state.allowed }?.id ?? visible.first?.id ?? 0
        }
    }

    /// The open sub-category of `root`, or the root itself when none (or a stale id).
    private static func node(in root: GiftCategoryNode, sub: Int64) -> GiftCategoryNode {
        if sub == 0 { return root }
        return root.children.first(where: { $0.id == sub }) ?? root
    }

    private func send() {
        guard let leaf = selectedLeaf, receiverId != 0, dialog == nil else { return }
        let g = leaf.gift
        if !leaf.state.allowed {
            combo = 0
            dialog = .locked(message: leaf.state.text.isEmpty ? Copy.t(.liveGiftLockedTitle) : leaf.state.text)
            return
        }
        // Refuse locally only on a balance the server has actually told us;
        // an unknown balance goes to the ledger, which is the only judge.
        if profile.balanceKnown, coins < g.coinPrice {
            combo = 0
            dialog = .notEnough(gift: g.title, price: g.coinPrice)
            return
        }
        let next = (combo > 0 && comboGift == g.id) ? combo + 1 : 1
        guard let clientId = LiveSession.shared.sendGift(g, receiverId: receiverId, combo: next) else { return }
        pending[clientId] = g.coinPrice
        combo = next
        comboGift = g.id
        comboTask?.cancel()
        ring = 1
        withAnimation(.linear(duration: 3)) { ring = 0 }
        comboTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if !Task.isCancelled, comboGift == g.id { combo = 0 }
        }
    }

    // MARK: - pieces

    private var receiverRow: some View {
        HStack(spacing: 10 * scale) {
            AppText(Copy.t(.liveGiftSendTo), style: Type.caption, color: Color.white.opacity(0.5), sizeOverride: 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10 * scale) {
                    ForEach(people) { p in
                        let on = p.id == receiverId
                        let name = p.displayName.isEmpty ? (p.role == "host" ? Copy.t(.liveGiftHost) : "…") : p.displayName
                        Button(action: { receiverId = p.id }) {
                            ZStack(alignment: .bottom) {
                                PersonAvatar(id: p.id, name: name, avatarUrl: p.avatarUrl, size: 40 * scale, frameUrl: p.frameUrl, frameMime: p.frameMime)
                                if p.role == "host" {
                                    AppText(Copy.t(.liveGiftHost), style: Type.labelBold, color: .white, sizeOverride: 8)
                                        .padding(.horizontal, 4 * scale).padding(.vertical, 1)
                                        .background(Color(rgb: 0x7C3AED)).clipShape(RoundedRectangle(cornerRadius: 6 * scale))
                                }
                            }
                            .padding(3)
                            .overlay(
                                Circle().stroke(
                                    on ? AnyShapeStyle(LinearGradient(colors: [Color(rgb: 0xA78BFA), Color(rgb: 0xF472B6)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                       : AnyShapeStyle(Color.white.opacity(0.12)),
                                    lineWidth: on ? 2 : 1
                                )
                            )
                            .scaleEffect(on ? 1.08 : 1)
                            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: on)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .padding(.top, 12 * scale)
        .padding(.horizontal, 14 * scale)
    }

    private var categoryRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 18 * scale) {
                ForEach(roots) { c in
                    let on = c.id == categoryId
                    Button(action: {
                        categoryId = c.id; subId = 0
                        let g = c.allGifts
                        selectedId = g.first { $0.state.allowed }?.id ?? g.first?.id ?? 0
                    }) {
                        VStack(spacing: 0) {
                            AppText(c.lucky ? "🍀 " + c.title : c.title, style: Type.labelBold,
                                    color: on ? .white : Color.white.opacity(0.5), sizeOverride: 13)
                                .padding(.vertical, 8 * scale)
                            Capsule().fill(on ? Color(rgb: 0xA78BFA) : .clear).frame(width: 22 * scale, height: 2 * scale)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14 * scale)
        }
        .padding(.top, 6 * scale)
    }

    /// Sub-categories of the open root: "All" first, then each child.
    private func subRow(_ root: GiftCategoryNode) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8 * scale) {
                subPill(Copy.t(.liveAll), on: subId == 0) { subId = 0 }
                ForEach(root.children) { c in
                    subPill(c.lucky ? "🍀 " + c.title : c.title, on: subId == c.id) {
                        subId = c.id
                        let g = c.allGifts
                        if !g.contains(where: { $0.id == selectedId }) { selectedId = g.first { $0.state.allowed }?.id ?? g.first?.id ?? 0 }
                    }
                }
            }
            .padding(.horizontal, 14 * scale)
        }
        .padding(.top, 4 * scale)
    }

    private func subPill(_ label: String, on: Bool, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            AppText(label, style: Type.labelBold, color: on ? .white : Color.white.opacity(0.6), sizeOverride: 11)
                .padding(.horizontal, 10 * scale).padding(.vertical, 5 * scale)
                .background(on ? Color(rgb: 0x8B5CF6).opacity(0.35) : Color.white.opacity(0.06))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var grid: some View {
        let root = roots.first(where: { $0.id == categoryId })
        let node = root.map { Self.node(in: $0, sub: subId) }
        let visible = node?.allGifts ?? []
        if loading, roots.isEmpty {
            centered(Copy.t(.liveGiftLoading))
        } else if visible.isEmpty {
            centered(Copy.t(.liveGiftEmpty))
        } else {
            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6 * scale), count: 4), spacing: 6 * scale) {
                    ForEach(visible) { leaf in
                        let g = leaf.gift
                        GiftCell(
                            gift: g, selected: g.id == selectedId, lucky: (node?.lucky ?? false) || g.lucky,
                            locked: !leaf.state.allowed, lockLabel: Copy.t(.liveGiftLocked)
                        ) {
                            if !leaf.state.allowed {
                                combo = 0
                                dialog = .locked(message: leaf.state.text.isEmpty ? Copy.t(.liveGiftLockedTitle) : leaf.state.text)
                            } else if selectedId == g.id, combo > 0 {
                                send()
                            } else {
                                selectedId = g.id; combo = 0
                            }
                        }
                    }
                }
                .padding(.horizontal, 10 * scale)
                .padding(.vertical, 8 * scale)
            }
        }
    }

    private func centered(_ s: String) -> some View {
        AppText(s, style: Type.caption, color: Color.white.opacity(0.55), sizeOverride: 13)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var bottomBar: some View {
        HStack(spacing: 10 * scale) {
            Button(action: { onRecharge?() }) {
                HStack(spacing: 6 * scale) {
                    CoinGlyph(size: 16 * scale)
                    AppText(formatCoins(coins), style: Type.labelBold, color: Color(rgb: 0xFDE68A), sizeOverride: 14)
                    if onRecharge != nil {
                        AppText(Copy.t(.liveGiftRecharge) + " ›", style: Type.caption, color: Color.white.opacity(0.55), sizeOverride: 12)
                    }
                }
                .padding(.horizontal, 12 * scale).padding(.vertical, 7 * scale)
                .background(Color.white.opacity(0.06)).clipShape(Capsule())
            }
            .buttonStyle(.plain)
            Spacer()
            if combo > 0, let g = selected, comboGift == g.id {
                ComboButton(count: combo, ring: ring, onTap: send)
            } else {
                Button(action: send) {
                    AppText(Copy.t(.liveGiftSend), style: Type.labelBold, color: .white, sizeOverride: 14)
                        .padding(.horizontal, 26 * scale)
                        .frame(height: 40 * scale)
                        .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(selected == nil || receiverId == 0)
                .opacity(selected == nil || receiverId == 0 ? 0.5 : 1)
            }
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 10 * scale)
        .background(Color.black.opacity(0.25))
    }
}

/// What the sheet has to say when a send does not go through.
enum GiftDialog: Equatable {
    case notEnough(gift: String, price: Int64)
    case failed(message: String, retry: Bool)
    /// A rule forbids this gift for this pair; `message` is the server's reason.
    case locked(message: String)
}

/// Centered card over the dimmed room, the way a refusal looks on any large
/// social app: a title, one sentence, and the actions that matter.
struct GiftModal: View {
    let dialog: GiftDialog
    let coins: Int64
    var onDismiss: () -> Void
    var onRecharge: (() -> Void)?
    var onRetry: () -> Void
    @Environment(\.uiScale) private var scale

    private var isNotEnough: Bool { if case .notEnough = dialog { return true } else { return false } }
    private var isLocked: Bool { if case .locked = dialog { return true } else { return false } }

    private var badge: LinearGradient {
        if isNotEnough { return LinearGradient(colors: [Color(rgb: 0xFDE68A), Color(rgb: 0xF59E0B)], startPoint: .topLeading, endPoint: .bottomTrailing) }
        if isLocked { return LinearGradient(colors: [Color(rgb: 0xC4B5FD), Color(rgb: 0x7C3AED)], startPoint: .topLeading, endPoint: .bottomTrailing) }
        return LinearGradient(colors: [Color(rgb: 0xFCA5A5), Color(rgb: 0xEF4444)], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(badge)
                    AppText(isNotEnough ? "🪙" : (isLocked ? "🔒" : "!"), style: Type.labelBold, color: .white, sizeOverride: 24)
                }
                .frame(width: 56 * scale, height: 56 * scale)
                Spacer().frame(height: 14 * scale)
                AppText(title, style: Type.labelBold, color: .white, sizeOverride: 17)
                Spacer().frame(height: 8 * scale)
                AppText(message, style: Type.caption, color: Color.white.opacity(0.72), sizeOverride: 13)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 20 * scale)
                if let primary = primaryAction {
                    Button(action: primary) {
                        AppText(isNotEnough ? Copy.t(.liveGiftRecharge) : Copy.t(.liveGiftRetry), style: Type.labelBold, color: .white, sizeOverride: 14)
                            .frame(maxWidth: .infinity).frame(height: 44 * scale)
                            .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    Spacer().frame(height: 8 * scale)
                }
                Button(action: onDismiss) {
                    AppText(Copy.t(.liveGiftDismiss), style: Type.labelBold, color: Color.white.opacity(0.85), sizeOverride: 14)
                        .frame(maxWidth: .infinity).frame(height: 44 * scale)
                        .background(Color.white.opacity(0.06))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 22 * scale)
            .frame(maxWidth: 300 * scale)
            .background(LinearGradient(colors: [Color(rgb: 0x241A3D), Color(rgb: 0x150F27)], startPoint: .top, endPoint: .bottom))
            .clipShape(RoundedRectangle(cornerRadius: 22 * scale, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22 * scale, style: .continuous).stroke(Color(rgb: 0x8B5CF6).opacity(0.25), lineWidth: 1))
        }
    }

    private var title: String {
        switch dialog {
        case .notEnough: return Copy.t(.liveGiftNotEnoughTitle)
        case .failed: return Copy.t(.liveGiftFailedTitle)
        case .locked: return Copy.t(.liveGiftLockedTitle)
        }
    }

    private var message: String {
        switch dialog {
        case let .notEnough(gift, price):
            return Copy.t(.liveGiftNotEnoughBody, [
                "price": formatCoins(price),
                "coins": formatCoins(coins),
                "gift": gift.isEmpty ? Copy.t(.liveGiftsTitle).lowercased() : gift,
            ])
        case let .failed(message, _):
            return message
        case let .locked(message):
            return message
        }
    }

    private var primaryAction: (() -> Void)? {
        switch dialog {
        case .notEnough: return onRecharge
        case let .failed(_, retry): return retry ? onRetry : nil
        case .locked: return nil
        }
    }
}

private struct GiftCell: View {
    let gift: GiftData
    let selected: Bool
    let lucky: Bool
    /// A rule forbids this gift for this sender → receiver: dimmed, with a lock.
    var locked = false
    var lockLabel = ""
    var onTap: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                ZStack(alignment: .topTrailing) {
                    RemoteImage(url: giftImageURL(gift.listingUrl), contentMode: .fit, fallback: .clear)
                        .padding(6 * scale)
                        .aspectRatio(1, contentMode: .fit)
                    if lucky { AppText("🍀", style: Type.caption, sizeOverride: 10) }
                    if locked {
                        VStack {
                            Spacer()
                            AppText("🔒 " + lockLabel, style: Type.caption, color: .white, lineLimit: 1, sizeOverride: 9)
                                .padding(.horizontal, 6 * scale).padding(.vertical, 2 * scale)
                                .background(Color.black.opacity(0.55))
                                .clipShape(RoundedRectangle(cornerRadius: 8 * scale, style: .continuous))
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                AppText(gift.title, style: Type.caption, color: Color.white.opacity(0.9), lineLimit: 1, sizeOverride: 11)
                    .padding(.top, 4 * scale)
                HStack(spacing: 3 * scale) {
                    CoinGlyph(size: 9 * scale)
                    AppText(formatCoins(gift.coinPrice), style: Type.labelBold, color: Color(rgb: 0xFDE68A), sizeOverride: 11)
                }
            }
            .padding(.vertical, 8 * scale)
            .padding(.horizontal, 4 * scale)
            .frame(maxWidth: .infinity)
            .background(selected ? Color(rgb: 0x8B5CF6).opacity(0.2) : Color.white.opacity(0.035))
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                    .stroke(LinearGradient(colors: [Color(rgb: 0xA78BFA), Color(rgb: 0xF472B6)], startPoint: .topLeading, endPoint: .bottomTrailing),
                            lineWidth: selected ? 1.5 : 0)
            )
            .scaleEffect(selected ? 1.06 : 1)
            .opacity(locked ? 0.55 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: selected)
        }
        .buttonStyle(.plain)
    }
}

/// Send-again button: count in the middle, a ring that empties over 3 s.
private struct ComboButton: View {
    let count: Int
    let ring: CGFloat
    var onTap: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var pop: CGFloat = 1

    var body: some View {
        Button(action: onTap) {
            ZStack {
                Circle().fill(RadialGradient(colors: [Color(rgb: 0xF59E0B), Color(rgb: 0xEC4899)], center: .center, startRadius: 0, endRadius: 30 * scale))
                Circle().stroke(Color.white.opacity(0.25), lineWidth: 3).padding(3)
                Circle().trim(from: 0, to: ring).stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90)).padding(3)
                VStack(spacing: 0) {
                    AppText("×\(count)", style: Type.labelBold, color: .white, sizeOverride: 16)
                    AppText(Copy.t(.liveGiftCombo), style: Type.caption, color: Color.white.opacity(0.85), sizeOverride: 8)
                }
            }
            .frame(width: 58 * scale, height: 58 * scale)
            .scaleEffect(pop)
        }
        .buttonStyle(.plain)
        .onChange(of: count) { _ in
            pop = 1.18
            withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) { pop = 1 }
        }
    }
}

struct CoinGlyph: View {
    let size: CGFloat
    var body: some View {
        Circle()
            .fill(LinearGradient(colors: [Color(rgb: 0xFDE68A), Color(rgb: 0xF59E0B)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay(Circle().stroke(Color.white.opacity(0.4), lineWidth: 1))
            .frame(width: size, height: size)
    }
}

/// Top-rounded sheet shape.
struct RoundedCorners: Shape {
    let radius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}

func formatCoins(_ n: Int64) -> String {
    if n >= 1_000_000 { return String(format: "%.1fM", Double(n) / 1_000_000) }
    if n >= 100_000 { return "\(n / 1000)K" }
    let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = Locale(identifier: "en_US")
    return f.string(from: NSNumber(value: n)) ?? String(n)
}
