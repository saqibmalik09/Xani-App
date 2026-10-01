package app.xani.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import app.xani.core.net.PersonData
import app.xani.core.net.Xani
import app.xani.feature.social.FollowStore
import app.xani.feature.social.PersonRouter
import app.xani.feature.social.PersonRow
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class RecentSearch(val id: Int, val text: String, val thumbColor: Long?)
private data class SearchSuggest(val text: String, val sub: String, val hot: Boolean)

private val Recents = listOf(
    RecentSearch(1, "trending dance challenge", null),
    RecentSearch(2, "cooking with asmr", null),
    RecentSearch(3, "", 0xFF7C3AED),
    RecentSearch(4, "viral cat videos funny", null),
    RecentSearch(5, "", 0xFFEC4899),
)

private val Suggestions = listOf(
    SearchSuggest("trending sounds this week", "Related to your search", true),
    SearchSuggest("funny fails compilation", "Just watched", true),
    SearchSuggest("morning routine aesthetic", "", false),
    SearchSuggest("street food pakistan", "", false),
    SearchSuggest("diy home decor ideas", "Related to your search", false),
    SearchSuggest("satisfying videos compilation", "", false),
    SearchSuggest("cricket highlights today", "", false),
    SearchSuggest("study with me lofi", "Related to your search", false),
)

/** Figma search page: white slide-up, recents + "You may like". */
@Composable
internal fun FeedSearchPage(
    copy: Copy,
    onClose: () -> Unit,
) {
    val enter = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var recents by remember { mutableStateOf(Recents) }
    // People results for the current query. Searches fire per keystroke after a
    // short settle so the list tracks typing without a request per character;
    // a stale answer (older query) is dropped by the key check.
    var people by remember { mutableStateOf<List<PersonData>>(emptyList()) }
    var searched by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val trimmed = query.trim()
    LaunchedEffect(trimmed) {
        if (trimmed.length < 2) {
            people = emptyList()
            searched = ""
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(180)
        val r = Xani.people.search(trimmed)
        r.value?.let {
            FollowStore.learn(it)
            people = it
            searched = trimmed
        }
        searching = false
    }

    LaunchedEffect(Unit) { enter.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            enter.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler(onBack = ::dismiss)

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = size.height * enter.value }
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        // Search bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(14), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            Box(Modifier.size(rdp(32)).pressable(onClick = ::dismiss), contentAlignment = Alignment.Center) {
                BackChevron(Color(0xFF111111))
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(rdp(22)))
                    .background(Color(0xFFF3F3F3))
                    .padding(horizontal = rdp(14), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                SearchGlyph(Color(0xFF888888), rdp(15))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    textStyle = Type.Caption.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Color(0xFF7C3AED)),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                XaniText(copy.t(Copy.HomeSearchHint), Type.Caption, color = Color(0xFF888888), fontSizeOverride = 15f)
                            }
                            inner()
                        }
                    },
                )
            }
            XaniText(
                copy.t(Copy.HomeSearchGo),
                Type.LabelBold,
                color = Color(0xFF7C3AED),
                fontSizeOverride = 14f,
                modifier = Modifier.pressable(onClick = { }),
            )
        }

        if (trimmed.length >= 2) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "peopleHead") {
                    XaniText(
                        copy.t(Copy.HomeSearchPeople),
                        Type.LabelBold,
                        color = Color(0xFF111111),
                        fontSizeOverride = 16f,
                        modifier = Modifier.padding(horizontal = rdp(18)).padding(top = rdp(10), bottom = rdp(4)),
                    )
                }
                if (people.isEmpty() && searched == trimmed && !searching) {
                    item(key = "none") {
                        XaniText(
                            copy.t(Copy.HomeSearchNoPeople, "q" to trimmed),
                            Type.Caption,
                            color = Color(0xFF888888),
                            fontSizeOverride = 14f,
                            modifier = Modifier.fillMaxWidth().padding(rdp(24)),
                            align = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(people, key = { it.id }) { p ->
                    // Tapping a hit opens that account's profile, which is also
                    // what records the visit on their side.
                    PersonRow(person = p, copy = copy, onOpen = { PersonRouter.open(it.id) })
                }
                item(key = "pad") { Spacer(Modifier.size(rdp(40))) }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(recents.size, key = { recents[it].id }) { i ->
                val r = recents[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { })
                        .padding(horizontal = rdp(18), vertical = rdp(11)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(12)),
                ) {
                    Box(
                        Modifier
                            .size(rdp(32))
                            .clip(if (r.thumbColor != null) RoundedCornerShape(rdp(8)) else CircleShape)
                            .background(if (r.thumbColor != null) Color(r.thumbColor) else Color(0xFFF3F3F3)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (r.thumbColor == null) {
                            VectorCanvas(
                                shapes = listOf(
                                    VShape.Circle(12f, 12f, 9f, stroke(Color(0xFF888888), 1.6f)),
                                    VShape.D("M12 7v5l3 3", stroke(Color(0xFF888888), 1.6f)),
                                ),
                                viewport = 24f,
                                modifier = Modifier.size(rdp(16)),
                            )
                        }
                    }
                    XaniText(
                        r.text.ifEmpty { copy.t(Copy.HomeSearchRecent) },
                        Type.Caption,
                        color = Color(0xFF111111),
                        fontSizeOverride = 15f,
                        modifier = Modifier.weight(1f),
                    )
                    XaniText(
                        "×",
                        Type.Label,
                        color = Color(0xFF888888),
                        fontSizeOverride = 18f,
                        modifier = Modifier.pressable(onClick = { recents = recents.filterNot { it.id == r.id } }),
                    )
                }
            }
            item(key = "seeMore") {
                XaniText(
                    copy.t(Copy.HomeSearchSeeMore) + " ▾",
                    Type.Caption,
                    color = Color(0xFF888888),
                    fontSizeOverride = 14f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { })
                        .padding(vertical = rdp(10)),
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            item(key = "youMayLike") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(18))
                        .padding(top = rdp(10), bottom = rdp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(copy.t(Copy.HomeSearchYouMayLike), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 16f)
                    Spacer(Modifier.weight(1f))
                    XaniText(
                        copy.t(Copy.HomeSearchRefresh),
                        Type.LabelBold,
                        color = Color(0xFF888888),
                        fontSizeOverride = 13f,
                        modifier = Modifier.pressable(onClick = { }),
                    )
                }
            }
            items(Suggestions.size, key = { "s$it" }) { i ->
                val s = Suggestions[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { })
                        .padding(horizontal = rdp(18), vertical = rdp(11)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(14)),
                ) {
                    Box(
                        Modifier
                            .padding(top = rdp(6))
                            .size(rdp(8))
                            .clip(CircleShape)
                            .background(if (s.hot) Color(0xFFEC4899) else Color(0xFFCCCCCC)),
                    )
                    Column(Modifier.weight(1f)) {
                        XaniText(
                            s.text,
                            if (s.hot) Type.LabelBold else Type.Caption,
                            color = if (s.hot) Color(0xFFEC4899) else Color(0xFF111111),
                            fontSizeOverride = 15f,
                        )
                        if (s.sub.isNotEmpty()) {
                            XaniText(
                                s.sub,
                                Type.Caption,
                                color = Color(0xFF888888),
                                fontSizeOverride = 12f,
                                modifier = Modifier.padding(top = rdp(2)),
                            )
                        }
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.size(rdp(40))) }
        }
    }
}
