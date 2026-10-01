package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Gifting levels. A person's lifetime coins sent (or received) place them on
 * a rung of the dashboard's ladder; the rung's picture sits next to their
 * name on every surface. The server resolves the rung, the app only draws.
 */
@Immutable
data class LevelData(
    /** Rung number, 1 = lowest reached. */
    val level: Int,
    /** The dashboard's label for the rung ("1", "Gold"). */
    val slug: String,
    val title: String,
    /** The rung's picture (still or animated); key or absolute URL. */
    val assetUrl: String,
    val assetMime: String,
    val thresholdCoins: Long,
    /** Next rung's bar; 0 = top of the ladder. */
    val nextCoins: Long,
) {
    fun toJson(): JSONObject = jsonOf(
        "level" to level,
        "slug" to slug,
        "title" to title,
        "asset_url" to assetUrl,
        "asset_mime" to assetMime,
        "threshold_coins" to thresholdCoins,
        "next_coins" to nextCoins,
    )

    companion object {
        /** Null for a missing / empty object: nobody wears "no level". */
        fun fromOrNull(json: JSONObject?): LevelData? {
            if (json == null) return null
            val level = json.int("level")
            if (level <= 0) return null
            return LevelData(
                level = level,
                slug = json.str("slug"),
                title = json.str("title"),
                assetUrl = json.str("asset_url"),
                assetMime = json.str("asset_mime"),
                thresholdCoins = json.long("threshold_coins"),
                nextCoins = json.long("next_coins"),
            )
        }
    }
}

/** One ladder, lowest rung first. */
@Immutable
data class LevelLadderData(val kind: String, val tiers: List<LevelData>) {
    companion object {
        fun from(json: JSONObject?) = LevelLadderData(
            kind = json?.str("kind") ?: "",
            tiers = json?.objects("tiers")?.mapNotNull(LevelData::fromOrNull) ?: emptyList(),
        )
    }
}

@Immutable
data class LevelCatalogData(val sender: LevelLadderData, val receiver: LevelLadderData, val rev: Long) {
    companion object {
        fun from(json: JSONObject) = LevelCatalogData(
            sender = LevelLadderData.from(json.obj("sender")),
            receiver = LevelLadderData.from(json.obj("receiver")),
            rev = json.long("rev"),
        )
    }
}

/** The signed-in account's gifting standing: totals and both rungs. */
@Immutable
data class GiftStandingData(
    val sentCoins: Long,
    val sentCount: Long,
    val receivedCoins: Long,
    val receivedCount: Long,
    val senderLevel: LevelData?,
    val receiverLevel: LevelData?,
    /** Coins for the next rung on each ladder; 0 = top or no ladder. */
    val senderNext: Long,
    val receiverNext: Long,
) {
    companion object {
        val Empty = GiftStandingData(0L, 0L, 0L, 0L, null, null, 0L, 0L)
        fun from(json: JSONObject?) = if (json == null) Empty else GiftStandingData(
            sentCoins = json.long("sent_coins"),
            sentCount = json.long("sent_count"),
            receivedCoins = json.long("received_coins"),
            receivedCount = json.long("received_count"),
            senderLevel = LevelData.fromOrNull(json.obj("sender_level")),
            receiverLevel = LevelData.fromOrNull(json.obj("receiver_level")),
            senderNext = json.long("sender_next"),
            receiverNext = json.long("receiver_next"),
        )
    }
}

/** One UTC day of the caller's gifting. */
@Immutable
data class GiftDayData(
    /** YYYY-MM-DD */
    val day: String,
    val sentCoins: Long,
    val sentCount: Long,
    val receivedCoins: Long,
    val receivedCount: Long,
) {
    companion object {
        fun from(json: JSONObject) = GiftDayData(
            day = json.str("day"),
            sentCoins = json.long("sent_coins"),
            sentCount = json.long("sent_count"),
            receivedCoins = json.long("received_coins"),
            receivedCount = json.long("received_count"),
        )
    }
}

/** The record screen in one read: standing, the range's sums, and its days. */
@Immutable
data class GiftStatsData(
    val me: GiftStandingData,
    val from: String,
    val to: String,
    val rangeSentCoins: Long,
    val rangeSentCount: Long,
    val rangeReceivedCoins: Long,
    val rangeReceivedCount: Long,
    val days: List<GiftDayData>,
) {
    companion object {
        fun from(json: JSONObject): GiftStatsData {
            val range = json.obj("range")
            return GiftStatsData(
                me = GiftStandingData.from(json.obj("me")),
                from = json.str("from"),
                to = json.str("to"),
                rangeSentCoins = range?.long("sent_coins") ?: 0L,
                rangeSentCount = range?.long("sent_count") ?: 0L,
                rangeReceivedCoins = range?.long("received_coins") ?: 0L,
                rangeReceivedCount = range?.long("received_count") ?: 0L,
                days = json.objects("days").map(GiftDayData::from),
            )
        }
    }
}

/** Typed level endpoints. */
class LevelApi internal constructor(private val api: ApiClient) {

    /** Both ladders, for the progress strip. */
    suspend fun catalog(): ApiResponse<LevelCatalogData> =
        api.get("/v1/levels/catalog", authenticated = true) { LevelCatalogData.from(it) }

    /** The caller's record for [from]..[to] (YYYY-MM-DD, UTC, inclusive). */
    suspend fun myStats(from: String, to: String): ApiResponse<GiftStatsData> =
        api.get(
            "/v1/me/gifts/stats",
            mapOf("from" to from, "to" to to),
            authenticated = true,
        ) { GiftStatsData.from(it) }
}
