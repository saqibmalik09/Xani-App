package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** One promotional banner from GET /v1/banners. */
@Immutable
data class BannerData(
    val id: Long,
    val title: String,
    val description: String,
    val imageKey: String,
    val placement: String,
    val sortOrder: Int,
) {
    companion object {
        fun from(json: JSONObject) = BannerData(
            id = json.id("id"),
            title = json.str("title"),
            description = json.str("description"),
            imageKey = json.str("image_key"),
            placement = json.str("placement"),
            sortOrder = json.optInt("sort_order", 0),
        )
    }
}

/** Public banner list (no auth). Placement tags where the client shows them. */
class BannerApi(private val api: ApiClient) {
    suspend fun list(placement: String, limit: Int = 20): ApiResponse<List<BannerData>> =
        api.get(
            "/v1/banners",
            query = mapOf(
                "placement" to placement,
                "limit" to limit.toString(),
            ),
            authenticated = false,
        ) { data -> data.objects("items").map(BannerData::from) }
}
