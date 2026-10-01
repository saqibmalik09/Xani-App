package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Avatar frames wire types. A frame is a store item with duration packages;
 * owning one puts a ring around the user's avatar on every surface. Keys are
 * storage keys resolved through [MediaConfig], same as avatars.
 */

@Immutable
data class FramePriceData(val id: Long, val days: Int, val coinPrice: Long) {
    companion object {
        fun from(json: JSONObject) = FramePriceData(
            id = json.id("id"),
            days = json.int("days"),
            coinPrice = json.long("coin_price"),
        )
    }
}

@Immutable
data class FrameItemData(
    val id: Long,
    val categoryId: Long,
    /** frame · badge · entrance · vip · bubble — which surface the item decorates. */
    val kind: String,
    val title: String,
    val description: String,
    val previewKey: String,
    val assetKey: String,
    val assetMime: String,
    val prices: List<FramePriceData>,
) {
    companion object {
        fun from(json: JSONObject) = FrameItemData(
            id = json.id("id"),
            categoryId = json.id("category_id"),
            kind = json.str("kind").ifEmpty { "frame" },
            title = json.str("title"),
            description = json.str("description"),
            previewKey = json.str("preview_key"),
            assetKey = json.str("asset_key"),
            assetMime = json.str("asset_mime"),
            prices = json.objects("prices").map(FramePriceData::from),
        )
    }
}

@Immutable
data class FrameCategoryData(val id: Long, val title: String) {
    companion object {
        fun from(json: JSONObject) = FrameCategoryData(id = json.id("id"), title = json.str("title"))
    }
}

@Immutable
data class FrameCatalogData(val categories: List<FrameCategoryData>, val items: List<FrameItemData>) {
    companion object {
        val Empty = FrameCatalogData(emptyList(), emptyList())
        fun from(json: JSONObject) = FrameCatalogData(
            categories = json.objects("categories").map(FrameCategoryData::from),
            items = json.objects("items").map(FrameItemData::from),
        )
    }
}

/** One owned frame: active, parked, or expired. */
@Immutable
data class OwnedFrameData(
    val id: Long,
    val itemId: Long,
    val kind: String,
    val title: String,
    val previewKey: String,
    val assetKey: String,
    val assetMime: String,
    val source: String,
    val pricePaid: Long,
    val days: Int,
    val active: Boolean,
    val expired: Boolean,
    /** 0 = permanent. */
    val expiresAtMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = OwnedFrameData(
            id = json.id("id"),
            itemId = json.id("item_id"),
            kind = json.str("kind").ifEmpty { "frame" },
            title = json.str("item_title"),
            previewKey = json.str("preview_key"),
            assetKey = json.str("asset_key"),
            assetMime = json.str("asset_mime"),
            source = json.str("source"),
            pricePaid = json.long("price_paid"),
            days = json.int("days"),
            active = json.bool("active"),
            expired = json.bool("expired"),
            expiresAtMs = json.long("expires_at_ms"),
        )
    }
}

@Immutable
data class FramePurchaseData(val frame: OwnedFrameData, val balance: Balance, val applied: Boolean)

/** Typed frames-shop endpoints. */
class FramesApi internal constructor(private val api: ApiClient) {

    /** The shop: active frames with their duration packages, grouped by category. */
    suspend fun catalog(): ApiResponse<FrameCatalogData> =
        api.get("/v1/store/frames", authenticated = true) { FrameCatalogData.from(it) }

    /**
     * Buys one duration package of a frame. [clientId] is the idempotency key:
     * a retried tap buys once. 409 `insufficient_funds` when coins don't cover
     * it. The new frame is active on arrival; the old one parks, still owned.
     */
    suspend fun purchase(itemId: Long, priceId: Long, clientId: String): ApiResponse<FramePurchaseData> =
        api.post(
            "/v1/store/frames/purchase",
            jsonOf(
                "item_id" to itemId.toString(),
                "price_id" to priceId.toString(),
                "client_id" to clientId,
            ),
            authenticated = true,
        ) { d ->
            FramePurchaseData(
                frame = OwnedFrameData.from(d.obj("frame") ?: JSONObject()),
                balance = Balance.from(d.obj("balance") ?: JSONObject()),
                applied = d.bool("applied", true),
            )
        }

    /** Everything owned: active first, owned next, expired last. */
    suspend fun mine(): ApiResponse<List<OwnedFrameData>> =
        api.get("/v1/me/frames", authenticated = true) { d ->
            d.objects("items").map(OwnedFrameData::from)
        }

    /** Wear an owned, unexpired frame; the previous active one parks. */
    suspend fun activate(frameId: Long): ApiResponse<OwnedFrameData> =
        api.post("/v1/me/frames/$frameId/activate", JSONObject(), authenticated = true) {
            OwnedFrameData.from(it)
        }
}
