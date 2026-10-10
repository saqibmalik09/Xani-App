import SwiftUI

/// What the host's time on air earned. Today / Week / Month / All, each with
/// the live time counted in that range beside the rewards it produced, then
/// every business day with its rewards: when the required time was completed,
/// when the balance moved, from what to what. Every figure and time is the
/// server's, in the business timezone the admin set for live time — the same
/// ledger row support reads, so what the host sees here is final.
struct MyRewardsView: View {
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var range = "today"
    @State private var filter = ""
    @State private var summary = RewardSummary()
    @State private var claimable: [RewardClaimable] = []
    @State private var calendar = RewardCalendar()
    @State private var enabled = true
    @State private var groups: [RewardDayGroup] = []
    @State private var cursor = ""
    @State private var loading = true
    @State private var loadingMore = false
    @State private var error = ""
    @State private var detailId: Int64?
    @State private var toast = ""
    @State private var revision = 0
    @State private var token: LiveFeed.Token?

    private static let gold = Color(rgb: 0xD97706)

    var body: some View {
        ZStack {
            content
            if let id = detailId {
                RewardDetailSheet(id: id, zone: calendar.zone, onClose: { detailId = nil })
                    .transition(.move(edge: .bottom))
                    .zIndex(3)
            }
            if !toast.isEmpty {
                VStack {
                    Spacer()
                    AppText(toast, style: Type.labelBold, color: .white, sizeOverride: 13)
                        .padding(.horizontal, 16 * scale).padding(.vertical, 12 * scale)
                        .background(ProfileTokens.text)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                        .padding(.bottom, insets.bottom + 24 * scale)
                }
                .transition(.opacity)
                .zIndex(4)
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.24), value: detailId)
        .animation(.easeInOut(duration: 0.2), value: toast)
        .task(id: "\(range)|\(filter)|\(revision)") { await load() }
        // A reward landed on the hub while this screen is open: re-read, never patch.
        .onAppear { token = LiveFeed.shared.listenRewards { _ in revision += 1 } }
        .onDisappear {
            if let t = token { LiveFeed.shared.stopListening(t) }
            token = nil
        }
        .task(id: toast) {
            guard !toast.isEmpty else { return }
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            toast = ""
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(Copy.t(.rewardsTitle), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
                if loading { ProgressView().tint(ProfileTokens.accent) }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 12 * scale)

            // Today / Week / Month / All
            HStack(spacing: 0) {
                ForEach([("today", CopyKey.rewardsToday), ("week", .rewardsWeek), ("month", .rewardsMonth), ("all", .rewardsAll)], id: \.0) { k, label in
                    let on = range == k
                    Button(action: { if !on { range = k } }) {
                        AppText(Copy.t(label), style: on ? Type.labelBold : Type.label, color: on ? ProfileTokens.text : ProfileTokens.sub, sizeOverride: 13)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8 * scale)
                            .background(on ? ProfileTokens.bg : Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: 11 * scale, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3 * scale)
            .background(ProfileTokens.bg2)
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            .padding(.horizontal, 16 * scale)

            ScrollView {
                LazyVStack(spacing: 0) {
                    hero
                    ForEach(claimable.filter { $0.amount > 0 || $0.waitingAmount > 0 }) { c in
                        ClaimRow(c: c, zone: calendar.zone, onClaimed: { msg in toast = msg; revision += 1 }, onError: { error = $0 })
                    }
                    if !error.isEmpty {
                        HStack {
                            AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 12)
                            Spacer()
                            Button(action: { revision += 1 }) {
                                AppText(Copy.t(.rewardsRetry), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 11)
                                    .padding(.horizontal, 12 * scale).padding(.vertical, 6 * scale)
                                    .background(ProfileTokens.bg2)
                                    .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(12 * scale)
                        .background(ProfileTokens.dangerSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        .padding(.top, 12 * scale)
                    }

                    AppText(Copy.t(.rewardsByDay), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 18 * scale).padding(.bottom, 8 * scale).padding(.leading, 4 * scale)
                    filters

                    if !loading && days.isEmpty {
                        AppText(Copy.t(range == "all" && filter.isEmpty ? .rewardsEmpty : .rewardsEmptyRange), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.leading, 4 * scale).padding(.top, 4 * scale)
                    }
                    ForEach(days) { d in
                        dayHeader(d)
                        if d.rewards.isEmpty {
                            AppText(Copy.t(.rewardsNoRewardsDay), style: Type.caption, color: ProfileTokens.muted, sizeOverride: 12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.leading, 14 * scale).padding(.bottom, 10 * scale)
                        }
                        ForEach(d.rewards) { r in
                            RewardRowView(r: r, zone: calendar.zone) { detailId = r.id }
                        }
                    }
                    if !cursor.isEmpty {
                        Button(action: { Task { await loadMore() } }) {
                            Group {
                                if loadingMore { ProgressView().tint(ProfileTokens.accent) }
                                else { AppText(Copy.t(.rewardsOlder), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 13) }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12 * scale)
                            .background(ProfileTokens.bg2)
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(loadingMore)
                        .padding(.top, 10 * scale)
                    }
                    AppText(Copy.t(.rewardsDayNote, ["start": calendar.dayStart, "tz": calendar.tz]), style: Type.micro, color: ProfileTokens.muted, sizeOverride: 11)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 4 * scale).padding(.top, 16 * scale)
                }
                .padding(.horizontal, 16 * scale)
                .padding(.top, 12 * scale)
                .padding(.bottom, 32 * scale + insets.bottom)
            }
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            HStack {
                AppText(Copy.t(.rewardsAdded), style: Type.caption, color: .white.opacity(0.85), sizeOverride: 12)
                Spacer()
                HStack(spacing: 6 * scale) {
                    AppText(Copy.t(.rewardsLiveTime), style: Type.micro, color: .white.opacity(0.85), sizeOverride: 10)
                    AppText(RewardFormat.clock(summary.liveMs, seconds: true), style: Type.labelBold, color: .white, sizeOverride: 12)
                }
                .padding(.horizontal, 10 * scale).padding(.vertical, 5 * scale)
                .background(Color.white.opacity(0.18))
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
            }
            AppText(summary.added.isEmpty ? "0" : summary.added.text, style: Type.h1, color: .white, lineLimit: 1, sizeOverride: 32)
            let sub: [String] = {
                var s: [String] = []
                if !summary.pending.isEmpty { s.append("\(Copy.t(.rewardsPending)) \(summary.pending.text)") }
                if !summary.claimable.isEmpty { s.append("\(Copy.t(.rewardsClaimable)) \(summary.claimable.text)") }
                if !enabled { s.append(Copy.t(.rewardsOff)) }
                return s
            }()
            if !sub.isEmpty {
                AppText(sub.joined(separator: " · "), style: Type.micro, color: .white.opacity(0.85), lineLimit: 2, sizeOverride: 11)
            }
        }
        .padding(18 * scale)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinearGradient(colors: [Self.gold, Color(rgb: 0xF59E0B)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
    }

    private var filters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6 * scale) {
                ForEach([("", CopyKey.rewardsFilterAll), ("earned", .rewardsStatusPending), ("issued", .rewardsStatusAdded), ("claimable", .rewardsStatusClaimable), ("claimed", .rewardsStatusClaimed), ("expired", .rewardsStatusExpired)], id: \.0) { k, label in
                    let on = filter == k
                    Button(action: { filter = k }) {
                        AppText(Copy.t(label), style: Type.labelBold, color: on ? .white : ProfileTokens.sub, sizeOverride: 11)
                            .padding(.horizontal, 12 * scale).padding(.vertical, 6 * scale)
                            .background(on ? ProfileTokens.text : ProfileTokens.bg2)
                            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.bottom, 8 * scale)
    }

    private func dayHeader(_ d: DayCard) -> some View {
        HStack(spacing: 8 * scale) {
            AppText(RewardFormat.dayLabel(d.day, serverLabel: d.label, today: calendar.today), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
            Spacer()
            if d.liveMs > 0 {
                AppText("\(Copy.t(.rewardsLiveTime)) \(RewardFormat.clock(d.liveMs, seconds: true))", style: Type.caption, color: ProfileTokens.sub, sizeOverride: 11)
            }
            if !d.totals.isEmpty {
                AppText("+\(d.totals.text)", style: Type.labelBold, color: Self.gold, sizeOverride: 12)
            }
        }
        .padding(.top, 10 * scale).padding(.bottom, 6 * scale).padding(.horizontal, 4 * scale)
    }

    /// A business day on the list: live time, totals, the rewards (possibly none).
    private struct DayCard: Identifiable {
        let day: String, label: String
        var liveMs: Int64
        let totals: RewardAmounts
        let rewards: [RewardLedger]
        var id: String { day }
    }

    /// Days with a reward row ∪ days with live time but no reward (so a host sees
    /// "you were live 20m, no level reached" rather than a hole). Newest first.
    private var days: [DayCard] {
        var byDay: [String: DayCard] = [:]
        for g in groups { byDay[g.day] = DayCard(day: g.day, label: g.label, liveMs: g.liveMs, totals: g.totals, rewards: g.rewards) }
        if filter.isEmpty {
            for d in summary.days {
                if var cur = byDay[d.day] {
                    if cur.liveMs == 0 && d.liveMs > 0 { cur.liveMs = d.liveMs; byDay[d.day] = cur }
                } else {
                    byDay[d.day] = DayCard(day: d.day, label: d.label, liveMs: d.liveMs, totals: d.added, rewards: [])
                }
            }
        }
        return byDay.values.sorted { $0.day > $1.day }
    }

    private func load() async {
        loading = true
        let home = await Xani.rewards.me(range: range)
        guard let h = home.value else {
            error = home.message.isEmpty ? Copy.t(.rewardsLoadFailed) : home.message
            loading = false
            return
        }
        summary = h.summary; claimable = h.claimable; calendar = h.calendar; enabled = h.enabled; error = ""
        // "All" is keyset-paged with no date bound; the others are bounded by the range.
        let bounded = range != "all"
        let hist = await Xani.rewards.history(from: bounded ? h.summary.from : "", to: bounded ? h.summary.to : "", status: filter)
        if let g = hist.value { groups = g.days; cursor = g.cursor } else { error = hist.message }
        loading = false
    }

    private func loadMore() async {
        guard !cursor.isEmpty, !loadingMore else { return }
        loadingMore = true
        let bounded = range != "all"
        let r = await Xani.rewards.history(from: bounded ? summary.from : "", to: bounded ? summary.to : "", status: filter, cursor: cursor)
        if let g = r.value {
            // Merge by day: the page boundary can split one day across two pages.
            var merged: [String: RewardDayGroup] = [:]
            var order: [String] = []
            for d in groups { merged[d.day] = d; order.append(d.day) }
            for d in g.days {
                if var prev = merged[d.day] {
                    prev.rewards += d.rewards.filter { n in !prev.rewards.contains { $0.id == n.id } }
                    merged[d.day] = prev
                } else {
                    merged[d.day] = d; order.append(d.day)
                }
            }
            groups = order.compactMap { merged[$0] }
            cursor = g.cursor
        }
        loadingMore = false
    }
}

/// One asset's claim button. The idempotency key is minted on the first tap and
/// kept until the server answers OK: a retry after a dropped reply gets the
/// first result back, never a second payment.
private struct ClaimRow: View {
    let c: RewardClaimable
    let zone: TimeZone
    var onClaimed: (String) -> Void
    var onError: (String) -> Void

    @Environment(\.uiScale) private var scale
    @State private var idem = ""
    @State private var busy = false

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2 * scale) {
                if c.amount > 0 {
                    AppText("\(Copy.t(.rewardsClaimable)) · \(RewardFormat.amount(c.asset, c.amount))", style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 13)
                }
                if c.waitingAmount > 0 {
                    let opens = c.nextOpensAt > 0 ? " · " + Copy.t(.rewardsClaimOpens, ["t": RewardFormat.dayTime(c.nextOpensAt, zone: zone)]) : ""
                    AppText(Copy.t(.rewardsClaimWaiting, ["amt": RewardFormat.amount(c.asset, c.waitingAmount)]) + opens, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 11)
                }
            }
            Spacer()
            if c.amount > 0 {
                Button(action: { Task { await claim() } }) {
                    Group {
                        if busy { ProgressView().tint(.white) }
                        else { AppText(Copy.t(.rewardsClaim, ["amt": RewardFormat.amount(c.asset, c.amount)]), style: Type.labelBold, color: .white, sizeOverride: 12) }
                    }
                    .padding(.horizontal, 14 * scale).padding(.vertical, 8 * scale)
                    .background(ProfileTokens.text)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
        }
        .padding(.horizontal, 14 * scale).padding(.vertical, 12 * scale)
        .background(ProfileTokens.bg2)
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .padding(.top, 10 * scale)
    }

    private func claim() async {
        if idem.isEmpty { idem = UUID().uuidString.replacingOccurrences(of: "-", with: "") }
        busy = true
        let r = await Xani.rewards.claim(asset: c.asset, idemKey: idem)
        if let got = r.value {
            idem = ""
            onClaimed(Copy.t(.rewardsClaimed, ["amt": RewardFormat.amount(got.asset, got.amount)]))
        } else {
            onError(r.message)
        }
        busy = false
    }
}

private struct RewardRowView: View {
    let r: RewardLedger
    let zone: TimeZone
    var onTap: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10 * scale) {
                RewardAssetGlyph(asset: r.asset)
                    .frame(width: 36 * scale, height: 36 * scale)
                    .background(Color(rgb: 0xF59E0B).opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 11 * scale, style: .continuous))
                VStack(alignment: .leading, spacing: 2 * scale) {
                    HStack(spacing: 8 * scale) {
                        AppText((r.amount >= 0 ? "+" : "") + RewardFormat.amount(r.asset, r.amount), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
                        RewardStatusPill(status: r.status)
                    }
                    AppText(line, style: Type.caption, color: ProfileTokens.sub, lineLimit: 1, sizeOverride: 11)
                    if !sub.isEmpty {
                        AppText(sub, style: Type.micro, color: ProfileTokens.muted, lineLimit: 1, sizeOverride: 10.5)
                    }
                }
                Spacer(minLength: 6 * scale)
                VectorCanvas([.data("M9 6l6 6-6 6", .stroke(Color(rgb: 0xBBBBBB), 2))], viewport: 24, side: 15 * scale)
            }
            .padding(.horizontal, 14 * scale).padding(.vertical, 12 * scale)
            .background(ProfileTokens.bg2)
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            .padding(.bottom, 6 * scale)
        }
        .buttonStyle(.plain)
    }

    private var line: String {
        if r.kind == "earn" {
            var s = "\(Copy.t(.rewardsCompletedAt)) \(RewardFormat.time(r.reachedAt, zone: zone))"
            if r.issuedAt > 0 { s += " · \(Copy.t(.rewardsAddedAt)) \(RewardFormat.time(r.issuedAt, zone: zone))" }
            return s
        }
        var s = r.message.title.isEmpty ? r.kind : r.message.title
        if r.issuedAt > 0 { s += " · " + RewardFormat.time(r.issuedAt, zone: zone) }
        return s
    }

    private var sub: String {
        var parts: [String] = []
        if !r.campaign.isEmpty { parts.append(r.campaign) }
        if r.kind == "earn" { parts.append(RewardFormat.clock(r.requiredMs)) }
        if r.role == "cohost" { parts.append(Copy.t(.rewardsCohost)) }
        if let b = r.balanceBefore, let a = r.balanceAfter { parts.append("\(b) → \(a)") }
        return parts.joined(separator: " · ")
    }
}

private struct RewardAssetGlyph: View {
    let asset: String
    @Environment(\.uiScale) private var scale
    var body: some View {
        switch asset {
        case "diamonds":
            VectorCanvas([.data("M6 3h12l4 6-10 12L2 9l4-6z", .stroke(Color(rgb: 0x3B82F6), 1.8))], viewport: 24, side: 18 * scale)
        case "beans":
            VectorCanvas([.data("M7 4c4 0 8 3 8 8s-3 8-7 8-6-3-6-7 2-9 5-9z", .stroke(Color(rgb: 0x16A34A), 1.8))], viewport: 24, side: 18 * scale)
        default:
            ZStack {
                Circle().fill(Color(rgb: 0xD97706)).frame(width: 16 * scale, height: 16 * scale)
                Circle().fill(Color(rgb: 0xFCD34D)).frame(width: 10 * scale, height: 10 * scale)
            }
        }
    }
}

struct RewardStatusPill: View {
    let status: String
    @Environment(\.uiScale) private var scale
    var body: some View {
        let (bg, fg, key): (Color, Color, CopyKey) = {
            switch status {
            case "added": return (Color(rgb: 0x16A34A).opacity(0.08), Color(rgb: 0x15803D), .rewardsStatusAdded)
            case "claimed": return (Color(rgb: 0x16A34A).opacity(0.08), Color(rgb: 0x15803D), .rewardsStatusClaimed)
            case "claimable": return (Color(rgb: 0x3B82F6).opacity(0.08), Color(rgb: 0x1D4ED8), .rewardsStatusClaimable)
            case "pending": return (Color(rgb: 0xF59E0B).opacity(0.08), Color(rgb: 0xB45309), .rewardsStatusPending)
            case "expired": return (Color(rgb: 0x777777).opacity(0.08), Color(rgb: 0x666666), .rewardsStatusExpired)
            default: return (Color(rgb: 0x777777).opacity(0.08), Color(rgb: 0x666666), .rewardsStatusCancelled)
            }
        }()
        AppText(Copy.t(key), style: Type.micro, color: fg, sizeOverride: 10)
            .padding(.horizontal, 7 * scale).padding(.vertical, 2 * scale)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: 8 * scale, style: .continuous))
    }
}

/// One reward in full: reward day, completed-at, added-at, the balance move and the
/// timeline, every time in the business timezone so host and support read one clock.
private struct RewardDetailSheet: View {
    let id: Int64
    let zone: TimeZone
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var detail: RewardDetail?
    @State private var error = ""

    var body: some View {
        ZStack(alignment: .bottom) {
            ProfileTokens.sheetScrim.ignoresSafeArea().onTapGesture(perform: onClose)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    AppText(Copy.t(.rewardsDetail), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                    Spacer()
                    Button(action: onClose) {
                        VectorCanvas([.data("M6 6l12 12M18 6L6 18", .stroke(ProfileTokens.text, 2))], viewport: 24, side: 14 * scale)
                            .frame(width: 30 * scale, height: 30 * scale)
                            .background(ProfileTokens.bg2)
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                if let d = detail {
                    ScrollView { detailBody(d) }
                } else if error.isEmpty {
                    ProgressView().tint(ProfileTokens.accent).frame(maxWidth: .infinity).padding(24 * scale)
                } else {
                    AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 12).padding(.vertical, 12 * scale)
                }
            }
            .padding(.horizontal, 18 * scale).padding(.top, 16 * scale)
            .padding(.bottom, insets.bottom + 16 * scale)
            .frame(maxWidth: .infinity)
            .frame(maxHeight: UIScreen.main.bounds.height * 0.8)
            .background(ProfileTokens.bg)
            .clipShape(RoundedRectangle(cornerRadius: 22 * scale, style: .continuous))
        }
        .task(id: id) {
            let r = await Xani.rewards.detail(id)
            detail = r.value
            if r.value == nil { error = r.message }
        }
    }

    @ViewBuilder
    private func detailBody(_ d: RewardDetail) -> some View {
        let r = d.reward
        let z = d.calendar.zone
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10 * scale) {
                AppText((r.amount >= 0 ? "+" : "") + RewardFormat.amount(r.asset, r.amount), style: Type.h1, color: ProfileTokens.text, sizeOverride: 30)
                RewardStatusPill(status: r.status)
            }
            .padding(.top, 10 * scale)
            if !r.message.body.isEmpty {
                AppText(r.message.body, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12).padding(.top, 4 * scale)
            }
            VStack(spacing: 0) {
                fact(Copy.t(.rewardsDay), RewardFormat.prettyDay(r.day))
                if !r.campaign.isEmpty { fact("·", r.campaign) }
                if r.kind == "earn" {
                    fact(Copy.t(.rewardsRequired), RewardFormat.clock(r.requiredMs, seconds: true))
                    fact(Copy.t(.rewardsVerified), RewardFormat.clock(r.verifiedMs, seconds: true))
                    fact(Copy.t(.rewardsCompletedAt), RewardFormat.full(r.reachedAt, zone: z))
                }
                if r.issuedAt > 0 { fact(Copy.t(.rewardsAddedAt), RewardFormat.full(r.issuedAt, zone: z)) }
                if r.claimedAt > 0 { fact(Copy.t(.rewardsStatusClaimed), RewardFormat.full(r.claimedAt, zone: z)) }
                if let b = r.balanceBefore, let a = r.balanceAfter { fact(Copy.t(.rewardsBalance), "\(b) → \(a)") }
                fact(Copy.t(.rewardsRole), Copy.t(r.role == "cohost" ? .rewardsCohost : .rewardsHost))
                if !r.note.isEmpty { fact("·", r.note) }
            }
            .padding(12 * scale)
            .background(ProfileTokens.bg2)
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            .padding(.top, 14 * scale)
            if !d.timeline.isEmpty {
                AppText(Copy.t(.rewardsTimeline), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14).padding(.top, 14 * scale)
                ForEach(d.timeline) { s in
                    HStack(alignment: .top, spacing: 10 * scale) {
                        Circle().fill(Color(rgb: 0xD97706)).frame(width: 7 * scale, height: 7 * scale).padding(.top, 5 * scale)
                        VStack(alignment: .leading, spacing: 1 * scale) {
                            AppText(s.what, style: Type.label, color: ProfileTokens.text, sizeOverride: 13)
                            AppText(s.local.isEmpty ? RewardFormat.full(s.at, zone: z) : s.local, style: Type.micro, color: ProfileTokens.sub, sizeOverride: 11)
                        }
                    }
                    .padding(.vertical, 5 * scale)
                }
            }
            AppText(Copy.t(.rewardsDayNote, ["start": d.calendar.dayStart, "tz": d.calendar.tz]), style: Type.micro, color: ProfileTokens.muted, sizeOverride: 10.5)
                .padding(.top, 10 * scale)
        }
    }

    private func fact(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            AppText(label, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12).frame(width: 110 * scale, alignment: .leading)
            AppText(value, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 12.5)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4 * scale)
    }
}

/// Formatting shared by the rewards surfaces. Times are rendered in the business
/// timezone the server named, never the phone's.
enum RewardFormat {
    static func amount(_ asset: String, _ amount: Int64) -> String { "\(amount) \(asset)" }

    /// `h:mm:ss` with seconds, else `Xh YYm` / `Xm`.
    static func clock(_ ms: Int64, seconds: Bool = false) -> String {
        let s = max(0, ms / 1000)
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        if seconds { return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%02d:%02d", m, sec) }
        return h > 0 ? String(format: "%dh %02dm", h, m) : "\(m)m"
    }

    /// "30m" / "1h 30m" for thresholds.
    static func short(_ ms: Int64) -> String {
        let m = max(0, ms / 60_000), h = m / 60
        if h > 0 { return m % 60 == 0 ? "\(h)h" : "\(h)h \(m % 60)m" }
        return "\(m)m"
    }

    /// "12:30" / "1:02:05" for a countdown.
    static func countdown(_ ms: Int64) -> String {
        let s = (max(0, ms) + 999) / 1000
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%d:%02d", m, sec)
    }

    static func time(_ t: TimeInterval, zone: TimeZone) -> String {
        guard t > 0 else { return "—" }
        let f = DateFormatter(); f.timeZone = zone; f.dateFormat = "HH:mm:ss"
        return f.string(from: Date(timeIntervalSince1970: t))
    }

    static func full(_ t: TimeInterval, zone: TimeZone) -> String {
        guard t > 0 else { return "—" }
        let f = DateFormatter(); f.timeZone = zone; f.dateFormat = "EEE, d MMM · HH:mm:ss"
        return f.string(from: Date(timeIntervalSince1970: t))
    }

    static func dayTime(_ t: TimeInterval, zone: TimeZone) -> String {
        guard t > 0 else { return "—" }
        let f = DateFormatter(); f.timeZone = zone; f.dateFormat = "d MMM HH:mm"
        return f.string(from: Date(timeIntervalSince1970: t))
    }

    private static let dayIn: DateFormatter = {
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "yyyy-MM-dd"; return f
    }()

    /// "Today" or "Fri, 3 Oct" for a business day.
    static func dayLabel(_ day: String, serverLabel: String, today: String) -> String {
        if day == today { return Copy.t(.rewardsToday) }
        guard let d = dayIn.date(from: day) else { return serverLabel.isEmpty ? day : serverLabel }
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "EEE, d MMM"
        return f.string(from: d)
    }

    static func prettyDay(_ day: String) -> String {
        guard let d = dayIn.date(from: day) else { return day }
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "EEE, d MMM yyyy"
        return f.string(from: d)
    }
}
