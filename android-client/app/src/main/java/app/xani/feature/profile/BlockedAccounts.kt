package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.isolate.runFeature
import app.xani.core.net.ApiResponse
import app.xani.core.net.PeoplePage
import app.xani.core.net.PersonData
import app.xani.core.net.Xani
import app.xani.feature.inbox.InboxController
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

private enum class BlockedLane { Accounts, Live }

/**
 * Who the user has shut out, in two independent lists: accounts (no messages
 * or calls either way) and people kept out of the user's own lives (set by the
 * user or their moderators). Unblock waits for the server before the row goes.
 */
@Composable
internal fun BlockedAccountsScreen(copy: Copy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var lane by rememberSaveable { mutableStateOf(BlockedLane.Accounts) }
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
            Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                ProfileIco.Back(ProfileTokens.Text)
            }
            XaniText(copy.t(Copy.ProfileBlocked), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(16), vertical = rdp(8)),
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            LaneChip(Modifier.weight(1f), copy.t(Copy.BlockedChats), lane == BlockedLane.Accounts) { lane = BlockedLane.Accounts }
            LaneChip(Modifier.weight(1f), copy.t(Copy.BlockedLive), lane == BlockedLane.Live) { lane = BlockedLane.Live }
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
        when (lane) {
            BlockedLane.Accounts -> BlockedList(
                copy = copy,
                hint = copy.t(Copy.BlockedChatsHint),
                load = { Xani.people.blocked(it) },
                unblock = { id ->
                    val ok = Xani.chat.block(id, false).ok
                    if (ok) InboxController.syncPeerBlocked(id, false)
                    ok
                },
            )
            BlockedLane.Live -> BlockedList(
                copy = copy,
                hint = copy.t(Copy.BlockedLiveHint),
                load = { Xani.people.liveBlocked(it) },
                unblock = { id -> Xani.people.liveUnblock(id).ok },
            )
        }
    }
}

@Composable
private fun BlockedList(
    copy: Copy,
    hint: String,
    load: suspend (String) -> ApiResponse<PeoplePage>,
    unblock: suspend (Long) -> Boolean,
) {
    val items = remember(hint) { mutableStateListOf<PersonData>() }
    var cursor by remember(hint) { mutableStateOf("") }
    var loaded by remember(hint) { mutableStateOf(false) }
    var loading by remember(hint) { mutableStateOf(false) }
    var failed by remember(hint) { mutableStateOf(false) }
    val busy = remember(hint) { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    suspend fun fetch(after: String) {
        loading = true
        val page = runFeature(null as PeoplePage?) { load(after).value }
        if (page == null) {
            failed = after.isEmpty()
        } else {
            val seen = items.mapTo(HashSet()) { it.id }
            if (after.isEmpty()) items.clear()
            items.addAll(page.items.filter { after.isEmpty() || seen.add(it.id) })
            cursor = page.next
            loaded = true
            failed = false
        }
        loading = false
    }
    LaunchedEffect(hint) { fetch("") }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "hint") {
            XaniText(
                hint,
                Type.Caption,
                color = ProfileTokens.Muted,
                fontSizeOverride = 13f,
                modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(12)),
            )
        }
        when {
            failed -> item(key = "failed") {
                Column(Modifier.fillMaxWidth().padding(rdp(24)), horizontalAlignment = Alignment.CenterHorizontally) {
                    XaniText(copy.t(Copy.NotifyLoadFailed), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
                    XaniText(
                        copy.t(Copy.NotifyRetry),
                        Type.LabelBold,
                        color = ProfileTokens.Accent,
                        fontSizeOverride = 14f,
                        modifier = Modifier.padding(top = rdp(10)).pressable { scope.launch { fetch("") } },
                    )
                }
            }
            !loaded -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) {
                    Spinner(rdp(20), ProfileTokens.Accent)
                }
            }
            items.isEmpty() -> item(key = "empty") {
                XaniText(
                    copy.t(Copy.BlockedEmpty),
                    Type.Caption,
                    color = ProfileTokens.Muted,
                    fontSizeOverride = 14f,
                    modifier = Modifier.fillMaxWidth().padding(rdp(32)),
                    align = TextAlign.Center,
                )
            }
        }
        itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
            BlockedRow(p, copy, working = p.id in busy) {
                if (p.id in busy) return@BlockedRow
                busy.add(p.id)
                scope.launch {
                    val ok = runFeature(false) { unblock(p.id) }
                    busy.remove(p.id)
                    if (ok) items.removeAll { it.id == p.id }
                }
            }
            if (i == items.lastIndex && cursor.isNotEmpty() && !loading) {
                LaunchedEffect(p.id) { fetch(cursor) }
            }
        }
    }
}

@Composable
private fun BlockedRow(person: PersonData, copy: Copy, working: Boolean, onUnblock: () -> Unit) {
    val display = person.fullName.ifBlank { person.username }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = rdp(18), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.id, display, person.avatarUrl, rdp(44), frameUrl = person.frameUrl, frameMime = person.frameMime)
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(display, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
            if (person.username.isNotBlank()) {
                XaniText("@${person.username}", Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f, maxLines = 1)
            }
        }
        Spacer(Modifier.width(rdp(10)))
        val shape = RoundedCornerShape(rdp(18))
        Box(
            Modifier
                .clip(shape)
                .border(rdp(1), ProfileTokens.Hairline, shape)
                .pressable(onClick = onUnblock)
                .padding(horizontal = rdp(14), vertical = rdp(7)),
            contentAlignment = Alignment.Center,
        ) {
            if (working) {
                Spinner(rdp(14), ProfileTokens.Accent)
            } else {
                XaniText(copy.t(Copy.InboxUnblock), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f)
            }
        }
    }
}

@Composable
private fun LaneChip(modifier: Modifier, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(rdp(32))
            .clip(RoundedCornerShape(rdp(16)))
            .background(if (on) ProfileTokens.Accent else ProfileTokens.Bg2)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ProfileTokens.Text, fontSizeOverride = 13f)
    }
}
