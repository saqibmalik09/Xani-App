package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Gifting engine wire types. The engine decides who may send which gift to
 * whom and where the value goes; the client only renders its answers — a
 * category tree whose gifts carry a lock state and an English reason, and
 * per-person statements (received / sent) summed server-side per period.
 */

/** Whether one gift may be sent right now, and if not, why (English). */
@Immutable
data class GiftLockState(val allowed: Boolean, val reason: String, val text: String) {
    companion object {
        val Open = GiftLockState(true, "", "")
        fun from(json: JSONObject?): GiftLockState {
            if (json == null) return Open
            return GiftLockState(json.bool("allowed", true), json.str("reason"), json.str("text"))
        }
    }
}

/** One gift as the sheet lists it: the catalog row plus its lock state. */
@Immutable
data class GiftLeafData(val gift: GiftData, val state: GiftLockState) {
    val id: Long get() = gift.id

    companion object {
        fun from(json: JSONObject) = GiftLeafData(GiftData.from(json), GiftLockState.from(json.obj("state")))
    }
}

/** One category tab; children are sub-tabs shown as a second row. */
@Immutable
data class GiftCategoryNode(
    val id: Long,
    val parentId: Long,
    val title: String,
    val lucky: Boolean,
    val gifts: List<GiftLeafData>,
    val children: List<GiftCategoryNode>,
) {
    /** This node's gifts and every descendant's, in tree order. */
    val allGifts: List<GiftLeafData> get() = gifts + children.flatMap { it.allGifts }

    companion object {
        fun from(json: JSONObject): GiftCategoryNode = GiftCategoryNode(
            id = json.id("id"),
            parentId = json.id("parent_id"),
            title = json.str("title"),
            lucky = json.bool("lucky"),
            gifts = json.objects("gifts").map(GiftLeafData::from),
            children = json.objects("children").map(::from),
        )
    }
}

@Immutable
data class GiftTreeData(val categories: List<GiftCategoryNode>, val rev: Long, val version: Long) {
    val all: List<GiftLeafData> get() = categories.flatMap { it.allGifts }

    companion object {
        val Empty = GiftTreeData(emptyList(), 0L, 0L)
        fun from(json: JSONObject) = GiftTreeData(
            categories = json.objects("categories").map(GiftCategoryNode::from),
            rev = json.long("rev"),
            version = json.long("version"),
        )
    }
}

@Immutable
data class GiftingTotals(val coins: Long, val value: Long, val diamonds: Long, val cashbackCoins: Long, val count: Long) {
    companion object {
        val Zero = GiftingTotals(0L, 0L, 0L, 0L, 0L)
        fun from(json: JSONObject?) = if (json == null) Zero else GiftingTotals(
            coins = json.long("coins"),
            value = json.long("value"),
            diamonds = json.long("diamonds"),
            cashbackCoins = json.long("cashback_coins"),
            count = json.long("count"),
        )
    }
}

/** A counterpart on a statement: who, how much, when last. */
@Immutable
data class GiftingPeerData(
    val peer: LivePersonData,
    val coins: Long,
    val diamonds: Long,
    val count: Long,
    val lastAtMs: Long,
    val lastGiftId: Long,
    val listingUrl: String,
    val rank: Int,
) {
    companion object {
        fun from(json: JSONObject) = GiftingPeerData(
            peer = LivePersonData.from(json.obj("peer") ?: JSONObject()),
            coins = json.long("coins"),
            diamonds = json.long("diamonds"),
            count = json.long("count"),
            lastAtMs = json.long("last_at_ms"),
            lastGiftId = json.id("last_gift_id"),
            listingUrl = json.str("listing_url"),
            rank = json.int("rank"),
        )
    }
}

/** One gift on a statement. */
@Immutable
data class GiftingRowData(
    val id: Long,
    val sender: LivePersonData,
    val receiver: LivePersonData,
    val giftId: Long,
    val giftTitle: String,
    val listingUrl: String,
    val unitCoins: Long,
    val count: Int,
    val coins: Long,
    val diamonds: Long,
    val cashbackCoins: Long,
    val combo: Int,
    val roomId: Long,
    val status: Int,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = GiftingRowData(
            id = json.id("id"),
            sender = LivePersonData.from(json.obj("sender") ?: JSONObject()),
            receiver = LivePersonData.from(json.obj("receiver") ?: JSONObject()),
            giftId = json.id("gift_id"),
            giftTitle = json.str("gift_title"),
            listingUrl = json.str("listing_url"),
            unitCoins = json.long("unit_coins"),
            count = json.int("count", 1),
            coins = json.long("coins"),
            diamonds = json.long("diamonds"),
            cashbackCoins = json.long("cashback_coins"),
            combo = json.int("combo", 1),
            roomId = json.id("room_id"),
            status = json.int("status", 1),
            atMs = json.long("at_ms"),
        )
    }
}

/** A received or sent statement for one period: totals, counterparts, gifts. */
@Immutable
data class GiftingStatementData(
    val period: String,
    val from: String,
    val to: String,
    val totals: GiftingTotals,
    val peers: List<GiftingPeerData>,
    val gifts: List<GiftingRowData>,
    val nextCursor: String,
    val sort: String,
) {
    companion object {
        val Empty = GiftingStatementData("", "", "", GiftingTotals.Zero, emptyList(), emptyList(), "", "top")
        fun from(json: JSONObject) = GiftingStatementData(
            period = json.str("period"),
            from = json.str("from"),
            to = json.str("to"),
            totals = GiftingTotals.from(json.obj("totals")),
            peers = json.objects("peers").map(GiftingPeerData::from),
            gifts = json.objects("gifts").map(GiftingRowData::from),
            nextCursor = json.str("next_cursor"),
            sort = json.str("sort", "top"),
        )
    }
}

@Immutable
data class GiftingSummaryData(
    val receivedToday: GiftingTotals,
    val receivedWeek: GiftingTotals,
    val receivedMonth: GiftingTotals,
    val sentToday: GiftingTotals,
    val sentWeek: GiftingTotals,
    val sentMonth: GiftingTotals,
    val day: String,
) {
    companion object {
        fun from(json: JSONObject) = GiftingSummaryData(
            receivedToday = GiftingTotals.from(json.obj("received_today")),
            receivedWeek = GiftingTotals.from(json.obj("received_week")),
            receivedMonth = GiftingTotals.from(json.obj("received_month")),
            sentToday = GiftingTotals.from(json.obj("sent_today")),
            sentWeek = GiftingTotals.from(json.obj("sent_week")),
            sentMonth = GiftingTotals.from(json.obj("sent_month")),
            day = json.str("day"),
        )
    }
}

/**
 * A gift that reached (or left) the signed-in account, on the per-user
 * stream: `gift_received` carries the receiver's new day total so every
 * surface moves without a re-read; `gift_sent` carries cashback.
 */
@Immutable
data class GiftingUserEvent(
    val kind: String,
    val giftId: Long,
    val peerId: Long,
    val catalogGiftId: Long,
    val title: String,
    val count: Int,
    val coins: Long,
    val diamonds: Long,
    val cashbackCoins: Long,
    // randomWinCoins/randomWinMessage: set only when this send (a "gift_sent"
    // frame) drew a win from an admin-configured random-reward policy;
    // 0/"" on every other gift, which is almost all of them until a policy
    // is turned on for that gift's category.
    val randomWinCoins: Long,
    val randomWinMessage: String,
    val todayDiamonds: Long,
    val todayCount: Long,
    val todayCoins: Long,
    val roomId: Long,
    val atMs: Long,
) {
    val received: Boolean get() = kind == "gift_received"

    companion object {
        fun from(kind: String, json: JSONObject) = GiftingUserEvent(
            kind = kind,
            giftId = json.id("id"),
            peerId = if (kind == "gift_received") json.id("sender_id") else json.id("receiver_id"),
            catalogGiftId = json.id("gift_id"),
            title = json.str("title"),
            count = json.int("count", 1),
            coins = json.long("coins"),
            diamonds = json.long("diamonds"),
            cashbackCoins = json.long("cashback_coins"),
            randomWinCoins = json.long("random_win_coins"),
            randomWinMessage = json.str("random_win_message"),
            todayDiamonds = json.long("today_diamonds"),
            todayCount = json.long("today_count"),
            todayCoins = json.long("today_coins"),
            roomId = json.id("room_id"),
            atMs = json.long("at_ms"),
        )
    }
}

/** Typed gifting-engine endpoints. */
class GiftingApi internal constructor(private val api: ApiClient) {

    /**
     * The category tree for this sender → [receiverId] in [roomId]: every gift
     * carries whether it may be sent now and, when not, the reason. Gifts a
     * rule hides are not in the list at all.
     */
    suspend fun catalog(roomId: Long = 0L, receiverId: Long = 0L): ApiResponse<GiftTreeData> =
        api.get(
            "/v1/gifting/catalog",
            buildMap {
                if (roomId > 0L) put("room_id", roomId.toString())
                if (receiverId > 0L) put("receiver_id", receiverId.toString())
            },
            authenticated = true,
        ) { GiftTreeData.from(it) }

    /**
     * What [userId] received: [period] is live | today | week | month | range
     * (with [from] / [to] as YYYY-MM-DD); [roomId] narrows "live" to one room;
     * [sort] is top (by coins) or latest. 403 `gifting_private` when hidden.
     */
    suspend fun received(
        userId: Long,
        period: String,
        from: String = "",
        to: String = "",
        roomId: Long = 0L,
        sort: String = "top",
        cursor: String = "",
        limit: Int = 30,
    ): ApiResponse<GiftingStatementData> = statement("received", userId, period, from, to, roomId, sort, cursor, limit)

    /** What [userId] sent; only the person (or an admin) may read it. */
    suspend fun sent(
        userId: Long,
        period: String,
        from: String = "",
        to: String = "",
        roomId: Long = 0L,
        sort: String = "top",
        cursor: String = "",
        limit: Int = 30,
    ): ApiResponse<GiftingStatementData> = statement("sent", userId, period, from, to, roomId, sort, cursor, limit)

    private suspend fun statement(
        dir: String,
        userId: Long,
        period: String,
        from: String,
        to: String,
        roomId: Long,
        sort: String,
        cursor: String,
        limit: Int,
    ): ApiResponse<GiftingStatementData> =
        api.get(
            "/v1/users/$userId/gifting/$dir",
            buildMap {
                put("period", period)
                if (from.isNotEmpty()) put("from", from)
                if (to.isNotEmpty()) put("to", to)
                if (roomId > 0L) put("room_id", roomId.toString())
                put("sort", sort)
                if (cursor.isNotEmpty()) put("cursor", cursor)
                put("limit", limit.toString())
            },
            authenticated = true,
        ) { GiftingStatementData.from(it) }

    /** Today / week / month totals both ways (sent is zeroed for other people). */
    suspend fun summary(userId: Long): ApiResponse<GiftingSummaryData> =
        api.get("/v1/users/$userId/gifting/summary", authenticated = true) { GiftingSummaryData.from(it) }
}
