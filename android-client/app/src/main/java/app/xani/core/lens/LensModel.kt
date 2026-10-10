package app.xani.core.lens

import android.net.Uri
import app.xani.core.device.DeviceClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Xani Lens — the app's own real-time camera effect service.
 *
 * One immutable [LensState] describes everything the GPU applies to a frame:
 * skin work, a colour look, what replaces the background and which face masks
 * are worn. The state is data, so a filter pack from the server (or a licensed
 * B2B catalogue) is just more rows in the same tables — nothing here is an
 * enum the renderer switches on.
 *
 * Every surface that owns a camera (Create, Go Live, 1:1 calls) reads the
 * state through a [LensController]; the engine that renders it is chosen by
 * the host (Camera2 → [LensEngine], libwebrtc/LiveKit → the video processor).
 */
data class Beauty(
    /** Edge-preserving skin smoothing, 0..1. */
    val smooth: Float = 0f,
    /** Skin brightening, 0..1. */
    val whiten: Float = 0f,
    /** Micro-contrast on non-skin detail (hair, eyes, lashes), 0..1. */
    val sharpen: Float = 0f,
    /** Eye enlargement, 0..0.3. */
    val eyes: Float = 0f,
    /** Jaw slimming, 0..0.3. */
    val slim: Float = 0f,
    /** Warm "glow" lift on skin highlights, 0..1. */
    val glow: Float = 0f,
) {
    val active: Boolean get() = smooth > 0f || whiten > 0f || sharpen > 0f || eyes > 0f || slim > 0f || glow > 0f
    val needsFace: Boolean get() = eyes > 0f || slim > 0f || smooth > 0f || whiten > 0f || glow > 0f

    companion object {
        val Off = Beauty()
    }
}

/** Colour grade. All neutral at zero; `saturation`/`contrast` are deltas. */
data class Look(
    val id: String,
    val name: String,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val hue: Float = 0f,
    /** Split toning: shadows → `shadowRgb`, highlights → `highRgb`, 0..1 strength. */
    val split: Float = 0f,
    val shadowRgb: Int = 0xFF2A4B8D.toInt(),
    val highRgb: Int = 0xFFF2B36B.toInt(),
    /** 1 = monochrome. */
    val mono: Float = 0f,
    /** Film grain, 0..1. */
    val grain: Float = 0f,
    /** Swatch shown in the tray. */
    val swatch: Int = 0xFF9CA3AF.toInt(),
) {
    val neutral: Boolean
        get() = exposure == 0f && contrast == 0f && saturation == 0f && temperature == 0f && tint == 0f &&
            fade == 0f && vignette == 0f && hue == 0f && split == 0f && mono == 0f && grain == 0f
}

sealed class Background {
    data object None : Background()

    /** Portrait-mode style: the real room, defocused. */
    data object Blur : Background()

    /** Vertical two-stop gradient, GPU-generated — zero assets. */
    data class Gradient(val id: String, val top: Int, val bottom: Int) : Background()

    /** A picture the user chose; decoded once, uploaded once. */
    data class Image(val uri: Uri) : Background()

    val isNone: Boolean get() = this is None
}

/**
 * A face-anchored overlay. Art is drawn procedurally ([LensStickers]) so a
 * mask is a few hundred bytes of description, never a bitmap download.
 */
data class Mask(
    val id: String,
    val name: String,
    val layers: List<MaskLayer>,
    /** Emoji glyph for the tray; the real art renders on the face. */
    val glyph: String,
)

/** Where a layer sits on the face, in units of the distance between the eyes. */
data class MaskLayer(
    val art: String,
    val anchor: Anchor,
    /** Width as a multiple of inter-eye distance. */
    val width: Float,
    /** Height / width of the drawn bitmap. */
    val aspect: Float,
    /** Shift along the face's up vector, in inter-eye units (+ = up). */
    val up: Float = 0f,
) {
    enum class Anchor { EyesMid, NoseTip, Mouth, Forehead }
}

data class LensState(
    val beauty: Beauty = Beauty.Off,
    val lookId: String = LensCatalog.NaturalLook,
    val background: Background = Background.None,
    val masks: List<String> = emptyList(),
) {
    /** Nothing to do: the engine copies the frame through untouched. */
    val identity: Boolean
        get() = !beauty.active && lookId == LensCatalog.NaturalLook && background.isNone && masks.isEmpty()

    val needsFace: Boolean get() = beauty.needsFace || masks.isNotEmpty()
    val needsSegmentation: Boolean get() = !background.isNone
}

/**
 * What a device can run at 30 fps without dropping frames in a live room.
 * Segmentation and the smoothing kernel are the two expensive pieces.
 */
data class LensCaps(
    val background: Boolean,
    val masks: Boolean,
    /** Long edge of the processing canvas. */
    val workLongEdge: Int,
    /** Run the ML analysis every Nth frame. */
    val analyzeEvery: Int,
) {
    companion object {
        fun forDevice(tier: DeviceClass): LensCaps = when (tier) {
            DeviceClass.Low -> LensCaps(background = false, masks = true, workLongEdge = 640, analyzeEvery = 3)
            DeviceClass.Mid -> LensCaps(background = true, masks = true, workLongEdge = 960, analyzeEvery = 2)
            DeviceClass.High -> LensCaps(background = true, masks = true, workLongEdge = 1280, analyzeEvery = 1)
        }
    }
}

/**
 * Shared, observable effect state for one camera surface. UI writes, the
 * renderer reads the latest value on every frame — no callbacks into GL.
 */
class LensController(initial: LensState = LensState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<LensState> = _state

    /** Device limits, applied at write time so the renderer never sees a request it cannot serve. */
    @Volatile var caps: LensCaps = LensCaps.forDevice(DeviceClass.High)

    val value: LensState get() = _state.value

    fun setBeauty(b: Beauty) = update { it.copy(beauty = b) }

    fun setLook(id: String) = update { it.copy(lookId = if (LensCatalog.look(id) != null) id else LensCatalog.NaturalLook) }

    fun setBackground(bg: Background) = update { it.copy(background = if (caps.background || bg.isNone) bg else it.background) }

    fun toggleMask(id: String) = update { s ->
        if (!caps.masks || LensCatalog.mask(id) == null) return@update s
        val next = if (id in s.masks) s.masks - id else (s.masks + id).takeLast(MaxMasks)
        s.copy(masks = next)
    }

    fun clearMasks() = update { it.copy(masks = emptyList()) }

    fun reset() = update { LensState() }

    private inline fun update(f: (LensState) -> LensState) {
        while (true) {
            val cur = _state.value
            val next = f(cur)
            if (cur == next || _state.compareAndSet(cur, next)) return
        }
    }

    companion object {
        const val MaxMasks = 3
    }
}

/** Process-wide controllers: one per camera owner, so Create's picks do not leak into a live room. */
object Lens {
    val post = LensController()
    val live = LensController()
}

/**
 * Built-in catalogue. Server-delivered packs merge into these lists at
 * runtime; ids are stable strings so a saved draft keeps its look.
 */
object LensCatalog {
    const val NaturalLook = "look.natural"

    enum class Category { Beauty, Looks, Background, Masks }

    data class BeautyPreset(val id: String, val name: String, val beauty: Beauty, val glyph: String)

    val beauty: List<BeautyPreset> = listOf(
        BeautyPreset("b.off", "Off", Beauty.Off, "○"),
        BeautyPreset("b.soft", "Soft", Beauty(smooth = 0.48f, sharpen = 0.14f), "✦"),
        BeautyPreset("b.natural", "Natural", Beauty(smooth = 0.62f, whiten = 0.18f, sharpen = 0.22f, eyes = 0.07f, glow = 0.12f), "✧"),
        BeautyPreset("b.glow", "Glow", Beauty(smooth = 0.72f, whiten = 0.32f, sharpen = 0.26f, glow = 0.55f, eyes = 0.1f), "✨"),
        BeautyPreset("b.glam", "Glam", Beauty(smooth = 0.86f, whiten = 0.4f, sharpen = 0.3f, eyes = 0.16f, slim = 0.16f, glow = 0.42f), "💄"),
        BeautyPreset("b.porcelain", "Porcelain", Beauty(smooth = 0.94f, whiten = 0.52f, sharpen = 0.34f, eyes = 0.18f, slim = 0.2f, glow = 0.55f), "🤍"),
        BeautyPreset("b.sculpt", "Sculpt", Beauty(smooth = 0.52f, sharpen = 0.38f, eyes = 0.22f, slim = 0.28f, glow = 0.1f), "🪞"),
    )

    val looks: List<Look> = listOf(
        Look(NaturalLook, "Natural", swatch = 0xFFE5E7EB.toInt()),
        Look("look.vivid", "Vivid", contrast = 0.16f, saturation = 0.40f, exposure = 0.04f, swatch = 0xFFF97316.toInt()),
        Look("look.clarity", "Clarity", contrast = 0.26f, saturation = 0.12f, exposure = 0.07f, swatch = 0xFF60A5FA.toInt()),
        Look("look.golden", "Golden Hour", temperature = 0.30f, tint = 0.05f, exposure = 0.06f, contrast = 0.10f, fade = 0.06f, split = 0.42f, shadowRgb = 0xFF4A3A7E.toInt(), highRgb = 0xFFFFC66B.toInt(), swatch = 0xFFF59E0B.toInt()),
        Look("look.teal", "Teal & Orange", split = 0.66f, shadowRgb = 0xFF0E6B74.toInt(), highRgb = 0xFFFF9D5C.toInt(), contrast = 0.16f, saturation = 0.14f, swatch = 0xFF14B8A6.toInt()),
        Look("look.film.warm", "Warm Film", temperature = 0.19f, fade = 0.20f, contrast = -0.06f, saturation = -0.06f, grain = 0.24f, vignette = 0.24f, swatch = 0xFFD97706.toInt()),
        Look("look.film.cool", "Cool Film", temperature = -0.21f, tint = -0.05f, fade = 0.18f, saturation = -0.08f, grain = 0.24f, vignette = 0.24f, swatch = 0xFF3B82F6.toInt()),
        Look("look.pastel", "Pastel", fade = 0.28f, saturation = -0.14f, exposure = 0.11f, contrast = -0.14f, swatch = 0xFFF9A8D4.toInt()),
        Look("look.rose", "Rosé", tint = 0.18f, temperature = 0.08f, saturation = 0.14f, fade = 0.08f, swatch = 0xFFFB7185.toInt()),
        Look("look.peach", "Peach", temperature = 0.17f, tint = 0.11f, exposure = 0.06f, saturation = 0.16f, swatch = 0xFFFDBA74.toInt()),
        Look("look.cinematic", "Cinema", contrast = 0.26f, saturation = -0.04f, fade = 0.10f, vignette = 0.30f, split = 0.48f, shadowRgb = 0xFF1E3A5F.toInt(), highRgb = 0xFFE8C9A0.toInt(), swatch = 0xFF1F2937.toInt()),
        Look("look.drama", "Drama", contrast = 0.38f, saturation = 0.18f, exposure = -0.05f, vignette = 0.26f, swatch = 0xFF7C3AED.toInt()),
        Look("look.ice", "Ice", temperature = -0.32f, tint = -0.06f, contrast = 0.14f, saturation = 0.10f, exposure = 0.08f, swatch = 0xFF67E8F9.toInt()),
        Look("look.cherry", "Cherry", tint = 0.24f, temperature = 0.11f, saturation = 0.26f, contrast = 0.12f, swatch = 0xFFE11D48.toInt()),
        Look("look.cyber", "Cyber", contrast = 0.32f, saturation = 0.55f, temperature = -0.15f, split = 0.75f, shadowRgb = 0xFF4C1D95.toInt(), highRgb = 0xFF22D3EE.toInt(), vignette = 0.26f, swatch = 0xFF7C3AED.toInt()),
        Look("look.sunset", "Sunset", temperature = 0.36f, tint = 0.08f, exposure = 0.05f, split = 0.58f, shadowRgb = 0xFF7C2D12.toInt(), highRgb = 0xFFFDBA74.toInt(), swatch = 0xFFEA580C.toInt()),
        Look("look.neon", "Neon Night", contrast = 0.26f, saturation = 0.46f, temperature = -0.10f, split = 0.52f, shadowRgb = 0xFF3B0764.toInt(), highRgb = 0xFF22D3EE.toInt(), vignette = 0.32f, swatch = 0xFFA855F7.toInt()),
        Look("look.vintage", "Vintage", fade = 0.26f, temperature = 0.14f, saturation = -0.20f, contrast = -0.05f, vignette = 0.36f, grain = 0.32f, swatch = 0xFFB45309.toInt()),
        Look("look.noir", "Noir", mono = 1f, contrast = 0.34f, exposure = -0.02f, vignette = 0.40f, grain = 0.26f, swatch = 0xFF374151.toInt()),
        Look("look.silver", "Silver", mono = 1f, contrast = 0.12f, exposure = 0.09f, fade = 0.13f, swatch = 0xFF9CA3AF.toInt()),
        Look("look.sepia", "Sepia", mono = 1f, split = 0.92f, shadowRgb = 0xFF3F2A14.toInt(), highRgb = 0xFFE4C590.toInt(), contrast = 0.10f, vignette = 0.24f, swatch = 0xFF92400E.toInt()),
        Look("look.punch", "Punch", contrast = 0.32f, saturation = 0.50f, exposure = 0.05f, swatch = 0xFFEF4444.toInt()),
        Look("look.matte", "Matte", fade = 0.32f, contrast = -0.10f, saturation = -0.10f, vignette = 0.20f, swatch = 0xFF78716C.toInt()),
        Look("look.forest", "Forest", temperature = -0.07f, tint = -0.12f, saturation = 0.22f, contrast = 0.14f, split = 0.40f, shadowRgb = 0xFF14532D.toInt(), highRgb = 0xFFBBF7D0.toInt(), swatch = 0xFF16A34A.toInt()),
        Look("look.candy", "Candy", saturation = 0.46f, tint = 0.18f, temperature = 0.10f, exposure = 0.07f, swatch = 0xFFF472B6.toInt()),
    )

    val backgrounds: List<Background> = listOf(
        Background.None,
        Background.Blur,
        Background.Gradient("bg.studio", 0xFF111827.toInt(), 0xFF000000.toInt()),
        Background.Gradient("bg.sunset", 0xFFFF7E5F.toInt(), 0xFFFEB47B.toInt()),
        Background.Gradient("bg.ocean", 0xFF2193B0.toInt(), 0xFF6DD5ED.toInt()),
        Background.Gradient("bg.candy", 0xFFEC4899.toInt(), 0xFF8B5CF6.toInt()),
        Background.Gradient("bg.mint", 0xFF10B981.toInt(), 0xFFA7F3D0.toInt()),
        Background.Gradient("bg.midnight", 0xFF0F172A.toInt(), 0xFF4C1D95.toInt()),
        Background.Gradient("bg.gold", 0xFFB45309.toInt(), 0xFFFDE68A.toInt()),
        Background.Gradient("bg.aurora", 0xFF022C22.toInt(), 0xFF6D28D9.toInt()),
        Background.Gradient("bg.stage", 0xFF111111.toInt(), 0xFF7C3AED.toInt()),
        Background.Gradient("bg.sky", 0xFF38BDF8.toInt(), 0xFFE0F2FE.toInt()),
        Background.Gradient("bg.rose", 0xFF9F1239.toInt(), 0xFFFDA4AF.toInt()),
        Background.Gradient("bg.neon", 0xFF0F172A.toInt(), 0xFFDB2777.toInt()),
        Background.Gradient("bg.forest", 0xFF052E16.toInt(), 0xFF166534.toInt()),
        Background.Gradient("bg.lava", 0xFF7C2D12.toInt(), 0xFFF97316.toInt()),
        Background.Gradient("bg.snow", 0xFFE2E8F0.toInt(), 0xFFF8FAFC.toInt()),
    )

    val masks: List<Mask> = listOf(
        Mask("m.shades", "Shades", listOf(MaskLayer(LensStickers.Sunglasses, MaskLayer.Anchor.EyesMid, 2.7f, 0.42f)), "🕶️"),
        Mask("m.hearts", "Heart Eyes", listOf(MaskLayer(LensStickers.HeartGlasses, MaskLayer.Anchor.EyesMid, 2.9f, 0.5f)), "😍"),
        Mask("m.neon", "Neon", listOf(MaskLayer(LensStickers.NeonGlasses, MaskLayer.Anchor.EyesMid, 2.8f, 0.42f)), "🟦"),
        Mask("m.cat", "Kitty", listOf(
            MaskLayer(LensStickers.CatEars, MaskLayer.Anchor.Forehead, 3.0f, 0.5f, up = 0.75f),
            MaskLayer(LensStickers.CatNose, MaskLayer.Anchor.NoseTip, 1.6f, 0.55f, up = -0.1f),
        ), "🐱"),
        Mask("m.puppy", "Puppy", listOf(
            MaskLayer(LensStickers.DogEars, MaskLayer.Anchor.Forehead, 3.4f, 0.62f, up = 0.35f),
            MaskLayer(LensStickers.DogNose, MaskLayer.Anchor.NoseTip, 1.2f, 0.72f, up = -0.05f),
        ), "🐶"),
        Mask("m.bunny", "Bunny", listOf(MaskLayer(LensStickers.BunnyEars, MaskLayer.Anchor.Forehead, 2.4f, 1.1f, up = 1.35f)), "🐰"),
        Mask("m.crown", "Crown", listOf(MaskLayer(LensStickers.Crown, MaskLayer.Anchor.Forehead, 2.5f, 0.62f, up = 0.9f)), "👑"),
        Mask("m.flowers", "Flower Crown", listOf(MaskLayer(LensStickers.FlowerCrown, MaskLayer.Anchor.Forehead, 3.2f, 0.4f, up = 0.6f)), "🌸"),
        Mask("m.devil", "Devil", listOf(MaskLayer(LensStickers.DevilHorns, MaskLayer.Anchor.Forehead, 2.6f, 0.6f, up = 0.95f)), "😈"),
        Mask("m.stache", "Moustache", listOf(MaskLayer(LensStickers.Moustache, MaskLayer.Anchor.Mouth, 1.9f, 0.42f, up = 0.28f)), "🥸"),
        Mask("m.blush", "Blush", listOf(MaskLayer(LensStickers.Blush, MaskLayer.Anchor.NoseTip, 3.1f, 0.34f, up = -0.05f)), "☺️"),
        Mask("m.freckles", "Freckles", listOf(MaskLayer(LensStickers.Freckles, MaskLayer.Anchor.NoseTip, 2.6f, 0.4f, up = 0.1f)), "🙂"),
        Mask("m.stars", "Star Eyes", listOf(MaskLayer(LensStickers.StarGlasses, MaskLayer.Anchor.EyesMid, 2.8f, 0.48f)), "⭐"),
        Mask("m.angel", "Angel", listOf(MaskLayer(LensStickers.Halo, MaskLayer.Anchor.Forehead, 2.4f, 0.45f, up = 1.1f)), "😇"),
        Mask("m.sparkle", "Sparkle", listOf(MaskLayer(LensStickers.Sparkle, MaskLayer.Anchor.EyesMid, 3.4f, 0.7f, up = 0.15f)), "✨"),
        Mask("m.wings", "Wings", listOf(MaskLayer(LensStickers.Wings, MaskLayer.Anchor.Forehead, 4.2f, 0.7f, up = 0.2f)), "🪽"),
        Mask("m.bow", "Bow", listOf(MaskLayer(LensStickers.Bow, MaskLayer.Anchor.Forehead, 1.6f, 0.85f, up = 1.05f)), "🎀"),
        Mask("m.tears", "Tears", listOf(MaskLayer(LensStickers.Tears, MaskLayer.Anchor.EyesMid, 2.6f, 0.7f, up = -0.35f)), "😢"),
    )

    private val lookIndex = looks.associateBy { it.id }
    private val maskIndex = masks.associateBy { it.id }
    private val beautyIndex = beauty.associateBy { it.id }

    fun look(id: String): Look? = lookIndex[id]
    fun mask(id: String): Mask? = maskIndex[id]
    fun beautyPreset(id: String): BeautyPreset? = beautyIndex[id]

    /** The preset whose values match, for tray selection; null for a custom slider mix. */
    fun beautyPresetOf(b: Beauty): BeautyPreset? = beauty.firstOrNull { it.beauty == b }

    fun backgroundId(bg: Background): String = when (bg) {
        Background.None -> "bg.none"
        Background.Blur -> "bg.blur"
        is Background.Gradient -> bg.id
        is Background.Image -> "bg.image"
    }
}
