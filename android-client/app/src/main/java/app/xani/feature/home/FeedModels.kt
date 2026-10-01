package app.xani.feature.home

import androidx.compose.runtime.Immutable
import app.xani.core.net.LevelData

@Immutable
data class Creator(
    val handle: String,
    val name: String,
    val verified: Boolean,
    val initials: String,
    val color: Long,
    /** Absolute URL of the author's picture; empty falls back to [initials]. */
    val avatarUrl: String = "",
)

enum class FeedKind { Video, Photo, Text }

/**
 * Server counters for one post, as the realtime channel last reported them.
 * `-1` is "the server has not said", which is how a surface knows to keep the
 * number it already had rather than paint a zero.
 */
@Immutable
data class LiveCounts(
    val likes: Int = -1,
    val comments: Int = -1,
    val saves: Int = -1,
    val shares: Int = -1,
    val views: Long = -1,
)

/**
 * One feed card. The display strings ([likes], [comments]…) are what the chrome
 * renders; the media fields are what the poster area plays. Built from the wire
 * shape in [FeedRepo], never in a composable.
 */
@Immutable
data class FeedPost(
    val id: Long,
    val creator: Creator,
    val description: String,
    val sound: String,
    val likes: String,
    val comments: String,
    val shares: String,
    val saves: String,
    val kind: FeedKind = FeedKind.Video,
    val authorId: Long = 0,
    /** Absolute URL of the still shown under (and before) the video; empty for text. */
    val posterUrl: String = "",
    /** Absolute URL of the rung for this device; empty for photo/text. */
    val videoUrl: String = "",
    /** width / height; 9:16 when unknown. */
    val aspect: Float = 9f / 16f,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val saveCount: Int = 0,
    val shareCount: Int = 0,
    val viewCount: Long = 0,
    /** Clip length, so the card knows what 20% of it is. 0 for photo and text. */
    val durationMs: Long = 0,
    val allowComments: Boolean = true,
    /** Server truth on first paint: the viewer follows this author. */
    val following: Boolean = false,
)

enum class LiveKind { Video, Audio }

/** Browse category — server sends these as data; enum is the client's typed view. */
enum class LiveCategory { Explore, Live, Pk, Audio }

enum class LiveMedia { All, Video, Audio }

@Immutable
data class LiveTile(
    /** The server room id: stable across list updates so cards never remount. */
    val id: Long,
    val host: String,
    val name: String,
    val viewers: String,
    val category: String,
    val initials: String,
    val color: Long,
    val flag: String = "🌍",
    val country: String = "ALL",
    val diamonds: String = "0",
    val kind: LiveKind = LiveKind.Video,
    val pk: Boolean = false,
    /** Server live room; 0 for a tile that has no broadcast behind it yet. */
    val roomId: Long = 0L,
    val hostId: Long = 0L,
    val avatarUrl: String = "",
    val viewerCount: Long = 0L,
    /** Private room: the tap asks for the host's password before joining. */
    val locked: Boolean = false,
    /** Friends room: listed to the host's circle only; the card says so. */
    val friendsOnly: Boolean = false,
    /** Host-picked card picture (storage key); empty → tinted gradient. */
    val coverUrl: String = "",
    /** The host's receiver level (what they have been gifted); null = none. */
    val hostLevel: LevelData? = null,
)

@Immutable
data class LiveCountry(val code: String, val flag: String, val label: String)

@Immutable
data class LiveChatLine(val id: Int, val user: String, val text: String, val tint: Long)

@Immutable
data class FeedComment(
    val id: Long,
    val user: String,
    val text: String,
    val time: String,
    val likes: Int,
    val color: Long,
    /** Non-zero: this row is a reply hanging under that comment. */
    val parentId: Long = 0,
    /** Whether the signed-in person has liked it (server truth, optimistic on tap). */
    val liked: Boolean = false,
    /** Absolute URL of an attached picture; empty for a text-only comment. */
    val imageUrl: String = "",
    val replies: List<FeedComment> = emptyList(),
    /** Who wrote it, so the row can open their profile. */
    val authorId: Long = 0,
    /** Absolute URL of their picture; empty falls back to the first letter. */
    val avatarUrl: String = "",
)

@Immutable
data class FeedPage(
    val items: List<FeedPost>,
    val cursor: String?,
)

enum class HomeLane {
    Live,
    Following,
    ForYou,
}
