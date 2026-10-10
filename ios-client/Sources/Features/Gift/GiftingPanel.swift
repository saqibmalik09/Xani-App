import SwiftUI

/// A person's gifting statement: what they received (or, for yourself, sent)
/// this live / today / this week / this month / between two dates, as top
/// counterparts or latest gifts. Reads one statement per filter; frames that
/// name this person bump the totals at once and schedule a single re-read.
/// Dark over a broadcast, light on the profile.
struct GiftingPanel: View {
    let userId: Int64
    /// The room this card was opened in; > 0 adds and selects "This LIVE".
    var roomId: Int64 = 0
    var dark = true
    /// Height of the list body; the chrome above is intrinsic.
    var listHeight: CGFloat = 250

    @Environment(\.uiScale) private var scale
    @State private var period = "today"
    @State private var sort = "top"
    @State private var received = true
    @State private var fromText = ""
    @State private var toText = ""
    @State private var range: (String, String) = ("", "")
    @State private var statement: GiftingStatementData?
    @State private var loading = true
    @State private var error = ""
    @State private var isPrivate = false
    @State private var reload = 0
    @State private var tokens: [Int] = []
    @State private var feedToken: LiveFeed.Token?
    @State private var holder: Task<Void, Never>?
    @State private var reloadDebounce: Task<Void, Never>?

    private var me: Bool { userId == Xani.currentUserId }
    private var ink: Color { dark ? .white : Color(rgb: 0x1E0A50) }
    private var sub: Color { ink.opacity(dark ? 0.55 : 0.6) }
    private var chipBg: Color { dark ? Color.white.opacity(0.07) : Color(rgb: 0x6D28D9).opacity(0.07) }
    private var line: Color { dark ? Color.white.opacity(0.08) : Color(rgb: 0x6D28D9).opacity(0.08) }

    private struct Filter: Equatable { let period: String, sort: String, received: Bool, from: String, to: String, reload: Int }
    private var filter: Filter { Filter(period: period, sort: sort, received: received, from: range.0, to: range.1, reload: reload) }

    var body: some View {
        VStack(spacing: 0) {
            periodRow
            if period == "range" { dateRow }
            controlRow
            if let s = statement, !isPrivate { totals(s) }
            Rectangle().fill(line).frame(height: 1).padding(.horizontal, 16 * scale)
            list.frame(height: listHeight * scale)
        }
        .onAppear {
            if period == "today", roomId > 0 { period = "live" }
            let today = Self.isoDay(Date())
            let weekAgo = Self.isoDay(Date().addingTimeInterval(-6 * 86_400))
            if fromText.isEmpty { fromText = weekAgo; toText = today; range = (weekAgo, today) }
            // Room frames: a gift to / from this person updates the totals now.
            tokens.append(LiveSession.shared.listenGifts { ev in
                guard ev.id != 0, (received ? ev.receiver.id : ev.sender.id) == userId else { return }
                if var s = statement {
                    s.totals.coins += ev.totalCoins
                    s.totals.count += 1
                    s.totals.cashbackCoins += ev.cashbackCoins
                    if received, ev.receiverTodayDiamonds > 0, period == "today" { s.totals.diamonds = ev.receiverTodayDiamonds }
                    statement = s
                }
                scheduleReload()
            })
            if me {
                feedToken = LiveFeed.shared.listenGifting { ev in
                    if ev.received == received { scheduleReload() }
                }
                holder = Task { await LiveFeed.shared.hold() }
            }
        }
        .onDisappear {
            tokens.forEach { LiveSession.shared.stopListening($0) }; tokens.removeAll()
            if let t = feedToken { LiveFeed.shared.stopListening(t); feedToken = nil }
            holder?.cancel(); holder = nil
            reloadDebounce?.cancel(); reloadDebounce = nil
        }
        .task(id: filter) { await load() }
    }

    // MARK: - data

    private func load() async {
        loading = statement == nil
        let from = period == "range" ? range.0 : ""
        let to = period == "range" ? range.1 : ""
        let r: ApiResponse<GiftingStatementData>
        if received {
            r = await Xani.gifting.received(userId: userId, period: period, from: from, to: to, roomId: roomId, sort: sort)
        } else {
            r = await Xani.gifting.sent(userId: userId, period: period, from: from, to: to, roomId: roomId, sort: sort)
        }
        guard !Task.isCancelled else { return }
        if r.ok, let got = r.value {
            statement = got; error = ""; isPrivate = false
        } else if r.code == "gifting_private" {
            isPrivate = true; error = ""
        } else {
            error = r.message.isEmpty ? Copy.t(.giftingLoadFailed) : r.message
        }
        loading = false
    }

    /// Bursts (a lucky combo) collapse into one re-read.
    private func scheduleReload() {
        reloadDebounce?.cancel()
        reloadDebounce = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 600_000_000)
            guard !Task.isCancelled else { return }
            reload += 1
        }
    }

    private func applyRange() {
        guard let a = Self.parseDay(fromText), let b = Self.parseDay(toText) else {
            error = Copy.t(.giftingLoadFailed)
            return
        }
        error = ""
        range = a <= b ? (fromText.trimmingCharacters(in: .whitespaces), toText.trimmingCharacters(in: .whitespaces))
                       : (toText.trimmingCharacters(in: .whitespaces), fromText.trimmingCharacters(in: .whitespaces))
    }

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(secondsFromGMT: 0)
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    private static func isoDay(_ d: Date) -> String { dayFormatter.string(from: d) }
    private static func parseDay(_ s: String) -> Date? { dayFormatter.date(from: s.trimmingCharacters(in: .whitespaces)) }

    // MARK: - pieces

    private var periodRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8 * scale) {
                if roomId > 0 { chip(Copy.t(.giftingThisLive), period == "live") { period = "live" } }
                chip(Copy.t(.giftingToday), period == "today") { period = "today" }
                chip(Copy.t(.giftingWeek), period == "week") { period = "week" }
                chip(Copy.t(.giftingMonth), period == "month") { period = "month" }
                chip(Copy.t(.giftingDates), period == "range") { period = "range" }
            }
            .padding(.horizontal, 16 * scale)
        }
    }

    private var dateRow: some View {
        HStack(spacing: 8 * scale) {
            dateBox($fromText, Copy.t(.giftingFrom))
            dateBox($toText, Copy.t(.giftingTo))
            Button(action: applyRange) {
                AppText(Copy.t(.giftingShow), style: Type.labelBold, color: .white, sizeOverride: 12)
                    .padding(.horizontal, 14 * scale).padding(.vertical, 10 * scale)
                    .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.95))
        }
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 8 * scale)
    }

    private func dateBox(_ text: Binding<String>, _ hint: String) -> some View {
        TextField(hint, text: text)
            .font(.system(size: 13 * scale, weight: .semibold))
            .foregroundColor(ink)
            .keyboardType(.numbersAndPunctuation)
            .autocorrectionDisabled()
            .padding(.horizontal, 12 * scale).padding(.vertical, 9 * scale)
            .background(chipBg)
            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
            .frame(maxWidth: .infinity)
    }

    private var controlRow: some View {
        HStack(spacing: 8 * scale) {
            if me {
                toggle(Copy.t(.giftingReceived), received) { received = true }
                toggle(Copy.t(.giftingSent), !received) { received = false }
                Spacer(minLength: 0)
            }
            toggle(Copy.t(received ? .giftingTopSenders : .giftingTopReceivers), sort == "top") { sort = "top" }
            toggle(Copy.t(.giftingLatest), sort == "latest") { sort = "latest" }
            if !me { Spacer(minLength: 0) }
        }
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 8 * scale)
    }

    private func totals(_ s: GiftingStatementData) -> some View {
        HStack(spacing: 10 * scale) {
            if received {
                AppText("💎 " + formatCoins(s.totals.diamonds), style: Type.labelBold, color: ink, sizeOverride: 16)
            }
            HStack(spacing: 4 * scale) {
                CoinGlyph(size: 11 * scale)
                AppText(formatCoins(s.totals.coins), style: Type.labelBold, color: received ? sub : ink, sizeOverride: received ? 12 : 16)
            }
            AppText(Copy.t(.giftingGiftsN, ["n": formatCoins(s.totals.count)]), style: Type.caption, color: sub, sizeOverride: 12)
            if !received, s.totals.cashbackCoins > 0 {
                AppText(Copy.t(.giftingCashback, ["n": formatCoins(s.totals.cashbackCoins)]), style: Type.caption, color: Color(rgb: 0x34D399), sizeOverride: 12)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16 * scale)
        .padding(.bottom, 6 * scale)
    }

    @ViewBuilder private var list: some View {
        let s = statement
        if isPrivate {
            centered(Copy.t(.giftingPrivate))
        } else if !error.isEmpty, s == nil {
            VStack(spacing: 8 * scale) {
                AppText(error, style: Type.caption, color: sub, alignment: .center, sizeOverride: 13)
                chip(Copy.t(.giftingRetry), true) { reload += 1 }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if loading, s == nil {
            centered("…")
        } else if let s, sort == "top", !s.peers.isEmpty {
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    ForEach(s.peers) { p in
                        personLine(
                            p.peer, rank: p.rank,
                            headline: received ? "💎 " + formatCoins(p.diamonds) : formatCoins(p.coins),
                            caption: Copy.t(.giftingGiftsN, ["n": formatCoins(p.count)]) + " · " + Self.ago(p.lastAtMs),
                            giftUrl: p.listingUrl
                        )
                    }
                }
                .padding(.vertical, 6 * scale)
            }
        } else if let s, sort == "latest", !s.gifts.isEmpty {
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    ForEach(s.gifts) { g in
                        personLine(
                            received ? g.sender : g.receiver, rank: 0,
                            headline: received ? "💎 " + formatCoins(g.diamonds) : formatCoins(g.coins),
                            caption: Self.latestCaption(g),
                            giftUrl: g.listingUrl
                        )
                    }
                }
                .padding(.vertical, 6 * scale)
            }
        } else {
            centered(Copy.t(.giftingEmpty))
        }
    }

    private func personLine(_ p: LivePersonData, rank: Int, headline: String, caption: String, giftUrl: String) -> some View {
        Button(action: { if p.id > 0 { PersonRouter.shared.open(p.id) } }) {
            HStack(spacing: 10 * scale) {
                if rank > 0 {
                    AppText("\(rank)", style: Type.labelBold, color: rank <= 3 ? Color(rgb: 0xFDE68A) : sub, sizeOverride: 12)
                        .frame(width: 18 * scale)
                }
                PersonAvatar(id: p.id, name: p.displayName, avatarUrl: p.avatarUrl, size: 36 * scale, frameUrl: p.frameUrl, frameMime: p.frameMime)
                VStack(alignment: .leading, spacing: 2 * scale) {
                    AppText(p.displayName.isEmpty ? "…" : p.displayName, style: Type.labelBold, color: ink, lineLimit: 1, sizeOverride: 13)
                    AppText(caption, style: Type.caption, color: sub, lineLimit: 1, sizeOverride: 11)
                }
                Spacer(minLength: 6 * scale)
                if let u = giftImageURL(giftUrl) {
                    RemoteImage(url: u, contentMode: .fit, fallback: .clear).frame(width: 26 * scale, height: 26 * scale)
                }
                AppText(headline, style: Type.labelBold, color: ink, sizeOverride: 13)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 8 * scale)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.98))
    }

    private func centered(_ s: String) -> some View {
        AppText(s, style: Type.caption, color: sub, alignment: .center, sizeOverride: 13)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func chip(_ label: String, _ on: Bool, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            AppText(label, style: Type.labelBold, color: on ? .white : ink, sizeOverride: 12)
                .padding(.horizontal, 12 * scale).padding(.vertical, 7 * scale)
                .background {
                    if on { LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing) }
                    else { chipBg }
                }
                .clipShape(Capsule())
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.95))
    }

    private func toggle(_ label: String, _ on: Bool, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            AppText(label, style: Type.labelBold, color: on ? ink : sub, sizeOverride: 11)
                .padding(.horizontal, 10 * scale).padding(.vertical, 6 * scale)
                .background(on ? chipBg : .clear)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(on ? ink.opacity(0.25) : .clear, lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.95))
    }

    private static func latestCaption(_ g: GiftingRowData) -> String {
        var s = g.giftTitle
        if g.count > 1 { s += " ×\(g.count)" }
        if g.roomId != 0 { s += " · " + Copy.t(.giftingLive) }
        return s + " · " + ago(g.atMs)
    }

    /// "Just now" · "5m" · "3h" · "2d" from an epoch-ms stamp.
    static func ago(_ atMs: Int64) -> String {
        guard atMs > 0 else { return "" }
        let d = max(0, Int64(Date().timeIntervalSince1970 * 1000) - atMs) / 1000
        if d < 60 { return Copy.t(.inboxJustNow) }
        if d < 3600 { return "\(d / 60)m" }
        if d < 86_400 { return "\(d / 3600)h" }
        return "\(d / 86_400)d"
    }
}

/// Profile · Gifting segmented pill at the top of a person card in live.
struct CardTabs: View {
    @Binding var selected: Int
    var dark = true
    @Environment(\.uiScale) private var scale

    var body: some View {
        let ink: Color = dark ? .white : Color(rgb: 0x1E0A50)
        HStack(spacing: 4 * scale) {
            tab(Copy.t(.giftingTabProfile), 0, ink)
            tab(Copy.t(.giftingTabGifting), 1, ink)
        }
        .padding(3 * scale)
        .background(dark ? Color.white.opacity(0.07) : Color(rgb: 0x6D28D9).opacity(0.07))
        .clipShape(Capsule())
    }

    private func tab(_ label: String, _ i: Int, _ ink: Color) -> some View {
        let on = selected == i
        return Button(action: { withAnimation(.easeOut(duration: 0.18)) { selected = i } }) {
            AppText(label, style: Type.labelBold, color: on ? .white : ink.opacity(0.7), sizeOverride: 12)
                .padding(.horizontal, 16 * scale).padding(.vertical, 7 * scale)
                .background {
                    if on { LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing) }
                }
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// The person card in a live room: who they are and, on the second tab, what
/// they are receiving — this live first. Opens from the host pill or a chat name.
struct LivePersonSheet: View {
    let userId: Int64
    let roomId: Int64
    var isHost = false
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @ObservedObject private var follows = FollowStore.shared
    @State private var tab = 0
    @State private var person: PersonData?
    @State private var dragY: CGFloat = 0

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.35 * (1 - min(1, Double(max(0, dragY)) / 320)))
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
            VStack(spacing: 0) {
                Capsule().fill(Color.white.opacity(0.2)).frame(width: 36 * scale, height: 4 * scale).padding(.top, 10 * scale)
                CardTabs(selected: $tab).padding(.top, 10 * scale)
                if tab == 1 {
                    GiftingPanel(userId: userId, roomId: roomId)
                        .padding(.top, 10 * scale)
                        .padding(.bottom, 12 * scale)
                } else {
                    profileBody
                }
            }
            .padding(.bottom, max(insets.bottom, 8 * scale))
            .frame(maxWidth: .infinity)
            .background(LinearGradient(colors: [Color(rgb: 0x1B1430), Color(rgb: 0x110C21)], startPoint: .top, endPoint: .bottom))
            .clipShape(RoundedCorners(radius: 24 * scale))
            .overlay(RoundedCorners(radius: 24 * scale).stroke(Color(rgb: 0x8B5CF6).opacity(0.25), lineWidth: 1))
            .offset(y: max(0, dragY) + min(0, dragY) * 0.25)
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
        }
        .ignoresSafeArea(edges: .bottom)
        .task(id: userId) {
            let r = await Xani.people.get(userId)
            if let p = r.value { person = p; FollowStore.shared.learn([p]) }
        }
    }

    private var profileBody: some View {
        let p = person
        let name = p?.fullName.isEmpty == false ? p!.fullName : (p?.username ?? "…")
        let following = follows.relations[userId]?.following ?? p?.following ?? false
        return VStack(spacing: 10 * scale) {
            PersonAvatar(id: userId, name: name, avatarUrl: p?.avatarUrl ?? "", size: 72 * scale)
                .padding(.top, 16 * scale)
            VStack(spacing: 2 * scale) {
                HStack(spacing: 6 * scale) {
                    AppText(name, style: Type.labelBold, color: .white, lineLimit: 1, sizeOverride: 17)
                    if isHost {
                        AppText(Copy.t(.liveGiftHost), style: Type.labelBold, color: .white, sizeOverride: 9)
                            .padding(.horizontal, 6 * scale).padding(.vertical, 2 * scale)
                            .background(Color(rgb: 0x7C3AED)).clipShape(Capsule())
                    }
                }
                if let u = p?.username, !u.isEmpty {
                    AppText("@" + u, style: Type.caption, color: Color.white.opacity(0.55), sizeOverride: 12)
                }
            }
            if let b = p?.bio, !b.isEmpty {
                AppText(b, style: Type.caption, color: Color.white.opacity(0.75), alignment: .center, lineLimit: 3, sizeOverride: 12)
                    .padding(.horizontal, 24 * scale)
            }
            HStack(spacing: 18 * scale) {
                stat(FeedRepo.compact(Int64(p?.followers ?? 0)), Copy.t(.profileFollowers))
                stat(FeedRepo.compact(Int64(p?.followingCount ?? 0)), Copy.t(.profileFollowing))
            }
            .padding(.top, 2 * scale)
            HStack(spacing: 10 * scale) {
                if userId != Xani.currentUserId {
                    Button(action: { FollowStore.shared.toggle(userId) }) {
                        AppText(following ? Copy.t(.profileFollowingBtn) : Copy.t(.liveFollow), style: Type.labelBold, color: .white, sizeOverride: 13)
                            .frame(maxWidth: .infinity).frame(height: 40 * scale)
                            .background {
                                if following { Color.white.opacity(0.1) }
                                else { LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing) }
                            }
                            .clipShape(Capsule())
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                }
                Button(action: { onClose(); PersonRouter.shared.open(userId) }) {
                    AppText(Copy.t(.giftingViewProfile), style: Type.labelBold, color: .white, sizeOverride: 13)
                        .frame(maxWidth: .infinity).frame(height: 40 * scale)
                        .background(Color.white.opacity(0.08))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                        .clipShape(Capsule())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.97))
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, 6 * scale)
            .padding(.bottom, 14 * scale)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 1) {
            AppText(value, style: Type.labelBold, color: .white, sizeOverride: 15)
            AppText(label, style: Type.caption, color: Color.white.opacity(0.5), sizeOverride: 11)
        }
    }
}
