package app.xani.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.Xani
import app.xani.ui.components.LightField
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Wraps the whole app and puts a blocking sign-in sheet over it when the session
 * cannot be renewed.
 *
 * It cannot be dismissed. Once the refresh window closes there is no credential left
 * on the device, so every authenticated request behind this sheet would fail.
 * Back is swallowed and taps cannot reach the content underneath. The identity is
 * remembered, so the user signs in as themselves rather than starting from a cold
 * "who are you" screen.
 */
@Composable
fun SessionExpiredGate(content: @Composable () -> Unit) {
    content()

    val expired by Xani.session.sessionExpired.collectAsState()
    if (expired) ExpiredSheet()
}

@Composable
private fun ExpiredSheet() {
    val context = LocalContext.current
    val controller = rememberAuthController()
    val remembered = remember { Xani.session.lastKnownUser }

    var identifier by remember { mutableStateOf(remembered?.email ?: remembered?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(24))
                .clip(RoundedCornerShape(rdp(20)))
                .background(Colors.Background)
                .padding(horizontal = rdp(24), vertical = rdp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            XaniMark(size = rdp(48))

            XaniText(
                text = "Welcome back",
                style = Type.H2,
                color = Colors.Text,
                align = TextAlign.Center,
            )

            XaniText(
                text = if (remembered?.username.isNullOrEmpty()) {
                    "Your session ended. Sign in to continue."
                } else {
                    "Your session ended. Sign in as @${remembered?.username} to continue."
                },
                style = Type.Caption,
                color = Colors.Hint,
                align = TextAlign.Center,
            )

            Spacer(Modifier.height(rdp(4)))

            LightField(
                value = identifier,
                onValueChange = { identifier = it.trim() },
                placeholder = "Email, username or phone",
                keyboardType = KeyboardType.Email,
                maxChars = 254,
            )

            PasswordField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                revealed = revealed,
                onToggleReveal = { revealed = !revealed },
                imeAction = ImeAction.Done,
            )

            AuthMessageCard(text = controller.error)

            PrimaryCta(
                text = "Sign in",
                enabled = identifier.trim().length > 2 && password.isNotEmpty(),
                loading = controller.busy,
                onClick = {
                    controller.logIn(identifier, password) {
                        Xani.session.dismissExpiryAfterSignIn()
                    }
                },
            )

            if (controller.googleAvailable) {
                TextLink(
                    text = "Continue with Google",
                    onClick = {
                        controller.signInWithGoogle(context) {
                            Xani.session.dismissExpiryAfterSignIn()
                        }
                    },
                    color = Dark.Primary,
                )
            }

            TextLink(
                text = "Use a different account",
                onClick = { Xani.session.signOut() },
                color = Colors.Hint,
            )
        }
    }
}
