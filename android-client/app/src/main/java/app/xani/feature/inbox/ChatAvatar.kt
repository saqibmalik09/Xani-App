package app.xani.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.xani.core.media.RemoteImage
import app.xani.core.net.MediaConfig
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * The circle every inbox surface draws for a person: their picture when they
 * have one, otherwise the initials tile the design already uses. A small green
 * dot marks presence when [showOnline] is set.
 */
@Composable
internal fun ChatAvatar(
    chat: InboxChat,
    size: Dp,
    fontSize: Float = 13f,
    showOnline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val url = remember(chat.avatarUrl) { MediaConfig.url(chat.avatarUrl.ifEmpty { null }) }
    Box(modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(chat.tint),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotEmpty()) {
                RemoteImage(url, Modifier.fillMaxSize(), maxEdge = 256, fallback = Color.Transparent)
            } else {
                XaniText(chat.initials, Type.LabelBold, color = Color.White, fontSizeOverride = fontSize)
            }
        }
        if (showOnline && chat.online) {
            Box(
                Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Colors.Background)
                    .border(rdp(1.5f), Colors.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF31D158)))
            }
        }
    }
}
