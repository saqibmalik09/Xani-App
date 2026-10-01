package app.xani.feature.inbox

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class InboxLane { Chats, Notifications, Activity }

enum class MsgKind { Text, Image, Video, Voice, Contact, Location, Document, Poll, Event, Ai }

enum class Delivery { Sending, Sent, Delivered, Seen }

enum class VanishTimer { Off, Seen, Hours, Days }

enum class ThreadPane { Chat, Profile, Search, Theme, Nicknames, Disappear, Media }

enum class InboxOverlay { None, NewChat, NewGroup, StoryCreate, StoryView, Context }

data class PendingAttach(
    val kind: MsgKind,
    val uri: android.net.Uri? = null,
    val label: String = "",
)

data class EventPayload(
    val name: String,
    val desc: String,
    val at: Long,
    val loc: String,
    val reminder: Int,
)

fun encodeEvent(name: String, desc: String, at: Long, loc: String, reminder: Int): String =
    listOf(name.trim(), desc.trim().replace('\n', ' '), at.toString(), loc.trim().replace('\n', ' '), reminder.toString())
        .joinToString("\n")

fun parseEvent(text: String): EventPayload {
    val p = text.split('\n')
    return EventPayload(
        name = p.getOrElse(0) { "" },
        desc = p.getOrElse(1) { "" },
        at = p.getOrElse(2) { "0" }.toLongOrNull() ?: 0L,
        loc = p.getOrElse(3) { "" },
        reminder = p.getOrElse(4) { "60" }.toIntOrNull() ?: 60,
    )
}

fun pollQuestion(text: String): String = text.lineSequence().firstOrNull().orEmpty()

fun pollOptions(text: String): List<String> =
    text.lines().drop(1).filter { it.isNotBlank() && it != "#multi" }

fun pollMulti(text: String): Boolean = text.lines().any { it == "#multi" }

internal val ChatLinkFind = Regex("""(?i)((?:https?://|www\.)[^\s<>"]+)""")

internal fun httpUrl(raw: String): String {
    val trimmed = raw.trimEnd('.', ',', ';', ')', ']', '!', '?')
    return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
}

@Immutable
data class InboxChat(
    val id: Int,
    val name: String,
    val handle: String,
    val preview: String,
    val time: String,
    val unread: Int,
    val initials: String,
    val color: Long,
    val group: Boolean = false,
    val members: List<Member> = emptyList(),
    /** The other account's id; 0 for groups and for a row still being resolved. */
    val peerId: Long = 0L,
    /** Storage key or absolute URL; empty paints initials. */
    val avatarUrl: String = "",
    /** Their "what's on your mind", shown on the chat's profile pane. */
    val status: String = "",
    val online: Boolean = false,
    /** Epoch ms of their last device leaving; 0 while online or unknown. */
    val lastSeenAt: Long = 0L,
    /** The signed-in user has blocked this person. */
    val blocked: Boolean = false,
    /** Epoch ms of the last activity, for ordering. */
    val sortAt: Long = 0L,
) {
    val tint: Color get() = Color(color)
}

@Immutable
data class Member(
    val name: String,
    val handle: String,
    val initials: String,
    val color: Long,
)

@Immutable
data class MsgReactor(
    val name: String,
    val initials: String,
    val color: Long,
    val emoji: String,
    val mine: Boolean = false,
)

@Immutable
data class ChatMessage(
    val id: Long,
    val chatId: Int,
    val mine: Boolean,
    val from: String,
    val initials: String,
    val color: Long,
    val text: String,
    val time: String,
    val kind: MsgKind = MsgKind.Text,
    val mediaUri: String? = null,
    val replyTo: Long? = null,
    val reaction: String? = null,
    val reactors: List<MsgReactor> = emptyList(),
    val delivery: Delivery = Delivery.Sent,
    val edited: Boolean = false,
    /** The phone's own name for a send, so an ack or a stream echo finds the bubble. */
    val clientId: String = "",
    /** Epoch ms when a disappearing message leaves the screen; 0 for never. */
    val expiresAt: Long = 0L,
)

@Immutable
data class ActivityItem(
    val id: Int,
    val type: String,
    val user: String,
    val handle: String,
    val msg: String,
    val time: String,
    val initials: String,
    val color: Long,
)

@Immutable
data class FollowNotice(
    val name: String,
    val handle: String,
    val initials: String,
    val color: Long,
    val time: String,
)

@Immutable
data class SystemNotice(
    val id: String,
    val kind: String,
    val title: String,
    val msg: String,
    val time: String,
    val refId: Long = 0L,
    val glyph: String = "megaphone",
)

@Immutable
data class ChatTheme(
    val label: String,
    val bg: Long,
    val start: Long,
    val end: Long,
)

@Immutable
data class InboxSnapshot(
    val chats: List<InboxChat>,
    val activity: List<ActivityItem>,
    val followers: List<FollowNotice>,
    val system: List<SystemNotice>,
    val stories: List<InboxChat>,
)

val ChatThemes = listOf(
    ChatTheme("Default", 0xFFFFFFFF, 0xFF8B5CF6, 0xFFEC4899),
    ChatTheme("Midnight", 0xFF0F0F1A, 0xFF1A1A3E, 0xFF6D28D9),
    ChatTheme("Sunset", 0xFFFFF5F0, 0xFFF97316, 0xFFEC4899),
    ChatTheme("Forest", 0xFFF0FDF4, 0xFF059669, 0xFF10B981),
    ChatTheme("Ocean", 0xFFF0F9FF, 0xFF0284C7, 0xFF38BDF8),
    ChatTheme("Rose", 0xFFFFF1F2, 0xFFF43F5E, 0xFFFB7185),
    ChatTheme("Gold", 0xFFFFFBEB, 0xFFD97706, 0xFFF59E0B),
    ChatTheme("Lavender", 0xFFFAF5FF, 0xFF7C3AED, 0xFFA78BFA),
    ChatTheme("Slate", 0xFFF8FAFC, 0xFF475569, 0xFF94A3B8),
)

val BubbleSolids = longArrayOf(
    0xFF8B5CF6, 0xFFEC4899, 0xFF10B981, 0xFF0EA5E9,
    0xFFF59E0B, 0xFFEF4444, 0xFF3B82F6, 0xFF111111,
)

val StoryBgs = longArrayOf(
    0xFF8B5CF6, 0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981, 0xFFEC4899, 0xFF111111,
)

val StoryBgEnds = longArrayOf(
    0xFFEC4899, 0xFF6366F1, 0xFFEF4444, 0xFF0EA5E9, 0xFFF59E0B, 0xFF374151,
)

internal const val MessageWindow = 80
internal val QuickReactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")
