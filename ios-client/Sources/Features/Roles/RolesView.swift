import SwiftUI
import UIKit

/// Organisation roles for the signed-in account: the roles they hold (with upline
/// and downline), referral codes they can share, "join with a code", requests they
/// made, and — for anyone who can approve — the approvals inbox.
///
/// Nested navigation lives here so `ProfileRoute` only grows by one case.
struct RolesView: View {
    var onBack: () -> Void
    /// Request to land on when opened from a push / inbox notice; 0 = home.
    var startRequestId: Int64 = 0

    private enum Tab: Int, CaseIterable {
        case mine, requests, approvals
    }

    private enum Page: Equatable {
        case membership(Int64)
        case request(Int64)
    }

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @ObservedObject private var session = Xani.session

    @State private var tab: Tab = .mine
    @State private var stack: [Page] = []
    @State private var pendingCount = 0
    @State private var openedStart: Int64 = 0

    private var canApprove: Bool {
        let a = session.currentAccess
        return a.holdsAnywhere("requests.approve") || a.holdsAnywhere("requests.view")
    }

    var body: some View {
        ZStack {
            home
            if let top = stack.last {
                switch top {
                case .membership(let id):
                    RolesMembershipDetail(membershipId: id, onBack: pop, onOpenMembership: { push(.membership($0)) })
                        .transition(.move(edge: .trailing))
                        .zIndex(2)
                case .request(let id):
                    RolesRequestDetail(requestId: id, onBack: pop, onChanged: { Task { await refreshCount() } })
                        .transition(.move(edge: .trailing))
                        .zIndex(3)
                }
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.28), value: stack.count)
        .onAppear { openStart(startRequestId) }
        .onChange(of: startRequestId) { id in openStart(id) }
        .task { await refreshCount() }
    }

    private var home: some View {
        VStack(spacing: 0) {
            RolesBar(title: Copy.t(.rolesTitle), back: onBack)
            tabs
            Group {
                switch tab {
                case .mine:
                    RolesMineTab(onOpenMembership: { push(.membership($0)) }, onOpenRequest: { push(.request($0)) })
                case .requests:
                    RolesRequestList(mode: .mine, onOpen: { push(.request($0)) })
                case .approvals:
                    RolesRequestList(mode: .inbox, onOpen: { push(.request($0)) })
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.top, insets.top)
    }

    private var tabs: some View {
        HStack(spacing: 8 * scale) {
            RolesTabChip(label: Copy.t(.rolesTabMine), selected: tab == .mine) { tab = .mine }
            RolesTabChip(label: Copy.t(.rolesTabRequests), selected: tab == .requests) { tab = .requests }
            if canApprove {
                RolesTabChip(label: Copy.t(.rolesTabApprovals), selected: tab == .approvals, badge: pendingCount) { tab = .approvals }
            }
            Spacer()
        }
        .padding(.horizontal, 20 * scale)
        .padding(.bottom, 10 * scale)
    }

    private func push(_ p: Page) { stack.append(p) }
    private func pop() { _ = stack.popLast() }

    private func openStart(_ id: Int64) {
        guard id > 0, id != openedStart else { return }
        openedStart = id
        stack = [.request(id)]
    }

    private func refreshCount() async {
        guard canApprove else { return }
        let r = await Xani.roles.inboxCount()
        if let n = r.value { pendingCount = n }
    }
}

// MARK: - Mine

private struct RolesMineTab: View {
    var onOpenMembership: (Int64) -> Void
    var onOpenRequest: (Int64) -> Void

    @Environment(\.uiScale) private var scale
    @State private var memberships: [MembershipData] = []
    @State private var codes: [ReferralCodeData] = []
    @State private var loading = true
    @State private var failed = false
    @State private var toast = ""

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if loading && memberships.isEmpty {
                        RolesLoading()
                    } else if failed && memberships.isEmpty {
                        RolesFailed { Task { await load() } }
                    } else if memberships.isEmpty {
                        AppText(Copy.t(.rolesMineEmpty), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 14)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 16 * scale)
                    } else {
                        ForEach(memberships) { m in
                            RolesMembershipRow(m: m, showPerson: false) { onOpenMembership(m.id) }
                        }
                    }

                    if !codes.isEmpty {
                        RolesSectionTitle(Copy.t(.rolesMyCodes))
                        ForEach(codes) { c in
                            codeRow(c)
                        }
                    }

                    RolesSectionTitle(Copy.t(.rolesJoinTitle))
                    RolesJoinWithCode(onSent: { req in
                        toast = Copy.t(.rolesApplied)
                        onOpenRequest(req.id)
                    })
                    .padding(.horizontal, 20 * scale)
                    .padding(.bottom, 32 * scale)
                }
            }
            .refreshable { await load() }

            if !toast.isEmpty {
                AppText(toast, style: Type.caption, color: .white, sizeOverride: 13)
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(Color.black.opacity(0.8))
                    .clipShape(Capsule())
                    .padding(.bottom, 24 * scale)
                    .transition(.opacity)
                    .task {
                        try? await Task.sleep(nanoseconds: 1_800_000_000)
                        toast = ""
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: toast)
        .task { await load() }
    }

    private func codeRow(_ c: ReferralCodeData) -> some View {
        HStack(spacing: 12 * scale) {
            VStack(alignment: .leading, spacing: 3 * scale) {
                AppText(c.code, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                    .textSelection(.enabled)
                let names = c.targetRoles.map(\.title).filter { !$0.isEmpty }.joined(separator: ", ")
                if !names.isEmpty {
                    AppText(Copy.t(.rolesCodeOpens, ["roles": names]), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                }
            }
            Spacer()
            RolesSmallButton(label: Copy.t(.profileCopy)) {
                UIPasteboard.general.string = c.code
                toast = Copy.t(.rolesCopied)
            }
            RolesSmallButton(label: Copy.t(.rolesShareCode), filled: true) {
                share(c.code)
            }
        }
        .padding(.horizontal, 20 * scale)
        .padding(.vertical, 10 * scale)
    }

    private func share(_ code: String) {
        let ac = UIActivityViewController(activityItems: [code], applicationActivities: nil)
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first(where: \.isKeyWindow)?.rootViewController else { return }
        var top = root
        while let shown = top.presentedViewController { top = shown }
        top.present(ac, animated: true)
    }

    private func load() async {
        loading = true
        async let m = Xani.roles.myMemberships()
        async let c = Xani.roles.myCodes()
        let (mr, cr) = await (m, c)
        if let v = mr.value {
            memberships = v
            failed = false
        } else {
            failed = memberships.isEmpty
        }
        if let v = cr.value { codes = v }
        loading = false
    }
}

// MARK: - Join with a code

private struct RolesJoinWithCode: View {
    var onSent: (RoleRequestData) -> Void

    @Environment(\.uiScale) private var scale
    @State private var code = ""
    @State private var note = ""
    @State private var info: CodeInfoData?
    @State private var lookedUp = ""
    @State private var roleSlug = ""
    @State private var checking = false
    @State private var sending = false
    @State private var error = ""
    @State private var lookupTask: Task<Void, Never>?

    private var normalized: String { code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
    private var canSend: Bool {
        guard let info, info.code == lookedUp, !sending else { return false }
        return info.targetRoles.count <= 1 || !roleSlug.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10 * scale) {
            AppText(Copy.t(.rolesJoinHint), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)

            HStack(spacing: 10 * scale) {
                TextField(Copy.t(.rolesCodePlaceholder), text: $code)
                    .font(.system(size: 15 * scale, weight: .semibold, design: .monospaced))
                    .textInputAutocapitalization(.characters)
                    .disableAutocorrection(true)
                    .padding(12 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                    .onChange(of: code) { _ in scheduleLookup() }
                if checking {
                    ProgressView().frame(width: 20 * scale, height: 20 * scale)
                }
            }

            if let info, info.code == lookedUp {
                AppText(
                    Copy.t(.rolesCodeOwner, ["name": info.owner.displayName, "role": info.ownerRole.title]),
                    style: Type.caption, color: ProfileTokens.text, sizeOverride: 13
                )
                if info.targetRoles.count > 1 {
                    AppText(Copy.t(.rolesPickRole), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 12)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8 * scale) {
                            ForEach(info.targetRoles, id: \.slug) { r in
                                RolesTabChip(label: r.title, selected: roleSlug == r.slug) { roleSlug = r.slug }
                            }
                        }
                    }
                }
                TextField(Copy.t(.rolesNotePlaceholder), text: $note)
                    .font(.system(size: 14 * scale))
                    .padding(12 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
            }

            if !error.isEmpty {
                AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 13)
            }

            Button(action: send) {
                AppText(Copy.t(.rolesApply), style: Type.labelBold, color: .white, sizeOverride: 15)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13 * scale)
                    .background(canSend ? AnyShapeStyle(ProfileTokens.cta) : AnyShapeStyle(ProfileTokens.muted))
                    .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.97))
            .disabled(!canSend)
        }
    }

    private func scheduleLookup() {
        lookupTask?.cancel()
        error = ""
        let c = normalized
        guard c.count >= 4 else {
            info = nil
            lookedUp = ""
            return
        }
        lookupTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }
            checking = true
            let r = await Xani.roles.lookupCode(c)
            if Task.isCancelled { return }
            checking = false
            if let v = r.value {
                info = v
                lookedUp = v.code
                roleSlug = v.targetRoles.count == 1 ? (v.targetRoles.first?.slug ?? "") : ""
            } else {
                info = nil
                lookedUp = ""
                if !r.message.isEmpty { error = r.message }
            }
        }
    }

    private func send() {
        guard canSend, let info else { return }
        sending = true
        error = ""
        Task {
            let r: ApiResponse<RoleRequestData>
            if info.targetRoles.count > 1 {
                r = await Xani.roles.apply(code: info.code, roleSlug: roleSlug, note: note)
            } else {
                r = await Xani.roles.redeem(code: info.code, note: note)
            }
            sending = false
            if let v = r.value {
                code = ""
                note = ""
                self.info = nil
                lookedUp = ""
                onSent(v)
            } else {
                error = r.message.isEmpty ? Copy.t(.rolesLoadFailed) : r.message
            }
        }
    }
}

// MARK: - Membership detail (upline + downline)

private struct RolesMembershipDetail: View {
    let membershipId: Int64
    var onBack: () -> Void
    var onOpenMembership: (Int64) -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var membership: MembershipData?
    @State private var upline: [MembershipData] = []
    @State private var summary: [DownlineSummaryRow] = []
    @State private var downline: [MembershipData] = []
    @State private var next: String?
    @State private var depth = 1
    @State private var loading = true
    @State private var paging = false
    @State private var failed = false

    var body: some View {
        VStack(spacing: 0) {
            RolesBar(title: membership?.role.title ?? Copy.t(.rolesTitle), back: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if let m = membership {
                        header(m)
                        RolesSectionTitle(Copy.t(.rolesUpline))
                        if upline.isEmpty {
                            AppText(Copy.t(.rolesTopOfTree), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                                .padding(.horizontal, 20 * scale)
                                .padding(.vertical, 8 * scale)
                        } else {
                            // Server returns nearest-first; show root → nearest, indented by depth.
                            let chain = Array(upline.reversed())
                            ForEach(Array(chain.enumerated()), id: \.element.id) { i, u in
                                RolesMembershipRow(m: u, showPerson: true, indent: CGFloat(i)) { onOpenMembership(u.id) }
                            }
                        }

                        RolesSectionTitle(Copy.t(.rolesDownline))
                        if !summary.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8 * scale) {
                                    ForEach(summary) { s in
                                        HStack(spacing: 6 * scale) {
                                            AppText("\(s.count)", style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 13)
                                            AppText(s.role.title, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                                            RolesStatusPill(label: memberStatusLabel(s.status), color: memberStatusColor(s.status))
                                        }
                                        .padding(.horizontal, 10 * scale)
                                        .padding(.vertical, 6 * scale)
                                        .background(ProfileTokens.bg2)
                                        .clipShape(Capsule())
                                    }
                                }
                                .padding(.horizontal, 20 * scale)
                            }
                            .padding(.bottom, 8 * scale)
                        }
                        HStack(spacing: 8 * scale) {
                            RolesTabChip(label: Copy.t(.rolesDirect), selected: depth == 1) { setDepth(1) }
                            RolesTabChip(label: Copy.t(.rolesWhole), selected: depth == 0) { setDepth(0) }
                            Spacer()
                        }
                        .padding(.horizontal, 20 * scale)
                        .padding(.bottom, 6 * scale)

                        if downline.isEmpty && !paging {
                            AppText(Copy.t(.rolesDownlineEmpty), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                                .padding(.horizontal, 20 * scale)
                                .padding(.vertical, 8 * scale)
                        }
                        ForEach(downline) { d in
                            RolesMembershipRow(m: d, showPerson: true, indent: depth == 0 ? CGFloat(max(0, d.depth - 1)) : 0) {
                                onOpenMembership(d.id)
                            }
                            .onAppear {
                                if d.id == downline.last?.id { Task { await loadMore() } }
                            }
                        }
                        if paging {
                            ProgressView().frame(maxWidth: .infinity).padding(.vertical, 12 * scale)
                        }
                    } else if loading {
                        RolesLoading()
                    } else if failed {
                        RolesFailed { Task { await load() } }
                    }
                }
                .padding(.bottom, 32 * scale)
            }
            .refreshable { await load() }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .task { await load() }
    }

    private func header(_ m: MembershipData) -> some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            HStack(spacing: 12 * scale) {
                PersonAvatar(id: m.userId, name: m.displayName, avatarUrl: m.avatarUrl, size: 48 * scale)
                VStack(alignment: .leading, spacing: 2 * scale) {
                    AppText(m.displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                    AppText("@\(m.username)", style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                }
                Spacer()
                RolesStatusPill(label: memberStatusLabel(m.status), color: memberStatusColor(m.status))
            }
            HStack(spacing: 8 * scale) {
                AppText(m.role.title, style: Type.labelBold, color: ProfileTokens.accent, sizeOverride: 13)
                if !m.code.isEmpty {
                    AppText(m.code, style: Type.micro, color: ProfileTokens.sub, sizeOverride: 11)
                }
                Spacer()
                AppText(Copy.t(.rolesDirectCount, ["n": "\(m.directChildren)"]), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
            }
            if !m.referralCode.isEmpty {
                HStack(spacing: 8 * scale) {
                    AppText(m.referralCode, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
                    RolesSmallButton(label: Copy.t(.profileCopy)) { UIPasteboard.general.string = m.referralCode }
                }
            }
        }
        .padding(.horizontal, 20 * scale)
        .padding(.vertical, 12 * scale)
    }

    private func setDepth(_ d: Int) {
        guard d != depth else { return }
        depth = d
        downline = []
        next = nil
        Task { await loadMore(reset: true) }
    }

    private func load() async {
        loading = membership == nil
        async let m = Xani.roles.membership(membershipId)
        async let u = Xani.roles.upline(membershipId)
        async let s = Xani.roles.summary(membershipId)
        let (mr, ur, sr) = await (m, u, s)
        if let v = mr.value {
            membership = v
            failed = false
        } else {
            failed = membership == nil
        }
        if let v = ur.value { upline = v }
        if let v = sr.value { summary = v }
        loading = false
        downline = []
        next = nil
        await loadMore(reset: true)
    }

    private func loadMore(reset: Bool = false) async {
        if paging { return }
        if !reset, next == nil, !downline.isEmpty { return }
        paging = true
        let r = await Xani.roles.downline(membershipId, depth: depth, cursor: reset ? "" : (next ?? ""))
        paging = false
        guard let page = r.value else { return }
        if reset {
            downline = page.items
        } else {
            let seen = Set(downline.map(\.id))
            downline.append(contentsOf: page.items.filter { !seen.contains($0.id) })
        }
        next = (page.nextCursor?.isEmpty == false) ? page.nextCursor : nil
    }
}

// MARK: - Request list (mine / inbox)

private struct RolesRequestList: View {
    enum Mode { case mine, inbox }
    let mode: Mode
    var onOpen: (Int64) -> Void

    @Environment(\.uiScale) private var scale
    @State private var items: [RoleRequestData] = []
    @State private var next: String?
    @State private var loading = true
    @State private var paging = false
    @State private var failed = false
    @State private var status = RoleRequestStatus.pending

    var body: some View {
        VStack(spacing: 0) {
            if mode == .inbox {
                HStack(spacing: 8 * scale) {
                    RolesTabChip(label: Copy.t(.rolesReqPending), selected: status == RoleRequestStatus.pending) { setStatus(RoleRequestStatus.pending) }
                    RolesTabChip(label: Copy.t(.inboxAll), selected: status == 0) { setStatus(0) }
                    Spacer()
                }
                .padding(.horizontal, 20 * scale)
                .padding(.bottom, 6 * scale)
            }
            ScrollView {
                LazyVStack(spacing: 0) {
                    if loading && items.isEmpty {
                        RolesLoading()
                    } else if failed && items.isEmpty {
                        RolesFailed { Task { await load(reset: true) } }
                    } else if items.isEmpty {
                        AppText(
                            Copy.t(mode == .inbox ? .rolesApprovalsEmpty : .rolesRequestsEmpty),
                            style: Type.caption, color: ProfileTokens.sub, sizeOverride: 14
                        )
                        .padding(.horizontal, 20 * scale)
                        .padding(.vertical, 16 * scale)
                    }
                    ForEach(items) { r in
                        RolesRequestRow(r: r) { onOpen(r.id) }
                            .onAppear {
                                if r.id == items.last?.id { Task { await load(reset: false) } }
                            }
                    }
                    if paging {
                        ProgressView().frame(maxWidth: .infinity).padding(.vertical, 12 * scale)
                    }
                }
                .padding(.bottom, 32 * scale)
            }
            .refreshable { await load(reset: true) }
        }
        .task { await load(reset: true) }
    }

    private func setStatus(_ s: Int) {
        guard s != status else { return }
        status = s
        Task { await load(reset: true) }
    }

    private func load(reset: Bool) async {
        if paging { return }
        if !reset, next == nil { return }
        if reset { loading = items.isEmpty }
        paging = true
        let cursor = reset ? "" : (next ?? "")
        let r: ApiResponse<ItemsResponse<RoleRequestData>>
        switch mode {
        case .mine: r = await Xani.roles.myRequests(cursor: cursor)
        case .inbox: r = await Xani.roles.inbox(status: status, cursor: cursor)
        }
        paging = false
        loading = false
        guard let page = r.value else {
            failed = items.isEmpty
            return
        }
        failed = false
        if reset {
            items = page.items
        } else {
            let seen = Set(items.map(\.id))
            items.append(contentsOf: page.items.filter { !seen.contains($0.id) })
        }
        next = (page.nextCursor?.isEmpty == false) ? page.nextCursor : nil
    }
}

private struct RolesRequestRow: View {
    let r: RoleRequestData
    var onTap: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12 * scale) {
                PersonAvatar(id: r.subject.id, name: r.subject.displayName, avatarUrl: r.subject.avatarUrl, size: 40 * scale)
                VStack(alignment: .leading, spacing: 3 * scale) {
                    AppText(describeRequest(r), style: Type.labelBold, color: ProfileTokens.text, lineLimit: 2, sizeOverride: 14)
                    HStack(spacing: 6 * scale) {
                        AppText(kindLabel(r), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                        AppText("·", style: Type.caption, color: ProfileTokens.muted, sizeOverride: 12)
                        AppText(FeedRepo.relativeTime(r.createdAt), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                    }
                }
                Spacer()
                RolesStatusPill(label: requestStatusLabel(r), color: requestStatusColor(r.status))
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 12 * scale)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Request detail (approve / reject / cancel)

private struct RolesRequestDetail: View {
    let requestId: Int64
    var onBack: () -> Void
    var onChanged: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var r: RoleRequestData?
    @State private var loading = true
    @State private var failed = false
    @State private var reason = ""
    @State private var busy = false
    @State private var error = ""

    var body: some View {
        VStack(spacing: 0) {
            RolesBar(title: r.map(kindLabel) ?? Copy.t(.rolesTitle), back: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: 14 * scale) {
                    if let r {
                        HStack(spacing: 12 * scale) {
                            PersonAvatar(id: r.subject.id, name: r.subject.displayName, avatarUrl: r.subject.avatarUrl, size: 48 * scale)
                            VStack(alignment: .leading, spacing: 2 * scale) {
                                AppText(r.subject.displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                                AppText("@\(r.subject.username)", style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                            }
                            Spacer()
                            RolesStatusPill(label: requestStatusLabel(r), color: requestStatusColor(r.status))
                        }

                        AppText(describeRequest(r), style: Type.caption, color: ProfileTokens.text, sizeOverride: 14)

                        VStack(spacing: 0) {
                            if !r.code.isEmpty { RolesKeyValue(k: "ID", v: r.code) }
                            RolesKeyValue(k: Copy.t(.rolesTabMine), v: r.role.title)
                            if let p = r.parent { RolesKeyValue(k: Copy.t(.rolesUpline), v: "\(p.displayName) · \(p.role.title)") }
                            RolesKeyValue(k: Copy.t(.rolesRequestedBy, ["name": ""]).trimmingCharacters(in: .whitespaces), v: r.requestedBy.displayName)
                            if let d = r.decidedBy, !d.displayName.isEmpty, d.id > 0 {
                                RolesKeyValue(k: Copy.t(.rolesDecided, ["name": ""]).trimmingCharacters(in: .whitespaces), v: d.displayName)
                            }
                            if !r.note.isEmpty { RolesKeyValue(k: Copy.t(.rolesNotePlaceholder), v: r.note) }
                            if !r.reason.isEmpty { RolesKeyValue(k: Copy.t(.rolesReasonPlaceholder), v: r.reason) }
                            if r.isPending && !r.expiresAt.isEmpty {
                                RolesKeyValue(k: Copy.t(.rolesWaiting), v: Copy.t(.rolesExpires, ["when": FeedRepo.relativeTime(r.expiresAt)]))
                            }
                            if r.kind == RoleRequestKind.revoke || r.kind == RoleRequestKind.transfer {
                                RolesKeyValue(k: Copy.t(.rolesDownline), v: "\(r.downlineCount)")
                            }
                        }
                        .background(ProfileTokens.bg2)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))

                        if r.isPending && (r.canDecide || r.canCancel) {
                            TextField(Copy.t(.rolesReasonPlaceholder), text: $reason)
                                .font(.system(size: 14 * scale))
                                .padding(12 * scale)
                                .background(ProfileTokens.bg2)
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))

                            if !error.isEmpty {
                                AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 13)
                            }

                            if r.canDecide {
                                HStack(spacing: 10 * scale) {
                                    actionButton(Copy.t(.rolesReject), bg: ProfileTokens.dangerSoft, fg: ProfileTokens.danger) { decide(.reject) }
                                    actionButton(Copy.t(.rolesApprove), bg: nil, fg: .white) { decide(.approve) }
                                }
                            }
                            if r.canCancel {
                                actionButton(Copy.t(.rolesCancel), bg: ProfileTokens.bg2, fg: ProfileTokens.text) { decide(.cancel) }
                            }
                        }
                    } else if loading {
                        RolesLoading()
                    } else if failed {
                        RolesFailed { Task { await load() } }
                    }
                }
                .padding(20 * scale)
            }
            .refreshable { await load() }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .task { await load() }
    }

    private enum Decision { case approve, reject, cancel }

    private func actionButton(_ label: String, bg: Color?, fg: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelBold, color: fg, sizeOverride: 15)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13 * scale)
                .background(bg.map { AnyShapeStyle($0) } ?? AnyShapeStyle(ProfileTokens.cta))
                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                .opacity(busy ? 0.6 : 1)
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
        .disabled(busy)
    }

    private func decide(_ d: Decision) {
        guard !busy else { return }
        let text = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        if d != .approve && text.isEmpty {
            error = Copy.t(.rolesReasonRequired)
            return
        }
        busy = true
        error = ""
        Task {
            let res: ApiResponse<RoleRequestData>
            switch d {
            case .approve: res = await Xani.roles.approve(requestId, note: text)
            case .reject: res = await Xani.roles.reject(requestId, reason: text)
            case .cancel: res = await Xani.roles.cancel(requestId, reason: text)
            }
            busy = false
            if let v = res.value {
                r = v
                reason = ""
                onChanged()
            } else {
                error = res.message.isEmpty ? Copy.t(.rolesLoadFailed) : res.message
            }
        }
    }

    private func load() async {
        loading = r == nil
        let res = await Xani.roles.request(requestId)
        loading = false
        if let v = res.value {
            r = v
            failed = false
        } else {
            failed = r == nil
        }
    }
}

// MARK: - Shared pieces

private struct RolesBar: View {
    let title: String
    var back: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: back) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(title, style: Type.labelBold, color: ProfileTokens.text, lineLimit: 1, sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, 8 * scale)
            .padding(.bottom, 12 * scale)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
        }
    }
}

private struct RolesTabChip: View {
    let label: String
    let selected: Bool
    var badge: Int = 0
    var action: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6 * scale) {
                AppText(label, style: Type.labelBold, color: selected ? .white : ProfileTokens.text, sizeOverride: 13)
                if badge > 0 {
                    AppText("\(badge)", style: Type.micro, color: selected ? ProfileTokens.accent : .white, sizeOverride: 11)
                        .padding(.horizontal, 6 * scale)
                        .padding(.vertical, 2 * scale)
                        .background(selected ? Color.white : ProfileTokens.accent)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 14 * scale)
            .padding(.vertical, 8 * scale)
            .background(selected ? ProfileTokens.accent : ProfileTokens.bg2)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct RolesSmallButton: View {
    let label: String
    var filled: Bool = false
    var action: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: action) {
            AppText(label, style: Type.labelBold, color: filled ? .white : ProfileTokens.accent, sizeOverride: 12)
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 7 * scale)
                .background(filled ? ProfileTokens.accent : ProfileTokens.accentSoft)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct RolesStatusPill: View {
    let label: String
    let color: Color
    @Environment(\.uiScale) private var scale

    var body: some View {
        AppText(label, style: Type.micro, color: color, sizeOverride: 11)
            .padding(.horizontal, 8 * scale)
            .padding(.vertical, 3 * scale)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }
}

private struct RolesSectionTitle: View {
    let title: String
    @Environment(\.uiScale) private var scale
    init(_ title: String) { self.title = title }

    var body: some View {
        AppText(title.uppercased(), style: Type.micro, color: Color(rgb: 0xAAAAAA), sizeOverride: 11)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20 * scale)
            .padding(.top, 18 * scale)
            .padding(.bottom, 6 * scale)
    }
}

private struct RolesKeyValue: View {
    let k: String
    let v: String
    @Environment(\.uiScale) private var scale

    var body: some View {
        HStack(alignment: .top, spacing: 12 * scale) {
            AppText(k, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                .frame(width: 110 * scale, alignment: .leading)
            AppText(v, style: Type.caption, color: ProfileTokens.text, sizeOverride: 13)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 9 * scale)
    }
}

private struct RolesMembershipRow: View {
    let m: MembershipData
    let showPerson: Bool
    var indent: CGFloat = 0
    var onTap: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12 * scale) {
                if showPerson {
                    PersonAvatar(id: m.userId, name: m.displayName, avatarUrl: m.avatarUrl, size: 38 * scale)
                } else {
                    Circle()
                        .fill(ProfileTokens.accentSoft)
                        .frame(width: 38 * scale, height: 38 * scale)
                        .overlay(
                            AppText(String(m.role.title.prefix(1)).uppercased(), style: Type.labelBold, color: ProfileTokens.accent, sizeOverride: 15)
                        )
                }
                VStack(alignment: .leading, spacing: 2 * scale) {
                    AppText(showPerson ? m.displayName : m.role.title, style: Type.labelBold, color: ProfileTokens.text, lineLimit: 1, sizeOverride: 14)
                    HStack(spacing: 6 * scale) {
                        if showPerson {
                            AppText(m.role.title, style: Type.caption, color: ProfileTokens.accent, sizeOverride: 12)
                        } else if !m.code.isEmpty {
                            AppText(m.code, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                        }
                        if m.directChildren > 0 {
                            AppText("·", style: Type.caption, color: ProfileTokens.muted, sizeOverride: 12)
                            AppText(Copy.t(.rolesDirectCount, ["n": "\(m.directChildren)"]), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                        }
                    }
                }
                Spacer()
                RolesStatusPill(label: memberStatusLabel(m.status), color: memberStatusColor(m.status))
                VectorCanvas([.data("M5 3l5 4.5L5 12", .stroke(Color(rgb: 0xCCCCCC), 1.6))], viewport: 15, side: 15 * scale)
            }
            .padding(.leading, 20 * scale + min(indent, 4) * 14 * scale)
            .padding(.trailing, 20 * scale)
            .padding(.vertical, 10 * scale)
        }
        .buttonStyle(.plain)
    }
}

private struct RolesLoading: View {
    @Environment(\.uiScale) private var scale
    var body: some View {
        ProgressView().frame(maxWidth: .infinity).padding(.vertical, 40 * scale)
    }
}

private struct RolesFailed: View {
    var retry: () -> Void
    @Environment(\.uiScale) private var scale
    var body: some View {
        VStack(spacing: 10 * scale) {
            AppText(Copy.t(.rolesLoadFailed), style: Type.caption, color: ProfileTokens.sub, alignment: .center, sizeOverride: 13)
            RolesSmallButton(label: Copy.t(.homeFeedRetry), action: retry)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32 * scale)
    }
}

// MARK: - Labels

private func describeRequest(_ r: RoleRequestData) -> String {
    let who = r.subject.displayName
    let role = r.role.title
    switch r.kind {
    case RoleRequestKind.assign: return "\(who) → \(role)"
    case RoleRequestKind.apply: return "\(who) · \(role)"
    case RoleRequestKind.transfer:
        if let p = r.parent { return "\(who) → \(p.displayName)" }
        return "\(who) · \(role)"
    case RoleRequestKind.revoke: return "\(who) · \(role)"
    case RoleRequestKind.reactivate: return "\(who) · \(role)"
    default: return "\(who) · \(role)"
    }
}

private func kindLabel(_ r: RoleRequestData) -> String {
    switch r.kind {
    case RoleRequestKind.assign: return Copy.t(.rolesKindAssign)
    case RoleRequestKind.apply: return Copy.t(.rolesKindApply)
    case RoleRequestKind.transfer: return Copy.t(.rolesKindTransfer)
    case RoleRequestKind.revoke: return Copy.t(.rolesKindRevoke)
    case RoleRequestKind.reactivate: return Copy.t(.rolesKindReactivate)
    default: return r.kindLabel
    }
}

private func requestStatusLabel(_ r: RoleRequestData) -> String {
    switch r.status {
    case RoleRequestStatus.pending: return Copy.t(.rolesReqPending)
    case RoleRequestStatus.approved: return Copy.t(.rolesReqApproved)
    case RoleRequestStatus.rejected: return Copy.t(.rolesReqRejected)
    case RoleRequestStatus.cancelled: return Copy.t(.rolesReqCancelled)
    case RoleRequestStatus.expired: return Copy.t(.rolesReqExpired)
    default: return r.statusLabel
    }
}

private func requestStatusColor(_ status: Int) -> Color {
    switch status {
    case RoleRequestStatus.pending: return Color(rgb: 0xD97706)
    case RoleRequestStatus.approved: return ProfileTokens.ok
    case RoleRequestStatus.rejected: return ProfileTokens.danger
    default: return ProfileTokens.sub
    }
}

private func memberStatusLabel(_ status: Int) -> String {
    switch status {
    case MembershipStatus.active: return Copy.t(.rolesStatusActive)
    case MembershipStatus.suspended: return Copy.t(.rolesStatusSuspended)
    case MembershipStatus.revoked: return Copy.t(.rolesStatusRevoked)
    case MembershipStatus.blocked: return Copy.t(.rolesStatusBlocked)
    default: return Copy.t(.rolesReqPending)
    }
}

private func memberStatusColor(_ status: Int) -> Color {
    switch status {
    case MembershipStatus.active: return ProfileTokens.ok
    case MembershipStatus.suspended: return Color(rgb: 0xD97706)
    case MembershipStatus.revoked: return ProfileTokens.sub
    case MembershipStatus.blocked: return ProfileTokens.danger
    default: return ProfileTokens.sub
    }
}
