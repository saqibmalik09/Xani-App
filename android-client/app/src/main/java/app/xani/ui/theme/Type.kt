package app.xani.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Figma uses Outfit (UI) and Fraunces (display). Drop the licensed TTFs into
 * `res/font/` and swap these two aliases; no other code changes.
 */
val Outfit: FontFamily = FontFamily.SansSerif
val Fraunces: FontFamily = FontFamily.Serif

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

object Type {
    val Wordmark = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp,
        letterSpacing = (-2.1).sp,
        lineHeightStyle = Trim,
    )
    val Tagline = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 3.4.sp,
    )
    val SlideTitle = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 41.sp,
        lineHeightStyle = Trim,
    )
    val SlideTag = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 2.2.sp,
    )
    val H1 = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.56).sp,
    )
    val H2 = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.52).sp,
    )
    val Body = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        lineHeightStyle = Trim,
    )
    val BodyLight = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = Trim,
    )
    val Label = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    )
    val LabelStrong = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
    val LabelBold = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
    val Caption = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = Trim,
    )
    val Micro = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = Trim,
    )
    val Otp = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}
