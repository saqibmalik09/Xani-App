package app.xani.feature.auth.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.BackIcon
import app.xani.ui.components.ChevronRightIcon
import app.xani.ui.components.CircleIconButton
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
fun FaqScreen(
    onBack: () -> Unit,
    onRecover: () -> Unit,
    onLoggedOut: () -> Unit,
    onSocial: () -> Unit,
) {
    SystemBarIcons()
    AuthScaffold(background = Colors.Background) {
        HelpTopBar(onBack = onBack)
        Column(Modifier.padding(horizontal = rdp(20))) {
            XaniText(
                text = "Frequently asked questions",
                style = Type.H2,
                color = Colors.Text,
            )
            Spacer(Modifier.height(rdp(20)))
            FaqRow(title = "Recover your account", onClick = onRecover)
            FaqRow(title = "Why was my account logged out automatically?", onClick = onLoggedOut)
            FaqRow(
                title = "Why can't I log in with my Facebook or Google Account?",
                onClick = onSocial,
                divider = false,
            )
        }
    }
}

@Composable
fun FaqArticleScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    SystemBarIcons()
    AuthScaffold(background = Colors.Background) {
        HelpTopBar(onBack = onBack)
        Column(Modifier.padding(horizontal = rdp(20))) {
            XaniText(text = title, style = Type.H2, color = Colors.Text)
            Spacer(Modifier.height(rdp(16)))
            XaniText(text = body, style = Type.Body, color = Colors.TextSoft)
        }
    }
}

@Composable
internal fun HelpTopBar(
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(16), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            diameter = rdp(36),
            borderColor = Colors.BorderIcon,
            onClick = onBack,
            contentDescription = "Back",
        ) {
            BackIcon(size = rdp(16))
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun FaqRow(
    title: String,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(role = Role.Button, onClick = onClick)
                .padding(vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            XaniText(
                text = title,
                style = Type.Body,
                color = Colors.Text,
                modifier = Modifier.weight(1f),
            )
            ChevronRightIcon(size = rdp(14), tint = Colors.Muted)
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(1))
                    .background(Colors.Divider),
            )
        }
    }
}

@Composable
internal fun loggedOutBody(): String {
    val name = LocalBrand.current.name
    return "You may be signed out automatically if you signed in on too many devices, " +
        "changed your password, or $name detected unusual activity. This protects your account.\n\n" +
        "If you were logged out and you did not authorize any new changes, your account " +
        "may be at risk. Sign in now. Once you are in, set a strong new password.\n\n" +
        "If you cannot sign in, recover the account first."
}

@Composable
internal fun socialLoginBody(): String {
    val name = LocalBrand.current.name
    return "Facebook and Google only work when that profile is already linked to your $name " +
        "account. If you created this account with email, or the provider revoked access, " +
        "sign in with email, username, or phone instead.\n\n" +
        "After you're in, you can link Facebook or Google from Settings."
}
