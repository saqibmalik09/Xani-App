package app.xani.feature.create

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Create visual tokens — one place to recolor the studio without hunting magic values.
 * Live setup uses the UpdatedFigma lavender light theme; capture chrome stays dark.
 */
internal object CreateTokens {
    val Scrim = Color.Black
    val Viewfinder = Color(0xFF0A0A12)
    val TopBarScrim = Color.Black.copy(alpha = 0.55f)
    val Glass = Color.Black.copy(alpha = 0.4f)
    val GlassSoft = Color.Black.copy(alpha = 0.35f)
    val ChipFill = Color.Black.copy(alpha = 0.55f)
    val ToolMuted = Color.White.copy(alpha = 0.75f)
    val ToolDim = Color.White.copy(alpha = 0.4f)
    val ModeOnBg = Color.White.copy(alpha = 0.92f)
    val ModeOnFg = Color.Black
    val ModeOffFg = Color.White.copy(alpha = 0.72f)
    val RecordAccent = Color(0xFFEF4444)
    val RecordRingA = Color(0xFF8B5CF6)
    val RecordRingB = Color(0xFFEC4899)
    val LaneActive = Color.White
    val LaneIdle = Color.White.copy(alpha = 0.45f)
    val GalleryRing = Color.White.copy(alpha = 0.55f)
    val GridLine = Color.White.copy(alpha = 0.18f)
    val BottomFade = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
    )
    val RecordWash = Brush.linearGradient(listOf(RecordRingA, RecordRingB))

    // Live setup (UpdatedFigma light lavender)
    val LiveAccent = Color(0xFF7C3AED)
    val LivePink = Color(0xFFEC4899)
    val LiveInk = Color(0xFF1E0A50)
    val LiveSub = Color(0xFF6450A0).copy(alpha = 0.6f)
    val LiveChip = Color(0xFF5B21B6)
    val LiveSetupBg = Color(0xFFF5F4FF)
    val LiveSetupWash = Brush.linearGradient(
        listOf(Color(0xFFF0EEFF), Color(0xFFF7F5FF), Color(0xFFEEF2FF), Color(0xFFF5F4FF)),
    )
    val LiveCta = Brush.horizontalGradient(listOf(LiveAccent, LivePink))
    val LiveCard = Color.White
    val LiveBorder = LiveAccent.copy(alpha = 0.2f)
    val LiveSoft = LiveAccent.copy(alpha = 0.08f)
    val LiveRoomDark = Color(0xFF06000F)
    val LivePill = Color.Black.copy(alpha = 0.52f)
}
