package app.xani.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.BackIcon
import app.xani.ui.components.CircleIconButton
import app.xani.ui.components.LightField
import app.xani.ui.components.OtpInput
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

private const val CodeLength = 6
private const val PasswordMin = 8

/**
 * Password recovery, in two steps on one screen.
 *
 * Step one takes any identifier the user remembers — email, username or phone — and
 * asks the server to send a code. The server answers identically whether or not the
 * account exists, so this screen cannot be used to discover which emails are
 * registered, and the copy is worded to match ("if that account exists").
 */
@Composable
fun PasswordResetScreen(
    busy: Boolean,
    error: String,
    maskedDestination: String,
    codeSent: Boolean,
    onBack: () -> Unit,
    onRequestCode: (identifier: String) -> Unit,
    onSubmit: (identifier: String, code: String, password: String, confirm: String) -> Unit,
) {
    SystemBarIcons()

    var identifier by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = code.length == CodeLength &&
        password.length >= PasswordMin &&
        confirm == password

    AuthScaffold(background = Colors.Background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(12)),
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            XaniMark(size = rdp(52))

            XaniText(
                text = if (codeSent) "Choose a new password" else "Reset your password",
                style = Type.H2,
                color = Colors.Text,
                align = TextAlign.Center,
            )

            XaniText(
                text = if (codeSent) {
                    if (maskedDestination.isEmpty()) {
                        "Enter the code we sent, then pick a new password."
                    } else {
                        "Enter the code we sent to $maskedDestination, then pick a new password."
                    }
                } else {
                    "Enter your email, username or phone and we'll send you a code."
                },
                style = Type.Caption,
                color = Colors.Hint,
                align = TextAlign.Center,
            )

            Spacer(Modifier.height(rdp(8)))

            LightField(
                value = identifier,
                onValueChange = { identifier = it.trim() },
                placeholder = "Email, username or phone",
                keyboardType = KeyboardType.Email,
                maxChars = 254,
            )

            if (codeSent) {
                OtpInput(
                    code = code,
                    onCodeChange = { code = it },
                    length = CodeLength,
                    verified = code.length == CodeLength,
                )

                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "New password (at least $PasswordMin characters)",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    showStrength = true,
                )

                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = "Confirm new password",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    imeAction = ImeAction.Done,
                )

                if (mismatch) {
                    XaniText(
                        text = "Those passwords don't match.",
                        style = Type.Caption,
                        color = Colors.Error,
                    )
                }
            }

            AuthMessageCard(text = error)

            Spacer(Modifier.height(rdp(4)))

            if (codeSent) {
                PrimaryCta(
                    text = "Update password",
                    enabled = canSubmit,
                    loading = busy,
                    onClick = { onSubmit(identifier, code, password, confirm) },
                )
                TextLink(
                    text = "Send a new code",
                    onClick = { onRequestCode(identifier) },
                    color = Dark.Primary,
                )
            } else {
                PrimaryCta(
                    text = "Send code",
                    enabled = identifier.trim().length > 2,
                    loading = busy,
                    onClick = { onRequestCode(identifier) },
                )
            }
        }

        Spacer(Modifier.height(rdp(40)))
    }
}
