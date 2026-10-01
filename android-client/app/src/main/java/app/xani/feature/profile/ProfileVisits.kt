package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.LiveFeed
import app.xani.core.net.VisitData
import app.xani.core.net.Xani
import app.xani.feature.social.FollowButton
import app.xani.feature.social.FollowStore
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Who has been looking at this profile — the month, newest first.
 *
 * A row is a person, not a tap: somebody who comes back every day appears once,
 * at the top. That is what makes the list readable and what keeps it a keyset
 * read instead of a growing log.
 *
 * It stays live while open: a visit that lands on the profile stream re-reads
 * the first page, so the name of whoever just looked appears without a pull.
 */
@Composable
internal fun ProfileVisitsScreen(copy: Copy, snapshot: ProfileSnapshot, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var items by remember { mutableStateOf<List<VisitData>>(emptyList()) }
    var cursor by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    suspend fun fetch(after: String) {
        if (loading) return
        loading = true
        val page = Xani.people.visits(after).value
        if (page != null) {
            val rows = page.items.filter { it.person.id > 0L }
            FollowStore.learn(rows.map { it.person })
            items = if (after.isEmpty()) {
                rows
            } else {
                val seen = items.mapTo(HashSet()) { it.person.id }
                items + rows.filter { seen.add(it.person.id) }
            }
            cursor = page.next
        }
        loaded = true
        loading = false
    }

    LaunchedEffect(Unit) { fetch("") }
    // A new visitor arrived while the list is open: re-read the head only.
    LaunchedEffect(Unit) {
        LiveFeed.profileViews.collect { fetch("") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(rdp(32))
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg2)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
            Spacer(Modifier.width(rdp(12)))
            Column {
                XaniText(
                    copy.t(Copy.ProfileVisitsTitle),
                    Type.LabelBold,
                    color = ProfileTokens.Text,
                    fontSizeOverride = 17f,
                )
                XaniText(
                    copy.t(Copy.ProfileVisitsMonth),
                    Type.Micro,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 11f,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(5)),
            ) {
                ProfileIco.Eye(ProfileTokens.AccentDeep, rdp(17))
                XaniText(
                    snapshot.profileViews,
                    Type.LabelBold,
                    color = ProfileTokens.AccentDeep,
                    fontSizeOverride = 15f,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Border))

        LazyColumn(Modifier.weight(1f)) {
            if (loaded && items.isEmpty()) {
                item(key = "empty") {
                    XaniText(
                        copy.t(Copy.ProfileVisitsEmpty),
                        Type.Caption,
                        color = ProfileTokens.Muted,
                        fontSizeOverride = 14f,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(rdp(32)),
                    )
                }
            }
            items(items, key = { it.person.id }) { visit ->
                if (visit.person.id == items.lastOrNull()?.person?.id && cursor.isNotEmpty() && !loading) {
                    LaunchedEffect(visit.person.id) { fetch(cursor) }
                }
                VisitRow(visit = visit, copy = copy)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(20))
                        .height(rdp(1))
                        .background(ProfileTokens.RowLine),
                )
            }
        }
    }
}

/** One visitor: picture, handle, when they came by, and one button. */
@Composable
private fun VisitRow(visit: VisitData, copy: Copy) {
    val person = visit.person
    val relations by FollowStore.relations.collectAsState()
    val following = relations[person.id]?.following ?: person.following
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = { PersonRouter.open(person.id) })
            .padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            id = person.id,
            name = person.fullName.ifBlank { person.username },
            avatarUrl = person.avatarUrl,
            size = rdp(44),
        )
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(person.username, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                if (person.verified) {
                    Spacer(Modifier.width(rdp(4)))
                    ProfileIco.Verified(rdp(11))
                }
            }
            XaniText(
                visitAgo(visit.at, copy),
                Type.Micro,
                color = ProfileTokens.Sub,
                fontSizeOverride = 11f,
                modifier = Modifier.padding(top = rdp(2)),
            )
        }
        Spacer(Modifier.width(rdp(10)))
        FollowButton(
            following = following,
            followsYou = person.followsYou,
            copy = copy,
            onClick = { FollowStore.set(person.id, !following) },
        )
    }
}

/**
 * "3h ago" from the server's RFC-3339 stamp. Parsed with the platform's own
 * instant parser rather than a formatter, so a stamp from another timezone
 * still reads correctly on this device.
 */
private fun visitAgo(at: String, copy: Copy): String {
    val millis = runCatching { java.time.Instant.parse(at).toEpochMilli() }.getOrNull() ?: return ""
    val delta = (System.currentTimeMillis() - millis).coerceAtLeast(0L) / 1000L
    return when {
        delta < 60 -> copy.t(Copy.InboxJustNow)
        delta < 3600 -> "${delta / 60}m"
        delta < 86_400 -> "${delta / 3600}h"
        else -> "${delta / 86_400}d"
    }
}
