package app.xani.feature.auth.help

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class HelpRoute {
    Faq,
    LookupEmail,
    LookupPhone,
    LoggedOut,
    SocialLogin,
}

@Composable
fun HelpFlow(
    start: HelpRoute = HelpRoute.Faq,
    onClose: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(start.name) }
    val current = HelpRoute.entries.find { it.name == route } ?: start

    fun go(next: HelpRoute) {
        route = next.name
    }

    fun back() {
        when (current) {
            HelpRoute.Faq -> onClose()
            HelpRoute.LookupEmail -> if (start == HelpRoute.LookupEmail) onClose() else go(HelpRoute.Faq)
            HelpRoute.LookupPhone -> go(HelpRoute.LookupEmail)
            HelpRoute.LoggedOut, HelpRoute.SocialLogin -> go(HelpRoute.Faq)
        }
    }

    BackHandler(onBack = ::back)

    when (current) {
        HelpRoute.Faq -> FaqScreen(
            onBack = ::back,
            onRecover = { go(HelpRoute.LookupEmail) },
            onLoggedOut = { go(HelpRoute.LoggedOut) },
            onSocial = { go(HelpRoute.SocialLogin) },
        )
        HelpRoute.LookupEmail -> LookupAccountScreen(
            onBack = ::back,
            onSearchByPhone = { go(HelpRoute.LookupPhone) },
        )
        HelpRoute.LookupPhone -> LookupPhoneScreen(
            onBack = ::back,
            onSearchByEmail = { go(HelpRoute.LookupEmail) },
        )
        HelpRoute.LoggedOut -> FaqArticleScreen(
            title = "Why was my account logged out automatically?",
            body = loggedOutBody(),
            onBack = ::back,
        )
        HelpRoute.SocialLogin -> FaqArticleScreen(
            title = "Why can't I log in with my Facebook or Google Account?",
            body = socialLoginBody(),
            onBack = ::back,
        )
    }
}
