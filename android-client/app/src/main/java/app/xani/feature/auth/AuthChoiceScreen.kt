package app.xani.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import app.xani.feature.auth.help.DialCountries
import app.xani.ui.components.AppleIcon
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.ChevronDownIcon
import app.xani.ui.components.CircleIconButton
import app.xani.ui.components.FacebookIcon
import app.xani.ui.components.GoogleIcon
import app.xani.ui.components.HelpIcon
import app.xani.ui.components.MailIcon
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.ProviderButton
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.revealScale
import app.xani.ui.components.revealUp
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
fun AuthChoiceScreen(
    appName: String,
    busy: Boolean,
    error: String,
    onEmail: () -> Unit,
    onProvider: (String) -> Unit,
    onLogIn: () -> Unit,
    onHelp: () -> Unit,
) {
    SystemBarIcons()
    val context = LocalContext.current
    val country = remember(context) { DialCountries.suggested(context).name }

    AuthScaffold(background = Colors.Background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(12)),
            horizontalArrangement = Arrangement.End,
        ) {
            CircleIconButton(
                diameter = rdp(30),
                borderColor = Colors.BorderIcon,
                onClick = onHelp,
                contentDescription = "Help",
            ) {
                HelpIcon(size = rdp(14))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(32)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(rdp(8)))

            XaniMark(size = rdp(68), modifier = Modifier.revealScale())

            Spacer(Modifier.height(rdp(20)))

            XaniText(
                text = "Log in to $appName",
                style = Type.H1,
                color = Colors.Text,
                align = TextAlign.Center,
                modifier = Modifier.revealUp(delayMillis = 50),
            )

            Spacer(Modifier.height(rdp(10)))

            XaniText(
                text = "Watch videos, go LIVE, and connect with millions of creators.",
                style = Type.Label,
                color = Colors.Sub,
                align = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = rdp(270))
                    .revealUp(delayMillis = 120),
            )

            Spacer(Modifier.height(rdp(8)))
            XaniText(
                text = remember(country) {
                    buildAnnotatedString {
                        append("You're in ")
                        withStyle(
                            SpanStyle(color = Dark.Primary, fontWeight = FontWeight.Bold),
                        ) { append(country) }
                    }
                },
                style = Type.Caption.copy(color = Colors.Muted),
                align = TextAlign.Center,
            )

            Spacer(Modifier.height(rdp(32)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                PrimaryCta(
                    text = "Continue with Email",
                    enabled = !busy,
                    loading = busy,
                    onClick = onEmail,
                    leading = { MailIcon(size = rdp(20)) },
                )

                ProviderButton(label = "Continue with Google", onClick = { onProvider("google") }) {
                    GoogleIcon(size = rdp(20))
                }
                ProviderButton(label = "Continue with Apple", onClick = { onProvider("apple") }) {
                    AppleIcon(size = rdp(20))
                }
                ProviderButton(
                    label = "Continue with Facebook",
                    onClick = { onProvider("facebook") },
                ) {
                    FacebookIcon(size = rdp(20))
                }

                AuthMessageCard(text = error)
            }

            Spacer(Modifier.height(rdp(24)))

            ChevronDownIcon(size = rdp(16), tint = Colors.Muted)
        }

        Spacer(Modifier.height(rdp(24)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(32), end = rdp(32), bottom = rdp(36)),
            verticalArrangement = Arrangement.spacedBy(rdp(16)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            XaniText(
                text = remember(country) {
                    buildAnnotatedString {
                        append("By continuing, you confirm you are located in ")
                        withStyle(
                            SpanStyle(color = Dark.Primary, fontWeight = FontWeight.Bold),
                        ) {
                            append("$country")
                        }
                        append(
                            " and agree to our Terms of Service and acknowledge that you " +
                                "have read our Privacy Policy.",
                        )
                    }
                },
                style = Type.Micro.copy(color = Colors.Muted),
                align = TextAlign.Center,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(4)),
            ) {
                XaniText(
                    text = "Already have an account?",
                    style = Type.Label,
                    color = Colors.TextSoft,
                )
                TextLink(text = "Log in", onClick = onLogIn, color = Dark.Primary)
            }
        }
    }
}
