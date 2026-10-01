package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant

/**
 * Chat wire types. Ids are strings on the wire (they exceed 2^53) and Longs
 * here. Times are RFC3339 and parsed once, to epoch millis.
 */

@Immutable
data class ChatPeer(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val verified: Boolean,
    /** Their "what's on your mind"; empty when they have not set one. */
    val status: String,
    val online: Boolean,
    /** Epoch ms of the last device leaving; 0 while online or unknown. */
    val lastSeenAt: Long,
    /** The signed-in user has blocked this person. */
    val blocked: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = ChatPeer(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
            verified = json.bool("verified"),
            status = json.str("status"),
            online = json.bool("online"),
            lastSeenAt = json.epoch("last_seen_at"),
            blocked = json.bool("blocked"),
        )
    }
}

@Immutable
data class ChatReaction(val userId: Long, val emoji: String)

@Immutable
data class ChatMessageData(
    val id: Long,
    val conversationId: Long,
    val seq: Long,
    val senderId: Long,
    /** text · image · video · voice · contact · location · document · poll · event · system */
    val kind: String,
    val body: String,
    val mediaKey: String,
    val mediaMime: String,
    val mediaBytes: Long,
    val mediaName: String,
    val mediaMs: Int,
    val replyTo: Long,
    val clientId: String,
    val createdAt: Long,
    val edited: Boolean,
    val deleted: Boolean,
    /** Epoch ms when the message disappears; 0 for never. */
    val expiresAt: Long,
    val reactions: List<ChatReaction>,
) {
    companion object {
        fun from(json: JSONObject) = ChatMessageData(
            id = json.id("id"),
            conversationId = json.id("conversation_id"),
            seq = json.long("seq"),
            senderId = json.id("sender_id"),
            kind = json.str("kind", "text"),
            body = json.str("body"),
            mediaKey = json.str("media_key"),
            mediaMime = json.str("media_mime"),
            mediaBytes = json.long("media_bytes"),
            mediaName = json.str("media_name"),
            mediaMs = json.int("media_ms"),
            replyTo = json.id("reply_to"),
            clientId = json.str("client_id"),
            createdAt = json.epoch("created_at"),
            edited = json.bool("edited"),
            deleted = json.bool("deleted"),
            expiresAt = json.epoch("expires_at"),
            reactions = json.objects("reactions").map { ChatReaction(it.id("user_id"), it.str("emoji")) },
        )
    }
}

@Immutable
data class ConversationData(
    val id: Long,
    val peer: ChatPeer,
    val lastSeq: Long,
    val unread: Long,
    val readSeq: Long,
    val deliveredSeq: Long,
    val peerReadSeq: Long,
    val peerReadAt: Long,
    val peerDeliveredSeq: Long,
    val peerDeliveredAt: Long,
    val pinned: Boolean,
    val muted: Boolean,
    val archived: Boolean,
    /** 0 off · -1 after seen · n seconds after send. */
    val vanishSeconds: Int,
    val last: ChatMessageData?,
    val sortAt: Long,
    /** The raw cursor value for paging the inbox. */
    val sortAtRaw: String,
) {
    companion object {
        fun from(json: JSONObject) = ConversationData(
            id = json.id("id"),
            peer = ChatPeer.from(json.obj("peer") ?: JSONObject()),
            lastSeq = json.long("last_seq"),
            unread = json.long("unread"),
            readSeq = json.long("read_seq"),
            deliveredSeq = json.long("delivered_seq"),
            peerReadSeq = json.long("peer_read_seq"),
            peerReadAt = json.epoch("peer_read_at"),
            peerDeliveredSeq = json.long("peer_delivered_seq"),
            peerDeliveredAt = json.epoch("peer_delivered_at"),
            pinned = json.bool("pinned"),
            muted = json.bool("muted"),
            archived = json.bool("archived"),
            vanishSeconds = json.int("vanish_seconds"),
            last = json.obj("last")?.let(ChatMessageData::from),
            sortAt = json.epoch("sort_at"),
            sortAtRaw = json.str("sort_at"),
        )
    }
}

/** The ack for a send: the canonical message and the sender's inbox row. */
@Immutable
data class SendAck(val message: ChatMessageData, val conversation: ConversationData) {
    companion object {
        fun from(json: JSONObject) = SendAck(
            message = ChatMessageData.from(json.obj("message") ?: JSONObject()),
            conversation = ConversationData.from(json.obj("conversation") ?: JSONObject()),
        )
    }
}

/** One member's markers moving. */
@Immutable
data class ReceiptEvent(val conversationId: Long, val userId: Long, val deliveredSeq: Long, val readSeq: Long, val at: Long) {
    companion object {
        fun from(json: JSONObject) = ReceiptEvent(
            conversationId = json.id("conversation_id"),
            userId = json.id("user_id"),
            deliveredSeq = json.long("delivered_seq"),
            readSeq = json.long("read_seq"),
            at = json.epoch("at"),
        )
    }
}

@Immutable
data class ReactionEvent(val conversationId: Long, val messageId: Long, val userId: Long, val emoji: String) {
    companion object {
        fun from(json: JSONObject) = ReactionEvent(
            conversationId = json.id("conversation_id"),
            messageId = json.id("message_id"),
            userId = json.id("user_id"),
            emoji = json.str("emoji"),
        )
    }
}

@Immutable
data class TypingEvent(val conversationId: Long, val userId: Long, val typing: Boolean) {
    companion object {
        fun from(json: JSONObject) = TypingEvent(
            conversationId = json.id("conversation_id"),
            userId = json.id("user_id"),
            typing = json.bool("typing"),
        )
    }
}

@Immutable
data class PresenceEvent(val userId: Long, val online: Boolean, val lastSeenAt: Long) {
    companion object {
        fun from(json: JSONObject) = PresenceEvent(
            userId = json.id("user_id"),
            online = json.bool("online"),
            lastSeenAt = json.epoch("last_seen_at"),
        )
    }
}

/** RFC3339 → epoch millis; 0 when absent or unparseable. */
internal fun JSONObject.epoch(key: String): Long {
    val raw = str(key)
    if (raw.isEmpty()) return 0L
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
}

/** One page of chats. */
@Immutable
data class InboxPage(val items: List<ConversationData>, val next: String)

/**
 * Chat endpoints. Writes answer with the canonical row; delivery to the other
 * side rides the shared user stream as `chat_*` events (see [LiveFeed]).
 */
class ChatApi internal constructor(private val api: ApiClient) {

    /** Finds or creates the direct chat with [userId]. */
    suspend fun open(userId: Long): ApiResponse<ConversationData> =
        api.post("/v1/chats/direct", jsonOf("user_id" to userId.toString()), authenticated = true) {
            ConversationData.from(it.obj("conversation") ?: JSONObject())
        }

    suspend fun inbox(cursor: String = "", limit: Int = 50): ApiResponse<InboxPage> = api.get(
        "/v1/chats",
        buildMap {
            put("limit", limit.toString())
            if (cursor.isNotEmpty()) put("cursor", cursor)
        },
        authenticated = true,
    ) { InboxPage(it.objects("items").map(ConversationData::from), it.str("next_cursor")) }

    suspend fun get(conversationId: Long): ApiResponse<ConversationData> =
        api.get("/v1/chats/$conversationId", authenticated = true) {
            ConversationData.from(it.obj("conversation") ?: JSONObject())
        }

    /** Newest first. [beforeSeq] walks back; [afterSeq] alone resumes after a reconnect. */
    suspend fun messages(
        conversationId: Long,
        beforeSeq: Long = 0,
        afterSeq: Long = 0,
        limit: Int = 50,
    ): ApiResponse<List<ChatMessageData>> = api.get(
        "/v1/chats/$conversationId/messages",
        buildMap {
            put("limit", limit.toString())
            if (beforeSeq > 0) put("before_seq", beforeSeq.toString())
            if (afterSeq > 0) put("after_seq", afterSeq.toString())
        },
        authenticated = true,
    ) { it.objects("items").map(ChatMessageData::from) }

    suspend fun send(
        conversationId: Long,
        clientId: String,
        body: String,
        kind: String = "text",
        replyTo: Long = 0,
    ): ApiResponse<SendAck> = api.post(
        "/v1/chats/$conversationId/messages",
        jsonOf(
            "kind" to kind,
            "body" to body,
            "client_id" to clientId,
            "reply_to" to replyTo.takeIf { it > 0 }?.toString(),
        ),
        authenticated = true,
    ) { SendAck.from(it) }

    /**
     * Uploads one attachment and creates its message in a single round trip.
     * The bytes stream from [open] — never a byte array — so a clip does not
     * live in memory twice.
     */
    suspend fun sendMedia(
        conversationId: Long,
        clientId: String,
        kind: String,
        mime: String,
        length: Long,
        caption: String = "",
        fileName: String = "",
        durationMs: Int = 0,
        replyTo: Long = 0,
        open: () -> InputStream?,
    ): ApiResponse<SendAck> = api.putStream(
        "/v1/chats/$conversationId/media",
        query = buildMap {
            put("kind", kind)
            put("client_id", clientId)
            if (caption.isNotEmpty()) put("caption", caption)
            if (replyTo > 0) put("reply_to", replyTo.toString())
        },
        headers = buildMap {
            if (fileName.isNotEmpty()) put("X-File-Name", fileName.filter { it.code in 32..126 })
            if (durationMs > 0) put("X-Media-Ms", durationMs.toString())
        },
        contentType = mime.ifEmpty { "application/octet-stream" },
        length = length,
        open = open,
    ) { SendAck.from(it) }

    suspend fun ack(conversationId: Long, deliveredSeq: Long, readSeq: Long): ApiResponse<ConversationData> = api.post(
        "/v1/chats/$conversationId/ack",
        jsonOf("delivered_seq" to deliveredSeq, "read_seq" to readSeq),
        authenticated = true,
    ) { ConversationData.from(it.obj("conversation") ?: JSONObject()) }

    suspend fun typing(conversationId: Long, on: Boolean): ApiResponse<Unit> =
        api.post("/v1/chats/$conversationId/typing", jsonOf("typing" to on), authenticated = true)

    suspend fun settings(
        conversationId: Long,
        pinned: Boolean? = null,
        muted: Boolean? = null,
        archived: Boolean? = null,
        vanishSeconds: Int? = null,
    ): ApiResponse<ConversationData> = api.post(
        "/v1/chats/$conversationId/settings",
        jsonOf("pinned" to pinned, "muted" to muted, "archived" to archived, "vanish_seconds" to vanishSeconds),
        authenticated = true,
    ) { ConversationData.from(it.obj("conversation") ?: JSONObject()) }

    suspend fun clear(conversationId: Long): ApiResponse<ConversationData> =
        api.post("/v1/chats/$conversationId/clear", JSONObject(), authenticated = true) {
            ConversationData.from(it.obj("conversation") ?: JSONObject())
        }

    suspend fun delete(conversationId: Long): ApiResponse<Unit> =
        api.delete("/v1/chats/$conversationId", authenticated = true) { }

    /** Empty [emoji] clears the signed-in user's reaction. */
    suspend fun react(messageId: Long, emoji: String): ApiResponse<Unit> =
        api.post("/v1/messages/$messageId/react", jsonOf("emoji" to emoji), authenticated = true)

    suspend fun edit(messageId: Long, body: String): ApiResponse<ChatMessageData> =
        api.post("/v1/messages/$messageId/edit", jsonOf("body" to body), authenticated = true) {
            ChatMessageData.from(it.obj("message") ?: JSONObject())
        }

    suspend fun unsend(messageId: Long): ApiResponse<ChatMessageData> =
        api.delete("/v1/messages/$messageId", authenticated = true) {
            ChatMessageData.from(it.obj("message") ?: JSONObject())
        }

    suspend fun block(userId: Long, on: Boolean): ApiResponse<Unit> =
        api.post("/v1/users/$userId/block", jsonOf("on" to on), authenticated = true)

    suspend fun report(userId: Long, reason: String, conversationId: Long = 0, messageId: Long = 0): ApiResponse<Unit> = api.post(
        "/v1/users/$userId/report",
        jsonOf(
            "reason" to reason,
            "conversation_id" to conversationId.takeIf { it > 0 }?.toString(),
            "message_id" to messageId.takeIf { it > 0 }?.toString(),
        ),
        authenticated = true,
    )
}
