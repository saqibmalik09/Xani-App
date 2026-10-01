package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.net.HelpArticleData
import app.xani.core.net.Xani
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

private enum class TermsPane { Home, Article }

@Composable
internal fun TermsPoliciesScreen(
    copy: Copy,
    onBack: () -> Unit,
) {
    var pane by remember { mutableStateOf(TermsPane.Home) }
    var articleId by remember { mutableStateOf(0L) }

    BackHandler {
        when (pane) {
            TermsPane.Home -> onBack()
            TermsPane.Article -> pane = TermsPane.Home
        }
    }

    when (pane) {
        TermsPane.Home -> TermsHome(
            copy = copy,
            onBack = onBack,
            onArticle = { articleId = it; pane = TermsPane.Article },
        )
        TermsPane.Article -> TermsArticleDetail(
            copy = copy,
            id = articleId,
            onBack = { pane = TermsPane.Home },
        )
    }
}

@Composable
private fun TermsChrome(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
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
            XaniText(title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
        content()
    }
}

@Composable
private fun TermsHome(
    copy: Copy,
    onBack: () -> Unit,
    onArticle: (Long) -> Unit,
) {
    var articles by remember { mutableStateOf<List<HelpArticleData>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val r = runFeature(null) { Xani.support.legalArticles() }
        if (r?.ok == true) articles = r.value ?: emptyList()
        else error = r?.message ?: "Could not load terms."
    }
    TermsChrome(copy.t(Copy.ProfileTerms), onBack) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            if (error.isNotEmpty()) {
                item { XaniText(error, Type.Caption, color = Color(0xFFDC2626), fontSizeOverride = 13f) }
            }
            items(articles, key = { it.id }) { a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color.White)
                        .border(1.dp, ProfileTokens.Hairline, RoundedCornerShape(rdp(12)))
                        .pressable(onClick = { onArticle(a.id) })
                        .padding(rdp(14)),
                ) {
                    XaniText(a.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                    if (a.preview.isNotEmpty()) {
                        XaniText(
                            a.preview,
                            Type.Caption,
                            color = ProfileTokens.Sub,
                            fontSizeOverride = 13f,
                            modifier = Modifier.padding(top = rdp(6)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsArticleDetail(copy: Copy, id: Long, onBack: () -> Unit) {
    var article by remember { mutableStateOf<HelpArticleData?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        val r = runFeature(null) { Xani.support.legalArticle(id) }
        if (r?.ok == true) article = r.value
        else error = r?.message ?: "Not found."
    }
    TermsChrome(article?.title ?: copy.t(Copy.ProfileTerms), onBack) {
        val a = article
        if (a == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XaniText(error.ifEmpty { "…" }, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f)
            }
            return@TermsChrome
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(rdp(16))
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
        ) {
            XaniText(a.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 20f)
            XaniText(
                a.body,
                Type.Body,
                color = ProfileTokens.Text,
                fontSizeOverride = 15f,
                modifier = Modifier.padding(top = rdp(12)),
            )
            Row(
                Modifier.padding(top = rdp(20)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                TermsVoteChip("Like ${a.likeCount}", a.myVote == 1) {
                    scope.launch {
                        val next = if (a.myVote == 1) 0 else 1
                        val r = runFeature(null) { Xani.support.legalVote(id, next) }
                        if (r?.ok == true) article = r.value
                    }
                }
                TermsVoteChip("Dislike ${a.dislikeCount}", a.myVote == -1) {
                    scope.launch {
                        val next = if (a.myVote == -1) 0 else -1
                        val r = runFeature(null) { Xani.support.legalVote(id, next) }
                        if (r?.ok == true) article = r.value
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsVoteChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (on) ProfileTokens.Accent else Color.White)
            .border(1.dp, if (on) ProfileTokens.Accent else ProfileTokens.Hairline, RoundedCornerShape(rdp(20)))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(8)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ProfileTokens.Text, fontSizeOverride = 12f)
    }
}
