package app.xani.feature.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

private data class TextPalette(val brush: Brush, val dark: Boolean, val white: Boolean = false)

private val Palettes = listOf(
    TextPalette(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))), dark = false),
    TextPalette(Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFEF4444))), dark = false),
    TextPalette(Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))), dark = false),
    TextPalette(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))), dark = false),
    TextPalette(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))), dark = false),
    TextPalette(Brush.linearGradient(listOf(Color(0xFF1A0533), Color(0xFF07070F))), dark = true),
    TextPalette(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))), dark = true),
    TextPalette(Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF333333))), dark = true),
    TextPalette(Brush.linearGradient(listOf(Color.White, Color.White)), dark = false, white = true),
    TextPalette(Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFCA5A5))), dark = false),
)

private val TextFonts = listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Cursive, FontFamily.Monospace)

private const val MaxChars = 300

/**
 * UpdatedFigma "Share your thoughts" text post. Full screen, slides in from the
 * right; gradient card with centered input, bold + font cycle, palette swatches.
 */
@Composable
internal fun CreateTextPost(
    copy: Copy,
    onClose: () -> Unit,
    onPost: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var bold by rememberSaveable { mutableStateOf(false) }
    var fontIdx by rememberSaveable { mutableIntStateOf(0) }
    var palette by rememberSaveable { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        slide.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
    }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            slide.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    BackHandler(enabled = !closing) { dismiss() }

    val pal = Palettes[palette]
    val ink = when {
        pal.white -> Color(0xFF111111)
        pal.dark -> Color.White.copy(alpha = 0.92f)
        else -> Color.White
    }
    val hint = if (pal.white) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.3f)
    val fontSize = when {
        text.length > 120 -> 16f
        text.length > 60 -> 20f
        text.length > 30 -> 26f
        else -> 32f
    }
    val canPost = text.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = size.width * slide.value }
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar — close · title · bold + font cycle
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(rdp(40))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .pressable { dismiss() },
                contentAlignment = Alignment.Center,
            ) { CreateIco.Close(Color.White, rdp(18)) }
            Spacer(Modifier.weight(1f))
            XaniText(copy.t(Copy.CreateTextTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(6)), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(rdp(38))
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(if (bold) Color(0x598B5CF6) else Color.White.copy(alpha = 0.1f))
                        .then(if (bold) Modifier.border(rdp(1.5f), Color(0xFF8B5CF6), RoundedCornerShape(rdp(10))) else Modifier)
                        .pressable { bold = !bold },
                    contentAlignment = Alignment.Center,
                ) { XaniText("B", Type.LabelBold, color = Color.White, fontSizeOverride = 15f) }
                Box(
                    Modifier
                        .height(rdp(38))
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(Color.White.copy(alpha = 0.1f))
                        .pressable { fontIdx = (fontIdx + 1) % TextFonts.size }
                        .padding(horizontal = rdp(12)),
                    contentAlignment = Alignment.Center,
                ) { XaniText("Aa", Type.Label, color = Color.White, fontSizeOverride = 13f) }
            }
        }

        // Gradient card with centered input
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = rdp(16))
                .clip(RoundedCornerShape(rdp(20)))
                .background(pal.brush)
                .padding(rdp(28)),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= MaxChars) text = it },
                textStyle = Type.Body.copy(
                    color = ink,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.45f).sp,
                    textAlign = TextAlign.Center,
                    fontFamily = TextFonts[fontIdx],
                    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
                ),
                cursorBrush = SolidColor(ink),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            XaniText(
                                copy.t(Copy.CreateTextType),
                                Type.Body,
                                color = hint,
                                fontSizeOverride = 32f,
                                align = TextAlign.Center,
                            )
                        }
                        inner()
                    }
                },
            )
            if (text.isNotEmpty()) {
                XaniText(
                    "${text.length}/$MaxChars",
                    Type.Micro,
                    color = ink.copy(alpha = 0.4f),
                    fontSizeOverride = 11f,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // Palette swatches
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = rdp(16), vertical = rdp(14)),
            horizontalArrangement = Arrangement.spacedBy(rdp(8), Alignment.CenterHorizontally),
        ) {
            Palettes.forEachIndexed { i, p ->
                val on = i == palette
                Box(
                    Modifier
                        .size(rdp(28))
                        .clip(CircleShape)
                        .then(
                            if (on) Modifier.border(rdp(3), Color.White, CircleShape)
                            else Modifier.border(rdp(2), Color.White.copy(alpha = 0.18f), CircleShape),
                        )
                        .background(p.brush)
                        .pressable { palette = i },
                )
            }
        }

        // Next
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16))
                .padding(bottom = rdp(20))
                .clip(RoundedCornerShape(rdp(14)))
                .then(
                    if (canPost) Modifier.background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                    else Modifier.background(Color.White.copy(alpha = 0.07f)),
                )
                .pressable(enabled = canPost) { onPost(text.trim()) }
                .padding(vertical = rdp(15)),
            contentAlignment = Alignment.Center,
        ) {
            XaniText(
                copy.t(Copy.CreateNext),
                Type.LabelBold,
                color = if (canPost) Color.White else Color.White.copy(alpha = 0.25f),
                fontSizeOverride = 16f,
            )
        }
    }
}
