package app.xani.feature.auth

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
import app.xani.ui.components.AppleIcon
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.BackIcon
import app.xani.ui.components.CircleIconButton
import app.xani.ui.components.FacebookIcon
import app.xani.ui.components.GoogleIcon
import app.xani.ui.components.HelpIcon
import app.xani.ui.components.LightField
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.ProviderTile
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
fun LogInScreen(
    appName: String,
    busy: Boolean,
    error: String,
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onHelp: () -> Unit,
    onRecover: () -> Unit,
    onProvider: (String) -> Unit,
    onSubmit: (identifier: String, password: String) -> Unit,
) {
    SystemBarIcons()

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val valid = identifier.trim().length > 2 && password.isNotEmpty()

    AuthScaffold(background = Colors.Background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(12)),
            horizontalArrangement = Arrangement.SpaceBetween,
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
            CircleIconButton(
                diameter = rdp(36),
                borderColor = Colors.BorderIcon,
                onClick = onHelp,
                contentDescription = "Help",
            ) {
                HelpIcon(size = rdp(16))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            XaniMark(size = rdp(60))

            Spacer(Modifier.height(rdp(18)))

            XaniText(
                text = "Log in to $appName",
                style = Type.H2,
                color = Colors.Text,
                align = TextAlign.Center,
            )

            Spacer(Modifier.height(rdp(6)))

            XaniText(
                text = "Enter your details to continue",
                style = Type.Caption,
                color = Colors.Hint,
            )

            Spacer(Modifier.height(rdp(28)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rdp(12)),
            ) {
                // One field for all three identifier kinds: the server classifies
                // what was typed, so the user never has to know which one their
                // account was created with.
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextLink(
                        text = "Forgot password?",
                        onClick = onRecover,
                        color = Dark.Primary,
                    )
                }

                AuthMessageCard(text = error)

                PrimaryCta(
                    text = "Log in",
                    enabled = valid,
                    loading = busy,
                    onClick = { onSubmit(identifier, password) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(12)),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(1))
                            .background(Colors.Divider),
                    )
                    XaniText(text = "or", style = Type.Micro, color = Colors.Muted)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(1))
                            .background(Colors.Divider),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                ) {
                    ProviderTile(
                        onClick = { onProvider("google") },
                        modifier = Modifier.weight(1f),
                    ) { GoogleIcon(size = rdp(20)) }
                    ProviderTile(
                        onClick = { onProvider("apple") },
                        modifier = Modifier.weight(1f),
                    ) { AppleIcon(size = rdp(20)) }
                    ProviderTile(
                        onClick = { onProvider("facebook") },
                        modifier = Modifier.weight(1f),
                    ) { FacebookIcon(size = rdp(20)) }
                }
            }
        }

        Spacer(Modifier.height(rdp(20)))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(28), end = rdp(28), bottom = rdp(40)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            XaniText(
                text = "Don't have an account?",
                style = Type.Label,
                color = Colors.TextSoft,
            )
            Spacer(Modifier.padding(horizontal = rdp(2)))
            TextLink(text = "Sign up", onClick = onSignUp, color = Dark.Primary)
        }
    }
}
