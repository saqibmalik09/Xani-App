package app.xani.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Dark {
    val Background = Color(0xFF07070F)
    val Foreground = Color(0xFFF0EEFF)
    val Card = Color(0xFF111124)
    val Primary = Color(0xFF8B5CF6)
    val PrimarySoft = Color(0xFFA78BFA)
    val Accent = Color(0xFFEC4899)
    val AccentSoft = Color(0xFFF472B6)
    val Secondary = Color(0xFF1A1A35)
    val MutedFg = Color(0xFF7C7CA8)
    val Dim = Color(0xFF5C5C84)
    val Faint = Color(0xFF4A4A72)
    val Cyan = Color(0xFF22D3EE)
    val Gold = Color(0xFFFCD34D)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFF59E0B)
    // Lighter than Light.Error: the same 0xEF4444 on a near-black surface fails
    // contrast for body text.
    val Error = Color(0xFFF87171)
    val WordmarkStart = Color(0xFFE2D9FF)
    val WordmarkEnd = Color(0xFFF9D6EE)
    val MarkStart = Color(0xFFA78BFA)
    val MarkEnd = Color(0xFFF472B6)
}

object Light {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF7F7F7)
    val Input = Color(0xFFFAFAFA)
    val Text = Color(0xFF111111)
    val TextSoft = Color(0xFF555555)
    val Sub = Color(0xFF888888)
    val Hint = Color(0xFF999999)
    val Muted = Color(0xFFAAAAAA)
    val Border = Color(0xFFEBEBEB)
    val BorderStrong = Color(0xFFDBDBDB)
    val BorderIcon = Color(0xFFD0D0D0)
    val Disabled = Color(0xFFE5E5E5)
    val Divider = Color(0xFFE8E8E8)
    val Error = Color(0xFFEF4444)
}

val BrandGradient = Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))
val WordmarkGradient = Brush.linearGradient(listOf(Dark.WordmarkStart, Dark.WordmarkEnd))

/**
 * Surfaces that used to be the static [Light] palette. Follows the device
 * appearance; [LightColors] is the default when night mode is off or unset.
 */
@Immutable
data class XaniColors(
    val isDark: Boolean,
    val Background: Color,
    val Surface: Color,
    val Input: Color,
    val Text: Color,
    val TextSoft: Color,
    val Sub: Color,
    val Hint: Color,
    val Muted: Color,
    val Border: Color,
    val BorderStrong: Color,
    val BorderIcon: Color,
    val Disabled: Color,
    val Divider: Color,
    val Error: Color,
)

val LightColors = XaniColors(
    isDark = false,
    Background = Light.Background,
    Surface = Light.Surface,
    Input = Light.Input,
    Text = Light.Text,
    TextSoft = Light.TextSoft,
    Sub = Light.Sub,
    Hint = Light.Hint,
    Muted = Light.Muted,
    Border = Light.Border,
    BorderStrong = Light.BorderStrong,
    BorderIcon = Light.BorderIcon,
    Disabled = Light.Disabled,
    Divider = Light.Divider,
    Error = Light.Error,
)

val NightColors = XaniColors(
    isDark = true,
    Background = Dark.Background,
    Surface = Dark.Card,
    Input = Dark.Secondary,
    Text = Dark.Foreground,
    TextSoft = Color(0xFFC4C4DC),
    Sub = Dark.MutedFg,
    Hint = Dark.Dim,
    Muted = Dark.Faint,
    Border = Color(0xFF252542),
    BorderStrong = Color(0xFF3D3D5C),
    BorderIcon = Dark.Faint,
    Disabled = Dark.Secondary,
    Divider = Color(0xFF252542),
    Error = Light.Error,
)

val LocalXaniColors = staticCompositionLocalOf { LightColors }

val Colors: XaniColors
    @Composable
    @ReadOnlyComposable
    get() = LocalXaniColors.current
