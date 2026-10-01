import SwiftUI
import UIKit

/// Help Centre: articles, Report a problem, and My reports — nested navigation
/// so ProfileRoute stays small (.help / .report only).
struct HelpCentreView: View {
    var onBack: () -> Void
    var start: Start = .home

    enum Start: Equatable {
        case home
        case report
        case ticket(Int64)
    }

    private enum Page: Equatable {
        case article(Int64)
        case report
        case myReports
        case ticket(Int64)
    }

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var stack: [Page] = []
    @State private var articles: [HelpArticleData] = []
    @State private var loading = false

    var body: some View {
        ZStack {
            home
            if let top = stack.last {
                switch top {
                case .article(let id):
                    HelpArticleDetailView(id: id, onBack: pop)
                        .transition(.move(edge: .trailing))
                        .zIndex(2)
                case .report:
                    HelpReportFormView(
                        onBack: pop,
                        onSubmitted: { id in
                            stack = [.myReports, .ticket(id)]
                        }
                    )
                    .transition(.move(edge: .trailing))
                    .zIndex(2)
                case .myReports:
                    HelpTicketListView(
                        onBack: pop,
                        onOpen: { push(.ticket($0)) },
                        onNew: { push(.report) }
                    )
                    .transition(.move(edge: .trailing))
                    .zIndex(2)
                case .ticket(let id):
                    HelpTicketDetailView(id: id, onBack: pop)
                        .transition(.move(edge: .trailing))
                        .zIndex(3)
                }
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.28), value: stack.count)
        .onAppear {
            if stack.isEmpty {
                switch start {
                case .report: stack = [.report]
                case .ticket(let id) where id > 0: stack = [.ticket(id)]
                case .home, .ticket: break
                }
            }
        }
        .task { await loadArticles() }
    }

    private var home: some View {
        VStack(spacing: 0) {
            helpBar(Copy.t(.profileHelpCentre), back: onBack) {
                Button(action: { push(.myReports) }) {
                    AppText(Copy.t(.helpMyReports), style: Type.caption, color: ProfileTokens.accent, sizeOverride: 13)
                }
                .buttonStyle(.plain)
            }

            ScrollView {
                VStack(spacing: 0) {
                    Button(action: { push(.report) }) {
                        HStack(spacing: 12 * scale) {
                            Image(systemName: "exclamationmark.bubble")
                                .font(.system(size: 16 * scale, weight: .semibold))
                                .foregroundColor(ProfileTokens.accent)
                                .frame(width: 36 * scale, height: 36 * scale)
                                .background(ProfileTokens.accentSoft)
                                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                            AppText(Copy.t(.profileReportProblem), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12 * scale, weight: .semibold))
                                .foregroundColor(ProfileTokens.muted)
                        }
                        .padding(.horizontal, 20 * scale)
                        .padding(.vertical, 14 * scale)
                    }
                    .buttonStyle(.plain)

                    Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
                        .padding(.horizontal, 20 * scale)

                    ForEach(articles) { article in
                        Button(action: { push(.article(article.id)) }) {
                            VStack(alignment: .leading, spacing: 6 * scale) {
                                AppText(article.title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                                if !article.preview.isEmpty {
                                    AppText(article.preview, style: Type.caption, color: ProfileTokens.sub,
                                            alignment: .leading, lineLimit: 2, sizeOverride: 13)
                                }
                                HStack(spacing: 12 * scale) {
                                    Label("\(article.likeCount)", systemImage: "hand.thumbsup")
                                    Label("\(article.dislikeCount)", systemImage: "hand.thumbsdown")
                                }
                                .font(.system(size: 11 * scale))
                                .foregroundColor(ProfileTokens.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 14 * scale)
                        }
                        .buttonStyle(.plain)
                        Rectangle().fill(ProfileTokens.rowLine).frame(height: 1)
                            .padding(.horizontal, 20 * scale)
                    }
                }
                .padding(.bottom, 24 * scale)
            }
        }
        .padding(.top, insets.top)
    }

    private func push(_ page: Page) {
        withAnimation(.easeInOut(duration: 0.28)) { stack.append(page) }
    }

    private func pop() {
        if stack.isEmpty {
            onBack()
            return
        }
        withAnimation(.easeInOut(duration: 0.28)) { _ = stack.popLast() }
        if stack.isEmpty, start == .report { onBack() }
    }

    private func loadArticles() async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        if let items = await Xani.support.articles().value {
            articles = items
        }
    }
}

// MARK: - Shared chrome

private func helpBar(_ title: String, back: @escaping () -> Void) -> some View {
    HelpBar(title: title, back: back, trailing: { EmptyView() })
}

private func helpBar<Trailing: View>(
    _ title: String,
    back: @escaping () -> Void,
    @ViewBuilder trailing: () -> Trailing
) -> some View {
    HelpBar(title: title, back: back, trailing: trailing)
}

private struct HelpBar<Trailing: View>: View {
    let title: String
    var back: () -> Void
    @ViewBuilder var trailing: () -> Trailing
    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: back) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
                trailing()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, 8 * scale)
            .padding(.bottom, 16 * scale)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
        }
    }
}

// MARK: - Article detail

private struct HelpArticleDetailView: View {
    let id: Int64
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var article = HelpArticleData()
    @State private var loaded = false
    @State private var voting = false

    var body: some View {
        VStack(spacing: 0) {
            helpBar(article.title.isEmpty ? Copy.t(.profileHelpCentre) : article.title, back: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: 16 * scale) {
                    if loaded {
                        AppText(article.title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 20)
                        AppText(article.body, style: Type.body, color: ProfileTokens.text, alignment: .leading, sizeOverride: 15)
                        HStack(spacing: 12 * scale) {
                            voteBtn(up: true, count: article.likeCount, on: article.myVote == 1) {
                                cast(1)
                            }
                            voteBtn(up: false, count: article.dislikeCount, on: article.myVote == -1) {
                                cast(-1)
                            }
                        }
                        .padding(.top, 8 * scale)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20 * scale)
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .task { await load() }
    }

    private func voteBtn(up: Bool, count: Int, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6 * scale) {
                Image(systemName: up ? "hand.thumbsup.fill" : "hand.thumbsdown.fill")
                    .font(.system(size: 14 * scale))
                AppText("\(count)", style: Type.labelBold, color: on ? .white : ProfileTokens.text, sizeOverride: 13)
            }
            .foregroundColor(on ? .white : ProfileTokens.text)
            .padding(.horizontal, 14 * scale)
            .padding(.vertical, 10 * scale)
            .background(on ? ProfileTokens.accent : ProfileTokens.bg2)
            .clipShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
        .disabled(voting)
    }

    private func load() async {
        if let a = await Xani.support.article(id).value {
            article = a
        }
        loaded = true
    }

    private func cast(_ vote: Int) {
        guard !voting else { return }
        let next = article.myVote == vote ? 0 : vote
        let prev = article
        // Optimistic: flip local counters so the tap feels instant.
        var local = article
        if prev.myVote == 1 { local.likeCount = max(0, local.likeCount - 1) }
        if prev.myVote == -1 { local.dislikeCount = max(0, local.dislikeCount - 1) }
        if next == 1 { local.likeCount += 1 }
        if next == -1 { local.dislikeCount += 1 }
        local.myVote = next
        article = local
        voting = true
        Task {
            if let updated = await Xani.support.vote(id, vote: next).value {
                article = updated
            } else {
                article = prev
            }
            voting = false
        }
    }
}

// MARK: - Report form

private struct HelpReportFormView: View {
    var onBack: () -> Void
    var onSubmitted: (Int64) -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var subject = ""
    @State private var message = ""
    @State private var previews: [UIImage] = []
    @State private var jpegs: [Data] = []
    @State private var pickGallery = false
    @State private var submitting = false
    @State private var error = ""

    private var canSubmit: Bool {
        !subject.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !submitting
    }

    var body: some View {
        VStack(spacing: 0) {
            helpBar(Copy.t(.profileReportProblem), back: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: 16 * scale) {
                    field(Copy.t(.helpSubject), text: $subject, single: true)
                    field(Copy.t(.profileMessage), text: $message, single: false)

                    AppText(Copy.t(.profilePhotoAdd), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 12)
                    HStack(spacing: 10 * scale) {
                        ForEach(Array(previews.enumerated()), id: \.offset) { i, img in
                            ZStack(alignment: .topTrailing) {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 72 * scale, height: 72 * scale)
                                    .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                                Button(action: { remove(at: i) }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 18 * scale))
                                        .foregroundColor(.white)
                                        .shadow(radius: 2)
                                }
                                .offset(x: 4 * scale, y: -4 * scale)
                            }
                        }
                        if previews.count < 3 {
                            Button(action: { pickGallery = true }) {
                                Image(systemName: "plus")
                                    .font(.system(size: 22 * scale, weight: .medium))
                                    .foregroundColor(ProfileTokens.sub)
                                    .frame(width: 72 * scale, height: 72 * scale)
                                    .background(ProfileTokens.bg2)
                                    .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 13)
                    }

                    Button(action: submit) {
                        AppText(
                            submitting ? Copy.t(.profilePhotoUploading) : Copy.t(.helpSubmit),
                            style: Type.labelBold,
                            color: .white,
                            sizeOverride: 15
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14 * scale)
                        .background(canSubmit ? AnyShapeStyle(ProfileTokens.cta) : AnyShapeStyle(ProfileTokens.muted))
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                    .disabled(!canSubmit)
                }
                .padding(20 * scale)
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .fullScreenCover(isPresented: $pickGallery) {
            HelpImagePicker { image in
                add(image)
                pickGallery = false
            } onCancel: {
                pickGallery = false
            }
            .ignoresSafeArea()
        }
    }

    private func field(_ label: String, text: Binding<String>, single: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            AppText(label, style: Type.micro, color: ProfileTokens.sub, sizeOverride: 12)
            if single {
                TextField("", text: text)
                    .font(.system(size: 15 * scale))
                    .padding(12 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
            } else {
                ZStack(alignment: .topLeading) {
                    if text.wrappedValue.isEmpty {
                        AppText(label, style: Type.caption, color: ProfileTokens.muted, sizeOverride: 14)
                            .padding(.top, 12 * scale)
                            .padding(.leading, 12 * scale)
                    }
                    TextEditor(text: text)
                        .frame(minHeight: 120 * scale)
                        .padding(8 * scale)
                }
                .background(ProfileTokens.bg2)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
            }
        }
    }

    private func add(_ image: UIImage) {
        guard previews.count < 3 else { return }
        guard let jpeg = image.jpegData(compressionQuality: 0.82) else { return }
        previews.append(image)
        jpegs.append(jpeg)
    }

    private func remove(at i: Int) {
        guard previews.indices.contains(i) else { return }
        previews.remove(at: i)
        jpegs.remove(at: i)
    }

    private func submit() {
        guard canSubmit else { return }
        submitting = true
        error = ""
        let subj = subject.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = message.trimmingCharacters(in: .whitespacesAndNewlines)
        let bytes = jpegs
        Task {
            var keys: [String] = []
            for data in bytes {
                if let key = await Xani.support.uploadTicketMedia(data).value {
                    keys.append(key)
                } else {
                    await MainActor.run {
                        error = Copy.t(.profileSaveFailed)
                        submitting = false
                    }
                    return
                }
            }
            if let ticket = await Xani.support.createTicket(subject: subj, body: body, keys: keys).value {
                await MainActor.run {
                    submitting = false
                    onSubmitted(ticket.id)
                }
            } else {
                await MainActor.run {
                    error = Copy.t(.profileSaveFailed)
                    submitting = false
                }
            }
        }
    }
}

/// Gallery-only image pick (iOS 15 — PhotosPicker needs 16+).
private struct HelpImagePicker: UIViewControllerRepresentable {
    var onPick: (UIImage) -> Void
    var onCancel: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.delegate = context.coordinator
        p.sourceType = .photoLibrary
        p.mediaTypes = ["public.image"]
        p.allowsEditing = false
        return p
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: HelpImagePicker
        init(_ parent: HelpImagePicker) { self.parent = parent }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.onPick(image)
            } else {
                parent.onCancel()
            }
        }
    }
}

// MARK: - Ticket list

private struct HelpTicketListView: View {
    var onBack: () -> Void
    var onOpen: (Int64) -> Void
    var onNew: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var items: [SupportTicketData] = []

    var body: some View {
        VStack(spacing: 0) {
            helpBar(Copy.t(.helpMyReports), back: onBack) {
                Button(action: onNew) {
                    AppText(Copy.t(.profileReportProblem), style: Type.caption, color: ProfileTokens.accent, sizeOverride: 13)
                }
                .buttonStyle(.plain)
            }

            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(items) { t in
                        Button(action: { onOpen(t.id) }) {
                            VStack(alignment: .leading, spacing: 6 * scale) {
                                HStack {
                                    AppText(t.subject, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                                    Spacer()
                                    statusChip(t.status)
                                }
                                AppText(t.body, style: Type.caption, color: ProfileTokens.sub,
                                        alignment: .leading, lineLimit: 2, sizeOverride: 13)
                                if t.replyCount > 0 {
                                    AppText("\(t.replyCount)", style: Type.micro, color: ProfileTokens.muted, sizeOverride: 11)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 14 * scale)
                        }
                        .buttonStyle(.plain)
                        Rectangle().fill(ProfileTokens.rowLine).frame(height: 1)
                            .padding(.horizontal, 20 * scale)
                    }
                }
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .task {
            if let list = await Xani.support.myTickets().value { items = list }
        }
    }
}

private func statusChip(_ status: String) -> some View {
    let label = status.prefix(1).uppercased() + status.dropFirst().lowercased()
    return Text(label)
        .font(.system(size: 11, weight: .semibold))
        .foregroundColor(ProfileTokens.accentDeep)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(ProfileTokens.accentSoft)
        .clipShape(Capsule())
}

// MARK: - Ticket detail

private struct HelpTicketDetailView: View {
    let id: Int64
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var detail = SupportTicketDetail()
    @State private var loaded = false
    @State private var replyText = ""
    @State private var sending = false

    var body: some View {
        VStack(spacing: 0) {
            helpBar(detail.ticket.subject.isEmpty ? Copy.t(.helpMyReports) : detail.ticket.subject, back: onBack)

            ScrollView {
                VStack(alignment: .leading, spacing: 14 * scale) {
                    if loaded {
                        HStack {
                            AppText(Copy.t(.profileStatusTitle), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 12)
                            statusChip(detail.ticket.status)
                        }
                        AppText(detail.ticket.body, style: Type.body, color: ProfileTokens.text, alignment: .leading, sizeOverride: 15)

                        if !detail.ticket.attachmentKeys.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8 * scale) {
                                    ForEach(detail.ticket.attachmentKeys, id: \.self) { key in
                                        if let url = MediaConfig.shared.url(key) {
                                            RemoteImage(url: url, contentMode: .fill, fallback: ProfileTokens.bg2)
                                                .frame(width: 88 * scale, height: 88 * scale)
                                                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                                        }
                                    }
                                }
                            }
                        }

                        ForEach(detail.replies) { r in
                            VStack(alignment: .leading, spacing: 4 * scale) {
                                HStack(spacing: 6 * scale) {
                                    AppText(
                                        r.isStaff ? Copy.t(.profileHelpCentre) : (r.username.isEmpty ? Copy.t(.inboxYou) : "@\(r.username)"),
                                        style: Type.micro,
                                        color: r.isStaff ? ProfileTokens.accentDeep : ProfileTokens.sub,
                                        sizeOverride: 12
                                    )
                                }
                                AppText(r.body, style: Type.caption, color: ProfileTokens.text, alignment: .leading, sizeOverride: 14)
                            }
                            .padding(12 * scale)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(r.isStaff ? ProfileTokens.accentSoft : ProfileTokens.bg2)
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        }
                    }
                }
                .padding(20 * scale)
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            HStack(spacing: 10 * scale) {
                TextField(Copy.t(.inboxTypeMessage), text: $replyText)
                    .font(.system(size: 15 * scale))
                    .padding(12 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                Button(action: sendReply) {
                    AppText(Copy.t(.inboxSendBtn), style: Type.labelBold, color: .white, sizeOverride: 14)
                        .padding(.horizontal, 14 * scale)
                        .padding(.vertical, 12 * scale)
                        .background(canSend ? ProfileTokens.accent : ProfileTokens.muted)
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                }
                .buttonStyle(PressScaleStyle())
                .disabled(!canSend)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 10 * scale)
            .background(Color.white)
        }
        .task { await load() }
    }

    private var canSend: Bool {
        !replyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !sending
    }

    private func load() async {
        if let d = await Xani.support.ticket(id).value { detail = d }
        loaded = true
    }

    private func sendReply() {
        let body = replyText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, !sending else { return }
        sending = true
        Task {
            if let r = await Xani.support.reply(id, body: body).value {
                await MainActor.run {
                    detail.replies.append(r)
                    replyText = ""
                }
            }
            await MainActor.run { sending = false }
        }
    }
}
