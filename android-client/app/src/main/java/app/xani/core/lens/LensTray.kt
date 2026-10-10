package app.xani.core.lens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Snapchat-style category rail + circular carousel. Writes [LensController]
 * only — the GPU reads the latest state on the next camera frame, so picking
 * a look never remounts the TextureView or the live comment overlay.
 *
 * [compact] is the Go Live strip: shorter chips so chat stays on screen.
 */
@Composable
fun LensTray(
    controller: LensController,
    copy: Copy,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    initial: LensCatalog.Category = LensCatalog.Category.Looks,
) {
    val state by controller.state.collectAsState()
    var cat by remember { mutableStateOf(initial) }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) controller.setBackground(Background.Image(uri))
    }
    val chip = if (compact) rdp(56) else rdp(68)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = rdp(12)),
            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatChip(copy.t(Copy.CreateBeauty), cat == LensCatalog.Category.Beauty) { cat = LensCatalog.Category.Beauty }
            CatChip(copy.t(Copy.CreateLooks), cat == LensCatalog.Category.Looks) { cat = LensCatalog.Category.Looks }
            if (controller.caps.background) {
                CatChip(copy.t(Copy.CreateBackground), cat == LensCatalog.Category.Background) {
                    cat = LensCatalog.Category.Background
                }
            }
            if (controller.caps.masks) {
                CatChip(copy.t(Copy.CreateMasks), cat == LensCatalog.Category.Masks) { cat = LensCatalog.Category.Masks }
            }
        }
        Spacer(Modifier.height(rdp(8)))
        LazyRow(
            contentPadding = PaddingValues(horizontal = rdp(12)),
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            when (cat) {
                LensCatalog.Category.Beauty -> items(LensCatalog.beauty, key = { it.id }) { b ->
                    val on = LensCatalog.beautyPresetOf(state.beauty)?.id == b.id
                    LensChip(b.name, b.glyph, Color(0xFFEC4899), on, chip) { controller.setBeauty(b.beauty) }
                }
                LensCatalog.Category.Looks -> items(LensCatalog.looks, key = { it.id }) { look ->
                    val on = state.lookId == look.id
                    LensChip(look.name, null, argb(look.swatch), on, chip, swatch = argb(look.swatch)) {
                        controller.setLook(look.id)
                    }
                }
                LensCatalog.Category.Background -> {
                    items(LensCatalog.backgrounds, key = { LensCatalog.backgroundId(it) }) { bg ->
                        val on = sameBg(state.background, bg)
                        val (label, color) = bgLabel(bg, copy)
                        LensChip(label, glyphOf(bg), color, on, chip, swatch = color) { controller.setBackground(bg) }
                    }
                    item(key = "bg.photo") {
                        LensChip(copy.t(Copy.CreateBannerPhoto), "🖼", Color(0xFF6366F1), state.background is Background.Image, chip) {
                            pickBg.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        }
                    }
                }
                LensCatalog.Category.Masks -> items(LensCatalog.masks, key = { it.id }) { mask ->
                    val on = mask.id in state.masks
                    LensChip(mask.name, mask.glyph, Color(0xFFA855F7), on, chip) { controller.toggleMask(mask.id) }
                }
            }
        }
    }
}

@Composable
private fun CatChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(6)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color(0xFF111111) else Color.White, fontSizeOverride = 12f)
    }
}

@Composable
private fun LensChip(
    label: String,
    glyph: String?,
    fallback: Color,
    on: Boolean,
    size: androidx.compose.ui.unit.Dp,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (on) Modifier.border(rdp(2.5f), Color.White, CircleShape)
                    else Modifier.border(rdp(1f), Color.White.copy(alpha = 0.25f), CircleShape),
                )
                .background(swatch ?: fallback)
                .pressable(role = Role.Button, pressedScale = 0.92f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) {
                XaniText(glyph, Type.Label, color = Color.White, fontSizeOverride = if (size > rdp(60)) 22f else 18f)
            }
        }
        Spacer(Modifier.height(rdp(4)))
        XaniText(
            label,
            Type.Micro,
            color = if (on) Color.White else Color.White.copy(alpha = 0.7f),
            fontSizeOverride = 10f,
            maxLines = 1,
        )
    }
}

private fun sameBg(a: Background, b: Background): Boolean = when {
    a is Background.None && b is Background.None -> true
    a is Background.Blur && b is Background.Blur -> true
    a is Background.Gradient && b is Background.Gradient -> a.id == b.id
    else -> false
}

private fun glyphOf(bg: Background): String = when (bg) {
    Background.None -> "🚫"
    Background.Blur -> "🌫"
    is Background.Gradient -> "🎨"
    is Background.Image -> "🖼"
}

private fun bgLabel(bg: Background, copy: Copy): Pair<String, Color> = when (bg) {
    Background.None -> copy.t(Copy.CreateLensOff) to Color(0xFF374151)
    Background.Blur -> copy.t(Copy.CreateBgBlur) to Color(0xFF64748B)
    is Background.Gradient -> bg.id.removePrefix("bg.").replaceFirstChar { it.uppercase() } to argb(bg.top)
    is Background.Image -> copy.t(Copy.CreateBannerPhoto) to Color(0xFF6366F1)
}

/** Live-room effects button: opens the tray without covering comments. */
@Composable
fun LensLiveButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(rdp(40))
            .clip(CircleShape)
            .background(if (on) Color.White else Color.Black.copy(alpha = 0.45f))
            .border(rdp(1), Color.White.copy(alpha = 0.2f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText("✦", Type.LabelBold, color = if (on) Color(0xFF111111) else Color.White, fontSizeOverride = 16f)
    }
}

/** Scrim under the post carousel so the rings read on a bright scene. */
fun lensTrayScrim(): Brush = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
)

private fun argb(c: Int): Color = Color(
    red = ((c shr 16) and 0xFF) / 255f,
    green = ((c shr 8) and 0xFF) / 255f,
    blue = (c and 0xFF) / 255f,
    alpha = ((c ushr 24) and 0xFF) / 255f,
)
