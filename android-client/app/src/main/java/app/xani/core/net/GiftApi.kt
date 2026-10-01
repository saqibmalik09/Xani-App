package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Live gift wire types. The catalog is what the sheet lists; a [LiveGiftEvent]
 * is one gift landing in a room — the same frame the sender gets as a reply
 * and everyone else gets on the room stream, so nobody needs a second read.
 */

@Immutable
data class GiftData(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val coinPrice: Long,
    val listingUrl: String,
    val animationUrl: String,
    val animationMime: String,
    val soundUrl: String,
    val soundMime: String,
    /** How long every viewer holds it on screen; 0 = animation's own length. */
    val durationMs: Int,
    /** Lucky: the sheet keeps it selected and every tap sends again with a combo. */
    val lucky: Boolean,
) {
    val isSvga: Boolean get() = animationMime.contains("svga") || animationUrl.endsWith(".svga", ignoreCase = true)

    /** Lottie JSON: played by the Lottie runtime, not the SVGA player. */
    val isLottie: Boolean get() = !isSvga &&
        (animationMime == "application/json" || animationMime.contains("lottie") ||
            animationUrl.endsWith(".json", ignoreCase = true))
    val isStill: Boolean get() = animationUrl.isEmpty() || animationMime.startsWith("image/")

    /**
     * An animation uploaded as WebP / GIF / APNG: played by the platform image
     * decoder. A still uploaded the same way costs one extra probe and then
     * draws as the still it is.
     */
    val isAnimatedImage: Boolean get() = animationUrl.isNotEmpty() &&
        animationMime.startsWith("image/") && !animationMime.contains("svg+xml")

    companion object {
        val Empty = GiftData(0L, 0L, "", 0L, "", "", "", "", "", 0, false)
        fun from(json: JSONObject) = GiftData(
            id = json.id("id"),
            categoryId = json.id("category_id"),
            title = json.str("title"),
            coinPrice = json.long("coin_price"),
            listingUrl = json.str("listing_url"),
            animationUrl = json.str("animation_url"),
            animationMime = json.str("animation_mime"),
            soundUrl = json.str("sound_url"),
            soundMime = json.str("sound_mime"),
            durationMs = json.int("duration_ms"),
            lucky = json.bool("lucky"),
        )
    }
}

@Immutable
data class GiftCategoryData(val id: Long, val title: String, val lucky: Boolean, val gifts: List<GiftData>) {
    companion object {
        fun from(json: JSONObject) = GiftCategoryData(
            id = json.id("id"),
            title = json.str("title"),
            lucky = json.bool("lucky"),
            gifts = json.objects("gifts").map(GiftData::from),
        )
    }
}

@Immutable
data class GiftCatalogData(val categories: List<GiftCategoryData>, val rev: Long) {
    val all: List<GiftData> get() = categories.flatMap { it.gifts }

    companion object {
        val Empty = GiftCatalogData(emptyList(), 0L)
        fun from(json: JSONObject) = GiftCatalogData(
            categories = json.objects("categories").map(GiftCategoryData::from),
            rev = json.long("rev"),
        )
    }
}

/** One gift landing in a room. */
@Immutable
data class LiveGiftEvent(
    val id: Long,
    val roomId: Long,
    /** The sender's own idempotency key; empty on frames from other people. */
    val clientId: String,
    val gift: GiftData,
    val sender: LivePersonData,
    val receiver: LivePersonData,
    val count: Int,
    val combo: Int,
    val totalCoins: Long,
    /** The room's running gift total after this one. */
    val roomCoins: Long,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = LiveGiftEvent(
            id = json.id("id"),
            roomId = json.id("room_id"),
            clientId = json.str("client_id"),
            gift = json.obj("gift")?.let(GiftData::from) ?: GiftData.Empty,
            sender = LivePersonData.from(json.obj("sender") ?: JSONObject()),
            receiver = LivePersonData.from(json.obj("receiver") ?: JSONObject()),
            count = json.int("count", 1),
            combo = json.int("combo", 1),
            totalCoins = json.long("total_coins"),
            roomCoins = json.long("room_coins"),
            atMs = json.long("at_ms"),
        )
    }
}

/**
 * Someone with an active entrance effect walked into the room: everyone plays
 * their effect for a few seconds. Quiet joins never produce this frame.
 */
@Immutable
data class LiveEnterEvent(
    val roomId: Long,
    val user: LivePersonData,
    /** The entrance asset; key or absolute URL, resolved like avatars. */
    val entranceUrl: String,
    val entranceMime: String,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = LiveEnterEvent(
            roomId = json.id("room_id"),
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            entranceUrl = json.str("entrance_url"),
            entranceMime = json.str("entrance_mime"),
            atMs = json.long("at_ms"),
        )
    }
}

@Immutable
data class SendGiftData(val gift: LiveGiftEvent, val balance: Balance, val replayed: Boolean)

/** One ledger row as the statement lists it. */
@Immutable
data class GiftRecordData(
    val id: Long,
    val roomId: Long,
    val sender: LivePersonData,
    val receiver: LivePersonData,
    val giftId: Long,
    val giftTitle: String,
    val listingUrl: String,
    val unitCoins: Long,
    val count: Int,
    val totalCoins: Long,
    val combo: Int,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = GiftRecordData(
            id = json.id("id"),
            roomId = json.id("room_id"),
            sender = LivePersonData.from(json.obj("sender") ?: JSONObject()),
            receiver = LivePersonData.from(json.obj("receiver") ?: JSONObject()),
            giftId = json.id("gift_id"),
            giftTitle = json.str("gift_title"),
            listingUrl = json.str("listing_url"),
            unitCoins = json.long("unit_coins"),
            count = json.int("count", 1),
            totalCoins = json.long("total_coins"),
            combo = json.int("combo", 1),
            atMs = json.long("at_ms"),
        )
    }
}

/** One chat sticker as the tray and a chat line render it. */
@Immutable
data class StickerData(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val imageUrl: String,
    val imageMime: String,
) {
    companion object {
        fun from(json: JSONObject) = StickerData(
            id = json.id("id"),
            categoryId = json.id("category_id"),
            title = json.str("title"),
            imageUrl = json.str("image_url"),
            imageMime = json.str("image_mime"),
        )
    }
}

@Immutable
data class StickerCategoryData(val id: Long, val title: String, val stickers: List<StickerData>) {
    companion object {
        fun from(json: JSONObject) = StickerCategoryData(
            id = json.id("id"),
            title = json.str("title"),
            stickers = json.objects("stickers").map(StickerData::from),
        )
    }
}

@Immutable
data class StickerCatalogData(val categories: List<StickerCategoryData>, val rev: Long) {
    companion object {
        val Empty = StickerCatalogData(emptyList(), 0L)
        fun from(json: JSONObject) = StickerCatalogData(
            categories = json.objects("categories").map(StickerCategoryData::from),
            rev = json.long("rev"),
        )
    }
}

/** Typed gift endpoints. */
class GiftApi internal constructor(private val api: ApiClient) {

    suspend fun catalog(): ApiResponse<GiftCatalogData> =
        api.get("/v1/gifts/catalog", authenticated = true) { GiftCatalogData.from(it) }

    /** The chat sticker tray: dashboard categories with their active stickers. */
    suspend fun stickerCatalog(): ApiResponse<StickerCatalogData> =
        api.get("/v1/stickers/catalog", authenticated = true) { StickerCatalogData.from(it) }

    /**
     * Sends [count] of gift [giftId] to [receiverId] (0 = host). [clientId] is the
     * idempotency key: a retry lands the same gift once and debits once.
     * 409 `insufficient_funds` when the sender cannot cover it.
     */
    suspend fun send(
        roomId: Long,
        giftId: Long,
        receiverId: Long,
        clientId: String,
        count: Int = 1,
        combo: Int = 1,
    ): ApiResponse<SendGiftData> =
        api.post(
            "/v1/live/rooms/$roomId/gifts",
            jsonOf(
                "gift_id" to giftId.toString(),
                "receiver_id" to receiverId.toString(),
                "count" to count,
                "combo" to combo,
                "client_id" to clientId,
            ),
            authenticated = true,
        ) { d ->
            SendGiftData(
                gift = LiveGiftEvent.from(d.obj("gift") ?: JSONObject()),
                balance = Balance.from(d.obj("balance") ?: JSONObject()),
                replayed = d.bool("replayed"),
            )
        }

    /** The room's recent gifts, newest first. */
    suspend fun roomGifts(roomId: Long, limit: Int = 50, beforeId: Long = 0L): ApiResponse<List<GiftRecordData>> =
        api.get(
            "/v1/live/rooms/$roomId/gifts",
            buildMap {
                put("limit", limit.toString())
                if (beforeId > 0L) put("before_id", beforeId.toString())
            },
            authenticated = true,
        ) { d -> d.objects("items").map(GiftRecordData::from) }

    /** The signed-in account's statement: received (default) or sent. */
    suspend fun mine(received: Boolean = true, limit: Int = 50, beforeId: Long = 0L): ApiResponse<List<GiftRecordData>> =
        api.get(
            "/v1/me/gifts",
            buildMap {
                put("dir", if (received) "received" else "sent")
                put("limit", limit.toString())
                if (beforeId > 0L) put("before_id", beforeId.toString())
            },
            authenticated = true,
        ) { d -> d.objects("items").map(GiftRecordData::from) }

    /**
     * The room's own stream: gift frames (and the end notice) for everyone
     * inside. Reconnects until the calling coroutine is cancelled.
     */
    internal suspend fun stream(roomId: Long, onEvent: (name: String, data: JSONObject) -> Unit) {
        api.stream("/v1/live/rooms/$roomId/stream", onEvent)
    }
}
