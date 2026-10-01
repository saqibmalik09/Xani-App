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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
internal fun ProfileSettingsScreen(
    copy: Copy,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    passwordLoginActive: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val passwordLabel = if (passwordLoginActive) {
        copy.t(Copy.ProfileChangePassword)
    } else {
        copy.t(Copy.ProfileActivatePassword)
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
            XaniText(copy.t(Copy.ProfileSettings), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
        ) {
            SettingsGroup(
                title = copy.t(Copy.ProfileDrawerAccount),
                items = listOf(
                    copy.t(Copy.ProfileSwitchAccounts) to "switch",
                    passwordLabel to "password",
                ),
                onAction = onAction,
            )
            SettingsGroup(
                title = copy.t(Copy.ProfileWallet),
                items = listOf(
                    copy.t(Copy.TxnTitle) to "txn",
                    copy.t(Copy.WithdrawTitle) to "withdraw",
                ),
                onAction = onAction,
            )
            SettingsGroup(
                title = copy.t(Copy.ProfilePrivacySafety),
                items = listOf(
                    copy.t(Copy.ProfilePrivacy) to "privacy",
                    copy.t(Copy.ProfileDms) to "dms",
                    copy.t(Copy.ProfileBlocked) to "blocked",
                    copy.t(Copy.ProfileSafeMode) to "safe",
                ),
                onAction = onAction,
            )
            SettingsGroup(
                title = copy.t(Copy.ProfileContentDisplay),
                items = listOf(
                    copy.t(Copy.ProfileDisplay) to "display",
                    copy.t(Copy.ProfileLanguage) to "language",
                    copy.t(Copy.ProfileTranslation) to "translation",
                ),
                onAction = onAction,
            )
            SettingsGroup(
                title = copy.t(Copy.ProfileNotifications),
                items = listOf(copy.t(Copy.ProfileNotifications) to "notifications"),
                onAction = onAction,
            )
            SettingsGroup(
                title = copy.t(Copy.ProfileSupportLegal),
                items = listOf(
                    copy.t(Copy.ProfileHelpCentre) to "help",
                    copy.t(Copy.ProfileTerms) to "terms",
                    copy.t(Copy.ProfileLocationContacts) to "location",
                ),
                onAction = onAction,
            )

            // Log out — Figma drawer also exposes this; settings is the other entry.
            Box(
                Modifier
                    .padding(horizontal = rdp(16))
                    .padding(top = rdp(28))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(ProfileTokens.DangerSoft)
                    .pressable(onClick = { onAction("logout") })
                    .padding(vertical = rdp(14)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileIco.Logout(ProfileTokens.Danger, rdp(18))
                    Spacer(Modifier.size(rdp(10)))
                    XaniText(
                        copy.t(Copy.ProfileLogOut),
                        Type.LabelBold,
                        color = ProfileTokens.Danger,
                        fontSizeOverride = 15f,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    items: List<Pair<String, String>>,
    onAction: (String) -> Unit,
) {
    XaniText(
        title.uppercase(),
        Type.Micro,
        color = Color(0xFFAAAAAA),
        fontSizeOverride = 12f,
        modifier = Modifier.padding(start = rdp(20), end = rdp(20), top = rdp(24), bottom = rdp(8)),
    )
    Column(
        Modifier
            .padding(horizontal = rdp(16))
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color.White),
    ) {
        items.forEachIndexed { i, (label, key) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = { onAction(key) })
                    .padding(horizontal = rdp(18), vertical = rdp(15)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(label, Type.Label, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.weight(1f))
                ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(16))
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
            }
        }
    }
}
