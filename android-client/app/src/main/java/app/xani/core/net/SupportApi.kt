package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

@Immutable
data class HelpArticleData(
    val id: Long,
    val title: String,
    val body: String,
    val preview: String,
    val likeCount: Int,
    val dislikeCount: Int,
    val myVote: Int,
    val sortOrder: Int,
) {
    companion object {
        fun from(json: JSONObject) = HelpArticleData(
            id = json.id("id"),
            title = json.str("title"),
            body = json.str("body"),
            preview = json.str("preview").ifEmpty { json.str("body").take(160) },
            likeCount = json.optInt("like_count", 0),
            dislikeCount = json.optInt("dislike_count", 0),
            myVote = json.optInt("my_vote", 0),
            sortOrder = json.optInt("sort_order", 0),
        )
    }
}

@Immutable
data class SupportTicketData(
    val id: Long,
    val subject: String,
    val body: String,
    val status: String,
    val attachmentKeys: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val replyCount: Int,
) {
    companion object {
        fun from(json: JSONObject) = SupportTicketData(
            id = json.id("id"),
            subject = json.str("subject"),
            body = json.str("body"),
            status = json.str("status"),
            attachmentKeys = json.strings("attachment_keys"),
            createdAt = json.str("created_at"),
            updatedAt = json.str("updated_at"),
            replyCount = json.optInt("reply_count", 0),
        )
    }
}

@Immutable
data class SupportReplyData(
    val id: Long,
    val body: String,
    val isStaff: Boolean,
    val createdAt: String,
    val username: String,
) {
    companion object {
        fun from(json: JSONObject) = SupportReplyData(
            id = json.id("id"),
            body = json.str("body"),
            isStaff = json.optBoolean("is_staff", false),
            createdAt = json.str("created_at"),
            username = json.str("username"),
        )
    }
}

@Immutable
data class SupportTicketDetail(
    val ticket: SupportTicketData,
    val replies: List<SupportReplyData>,
) {
    companion object {
        fun from(json: JSONObject) = SupportTicketDetail(
            ticket = SupportTicketData.from(json.optJSONObject("ticket") ?: JSONObject()),
            replies = json.objects("replies").map(SupportReplyData::from),
        )
    }
}

class SupportApi(private val api: ApiClient) {
    suspend fun articles(): ApiResponse<List<HelpArticleData>> =
        api.get("/v1/help/articles", authenticated = true) { d ->
            d.objects("items").map(HelpArticleData::from)
        }

    suspend fun article(id: Long): ApiResponse<HelpArticleData> =
        api.get("/v1/help/articles/$id", authenticated = true) { HelpArticleData.from(it) }

    suspend fun vote(id: Long, vote: Int): ApiResponse<HelpArticleData> =
        api.post(
            "/v1/help/articles/$id/vote",
            body = jsonOf("vote" to vote),
            authenticated = true,
        ) { HelpArticleData.from(it) }

    suspend fun uploadTicketMedia(bytes: ByteArray, mime: String, ext: String): ApiResponse<String> =
        api.putBytes(
            path = "/v1/support/tickets/media?ext=${java.net.URLEncoder.encode(ext, "UTF-8")}",
            bytes = bytes,
            contentType = mime.ifEmpty { "application/octet-stream" },
        ) { it.str("key") }

    suspend fun createTicket(subject: String, body: String, keys: List<String>): ApiResponse<SupportTicketData> {
        val arr = org.json.JSONArray()
        keys.forEach { arr.put(it) }
        return api.post(
            "/v1/support/tickets",
            body = jsonOf(
                "subject" to subject,
                "body" to body,
                "attachment_keys" to arr,
            ),
            authenticated = true,
        ) { SupportTicketData.from(it) }
    }

    suspend fun myTickets(): ApiResponse<List<SupportTicketData>> =
        api.get("/v1/support/tickets", authenticated = true) { d ->
            d.objects("items").map(SupportTicketData::from)
        }

    suspend fun ticket(id: Long): ApiResponse<SupportTicketDetail> =
        api.get("/v1/support/tickets/$id", authenticated = true) { SupportTicketDetail.from(it) }

    suspend fun reply(id: Long, body: String): ApiResponse<SupportReplyData> =
        api.post(
            "/v1/support/tickets/$id/replies",
            body = jsonOf("body" to body),
            authenticated = true,
        ) { SupportReplyData.from(it) }

    suspend fun legalArticles(): ApiResponse<List<HelpArticleData>> =
        api.get("/v1/legal/articles", authenticated = true) { d ->
            d.objects("items").map(HelpArticleData::from)
        }

    suspend fun legalArticle(id: Long): ApiResponse<HelpArticleData> =
        api.get("/v1/legal/articles/$id", authenticated = true) { HelpArticleData.from(it) }

    suspend fun legalVote(id: Long, vote: Int): ApiResponse<HelpArticleData> =
        api.post(
            "/v1/legal/articles/$id/vote",
            body = jsonOf("vote" to vote),
            authenticated = true,
        ) { HelpArticleData.from(it) }
}
