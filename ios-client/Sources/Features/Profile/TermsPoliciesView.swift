import SwiftUI

/// Terms & Policies: CMS articles (same pattern as Help Centre topics, without tickets).
struct TermsPoliciesView: View {
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var articleId: Int64?
    @State private var articles: [HelpArticleData] = []
    @State private var error = ""

    var body: some View {
        ZStack {
            home
            if let id = articleId {
                TermsArticleDetailView(id: id, onBack: { articleId = nil })
                    .transition(.move(edge: .trailing))
                    .zIndex(2)
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.28), value: articleId)
        .task { await load() }
    }

    private var home: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(Copy.t(.profileTerms), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 16 * scale)
            .background(Color.white)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

            ScrollView {
                LazyVStack(spacing: 10 * scale) {
                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: Color(rgb: 0xDC2626), sizeOverride: 13)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    ForEach(articles) { a in
                        Button(action: { articleId = a.id }) {
                            VStack(alignment: .leading, spacing: 6 * scale) {
                                AppText(a.title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                                if !a.preview.isEmpty {
                                    AppText(a.preview, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14 * scale)
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                    .stroke(ProfileTokens.hairline, lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16 * scale)
                .padding(.bottom, 40 * scale + insets.bottom)
            }
        }
        .background(ProfileTokens.bg2.ignoresSafeArea())
    }

    private func load() async {
        let r = await Xani.support.legalArticles()
        if r.ok {
            articles = r.value ?? []
            error = ""
        } else {
            error = r.message.isEmpty ? "Could not load terms." : r.message
        }
    }
}

private struct TermsArticleDetailView: View {
    let id: Int64
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var article = HelpArticleData()
    @State private var error = ""
    @State private var loaded = false

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(
                    loaded ? (article.title.isEmpty ? Copy.t(.profileTerms) : article.title) : Copy.t(.profileTerms),
                    style: Type.labelBold,
                    color: ProfileTokens.text,
                    sizeOverride: 17
                )
                .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 16 * scale)
            .background(Color.white)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

            ScrollView {
                if !loaded {
                    AppText(error.isEmpty ? "…" : error, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                        .padding(20 * scale)
                } else {
                    VStack(alignment: .leading, spacing: 12 * scale) {
                        AppText(article.title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 20)
                        AppText(article.body, style: Type.body, color: ProfileTokens.text, sizeOverride: 15)
                        HStack(spacing: 10 * scale) {
                            voteChip("Like \(article.likeCount)", on: article.myVote == 1) {
                                Task { await vote(article.myVote == 1 ? 0 : 1) }
                            }
                            voteChip("Dislike \(article.dislikeCount)", on: article.myVote == -1) {
                                Task { await vote(article.myVote == -1 ? 0 : -1) }
                            }
                        }
                        .padding(.top, 8 * scale)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16 * scale)
                    .padding(.bottom, 40 * scale + insets.bottom)
                }
            }
        }
        .background(ProfileTokens.bg2.ignoresSafeArea())
        .task { await load() }
    }

    private func voteChip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelBold, color: on ? .white : ProfileTokens.text, sizeOverride: 12)
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 8 * scale)
                .background(on ? ProfileTokens.accent : Color.white)
                .overlay(
                    Capsule().stroke(on ? ProfileTokens.accent : ProfileTokens.hairline, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        let r = await Xani.support.legalArticle(id)
        if r.ok, let a = r.value {
            article = a
            loaded = true
            error = ""
        } else {
            error = r.message.isEmpty ? "Not found." : r.message
            loaded = false
        }
    }

    private func vote(_ v: Int) async {
        let r = await Xani.support.legalVote(id, vote: v)
        if r.ok, let a = r.value { article = a }
    }
}
