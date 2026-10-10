package app.xani.feature.profile

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.xani.core.net.ContactMatch
import app.xani.core.net.ContactSync
import app.xani.core.net.Xani
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Find friends: a switch and the platform's recommendations — accounts matched
 * from the user's synced contacts. No notices or explanatory copy; the matches
 * are the content. Contact sync itself runs in [ContactSync], gated on the OS
 * contacts permission, whether or not the switch here is on.
 */
@Composable
internal fun FindFriendsScreen(copy: Copy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val state by ContactSync.state.collectAsState()
    val enabled = state?.enabled == true

    var matches by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        ContactSync.setEnabled(granted)
        if (granted) ContactSync.syncNow()
    }

    LaunchedEffect(Unit) {
        ContactSync.reportGallery()
        val res = Xani.contacts.matches()
        if (res.ok) matches = res.data ?: emptyList()
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
            XaniText(copy.t(Copy.FindFriendsTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            item {
                FindFriendsToggle(
                    copy = copy,
                    enabled = enabled,
                    onToggle = { want ->
                        if (want && !ContactSync.hasContactsPermission(context)) {
                            launcher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            ContactSync.setEnabled(want)
                            if (want) ContactSync.syncNow()
                        }
                    },
                )
            }

            items(matches.size, key = { matches[it].id }) { i ->
                RecommendationRow(matches[i])
            }
        }
    }
}

@Composable
private fun FindFriendsToggle(copy: Copy, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color.White)
            .padding(rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(
            copy.t(Copy.FindFriendsEnable),
            Type.LabelBold,
            color = ProfileTokens.Text,
            fontSizeOverride = 15f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(rdp(12)))
        PillSwitch(on = enabled, onToggle = onToggle)
    }
}

@Composable
private fun RecommendationRow(match: ContactMatch) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color.White)
            .pressable(onClick = { if (match.id != 0L) PersonRouter.open(match.id) })
            .padding(rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(match.id, match.displayName, match.avatarUrl, rdp(44))
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(match.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
            if (match.username.isNotBlank()) {
                XaniText("@${match.username}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PillSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    val track = if (on) ProfileTokens.Accent else Color(0xFFD5D5D5)
    Box(
        Modifier
            .size(width = rdp(46), height = rdp(28))
            .clip(CircleShape)
            .background(track)
            .pressable(onClick = { onToggle(!on) })
            .padding(rdp(3)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(rdp(22)).clip(CircleShape).background(Color.White))
    }
}
