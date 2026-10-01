package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.components.PasswordField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/**
 * Settings → Password: social accounts activate email/username login; accounts
 * that already have a password update it with the current one.
 */
@Composable
internal fun ProfilePasswordScreen(
    copy: Copy,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var passwordActive by remember {
        mutableStateOf(Xani.session.lastKnownUser?.passwordLoginActive == true)
    }
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val me = Xani.auth.me()
        if (me.ok) {
            val user = me.data?.first ?: return@LaunchedEffect
            passwordActive = user.passwordLoginActive
            Xani.session.updateUser(user)
        }
    }

    val match = password.isEmpty() || confirm.isEmpty() || password == confirm
    val valid = if (passwordActive) {
        current.length >= 8 && password.length >= 8 && password == confirm && password != current
    } else {
        password.length >= 8 && password == confirm
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg2)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(40)).pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { ProfileIco.Back(ProfileTokens.Text) }
            XaniText(
                if (passwordActive) copy.t(Copy.ProfileChangePassword)
                else copy.t(Copy.ProfileActivatePassword),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 17f,
            )
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = rdp(20), vertical = rdp(24)),
        ) {
            XaniText(
                if (passwordActive) copy.t(Copy.ProfileChangePasswordHint)
                else copy.t(Copy.ProfileActivatePasswordHint),
                Type.Caption,
                color = Color(0xFF888888),
                fontSizeOverride = 13f,
            )
            Spacer(Modifier.height(rdp(20)))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color.White)
                    .padding(horizontal = rdp(16), vertical = rdp(18)),
            ) {
                if (passwordActive) {
                    PasswordField(
                        value = current,
                        onValueChange = { current = it; error = "" },
                        placeholder = copy.t(Copy.ProfileCurrentPassword),
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed },
                        imeAction = ImeAction.Next,
                    )
                    Spacer(Modifier.height(rdp(12)))
                }
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    placeholder = copy.t(Copy.ProfileNewPassword),
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    showStrength = true,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(rdp(12)))
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it; error = "" },
                    placeholder = copy.t(Copy.ProfileConfirmPassword),
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    imeAction = ImeAction.Done,
                )
                if (!match) {
                    Spacer(Modifier.height(rdp(10)))
                    XaniText(
                        copy.t(Copy.ProfilePasswordMismatch),
                        Type.Caption,
                        color = Colors.Error,
                    )
                }
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(rdp(14)))
                XaniText(
                    error,
                    Type.Caption,
                    color = Colors.Error,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(rdp(24)))
            PrimaryCta(
                text = if (passwordActive) copy.t(Copy.ProfileUpdatePassword)
                else copy.t(Copy.ProfileActivatePassword),
                enabled = valid && !busy,
                loading = busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = ""
                        val response = if (passwordActive) {
                            Xani.auth.changePassword(current, password, confirm)
                        } else {
                            Xani.auth.activatePassword(password, confirm)
                        }
                        busy = false
                        if (!response.ok) {
                            error = response.message.ifBlank {
                                copy.t(Copy.FeatureUnavailable)
                            }
                            return@launch
                        }
                        val user = Xani.session.lastKnownUser
                        if (user != null) {
                            Xani.session.updateUser(user.copy(passwordLoginActive = true))
                        }
                        passwordActive = true
                        onBack()
                    }
                },
            )
        }
    }
}
