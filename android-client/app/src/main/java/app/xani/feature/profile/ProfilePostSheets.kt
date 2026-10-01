package app.xani.feature.profile

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.xani.core.net.PostData
import app.xani.feature.home.FeedRepo
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/**
 * What the author can do to their own post, from a long press or the viewer menu.
 *
 * Delete asks once — it is the one action here that cannot be undone — and both
 * actions answer through the post bus, so the tile, the feed card and any open
 * viewer all change from the same event instead of each re-reading.
 */
@Composable
internal fun ProfilePostActionsSheet(
    copy: Copy,
    post: PostData,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }

    fun dismiss() {
        if (working) return
        scope.launch {
            slide.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler { dismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.SheetScrim)
            .pressable(pressedScale = 1f) { dismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = size.height * slide.value }
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(ProfileTokens.Bg)
                .navigationBarsPadding()
                .padding(vertical = rdp(8)),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = rdp(6))
                    .width(rdp(36))
                    .height(rdp(4))
                    .clip(CircleShape)
                    .background(ProfileTokens.BorderStrong),
            )

            when {
                confirming -> {
                    XaniText(
                        copy.t(Copy.ProfilePostDeleteAsk),
                        Type.Caption,
                        color = ProfileTokens.Sub,
                        fontSizeOverride = 13f,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(24), vertical = rdp(12)),
                    )
                    SheetAction(copy.t(Copy.ProfilePostDeleteYes), ProfileTokens.Danger, enabled = !working) {
                        working = true
                        FeedRepo.delete(post.id) { ok ->
                            working = false
                            if (ok) onDeleted() else onClose()
                        }
                    }
                    SheetAction(copy.t(Copy.UploadCancel), ProfileTokens.Text, enabled = !working) { confirming = false }
                }
                privacy -> {
                    XaniText(
                        copy.t(Copy.UploadWhoCanSee),
                        Type.Caption,
                        color = ProfileTokens.Sub,
                        fontSizeOverride = 12f,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(24), vertical = rdp(8)),
                    )
                    PrivacyPick(copy.t(Copy.UploadPublic), post.visibility == "public", !working) {
                        setVisibility(post, "public", onWorking = { working = it }, onClose = onClose)
                    }
                    PrivacyPick(copy.t(Copy.UploadFriends), post.visibility == "friends", !working) {
                        setVisibility(post, "friends", onWorking = { working = it }, onClose = onClose)
                    }
                    PrivacyPick(copy.t(Copy.UploadPrivate), post.visibility == "private", !working) {
                        setVisibility(post, "private", onWorking = { working = it }, onClose = onClose)
                    }
                    SheetAction(copy.t(Copy.UploadCancel), ProfileTokens.Text, enabled = !working) { privacy = false }
                }
                else -> {
                    SheetAction(copy.t(Copy.ProfilePostEdit), ProfileTokens.Text) {
                        onEdit()
                        onClose()
                    }
                    SheetAction(copy.t(Copy.ProfileChangePrivacy), ProfileTokens.Text) { privacy = true }
                    SheetAction(copy.t(Copy.ProfilePostDelete), ProfileTokens.Danger) { confirming = true }
                }
            }
            Spacer(Modifier.height(rdp(6)))
        }
    }
}

private fun setVisibility(post: PostData, visibility: String, onWorking: (Boolean) -> Unit, onClose: () -> Unit) {
    if (post.visibility == visibility) {
        onClose()
        return
    }
    onWorking(true)
    FeedRepo.edit(postId = post.id, visibility = visibility) { ok ->
        onWorking(false)
        if (ok) onClose()
    }
}

@Composable
private fun SheetAction(label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    XaniText(
        label,
        Type.LabelBold,
        color = if (enabled) tint else ProfileTokens.Muted,
        fontSizeOverride = 15f,
        align = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = rdp(15)),
    )
}

@Composable
private fun PrivacyPick(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    XaniText(
        label,
        Type.LabelBold,
        color = when {
            !enabled -> ProfileTokens.Muted
            selected -> ProfileTokens.AccentDeep
            else -> ProfileTokens.Text
        },
        fontSizeOverride = 15f,
        align = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = rdp(13)),
    )
}

/**
 * Caption, audience and comments for a post that is already published.
 *
 * Saving does not reload anything: the server answers with the post as everyone
 * will now see it, that goes on the post bus, and the grid tile and the feed
 * card swap in place.
 */
@Composable
internal fun ProfilePostEditSheet(
    copy: Copy,
    post: PostData,
    onClose: () -> Unit,
) {
    var caption by remember(post.id) { mutableStateOf(post.caption) }
    var visibility by remember(post.id) { mutableStateOf(post.visibility) }
    var allowComments by remember(post.id) { mutableStateOf(post.allowComments) }
    var saving by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { slide.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }

    val dirty = caption != post.caption || visibility != post.visibility || allowComments != post.allowComments

    fun dismiss() {
        if (saving) return
        scope.launch {
            slide.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler { dismiss() }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = size.width * slide.value }
            .background(ProfileTokens.Bg)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(14), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(rdp(36))
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg2)
                    .pressable { dismiss() },
                contentAlignment = Alignment.Center,
            ) {
                XaniText("←", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
            }
            Spacer(Modifier.width(rdp(12)))
            XaniText(copy.t(Copy.ProfilePostEdit), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
            Spacer(Modifier.weight(1f))
            XaniText(
                copy.t(if (saving) Copy.ProfilePostSaving else Copy.ProfileSave),
                Type.LabelBold,
                color = if (dirty && !saving) ProfileTokens.AccentDeep else ProfileTokens.Muted,
                fontSizeOverride = 15f,
                modifier = Modifier.pressable(enabled = dirty && !saving) {
                    saving = true
                    FeedRepo.edit(
                        postId = post.id,
                        caption = caption.takeIf { it != post.caption },
                        visibility = visibility.takeIf { it != post.visibility },
                        allowComments = allowComments.takeIf { it != post.allowComments },
                    ) { ok ->
                        saving = false
                        if (ok) onClose()
                    }
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(rdp(14))
                .clip(RoundedCornerShape(rdp(14)))
                .background(ProfileTokens.Bg2)
                .padding(rdp(14)),
        ) {
            if (caption.isEmpty()) {
                XaniText(
                    copy.t(Copy.UploadCaptionHint),
                    Type.Caption,
                    color = ProfileTokens.Muted,
                    fontSizeOverride = 15f,
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = { if (it.length <= 2200) caption = it },
                textStyle = Type.Caption.copy(color = ProfileTokens.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(ProfileTokens.AccentDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = rdp(90)),
            )
        }

        XaniText(
            copy.t(Copy.UploadWhoCanSee),
            Type.LabelBold,
            color = ProfileTokens.Sub,
            fontSizeOverride = 12f,
            modifier = Modifier.padding(horizontal = rdp(18), vertical = rdp(6)),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(14)),
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            VisibilityChip(copy.t(Copy.UploadPublic), visibility == "public") { visibility = "public" }
            VisibilityChip(copy.t(Copy.UploadFriends), visibility == "friends") { visibility = "friends" }
            VisibilityChip(copy.t(Copy.UploadPrivate), visibility == "private") { visibility = "private" }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(18), vertical = rdp(18))
                .pressable(pressedScale = 1f) { allowComments = !allowComments },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(copy.t(Copy.UploadAllowComments), Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 15f)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(rdp(46))
                    .height(rdp(26))
                    .clip(CircleShape)
                    .background(if (allowComments) ProfileTokens.AccentDeep else ProfileTokens.Bg3),
                contentAlignment = if (allowComments) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = rdp(3))
                        .size(rdp(20))
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun VisibilityChip(label: String, on: Boolean, onClick: () -> Unit) {
    XaniText(
        label,
        if (on) Type.LabelBold else Type.Label,
        color = if (on) Color.White else ProfileTokens.Text,
        fontSizeOverride = 13f,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) ProfileTokens.AccentDeep else ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(16), vertical = rdp(9)),
    )
}
