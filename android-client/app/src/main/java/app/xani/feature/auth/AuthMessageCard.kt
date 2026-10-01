package app.xani.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Auth status strip — same idea as TikTok / Instagram under a form: soft wash,
 * left accent, short copy. Not a bordered card.
 */
@Composable
fun AuthMessageCard(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = true,
) {
    if (text.isEmpty()) return
    val wash = if (error) Dark.Error.copy(alpha = 0.10f) else Dark.Primary.copy(alpha = 0.10f)
    val accent = if (error) Dark.Error else Dark.PrimarySoft
    val ink = if (error) Dark.Error else Dark.PrimarySoft
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(rdp(12)))
            .background(wash)
            .padding(start = rdp(10), end = rdp(14), top = rdp(12), bottom = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        Box(
            Modifier
                .width(rdp(3))
                .fillMaxHeight()
                .clip(RoundedCornerShape(rdp(2)))
                .background(accent),
        )
        XaniText(
            text = text,
            style = Type.Caption,
            color = ink,
            align = TextAlign.Start,
            modifier = Modifier.weight(1f),
            fontSizeOverride = 13f,
        )
    }
}
