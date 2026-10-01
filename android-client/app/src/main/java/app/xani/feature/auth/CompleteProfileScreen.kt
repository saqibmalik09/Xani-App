package app.xani.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.AccountUser
import app.xani.feature.profile.AvatarPickerSheet
import app.xani.feature.profile.AvatarStore
import app.xani.feature.profile.MyAvatar
import app.xani.feature.profile.ProfileStore
import app.xani.i18n.rememberCopy
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.BirthdayPicker
import app.xani.ui.components.CountedField
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.XaniText
import app.xani.ui.components.daysInMonth
import app.xani.ui.components.sanitizeUsername
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

data class CompleteProfileForm(
    val fullName: String,
    val username: String,
    val birthday: String,
    val password: String,
    val confirmPassword: String,
)

/**
 * Post-Google onboarding — edit-profile style avatar + name / username /
 * birthday / password so the account can also sign in with credentials.
 */
@Composable
fun CompleteProfileScreen(
    appName: String,
    user: AccountUser,
    busy: Boolean,
    error: String,
    onSubmit: (CompleteProfileForm) -> Unit,
) {
    SystemBarIcons(lightBackground = true)

    val now = remember { LocalDate.now() }
    val maxYear = now.year - 13
    val minYear = now.year - 100
    val seed = remember(user.birthday) { parseBirthday(user.birthday) ?: LocalDate.of(maxYear, 1, 1) }

    var fullName by rememberSaveable(user.id) { mutableStateOf(user.fullName) }
    var username by rememberSaveable(user.id) { mutableStateOf(user.username) }
    var month by rememberSaveable(user.id) { mutableIntStateOf(seed.monthValue) }
    var day by rememberSaveable(user.id) { mutableIntStateOf(seed.dayOfMonth) }
    var year by rememberSaveable(user.id) { mutableIntStateOf(seed.year.coerceIn(minYear, maxYear)) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var photoSheet by rememberSaveable { mutableStateOf(false) }

    val copy = rememberCopy()
    val avatar by AvatarStore.state.collectAsState()
    val snapshot by ProfileStore.state.collectAsState()

    val age = remember(year, month, day) {
        try {
            Period.between(LocalDate.of(year, month, day), now).years
        } catch (_: DateTimeException) {
            -1
        }
    }
    val tooYoung = age in 0 until 13
    val valid = fullName.trim().length >= 2 &&
        username.trim().length >= 3 &&
        password.length >= 8 &&
        password == confirm &&
        !tooYoung &&
        age >= 0

    Box(Modifier.fillMaxSize()) {
        AuthScaffold(background = Colors.Background) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(28)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(rdp(12)))
                XaniText(
                    "Finish your profile",
                    Type.H1,
                    color = Colors.Text,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(rdp(8)))
                XaniText(
                    "You can also log in with your username and password — please set a password.",
                    Type.Label,
                    color = Colors.Sub,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (user.email.isNotBlank()) {
                    Spacer(Modifier.height(rdp(6)))
                    XaniText(
                        user.email,
                        Type.Caption,
                        color = Colors.Muted,
                        align = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(rdp(24)))

                // The picture is its own write, not part of this form: the account is
                // already authenticated by the time this screen is reached, so tapping
                // here uploads immediately and the result is live everywhere at once.
                // Continue is never blocked on it.
                MyAvatar(
                    size = rdp(90),
                    name = fullName.ifBlank { username },
                    onPick = { photoSheet = true },
                )
                Spacer(Modifier.height(rdp(8)))
                XaniText(
                    when {
                        avatar.uploading -> "Uploading your picture…"
                        avatar.error.isNotEmpty() -> avatar.error
                        avatar.preview != null || snapshot.avatarUrl.isNotEmpty() -> "Change photo"
                        else -> "Add a profile photo"
                    },
                    Type.Caption,
                    color = if (avatar.error.isEmpty()) Dark.Primary else Colors.Error,
                )

                Spacer(Modifier.height(rdp(22)))

                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rdp(12)),
                ) {
                    CountedField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = "Full name",
                        maxChars = 30,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    )
                    CountedField(
                        value = username,
                        onValueChange = { username = sanitizeUsername(it) },
                        placeholder = "Username",
                        maxChars = 30,
                        sanitize = ::sanitizeUsername,
                        imeAction = ImeAction.Next,
                    )

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        XaniText("When's your birthday?", Type.LabelStrong, color = Colors.Text)
                        Spacer(Modifier.height(rdp(4)))
                        XaniText(
                            "Your birthday won't be shown publicly.",
                            Type.Caption,
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
                                "Sorry. Looks like you're not eligible for $appName.",
                                Type.Micro,
                                color = Colors.Error,
                            )
                        }
                    }

                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password (at least 8 characters)",
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed },
                        showStrength = true,
                        imeAction = ImeAction.Next,
                    )
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        placeholder = "Confirm password",
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed },
                        showStrength = false,
                        imeAction = ImeAction.Done,
                    )

                    if (password.isNotEmpty() && confirm.isNotEmpty() && password != confirm) {
                        XaniText("Passwords do not match.", Type.Caption, color = Colors.Error)
                    }
                }

                Spacer(Modifier.height(rdp(20)))

                AuthMessageCard(text = error)
                if (error.isNotEmpty()) Spacer(Modifier.height(rdp(10)))

                PrimaryCta(
                    text = "Continue",
                    enabled = valid && !busy,
                    loading = busy,
                    trailingArrow = true,
                    onClick = {
                        onSubmit(
                            CompleteProfileForm(
                                fullName = fullName.trim(),
                                username = username.trim(),
                                birthday = "%04d-%02d-%02d".format(year, month, day),
                                password = password,
                                confirmPassword = confirm,
                            ),
                        )
                    },
                )
                Spacer(Modifier.height(rdp(36)))
            }
        }

        if (photoSheet) {
            AvatarPickerSheet(copy = copy, onClose = { photoSheet = false })
        }
    }
}

private fun parseBirthday(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
