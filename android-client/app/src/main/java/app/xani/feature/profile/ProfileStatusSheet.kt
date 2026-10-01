package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.xani.i18n.Copy
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MaxStatusChars = 60

/**
 * Status editor — slides in from the right like Create Text.
 * Typing auto-saves; no Confirm button.
 */
@Composable
internal fun ProfileStatusSheet(
    copy: Copy,
    displayName: String,
    initial: String,
    onClose: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var closing by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        slide.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
    }

    // Typing saves itself: the store applies the text at once and collapses the
    // burst into one request, so there is no Save button and no spinner.
    LaunchedEffect(draft) {
        delay(300)
        ProfileStore.updateStatus(draft)
    }

    // Closing the sheet mid-keystroke must not lose the last word.
    DisposableEffect(Unit) {
        onDispose { ProfileStore.updateStatus(draft) }
    }

    fun dismiss() {
        if (closing) return
        closing = true
        ProfileStore.updateStatus(draft)
        scope.launch {
            slide.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    BackHandler(enabled = !closing) { dismiss() }

    val hint = copy.t(Copy.ProfileWhatsOnMind)
    val fontSize = when {
        draft.length > 40 -> 22f
        draft.length > 24 -> 26f
        else -> 30f
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = size.width * slide.value }
            .background(ProfileTokens.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(12)),
        ) {
            Box(
                Modifier
                    .size(rdp(40))
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg2)
                    .border(rdp(1), ProfileTokens.Border, CircleShape)
                    .pressable { dismiss() }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                XaniText("←", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 18f)
            }
            XaniText(
                copy.t(Copy.ProfileStatusTitle),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 16f,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(Modifier.height(rdp(28)))

        Box(
            modifier = Modifier
                .size(rdp(120))
                .clip(CircleShape)
                .background(ProfileTokens.AvatarRing)
                .padding(rdp(4)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFF0EEFF))
                    .border(rdp(3), Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { XaniMark(rdp(56)) }
        }

        XaniText(
            displayName,
            Type.LabelBold,
            color = ProfileTokens.Text,
            fontSizeOverride = 18f,
            modifier = Modifier.padding(top = rdp(14)),
        )

        Spacer(Modifier.height(rdp(28)))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = rdp(28)),
            contentAlignment = Alignment.TopCenter,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= MaxStatusChars) draft = it },
                textStyle = Type.Body.copy(
                    color = ProfileTokens.Text,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.35f).sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(ProfileTokens.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.TopCenter) {
                        if (draft.isEmpty()) {
                            XaniText(
                                hint,
                                Type.Body,
                                color = ProfileTokens.Muted,
                                fontSizeOverride = 28f,
                                align = TextAlign.Center,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        XaniText(
            "${draft.length}/$MaxStatusChars",
            Type.Micro,
            color = ProfileTokens.Muted,
            fontSizeOverride = 11f,
            modifier = Modifier.padding(bottom = rdp(24)),
        )
    }
}
