import SwiftUI

/// Reward eligibility for the pre-live sheet and the host HUD. One read when the
/// sheet opens (with the role/mode the host is about to use), and while on air a
/// re-read on every reward event plus a slow 60 s safety net — the HUD countdown
/// itself runs on the verified clock the room already ticks, never on polling.
@MainActor
final class LiveRewardModel: ObservableObject {
    @Published private(set) var eligibility: RewardEligibility?
    /// Reward toasts to show, in arrival order, each once per ledger id.
    @Published private(set) var toast: RewardToast?

    private var seen = Set<Int64>()
    private var queue: [RewardToast] = []
    private var token: LiveFeed.Token?
    private var refresher: Task<Void, Never>?
    private var toastTimer: Task<Void, Never>?
    private var isVideo = true
    private var live = false

    func start(isVideo: Bool, live: Bool) {
        self.isVideo = isVideo
        self.live = live
        Task { await fetch() }
        guard live else { return }
        token = LiveFeed.shared.listenRewards { [weak self] t in
            guard let self else { return }
            Task { @MainActor in
                self.enqueue(t)
                await self.fetch()
            }
        }
        refresher = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                await self?.fetch()
            }
        }
    }

    /// The host flipped Video/Audio on the setup sheet: the campaign may differ.
    func update(isVideo: Bool) {
        guard isVideo != self.isVideo else { return }
        self.isVideo = isVideo
        Task { await fetch() }
    }

    func stop() {
        if let t = token { LiveFeed.shared.stopListening(t) }
        token = nil
        refresher?.cancel(); refresher = nil
        toastTimer?.cancel(); toastTimer = nil
    }

    private func fetch() async {
        // Live: the server reads the open segment; the presumption only matters before.
        let r = await Xani.rewards.eligibility(role: live ? "" : "host", mode: live ? "" : (isVideo ? "video" : "audio"))
        if let v = r.value { eligibility = v }
    }

    private func enqueue(_ t: RewardToast) {
        guard t.ledgerId > 0, !seen.contains(t.ledgerId) else { return }
        seen.insert(t.ledgerId)
        if queue.count >= 8 { queue.removeFirst() }
        queue.append(t)
        if toast == nil { showNext() }
    }

    private func showNext() {
        guard !queue.isEmpty else { toast = nil; return }
        toast = queue.removeFirst()
        toastTimer?.cancel()
        toastTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 4_500_000_000)
            guard !Task.isCancelled else { return }
            self?.showNext()
        }
    }
}

/// Pre-live: what going live earns. Green with the next level and time when
/// eligible, amber with the reason when not, hidden when nothing is running.
struct RewardEligibilityChip: View {
    let el: RewardEligibility?
    @Environment(\.uiScale) private var scale

    var body: some View {
        if let el, el.enabled, !el.campaigns.isEmpty {
            let next = el.next
            let allDone = next == nil && el.campaigns.contains { $0.complete }
            let ok = next != nil || allDone
            let fg = ok ? Color(rgb: 0x15803D) : Color(rgb: 0xB45309)
            VStack(alignment: .leading, spacing: 4 * scale) {
                HStack(spacing: 6 * scale) {
                    Circle().fill(fg).frame(width: 7 * scale, height: 7 * scale)
                    AppText(headline(el, next: next, allDone: allDone), style: Type.labelBold, color: fg, lineLimit: 2, sizeOverride: 12)
                }
                if let n = next, n.levels.count > 1 {
                    AppText(n.levels.map { (l: RewardLevel) -> String in
                        let a = RewardFormat.amount(l.asset, l.amount)
                        return l.paid ? "✓ \(RewardFormat.short(l.thresholdMs)) \(a)" : "\(RewardFormat.short(l.thresholdMs)) \(a)"
                    }.joined(separator: "  ·  "), style: Type.micro, color: fg.opacity(0.8), lineLimit: 2, sizeOverride: 10.5)
                }
            }
            .padding(.horizontal, 12 * scale).padding(.vertical, 8 * scale)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fg.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
        }
    }

    private func headline(_ el: RewardEligibility, next: RewardCampaignStatus?, allDone: Bool) -> String {
        if let n = next {
            return Copy.t(.rewardsEligible) + " · " + Copy.t(.rewardsEligibleNext, ["amt": RewardFormat.amount(n.nextAsset, n.nextAmount), "t": RewardFormat.short(n.nextThresholdMs)])
        }
        if allDone { return Copy.t(.rewardsEligibleDone) }
        return Copy.t(.rewardsNotEligible) + " · " + notEligibleWhy(el.campaigns.first { !$0.eligible })
    }
}

/// The HUD line under the clock: "100 coins in 12:30" counting on the verified
/// clock, or "100 coins earned" once the level is crossed and the ack is pending.
func rewardHudText(_ el: RewardEligibility?, verifiedMs: Int64) -> String? {
    guard let el, el.enabled, let n = el.next else { return nil }
    let amt = RewardFormat.amount(n.nextAsset, n.nextAmount)
    // Server remaining, advanced by what the room clock verified since that read.
    let remaining = n.remainingMs - max(0, verifiedMs - n.verifiedMs)
    if remaining <= 0 { return Copy.t(.rewardsHudEarned, ["amt": amt]) }
    return Copy.t(.rewardsHudNext, ["amt": amt, "t": RewardFormat.countdown(remaining)])
}

/// One reward landing, over the host's picture: amount, status, and the balance move.
struct RewardToastView: View {
    let t: RewardToast
    @Environment(\.uiScale) private var scale

    var body: some View {
        let amt = RewardFormat.amount(t.asset, t.amount)
        let title: String = {
            switch t.status {
            case "claimable": return Copy.t(.rewardsToastClaimable, ["amt": amt])
            case "pending": return Copy.t(.rewardsToastPending, ["amt": amt])
            default:
                if let b = t.before, let a = t.after { return Copy.t(.rewardsToastAdded, ["amt": amt, "b": "\(b)", "a": "\(a)"]) }
                return "+\(amt)"
            }
        }()
        HStack(spacing: 10 * scale) {
            ZStack {
                Circle().fill(Color(rgb: 0xD97706)).frame(width: 26 * scale, height: 26 * scale)
                Circle().fill(Color(rgb: 0xFCD34D)).frame(width: 16 * scale, height: 16 * scale)
            }
            VStack(alignment: .leading, spacing: 1) {
                AppText(title, style: Type.labelBold, color: .white, lineLimit: 1, sizeOverride: 13)
                if !t.body.isEmpty {
                    AppText(t.body, style: Type.micro, color: .white.opacity(0.85), lineLimit: 2, sizeOverride: 11)
                }
            }
        }
        .padding(.horizontal, 14 * scale).padding(.vertical, 10 * scale)
        .background(Color(rgb: 0x1C1917).opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous).stroke(Color(rgb: 0xF59E0B).opacity(0.5), lineWidth: 1))
    }
}

/// Prefer the first failed condition (type + required vs yours); else the reason code.
func notEligibleWhy(_ c: RewardCampaignStatus?) -> String {
    guard let c else { return Copy.t(.rewardsNotEligibleNone) }
    if let failed = c.conditions.first(where: { !$0.pass }) {
        return Copy.t(.rewardsNotEligibleNeed, ["which": failedConditionWhich(failed)])
    }
    switch c.reason {
    case "ROLE_NOT_ALLOWED", "MODE_NOT_ALLOWED": return Copy.t(.rewardsNotEligibleRole)
    case "USER_ROLE": return Copy.t(.rewardsNotEligibleNeed, ["which": "platform role"])
    case "TIME_NOT_REACHED": return Copy.t(.rewardsNotEligibleTime, ["t": RewardFormat.short(max(0, c.remainingMs))])
    case "MAX_TOTAL", "MAX_PER_DAY", "MAX_PER_WEEK", "MAX_PER_MONTH", "MIN_INTERVAL": return Copy.t(.rewardsNotEligibleLimit)
    case "", "POLICY_DISABLED", "NO_CAMPAIGN": return Copy.t(.rewardsNotEligibleNone)
    default: return Copy.t(.rewardsNotEligibleCondition)
    }
}

func failedConditionWhich(_ c: RewardCondition) -> String {
    let need = prettyVal(c.required)
    let have = prettyVal(c.actual)
    let label: String = {
        switch c.type {
        case "USER_ROLE": return "platform role"
        case "LIVE_ROLE": return "live role"
        case "LIVE_MODE": return "live type"
        case "SESSION_TIME": return "session live time"
        case "ACCOUNT_AGE": return "account age (days)"
        case "RECEIVED_GIFT_VALUE": return "gift value received"
        case "RECEIVED_GIFT_COUNT": return "gifts received"
        case "UNIQUE_GIFTERS": return "different gifters"
        case "SENT_GIFT_VALUE": return "gift value sent"
        case "SENT_GIFT_COUNT": return "gifts sent"
        case "UNIQUE_RECIPIENTS": return "different recipients"
        case "QUALIFIED_DAYS": return "qualifying days"
        default: return c.type.lowercased().replacingOccurrences(of: "_", with: " ")
        }
    }()
    let needPart = need.isEmpty ? label : "\(label) \(need)"
    if have.isEmpty || have == need { return needPart }
    return "\(needPart) (yours: \(have))"
}

private func prettyVal(_ raw: String) -> String {
    var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if s.isEmpty || s == "null" || s == "[]" { return "" }
    if s.hasPrefix("[") { s = String(s.dropFirst().dropLast()) }
    return s.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\"'")) }.filter { !$0.isEmpty && $0 != "null" }.joined(separator: ", ")
}
