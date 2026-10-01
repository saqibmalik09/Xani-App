package app.xani.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.CheckIcon
import app.xani.ui.components.DarkCta
import app.xani.ui.components.DarkGlow
import app.xani.ui.components.OtpBadgeIcon
import app.xani.ui.components.OtpInput
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniText
import app.xani.ui.components.revealUp
import app.xani.ui.theme.Dark
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

private const val CodeLength = 6

/**
 * Email verification.
 *
 * It is deliberately skippable. The account exists and is already signed in by the
 * time this screen appears; verification only unlocks privileged actions later. Mail
 * to a phone gets delayed, filtered and silently dropped often enough that treating
 * this as a hard gate would strand real users behind a screen they cannot pass.
 *
 * @param secondsLeft resend cooldown, supplied by the server rather than assumed, so
 *   the countdown matches what the server will actually accept.
 */
@Composable
fun OtpScreen(
    destination: String,
    busy: Boolean,
    error: String,
    notice: String,
    secondsLeft: Int,
    onTick: () -> Unit,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit,
    onSkip: () -> Unit,
) {
    SystemBarIcons(lightBackground = false)

    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val complete = code.length == CodeLength

    fun grabFocus() {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(Unit) { grabFocus() }

    // One coroutine for the whole countdown instead of restarting an effect per tick.
    LaunchedEffect(secondsLeft > 0) {
        while (secondsLeft > 0) {
            delay(1_000)
            onTick()
        }
    }

    AuthScaffold(background = Dark.Background, glow = DarkGlow) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(32), vertical = rdp(40)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .revealUp()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { grabFocus() },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(rdp(80))
                        .clip(RoundedCornerShape(rdp(24)))
                        .background(Dark.Primary.copy(alpha = 0.08f))
                        .border(
                            rdp(1),
                            Dark.Primary.copy(alpha = 0.20f),
                            RoundedCornerShape(rdp(24)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    OtpBadgeIcon(size = rdp(36))
                }

                Spacer(Modifier.height(rdp(28)))

                XaniText(
                    text = "Verify your email",
                    style = Type.H2,
                    color = Dark.Foreground,
                    align = TextAlign.Center,
                )

                Spacer(Modifier.height(rdp(8)))

                XaniText(
                    text = if (destination.isEmpty()) {
                        "We sent a $CodeLength-digit code to your inbox"
                    } else {
                        "Sent to $destination"
                    },
                    style = Type.Label,
                    color = Dark.Dim,
                    align = TextAlign.Center,
                )

                Spacer(Modifier.height(rdp(36)))

                OtpInput(
                    code = code,
                    onCodeChange = { code = it },
                    length = CodeLength,
                    verified = complete,
                    modifier = Modifier.focusRequester(focus),
                )

                Spacer(Modifier.height(rdp(24)))

                when {
                    error.isNotEmpty() -> {
                        AuthMessageCard(text = error)
                        Spacer(Modifier.height(rdp(24)))
                    }

                    notice.isNotEmpty() -> {
                        AuthMessageCard(text = notice, error = false)
                        Spacer(Modifier.height(rdp(24)))
                    }

                    complete -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(rdp(20)))
                                .background(Dark.Success.copy(alpha = 0.10f))
                                .border(
                                    rdp(1),
                                    Dark.Success.copy(alpha = 0.30f),
                                    RoundedCornerShape(rdp(20)),
                                )
                                .padding(horizontal = rdp(16), vertical = rdp(8)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                        ) {
                            CheckIcon(size = rdp(14), tint = Dark.Success)
                            XaniText(
                                text = "Code complete",
                                style = Type.Caption,
                                color = Dark.Success,
                            )
                        }
                        Spacer(Modifier.height(rdp(24)))
                    }
                }

                DarkCta(
                    text = if (busy) "Checking…" else "Verify code",
                    enabled = complete && !busy,
                    onClick = { onVerify(code) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(rdp(20)))

                if (secondsLeft > 0) {
                    XaniText(
                        text = "Resend in 0:${secondsLeft.toString().padStart(2, '0')}",
                        style = Type.Caption,
                        color = Dark.Dim,
                        align = TextAlign.Center,
                    )
                } else {
                    TextLink(
                        text = "Resend code",
                        onClick = onResend,
                        color = Dark.PrimarySoft,
                    )
                }

                Spacer(Modifier.height(rdp(16)))

                TextLink(
                    text = "Skip for now",
                    onClick = onSkip,
                    color = Dark.Dim,
                )
            }
        }
    }
}
