import SwiftUI

enum HelpRoute: String {
    case faq
    case lookupEmail
    case lookupPhone
    case loggedOut
    case socialLogin
}

struct HelpFlow: View {
    let start: HelpRoute
    let onClose: () -> Void

    @State private var route: HelpRoute

    init(start: HelpRoute = .faq, onClose: @escaping () -> Void) {
        self.start = start
        self.onClose = onClose
        _route = State(initialValue: start)
    }

    var body: some View {
        content
            .statusBar()
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case .faq:
            FaqView(
                onBack: back,
                onRecover: { route = .lookupEmail },
                onLoggedOut: { route = .loggedOut },
                onSocial: { route = .socialLogin }
            )
        case .lookupEmail:
            LookupAccountView(
                onBack: back,
                onSearchByPhone: { route = .lookupPhone }
            )
        case .lookupPhone:
            LookupPhoneView(
                onBack: { route = .lookupEmail },
                onSearchByEmail: { route = .lookupEmail }
            )
        case .loggedOut:
            FaqArticleView(
                title: "Why was my account logged out automatically?",
                body: loggedOutBody,
                onBack: { route = .faq }
            )
        case .socialLogin:
            FaqArticleView(
                title: "Why can't I log in with my Facebook or Google Account?",
                body: socialLoginBody,
                onBack: { route = .faq }
            )
        }
    }

    private func back() {
        switch route {
        case .faq:
            onClose()
        case .lookupEmail:
            if start == .lookupEmail { onClose() } else { route = .faq }
        case .lookupPhone:
            route = .lookupEmail
        case .loggedOut, .socialLogin:
            route = .faq
        }
    }
}
