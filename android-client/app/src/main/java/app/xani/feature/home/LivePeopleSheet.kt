package app.xani.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.xani.core.isolate.runFeature
import app.xani.core.net.LivePersonData
import app.xani.core.net.Xani
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Who is in the room right now: profile picture, full name, @username.
 * Loaded on open (bounded page) and re-read when the room's audience frame
 * moves — someone leaving drops off the list on that event, not on reopen.
 * Never polled.
 */
@Composable
internal fun LivePeopleSheet(
    roomId: Long,
    viewers: Long,
    copy: Copy,
    /** A row tapped: their card, in the room. */
    onUser: (LivePersonData) -> Unit,
    onClose: () -> Unit,
) {
    var items by remember(roomId) { mutableStateOf<List<LivePersonData>>(emptyList()) }
    var total by remember(roomId) { mutableStateOf(viewers) }
    var loading by remember(roomId) { mutableStateOf(true) }
    var failed by remember(roomId) { mutableStateOf(false) }

    LaunchedEffect(roomId, viewers) {
        // A count change while open is a join or a leave: coalesce a burst
        // into one re-read, and keep the current rows on screen meanwhile.
        if (items.isNotEmpty() || failed) delay(350)
        val r = withContext(Dispatchers.IO) {
            runFeature(null) { Xani.calls.livePeople(roomId, limit = 50) }
        }
        loading = false
        if (r?.ok == true && r.value != null) {
            items = r.value!!.items
            total = r.value!!.viewers
            failed = false
        } else if (items.isEmpty()) {
            failed = true
        }
    }

    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { _ ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(18))
                .padding(bottom = rdp(28)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(4), bottom = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.LivePeopleTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                Spacer(Modifier.weight(1f))
                XaniText(
                    compactCount(total),
                    Type.LabelBold,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSizeOverride = 13f,
                )
            }
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(rdp(120)),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText("…", Type.LabelBold, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 18f)
                }
                failed || items.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(rdp(120)),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        copy.t(Copy.LivePeopleEmpty),
                        Type.Caption,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSizeOverride = 13f,
                    )
                }
                else -> LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(rdp(360)),
                    verticalArrangement = Arrangement.spacedBy(rdp(4)),
                ) {
                    items(items, key = { it.id }) { p ->
                        PersonRow(p, copy, onClick = { onUser(p) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(p: LivePersonData, copy: Copy, onClick: () -> Unit) {
    val name = p.fullName.ifBlank { p.username }.ifBlank { "…" }
    val handle = if (p.username.isNotBlank()) "@${p.username}" else ""
    val badge = when (p.role) {
        "host" -> copy.t(Copy.LivePeopleHost)
        "guest" -> copy.t(Copy.LivePeopleGuest)
        else -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White.copy(alpha = 0.04f))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = rdp(10), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        PersonAvatar(p.id, name, p.avatarUrl, rdp(40), frameUrl = p.frameUrl, frameMime = p.frameMime)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(5))) {
                XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 14f, maxLines = 1)
                // Hosts wear what they have received; everyone else what they have given.
                LevelMark(if (p.role == "host") p.receiverLevel else p.senderLevel, rdp(16))
                if (p.badgeUrl.isNotEmpty()) NameBadge(p.badgeUrl, p.badgeMime, rdp(16))
            }
            if (handle.isNotEmpty()) {
                XaniText(handle, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 12f, maxLines = 1)
            }
        }
        if (badge.isNotEmpty()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Color(0x338B5CF6))
                    .padding(horizontal = rdp(8), vertical = rdp(3)),
            ) {
                XaniText(badge, Type.LabelBold, color = Color(0xFFC4B5FD), fontSizeOverride = 10f)
            }
        }
    }
}
