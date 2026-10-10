package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.xani.core.net.PersonData
import app.xani.core.net.TxnGiftGroupData
import app.xani.core.net.TxnGiftGroupsData
import app.xani.core.net.Xani
import app.xani.feature.social.FollowStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.feature.social.PersonRow
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun ProfileStatsScreen(
    copy: Copy,
    kind: ProfileStatsKind,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var period by remember { mutableStateOf(ReceivingPeriod.Daily) }
    val title = when (kind) {
        ProfileStatsKind.Following -> copy.t(Copy.ProfileFollowing)
        ProfileStatsKind.Followers -> copy.t(Copy.ProfileFollowers)
        ProfileStatsKind.Sending -> copy.t(Copy.ProfileSending)
        ProfileStatsKind.Receiving -> copy.t(Copy.ProfileReceiving)
    }
    // Gifting is a flow over a window; the follow graph is a list of people.
    val gifting = kind == ProfileStatsKind.Sending || kind == ProfileStatsKind.Receiving
    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(14)),
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
            XaniText(title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Border))

        if (gifting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(20), vertical = rdp(14)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                PeriodChip(Modifier.weight(1f), copy.t(Copy.ProfileDaily), period == ReceivingPeriod.Daily) { period = ReceivingPeriod.Daily }
                PeriodChip(Modifier.weight(1f), copy.t(Copy.ProfileWeekly), period == ReceivingPeriod.Weekly) { period = ReceivingPeriod.Weekly }
                PeriodChip(Modifier.weight(1f), copy.t(Copy.ProfileMonthly), period == ReceivingPeriod.Monthly) { period = ReceivingPeriod.Monthly }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Border))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (gifting) {
                GiftFlowPane(copy, period, sent = kind == ProfileStatsKind.Sending)
            } else {
                PeopleList(copy, kind)
            }
            Spacer(Modifier.height(rdp(24)))
        }
    }
}

/**
 * What gifting moved over the chosen window, and who it moved with: the people
 * this account sent to, or the people it took in from.
 *
 * One read per window against the statement endpoint — the same numbers the
 * transaction history shows, folded by person and already ordered biggest
 * first, so nothing here recomputes a total the ledger already knows. The last
 * answer for each window is kept, so coming back paints at once and re-reads
 * behind what is already on screen.
 */
@Composable
private fun GiftFlowPane(copy: Copy, period: ReceivingPeriod, sent: Boolean) {
    val key = "$sent/$period"
    var data by remember(key) { mutableStateOf(GiftFlowCache[key]) }
    var loading by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val today = LocalDate.now()
        val from = when (period) {
            ReceivingPeriod.Daily -> today
            ReceivingPeriod.Weekly -> today.with(DayOfWeek.MONDAY)
            ReceivingPeriod.Monthly -> today.withDayOfMonth(1)
        }
        // Only a window with nothing to show waits visibly.
        loading = data == null
        val got = Xani.wallet.giftGroups(from.format(IsoDay), today.format(IsoDay), sent = sent).value
        if (got != null) {
            data = got
            GiftFlowCache[key] = got
        }
        loading = false
    }

    val totals = data
    Row(
        Modifier
            .padding(rdp(20))
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .border(rdp(1), Color(0x266D28D9), RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.ReceivingWash)
            .padding(rdp(16)),
    ) {
        // Diamonds are what a gift earns, so they belong to the receiving side
        // only; spend is coins.
        if (!sent) {
            SummaryCell(compact(totals?.diamonds ?: 0L), copy.t(Copy.ProfileDiamonds)) {
                ProfileIco.Diamond(ProfileTokens.AccentDeep, rdp(22))
            }
        }
        SummaryCell(compact(totals?.coins ?: 0L), copy.t(Copy.ProfileCoins)) {
            ProfileIco.Coin(ProfileTokens.AccentDeep, rdp(22))
        }
        SummaryCell(compact(totals?.count ?: 0L), copy.t(Copy.ProfileGifts)) {
            ProfileIco.Path(
                "M20 12v8a2 2 0 01-2 2H6a2 2 0 01-2-2v-8M12 2v6m0 0l-3-3m3 3l3-3M4 10h16",
                ProfileTokens.AccentDeep,
                rdp(22),
            )
        }
    }
    XaniText(
        copy.t(if (sent) Copy.ProfileTopRecipients else Copy.ProfileTopSenders),
        Type.LabelBold,
        color = ProfileTokens.Sub,
        fontSizeOverride = 13f,
        modifier = Modifier.padding(horizontal = rdp(20)).padding(bottom = rdp(10)),
    )
    val people = data?.items.orEmpty().take(10)
    when {
        loading && people.isEmpty() -> Box(
            Modifier.fillMaxWidth().padding(rdp(24)),
            contentAlignment = Alignment.Center,
        ) { Spinner(rdp(20), ProfileTokens.Accent) }

        people.isEmpty() -> XaniText(
            copy.t(if (sent) Copy.ProfileSendingEmpty else Copy.ProfileReceivingEmpty),
            Type.Caption,
            color = ProfileTokens.Muted,
            fontSizeOverride = 14f,
            modifier = Modifier.fillMaxWidth().padding(rdp(32)),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )

        else -> people.forEachIndexed { i, g ->
            SenderRow(g, i + 1, sent)
            if (i < people.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.Border))
            }
        }
    }
}

/**
 * The last answer per window, for the life of the process. Gifting totals for a
 * window that has already been read are worth showing instantly: the screen is
 * opened, closed and opened again, and a spinner each time reads as the app
 * having forgotten.
 */
private object GiftFlowCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, TxnGiftGroupsData>()
    operator fun get(key: String): TxnGiftGroupsData? = map[key]
    operator fun set(key: String, value: TxnGiftGroupsData) {
        map[key] = value
    }
}

/**
 * The real follow graph, keyset paged from the server. The follow button on each
 * row is the app-wide relation, so unfollowing here flips the feed card too.
 */
@Composable
private fun PeopleList(copy: Copy, kind: ProfileStatsKind) {
    val me = ProfileStore.currentUserId
    var items by remember(kind) { mutableStateOf<List<PersonData>>(emptyList()) }
    var cursor by remember(kind) { mutableStateOf("") }
    var loaded by remember(kind) { mutableStateOf(false) }
    var loading by remember(kind) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(after: String) {
        if (me == 0L) return
        loading = true
        val r = if (kind == ProfileStatsKind.Followers) Xani.people.followers(me, after) else Xani.people.following(me, after)
        r.value?.let { page ->
            FollowStore.learn(page.items)
            val seen = items.mapTo(HashSet()) { it.id }
            items = if (after.isEmpty()) page.items else items + page.items.filter { seen.add(it.id) }
            cursor = page.next
            loaded = true
        }
        loading = false
    }
    LaunchedEffect(kind, me) { fetch("") }

    Column(Modifier.padding(vertical = rdp(8))) {
        if (loaded && items.isEmpty()) {
            XaniText(
                copy.t(Copy.ProfileNoPeople),
                Type.Caption,
                color = ProfileTokens.Muted,
                fontSizeOverride = 14f,
                modifier = Modifier.fillMaxWidth().padding(rdp(32)),
                align = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        items.forEachIndexed { i, p ->
            PersonRow(
                person = p,
                copy = copy,
                dark = true,
                showMeta = false,
                onOpen = { PersonRouter.open(it.id) },
            )
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.Border))
            }
            if (i == items.lastIndex && cursor.isNotEmpty() && !loading) {
                LaunchedEffect(p.id) { scope.launch { fetch(cursor) } }
            }
        }
    }
}

@Composable
private fun RowScope.SummaryCell(value: String, label: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(rdp(4)))
        XaniText(value, Type.LabelBold, color = ProfileTokens.AccentDeep, fontSizeOverride = 16f)
        XaniText(label, Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
    }
}

@Composable
private fun PeriodChip(modifier: Modifier, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(rdp(32))
            .clip(RoundedCornerShape(rdp(16)))
            .border(rdp(1), if (on) ProfileTokens.AccentDeep else ProfileTokens.Border, RoundedCornerShape(rdp(16)))
            .background(if (on) Color(0x1A6D28D9) else ProfileTokens.Bg2)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (on) ProfileTokens.AccentDeep else ProfileTokens.Sub, fontSizeOverride = 12f)
    }
}

@Composable
private fun SenderRow(group: TxnGiftGroupData, rank: Int, sent: Boolean) {
    val person = group.user
    val name = person.fullName.ifBlank { person.username }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = { if (person.id != 0L) PersonRouter.open(person.id) })
            .padding(horizontal = rdp(20), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(rdp(34))
                .clip(CircleShape)
                .background(ProfileTokens.Cta),
            contentAlignment = Alignment.Center,
        ) {
            XaniText("#$rank", Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
        }
        Spacer(Modifier.width(rdp(12)))
        PersonAvatar(person.id, name, person.avatarUrl, rdp(40), frameUrl = person.frameUrl, frameMime = person.frameMime)
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                XaniText(name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                // Who they are in this flow: a recipient wears their receiver
                // level, a sender their sender level.
                LevelMark(if (sent) person.receiverLevel else person.senderLevel, rdp(15))
            }
            if (person.username.isNotBlank()) {
                XaniText("@${person.username}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            // Sending is spend, so it is counted in coins; receiving is earnings,
            // counted in diamonds. Same convention as the statement.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sent) ProfileIco.Coin(ProfileTokens.AccentDeep, rdp(14))
                else ProfileIco.Diamond(ProfileTokens.AccentDeep, rdp(14))
                Spacer(Modifier.width(rdp(4)))
                XaniText(
                    compact(if (sent) group.coins else group.diamonds),
                    Type.LabelBold,
                    color = ProfileTokens.AccentDeep,
                    fontSizeOverride = 14f,
                )
            }
            XaniText(compact(group.count), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
        }
    }
}

private val IsoDay: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
