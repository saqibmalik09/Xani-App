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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.MediaConfig
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.BackIcon
import app.xani.ui.components.BirthdayPicker
import app.xani.ui.components.CircleIconButton
import app.xani.ui.components.CountedField
import app.xani.ui.components.HelpIcon
import app.xani.ui.components.LightField
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.daysInMonth
import app.xani.ui.components.sanitizeUsername
import app.xani.ui.theme.BrandGradient
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

private const val MinimumAgeYears = 13
private const val FullNameMax = 30
private const val UsernameMax = 24
private const val UsernameMin = 2

/** Matches the server's password policy so the two cannot drift. */
private const val PasswordMin = 8

@Composable
fun SignUpScreen(
    appName: String,
    busy: Boolean,
    error: String,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onSubmit: (SignUpForm) -> Unit,
) {
    SystemBarIcons()

    var fullName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val defaultBirth = remember(today) { today.minusYears(18) }
    val minYear = today.year - 120
    val maxYear = today.year
    var month by rememberSaveable { mutableIntStateOf(defaultBirth.monthValue) }
    var day by rememberSaveable { mutableIntStateOf(defaultBirth.dayOfMonth) }
    var year by rememberSaveable { mutableIntStateOf(defaultBirth.year) }

    val age = remember(month, day, year, today) { ageYears(year, month, day, today) }
    val tooYoung = age != null && age < MinimumAgeYears
    // The server's floor is 8 characters; enforcing it here turns a round trip and a
    // "weak_password" error into an inline hint the user can act on immediately.
    val valid = fullName.trim().length >= 2 &&
        username.length >= UsernameMin &&
        isPlausibleEmail(email) &&
        password.length >= PasswordMin &&
        age != null && age >= MinimumAgeYears

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
            XaniMark(size = rdp(56))

            Spacer(Modifier.height(rdp(16)))

            XaniText(
                text = "Create your account",
                style = Type.H2,
                color = Colors.Text,
                align = TextAlign.Center,
            )

            Spacer(Modifier.height(rdp(6)))

            XaniText(
                text = "Step 1 of 2 · Basic info",
                style = Type.Caption,
                color = Colors.Hint,
            )

            Spacer(Modifier.height(rdp(12)))

            Box(
                modifier = Modifier
                    .widthIn(max = rdp(280))
                    .fillMaxWidth()
                    .height(rdp(3))
                    .clip(RoundedCornerShape(rdp(2)))
                    .background(Colors.Border),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(rdp(3))
                        .clip(RoundedCornerShape(rdp(2)))
                        .background(BrandGradient),
                )
            }

            Spacer(Modifier.height(rdp(24)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rdp(12)),
            ) {
                CountedField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = "Full name",
                    maxChars = FullNameMax,
                    capitalization = KeyboardCapitalization.Words,
                )

                CountedField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    maxChars = UsernameMax,
                    prefix = "@",
                    keyboardType = KeyboardType.Ascii,
                    sanitize = ::sanitizeUsername,
                    // The lock window comes from Settings → Profile, so the
                    // promise made here is the one the server keeps.
                    footnote = "You can change your username after ${MediaConfig.usernameChangeDays} days.",
                )

                LightField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    placeholder = "Email address",
                    keyboardType = KeyboardType.Email,
                    maxChars = 254,
                )

                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password (at least $PasswordMin characters)",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    showStrength = true,
                    imeAction = ImeAction.Done,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    XaniText(
                        text = "When's your birthday?",
                        style = Type.LabelStrong,
                        color = Colors.Text,
                    )
                    Spacer(Modifier.height(rdp(4)))
                    XaniText(
                        text = "Your birthday won't be shown publicly.",
                        style = Type.Caption,
                        color = Colors.Hint,
                    )
                    Spacer(Modifier.height(rdp(10)))
                    BirthdayPicker(
                        month = month,
                        day = day,
                        year = year,
                        minYear = minYear,
                        maxYear = maxYear,
                        onMonthChange = { next ->
                            month = next
                            day = day.coerceAtMost(daysInMonth(next, year))
                        },
                        onDayChange = { day = it },
                        onYearChange = { next ->
                            year = next
                            day = day.coerceAtMost(daysInMonth(month, next))
                        },
                    )
                    if (tooYoung) {
                        Spacer(Modifier.height(rdp(8)))
                        XaniText(
                            text = "Sorry. Looks like you're not eligible for $appName ... but thanks for checking us out!",
                            style = Type.Micro,
                            color = Colors.Error,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(rdp(20)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(28), end = rdp(28), bottom = rdp(40)),
            verticalArrangement = Arrangement.spacedBy(rdp(14)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthMessageCard(text = error)

            PrimaryCta(
                text = "Continue",
                enabled = valid,
                loading = busy,
                trailingArrow = true,
                onClick = {
                    onSubmit(
                        SignUpForm(
                            fullName = fullName,
                            username = username,
                            email = email,
                            password = password,
                        ),
                    )
                },
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
                TextLink(text = "Log in", onClick = onBack, color = Dark.Primary)
            }
        }
    }
}

fun isPlausibleEmail(value: String): Boolean {
    val trimmed = value.trim()
    val at = trimmed.indexOf('@')
    if (at <= 0 || at != trimmed.lastIndexOf('@')) return false
    val domain = trimmed.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.length - 1 && !trimmed.contains(' ')
}

/**
 * Age gate is evaluated client-side purely to keep the CTA honest; the server is
 * still the authority on eligibility at registration time. Invalid calendar
 * dates return null instead of throwing, so a partial wheel never crashes.
 */
fun ageYears(year: Int, month: Int, day: Int, today: LocalDate): Int? {
    val birth = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        return null
    }
    if (birth.isAfter(today)) return null
    return Period.between(birth, today).years
}
