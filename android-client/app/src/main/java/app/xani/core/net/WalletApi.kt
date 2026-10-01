package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.util.TimeZone

/**
 * The owner's wallet statement and diamond withdrawals.
 *
 * Statement reads take a day window (inclusive, in the phone's clock; the
 * server is told the offset so "today" is the user's today) and come back
 * summed by category, then by day. Drill-downs page with a keyset cursor.
 */

/** One bucket of the statement: how many entries and their magnitude. */
@Immutable
data class TxnCategoryData(val count: Long, val coins: Long, val diamonds: Long) {
    companion object {
        val Zero = TxnCategoryData(0L, 0L, 0L)
        fun from(json: JSONObject?) = if (json == null) Zero else TxnCategoryData(
            count = json.long("count"), coins = json.long("coins"), diamonds = json.long("diamonds"),
        )
    }
}

/** One day's movement inside the window. */
@Immutable
data class TxnDayData(
    val day: String,
    val count: Long,
    val spentCoins: Long,
    val earnedCoins: Long,
    val earnedDiamonds: Long,
    val withdrawDiamonds: Long,
) {
    companion object {
        fun from(json: JSONObject) = TxnDayData(
            day = json.str("day"), count = json.long("count"),
            spentCoins = json.long("spent_coins"), earnedCoins = json.long("earned_coins"),
            earnedDiamonds = json.long("earned_diamonds"), withdrawDiamonds = json.long("withdraw_diamonds"),
        )
    }
}

/** The statement in one read. Category keys match [TxnCategory.key]. */
@Immutable
data class TxnSummaryData(
    val from: String,
    val to: String,
    val coins: Long,
    val diamonds: Long,
    val beans: Long,
    val spentCoins: Long,
    val earnedCoins: Long,
    val earnedDiamonds: Long,
    val withdrawnDiamonds: Long,
    val categories: Map<String, TxnCategoryData>,
    val days: List<TxnDayData>,
) {
    fun category(key: String): TxnCategoryData = categories[key] ?: TxnCategoryData.Zero

    companion object {
        fun from(json: JSONObject): TxnSummaryData {
            val bal = json.obj("balance")
            val totals = json.obj("totals")
            val cats = json.obj("categories")
            val map = HashMap<String, TxnCategoryData>(8)
            if (cats != null) {
                val keys = cats.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = TxnCategoryData.from(cats.obj(k))
                }
            }
            return TxnSummaryData(
                from = json.str("from"), to = json.str("to"),
                coins = bal?.long("coins") ?: 0L, diamonds = bal?.long("diamonds") ?: 0L, beans = bal?.long("beans") ?: 0L,
                spentCoins = totals?.long("spent_coins") ?: 0L, earnedCoins = totals?.long("earned_coins") ?: 0L,
                earnedDiamonds = totals?.long("earned_diamonds") ?: 0L, withdrawnDiamonds = totals?.long("withdrawn_diamonds") ?: 0L,
                categories = map,
                days = json.objects("days").map(TxnDayData::from),
            )
        }
    }
}

/** The statement's buckets; [spend] tells the UI which column they sit in. */
enum class TxnCategory(val key: String, val spend: Boolean) {
    GiftsSent("gifts_sent", true),
    Store("store", true),
    GameBets("game_bets", true),
    GiftsReceived("gifts_received", false),
    Topups("topups", false),
    GameWins("game_wins", false),
    Withdrawals("withdrawals", false),
    Adjustments("adjustments", false),
}

/** Gifts in the window folded by the other person. */
@Immutable
data class TxnGiftGroupData(
    val user: LiveHostData,
    val count: Long,
    val coins: Long,
    val diamonds: Long,
    val lastAtMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = TxnGiftGroupData(
            user = LiveHostData.from(json.obj("user")),
            count = json.long("count"), coins = json.long("coins"), diamonds = json.long("diamonds"),
            lastAtMs = json.long("last_at_ms"),
        )
    }
}

@Immutable
data class TxnGiftGroupsData(
    val items: List<TxnGiftGroupData>,
    val count: Long,
    val coins: Long,
    val diamonds: Long,
) {
    companion object {
        fun from(json: JSONObject): TxnGiftGroupsData {
            val t = json.obj("totals")
            return TxnGiftGroupsData(
                items = json.objects("items").map(TxnGiftGroupData::from),
                count = t?.long("count") ?: 0L, coins = t?.long("coins") ?: 0L, diamonds = t?.long("diamonds") ?: 0L,
            )
        }
    }
}

/** One gift row between me and one person. */
@Immutable
data class TxnGiftData(
    val id: Long,
    val roomId: Long,
    val giftId: Long,
    val title: String,
    val unitCoins: Long,
    val count: Int,
    val totalCoins: Long,
    val diamonds: Long,
    val atMs: Long,
    /** Catalog picture; key or absolute URL, "" when the gift was retired. */
    val listingUrl: String,
) {
    companion object {
        fun from(json: JSONObject) = TxnGiftData(
            id = json.id("id"), roomId = json.id("room_id"), giftId = json.id("gift_id"),
            title = json.str("gift_title"), unitCoins = json.long("unit_coins"), count = json.int("count"),
            totalCoins = json.long("total_coins"), diamonds = json.long("diamonds"), atMs = json.long("at_ms"),
            listingUrl = json.str("listing_url"),
        )
    }
}

@Immutable
data class TxnGiftPageData(val user: LiveHostData, val items: List<TxnGiftData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = TxnGiftPageData(
            user = LiveHostData.from(json.obj("user")),
            items = json.objects("items").map(TxnGiftData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** One store purchase (an avatar frame package today). */
@Immutable
data class TxnPurchaseData(
    val id: Long,
    val itemId: Long,
    val kind: String,
    val title: String,
    val previewKey: String,
    val assetKey: String,
    val assetMime: String,
    val coins: Long,
    val days: Int,
    val expiresAtMs: Long,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = TxnPurchaseData(
            id = json.id("id"), itemId = json.id("item_id"), kind = json.str("kind"), title = json.str("title"),
            previewKey = json.str("preview_key"), assetKey = json.str("asset_key"), assetMime = json.str("asset_mime"),
            coins = json.long("coins"), days = json.int("days"), expiresAtMs = json.long("expires_at_ms"),
            atMs = json.long("at_ms"),
        )
    }
}

@Immutable
data class TxnPurchasePageData(val items: List<TxnPurchaseData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = TxnPurchasePageData(
            items = json.objects("items").map(TxnPurchaseData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** One raw ledger line (signed amounts). */
@Immutable
data class TxnEntryData(
    val id: Long,
    val kind: Int,
    val category: String,
    val coins: Long,
    val diamonds: Long,
    val beans: Long,
    val atMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = TxnEntryData(
            id = json.id("id"), kind = json.int("kind"), category = json.str("category"),
            coins = json.long("coins"), diamonds = json.long("diamonds"), beans = json.long("beans"),
            atMs = json.long("at_ms"),
        )
    }
}

@Immutable
data class TxnEntryPageData(val items: List<TxnEntryData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = TxnEntryPageData(
            items = json.objects("items").map(TxnEntryData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

// ─── withdrawals ─────────────────────────────────────────────────────────────

/** A way the console pays out; bounds already resolved against the policy. */
@Immutable
data class PaymentMethodData(
    val id: Long,
    val name: String,
    val title: String,
    val description: String,
    val message: String,
    val accountLabel: String,
    val minDiamonds: Long,
    val maxDiamonds: Long,
) {
    companion object {
        fun from(json: JSONObject) = PaymentMethodData(
            id = json.id("id"), name = json.str("name"), title = json.str("title"),
            description = json.str("description"), message = json.str("message"),
            accountLabel = json.str("account_label").ifEmpty { "Account number" },
            minDiamonds = json.long("min_diamonds"), maxDiamonds = json.long("max_diamonds"),
        )
    }
}

/** Whether today is inside the month's withdrawal window. */
@Immutable
data class WithdrawWindowData(
    val open: Boolean,
    val dayFrom: Int,
    val dayTo: Int,
    val today: Int,
    val message: String,
    /** YYYY-MM-DD of the next open day when closed. */
    val nextOpen: String,
) {
    companion object {
        val Open = WithdrawWindowData(true, 1, 31, 1, "", "")
        fun from(json: JSONObject?) = if (json == null) Open else WithdrawWindowData(
            open = json.bool("open", true), dayFrom = json.int("day_from", 1), dayTo = json.int("day_to", 31),
            today = json.int("today"), message = json.str("message"), nextOpen = json.str("next_open"),
        )
    }
}

/** Everything the withdraw screen needs in one read. */
@Immutable
data class WithdrawHomeData(
    val diamonds: Long,
    val pendingDiamonds: Long,
    val pendingCount: Long,
    val minDiamonds: Long,
    val maxDiamonds: Long,
    val country: String,
    val window: WithdrawWindowData,
    val methods: List<PaymentMethodData>,
) {
    companion object {
        fun from(json: JSONObject) = WithdrawHomeData(
            diamonds = json.long("diamonds"), pendingDiamonds = json.long("pending_diamonds"),
            pendingCount = json.long("pending_count"), minDiamonds = json.long("min_diamonds"),
            maxDiamonds = json.long("max_diamonds"), country = json.str("country"),
            window = WithdrawWindowData.from(json.obj("window")),
            methods = json.objects("methods").map(PaymentMethodData::from),
        )
    }
}

enum class WithdrawStatus { Pending, Approved, Rejected;
    companion object {
        fun of(s: String) = when (s) {
            "approved" -> Approved
            "rejected" -> Rejected
            else -> Pending
        }
    }
}

@Immutable
data class WithdrawRequestData(
    val id: Long,
    val methodName: String,
    val accountTitle: String,
    val accountNo: String,
    val diamonds: Long,
    val status: WithdrawStatus,
    val note: String,
    val createdAtMs: Long,
    val decidedAtMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = WithdrawRequestData(
            id = json.id("id"), methodName = json.str("method_name"),
            accountTitle = json.str("account_title"), accountNo = json.str("account_no"),
            diamonds = json.long("diamonds"), status = WithdrawStatus.of(json.str("status")),
            note = json.str("note"), createdAtMs = json.long("created_at_ms"), decidedAtMs = json.long("decided_at_ms"),
        )
    }
}

@Immutable
data class WithdrawPageData(val items: List<WithdrawRequestData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = WithdrawPageData(
            items = json.objects("items").map(WithdrawRequestData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** The result of asking: the new request and the balance after the hold. */
@Immutable
data class WithdrawCreatedData(val request: WithdrawRequestData, val diamonds: Long, val applied: Boolean) {
    companion object {
        fun from(json: JSONObject) = WithdrawCreatedData(
            request = WithdrawRequestData.from(json.obj("request") ?: JSONObject()),
            diamonds = json.obj("balance")?.long("diamonds") ?: 0L,
            applied = json.bool("applied", true),
        )
    }
}

/** Typed wallet endpoints. */
class WalletApi internal constructor(private val api: ApiClient) {

    private fun window(from: String, to: String, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val q = HashMap<String, String>(extra.size + 3)
        q.putAll(extra)
        q["from"] = from
        q["to"] = to
        q["tz"] = tz.toString()
        return q
    }

    private fun page(beforeId: Long, limit: Int): Map<String, String> {
        val q = HashMap<String, String>(2)
        if (beforeId > 0L) q["before_id"] = beforeId.toString()
        q["limit"] = limit.toString()
        return q
    }

    /** Category totals, window totals and day rows for [from]..[to]. */
    suspend fun summary(from: String, to: String): ApiResponse<TxnSummaryData> =
        api.get("/v1/me/transactions", window(from, to), authenticated = true) { TxnSummaryData.from(it) }

    /** Gifts folded by the other person; [sent] = gifts I gave. */
    suspend fun giftGroups(from: String, to: String, sent: Boolean): ApiResponse<TxnGiftGroupsData> =
        api.get(
            "/v1/me/transactions/gifts",
            window(from, to, mapOf("dir" to if (sent) "sent" else "received")),
            authenticated = true,
        ) { TxnGiftGroupsData.from(it) }

    /** Every gift between me and [otherId] in the window, newest first. */
    suspend fun giftsWith(otherId: Long, from: String, to: String, sent: Boolean, beforeId: Long = 0L, limit: Int = 50): ApiResponse<TxnGiftPageData> =
        api.get(
            "/v1/me/transactions/gifts/$otherId",
            window(from, to, page(beforeId, limit) + ("dir" to if (sent) "sent" else "received")),
            authenticated = true,
        ) { TxnGiftPageData.from(it) }

    suspend fun purchases(from: String, to: String, beforeId: Long = 0L, limit: Int = 50): ApiResponse<TxnPurchasePageData> =
        api.get("/v1/me/transactions/store", window(from, to, page(beforeId, limit)), authenticated = true) { TxnPurchasePageData.from(it) }

    /** Raw ledger lines of one bucket; [category] "" = all. */
    suspend fun entries(category: String, from: String, to: String, beforeId: Long = 0L, limit: Int = 50): ApiResponse<TxnEntryPageData> =
        api.get(
            "/v1/me/transactions/entries",
            window(from, to, page(beforeId, limit) + ("category" to category)),
            authenticated = true,
        ) { TxnEntryPageData.from(it) }

    suspend fun withdrawHome(): ApiResponse<WithdrawHomeData> =
        api.get("/v1/me/withdraw", authenticated = true) { WithdrawHomeData.from(it) }

    /** Ask for a payout; [clientId] makes a retry land on the same request. */
    suspend fun withdraw(methodId: Long, accountTitle: String, accountNo: String, diamonds: Long, clientId: String): ApiResponse<WithdrawCreatedData> =
        api.post(
            "/v1/me/withdraw",
            jsonOf(
                "method_id" to methodId.toString(), "account_title" to accountTitle, "account_no" to accountNo,
                "diamonds" to diamonds, "client_id" to clientId,
            ),
            authenticated = true,
        ) { WithdrawCreatedData.from(it) }

    suspend fun withdrawals(beforeId: Long = 0L, limit: Int = 50): ApiResponse<WithdrawPageData> =
        api.get("/v1/me/withdraw/requests", page(beforeId, limit), authenticated = true) { WithdrawPageData.from(it) }
}
