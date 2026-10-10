package app.xani.feature.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.xani.core.media.RemoteImage
import app.xani.core.net.MediaConfig
import app.xani.core.net.PersonData
import app.xani.feature.home.FeedRepo
import app.xani.feature.profile.ProfileIco
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/** Palette used for every avatar fallback so a person looks the same on every surface. */
private val Tints = listOf(
    0xFF8B5CF6 to 0xFFEC4899, 0xFF10B981 to 0xFF3B82F6, 0xFFF59E0B to 0xFFEF4444,
    0xFF06B6D4 to 0xFF8B5CF6, 0xFFEC4899 to 0xFFF59E0B, 0xFF34D399 to 0xFF60A5FA,
)

internal fun tintsFor(id: Long): Pair<Long, Long> = Tints[((id ushr 5) % Tints.size).toInt().coerceAtLeast(0)]

/**
 * Avatar: the person's picture when they have one, initials on their gradient
 * otherwise — and, when they wear one, their frame ringed around it. Callers
 * whose payload carries the frame pass it in (and teach [FrameStore]); every
 * other surface reads the store by id, so one purchase updates all of them.
 */
@Composable
fun PersonAvatar(
    id: Long,
    name: String,
    avatarUrl: String,
    size: Dp,
    frameUrl: String? = null,
    frameMime: String = "",
) {
    if (frameUrl != null) {
        SideEffect { FrameStore.learn(id, frameUrl, frameMime) }
    }
    val frames by FrameStore.frames.collectAsState()
    val worn = frames[id]
    val fUrl = frameUrl?.ifEmpty { null } ?: worn?.url.orEmpty()
    val fMime = if (!frameUrl.isNullOrEmpty()) frameMime else worn?.mime.orEmpty()
    val (a, b) = tintsFor(id)
    // The outer box is the layout slot; the picture clips to the circle inside
    // it and the frame overflows it, so rows and grids never re-measure.
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(a), Color(b)))),
            contentAlignment = Alignment.Center,
        ) {
            val url = MediaConfig.avatarUrl(avatarUrl)
            if (url.isNotEmpty()) {
                RemoteImage(url = url, modifier = Modifier.size(size), maxEdge = 160, fallback = Color.Transparent)
            } else {
                XaniText(FeedRepo.initials(name), Type.LabelBold, color = Color.White, fontSizeOverride = (size.value * 0.36f))
            }
        }
        if (fUrl.isNotEmpty()) AvatarFrame(fUrl, fMime, size)
    }
}

/**
 * One person as every list shows them, with the follow button bound to
 * [FollowStore] — so a tap here flips the same button on the feed card.
 *
 * [showMeta] is what separates the two places this row appears. In search the
 * follower count is the signal that tells you whether you found the right
 * account. In a follower or following list the row is about recognising a
 * person and sizing them up as a gifter: full name on top, the handle under
 * it, and their lifetime gifting totals — sent and received, in coins — with
 * the follower count alongside.
 */
@Composable
fun PersonRow(
    person: PersonData,
    copy: Copy,
    dark: Boolean = false,
    showMeta: Boolean = true,
    onOpen: (PersonData) -> Unit = {},
) {
    val relations by FollowStore.relations.collectAsState()
    val rel = relations[person.id]
    val following = rel?.following ?: person.following
    val followers = rel?.followers?.takeIf { it >= 0 } ?: person.followers
    val ink = if (dark) Color.White else Color(0xFF111111)
    val sub = if (dark) Color(0xFFAAAAAA) else Color(0xFF888888)
    val display = person.fullName.ifBlank { person.username }

    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = { onOpen(person) })
            .padding(horizontal = rdp(18), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.id, display, person.avatarUrl, rdp(46), frameUrl = person.frameUrl, frameMime = person.frameMime)
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (person.badgeUrl.isNotEmpty()) {
                    NameBadge(person.badgeUrl, person.badgeMime, rdp(16))
                    Spacer(Modifier.width(rdp(5)))
                }
                XaniText(
                    display,
                    Type.LabelBold,
                    color = ink,
                    fontSizeOverride = 14f,
                )
                if (person.senderLevel != null) {
                    Spacer(Modifier.width(rdp(5)))
                    LevelMark(person.senderLevel, rdp(16))
                }
                if (person.verified) {
                    Spacer(Modifier.width(rdp(4)))
                    XaniText("✓", Type.LabelBold, color = Color(0xFF7C3AED), fontSizeOverride = 12f)
                }
            }
            if (showMeta) {
                val meta = buildString {
                    append('@').append(person.username)
                    append(" · ").append(copy.t(Copy.HomeSearchFollowers, "n" to FeedRepo.compact(followers.toLong())))
                    if (person.followsYou) append(" · ").append(copy.t(Copy.HomeFollowsYou))
                }
                XaniText(meta, Type.Micro, color = sub, fontSizeOverride = 11f, modifier = Modifier.padding(top = rdp(2)))
            } else {
                if (person.username.isNotBlank()) {
                    XaniText("@" + person.username, Type.Micro, color = sub, fontSizeOverride = 11f, modifier = Modifier.padding(top = rdp(2)))
                }
                // Gifting totals in coins, both directions — ↑ sent, ↓ received —
                // plus the follower count. One line, ellipsised, never two heights.
                Row(
                    Modifier.padding(top = rdp(3)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText("↑", Type.Micro, color = Color(0xFFF59E0B), fontSizeOverride = 10f)
                    Spacer(Modifier.width(rdp(2)))
                    ProfileIco.Coin(Color(0xFFF59E0B), rdp(11))
                    Spacer(Modifier.width(rdp(3)))
                    XaniText(FeedRepo.compact(person.sentCoins), Type.Micro, color = sub, fontSizeOverride = 10f)
                    Spacer(Modifier.width(rdp(10)))
                    XaniText("↓", Type.Micro, color = Color(0xFF7C3AED), fontSizeOverride = 10f)
                    Spacer(Modifier.width(rdp(2)))
                    ProfileIco.Coin(Color(0xFF7C3AED), rdp(11))
                    Spacer(Modifier.width(rdp(3)))
                    XaniText(FeedRepo.compact(person.receivedCoins), Type.Micro, color = sub, fontSizeOverride = 10f)
                    Spacer(Modifier.width(rdp(10)))
                    XaniText(
                        copy.t(Copy.HomeSearchFollowers, "n" to FeedRepo.compact(followers.toLong())),
                        Type.Micro,
                        color = sub,
                        fontSizeOverride = 10f,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(rdp(10)))
        FollowButton(
            following = following,
            followsYou = person.followsYou,
            copy = copy,
            dark = dark,
            onClick = { FollowStore.set(person.id, !following) },
        )
    }
}

@Composable
fun FollowButton(following: Boolean, followsYou: Boolean, copy: Copy, dark: Boolean = false, onClick: () -> Unit) {
    val label = when {
        following -> copy.t(Copy.ProfileFollowingBtn)
        followsYou -> copy.t(Copy.ProfileFollowBack)
        else -> copy.t(Copy.ProfileFollow)
    }
    val shape = RoundedCornerShape(rdp(20))
    Box(
        Modifier
            .clip(shape)
            .then(
                if (following) Modifier
                    .background(if (dark) Color(0xFF1F1F23) else Color(0xFFF3F3F3))
                    .border(rdp(1), if (dark) Color(0xFF3A3A40) else Color(0xFFDDDDDD), shape)
                else Modifier.background(Color(0xFF7C3AED)),
            )
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(16), vertical = rdp(7)),
    ) {
        XaniText(
            label,
            Type.LabelBold,
            color = if (following) (if (dark) Color.White else Color(0xFF111111)) else Color.White,
            fontSizeOverride = 12f,
        )
    }
}
