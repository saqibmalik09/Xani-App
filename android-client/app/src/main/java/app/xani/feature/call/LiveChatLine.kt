package app.xani.feature.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xani.core.net.LiveChatData
import app.xani.feature.social.AssetAnimation
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonAvatar
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * One line of room chat, the same on the host's and the viewer's screen:
 * the sender's picture on the left; their full name, gifting level and
 * badge on the top row (plus "@who" a sticker is for); the message — or the
 * sticker — underneath, then the emoji people put on it. A line still in
 * flight (id 0) is dimmed until the server echoes it. Nothing is drawn for
 * a level or badge the person does not have, so a plain viewer's line stays
 * plain.
 *
 * [onUser] fires on a tap of the picture or the name; [onHold] on a long
 * press anywhere on the line (the reaction row). Either may be null.
 */
@Composable
fun LiveChatLine(
    m: LiveChatData,
    nameTint: Color,
    background: Color,
    corner: Dp,
    badgeSize: Dp,
    nameSize: Float = 11f,
    textSize: Float = 12f,
    stickerSize: Dp = rdp(72),
    avatarSize: Dp = rdp(28),
    onUser: (() -> Unit)? = null,
    onHold: (() -> Unit)? = null,
    onReact: ((String) -> Unit)? = null,
) {
    val name = m.user.fullName.ifBlank { m.user.username }.ifBlank { "…" }
    val settled = m.id != 0L
    val to = m.to?.let { p -> p.fullName.ifBlank { p.username } }?.takeIf { it.isNotBlank() }
    val gestures = if (onHold != null || onUser != null) {
        Modifier.combinedClickable(onClick = { onUser?.invoke() }, onLongClick = { if (settled) onHold?.invoke() })
    } else Modifier
    Row(
        Modifier
            .clip(RoundedCornerShape(corner))
            .background(background)
            .then(gestures)
            .padding(horizontal = rdp(8), vertical = rdp(5)),
        verticalAlignment = Alignment.Top,
    ) {
        PersonAvatar(
            m.user.id, name, m.user.avatarUrl, avatarSize,
            frameUrl = m.user.frameUrl, frameMime = m.user.frameMime,
        )
        Spacer(Modifier.width(rdp(8)))
        Column(verticalArrangement = Arrangement.spacedBy(rdp(2))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(5)),
            ) {
                XaniText(name, Type.LabelBold, color = nameTint, fontSizeOverride = nameSize, maxLines = 1)
                LevelMark(m.user.senderLevel, badgeSize)
                if (m.user.badgeUrl.isNotEmpty()) {
                    NameBadge(m.user.badgeUrl, m.user.badgeMime, badgeSize)
                }
                if (m.user.mod) {
                    XaniText("MOD", Type.LabelBold, color = Color(0xFF34D399), fontSizeOverride = 8f, modifier = Modifier.clip(RoundedCornerShape(rdp(4))).background(Color(0x3334D399)).padding(horizontal = rdp(4), vertical = rdp(1)))
                }
                if (to != null) {
                    XaniText("@$to", Type.LabelBold, color = Color(0xFFA78BFA), fontSizeOverride = nameSize, maxLines = 1)
                }
            }
            if (m.text.isNotEmpty()) {
                XaniText(
                    m.text,
                    Type.Caption,
                    color = Color.White.copy(alpha = if (settled) 0.92f else 0.6f),
                    fontSizeOverride = textSize,
                )
            }
            m.sticker?.let { s ->
                AssetAnimation(s.imageUrl, s.imageMime, Modifier.size(stickerSize), maxEdge = 240)
            }
            if (m.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = rdp(2)), horizontalArrangement = Arrangement.spacedBy(rdp(4))) {
                    // A handful at most: a line rarely draws more than a few distinct emoji.
                    m.reactions.entries.sortedByDescending { it.value }.take(5).forEach { (emoji, n) ->
                        val mine = emoji == m.myReaction
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(rdp(10)))
                                .background(if (mine) Color(0x558B5CF6) else Color.White.copy(alpha = 0.12f))
                                .then(if (mine) Modifier.border(1.dp, Color(0xAA8B5CF6), RoundedCornerShape(rdp(10))) else Modifier)
                                .then(if (onReact != null) Modifier.pressable(pressedScale = 0.9f, onClick = { onReact(emoji) }) else Modifier)
                                .padding(horizontal = rdp(6), vertical = rdp(1)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(3)),
                        ) {
                            XaniText(emoji, Type.Caption, fontSizeOverride = 11f)
                            if (n > 1) XaniText("$n", Type.LabelBold, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 10f)
                        }
                    }
                }
            }
        }
    }
}
