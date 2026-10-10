package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Coin sellers. A seller (member of a console-flagged role) holds a float the
 * console tops up and sends it to users; users browse the active sellers,
 * request a recharge (with a payment screenshot when the console requires
 * one) and can dispute a transfer. Every money move already landed in the
 * ledger server-side; these endpoints read and drive the records around it.
 */

/** The seller's own numbers: sent today / this week / this month / ever. */
@Immutable
data class SellerTotalsData(
    val sentToday: Long,
    val sentWeek: Long,
    val sentMonth: Long,
    val sentTotal: Long,
    val sendCount: Long,
    val toppedUp: Long,
    val received: Long,
) {
    companion object {
        val Zero = SellerTotalsData(0, 0, 0, 0, 0, 0, 0)
        fun from(json: JSONObject?) = if (json == null) Zero else SellerTotalsData(
            sentToday = json.long("sent_today"), sentWeek = json.long("sent_week"),
            sentMonth = json.long("sent_month"), sentTotal = json.long("sent_total"),
            sendCount = json.long("send_count"), toppedUp = json.long("topped_up"),
            received = json.long("received"),
        )
    }
}

/** `GET /v1/seller/me`: drawer gate plus the whole Overview tab. */
@Immutable
data class SellerMeData(
    val isSeller: Boolean,
    val sellersEnabled: Boolean,
    val active: Boolean,
    val canSend: Boolean,
    val whatsapp: String,
    val accountNo: String,
    val minSend: Long,
    val maxSend: Long,
    val maxPerUserDay: Long,
    val feeBps: Long,
    val debitCurrency: String,
    val creditCurrency: String,
    val policy: String,
    val totals: SellerTotalsData,
    val coins: Long,
    val diamonds: Long,
    val beans: Long,
) {
    /** The seller's float in the configured debit currency. */
    val sendable: Long
        get() = when (debitCurrency) {
            "diamonds" -> diamonds
            "beans" -> beans
            else -> coins
        }

    companion object {
        fun from(json: JSONObject): SellerMeData {
            val b = json.obj("balance")
            return SellerMeData(
                isSeller = json.bool("is_seller"), sellersEnabled = json.bool("sellers_enabled"),
                active = json.bool("active"), canSend = json.bool("can_send"),
                whatsapp = json.str("whatsapp"), accountNo = json.str("account_no"),
                minSend = json.long("min_send"), maxSend = json.long("max_send"),
                maxPerUserDay = json.long("max_per_user_day"), feeBps = json.long("fee_bps"),
                debitCurrency = json.str("debit_currency").ifEmpty { "beans" },
                creditCurrency = json.str("credit_currency").ifEmpty { "coins" },
                policy = json.str("policy"), totals = SellerTotalsData.from(json.obj("totals")),
                coins = b?.long("coins") ?: 0L, diamonds = b?.long("diamonds") ?: 0L, beans = b?.long("beans") ?: 0L,
            )
        }
    }
}

/** The receiver preview before money moves: be sure it is the right person. */
@Immutable
data class SellerLookupData(
    val userId: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
) {
    companion object {
        fun from(json: JSONObject) = SellerLookupData(
            userId = json.id("user_id"), username = json.str("username"),
            fullName = json.str("full_name"), avatarUrl = json.str("avatar_url"),
        )
    }
}

/** One seller → user send, with both parties joined. */
@Immutable
data class SellerTransferData(
    val id: Long,
    val sellerId: Long,
    val receiverId: Long,
    val amount: Long,
    val received: Long,
    val debitCurrency: String,
    val creditCurrency: String,
    val feeAmount: Long,
    val note: String,
    /** pending | completed | disputed | failed */
    val status: String,
    val statusNote: String,
    val createdAtMs: Long,
    val sellerUsername: String,
    val sellerName: String,
    val receiverUsername: String,
    val receiverName: String,
    val receiverAvatar: String,
) {
    companion object {
        fun from(json: JSONObject) = SellerTransferData(
            id = json.id("id"), sellerId = json.id("seller_id"), receiverId = json.id("receiver_id"),
            amount = json.long("amount"), received = json.long("received"),
            debitCurrency = json.str("debit_currency"), creditCurrency = json.str("credit_currency"),
            feeAmount = json.long("fee_amount"), note = json.str("note"),
            status = json.str("status"), statusNote = json.str("status_note"),
            createdAtMs = json.long("created_at_ms"),
            sellerUsername = json.str("seller_username"), sellerName = json.str("seller_name"),
            receiverUsername = json.str("receiver_username"), receiverName = json.str("receiver_name"),
            receiverAvatar = json.str("receiver_avatar"),
        )
    }
}

@Immutable
data class SellerTransferPageData(val items: List<SellerTransferData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = SellerTransferPageData(
            items = json.objects("items").map(SellerTransferData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** The result of a send: the transfer and the seller's balance after it. */
@Immutable
data class SellerSendResultData(
    val transfer: SellerTransferData,
    val coins: Long,
    val diamonds: Long,
    val beans: Long,
) {
    companion object {
        fun from(json: JSONObject): SellerSendResultData {
            val b = json.obj("balance")
            return SellerSendResultData(
                transfer = SellerTransferData.from(json.obj("transfer") ?: JSONObject()),
                coins = b?.long("coins") ?: 0L, diamonds = b?.long("diamonds") ?: 0L, beans = b?.long("beans") ?: 0L,
            )
        }
    }
}

/** One downline member on the read-only Team tab. */
@Immutable
data class SellerTeamData(
    val userId: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val roleTitle: String,
    val depth: Int,
    val sent: Long,
    val sentCount: Long,
    val received: Long,
) {
    companion object {
        fun from(json: JSONObject) = SellerTeamData(
            userId = json.id("user_id"), username = json.str("username"),
            fullName = json.str("full_name"), avatarUrl = json.str("avatar_url"),
            roleTitle = json.str("role_title"), depth = json.int("depth"),
            sent = json.long("sent"), sentCount = json.long("sent_count"), received = json.long("received"),
        )
    }
}

/** A user's recharge request to one seller, both parties joined. */
@Immutable
data class SellerRequestData(
    val id: Long,
    val sellerId: Long,
    val userId: Long,
    val amount: Long,
    val note: String,
    /** pending | approved | rejected | cancelled */
    val status: String,
    val decisionNote: String,
    val screenshotUrl: String,
    val createdAtMs: Long,
    val decidedAtMs: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val sellerUsername: String,
    val sellerName: String,
) {
    companion object {
        fun from(json: JSONObject) = SellerRequestData(
            id = json.id("id"), sellerId = json.id("seller_id"), userId = json.id("user_id"),
            amount = json.long("amount"), note = json.str("note"), status = json.str("status"),
            decisionNote = json.str("decision_note"), screenshotUrl = json.str("screenshot_url"),
            createdAtMs = json.long("created_at_ms"), decidedAtMs = json.long("decided_at_ms"),
            username = json.str("username"), fullName = json.str("full_name"), avatarUrl = json.str("avatar_url"),
            sellerUsername = json.str("seller_username"), sellerName = json.str("seller_name"),
        )
    }
}

@Immutable
data class SellerRequestPageData(val items: List<SellerRequestData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = SellerRequestPageData(
            items = json.objects("items").map(SellerRequestData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** One seller in the public list; which fields show follows the console. */
@Immutable
data class PublicSellerData(
    val userId: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val whatsapp: String,
    val accountNo: String,
    val sentCount: Long,
) {
    companion object {
        fun from(json: JSONObject) = PublicSellerData(
            userId = json.id("user_id"), username = json.str("username"),
            fullName = json.str("full_name"), avatarUrl = json.str("avatar_url"),
            whatsapp = json.str("whatsapp"), accountNo = json.str("account_no"),
            sentCount = json.long("sent_count"),
        )
    }
}

/** `GET /v1/sellers`: the list plus the console's display rules. */
@Immutable
data class SellerListData(
    val enabled: Boolean,
    val sellers: List<PublicSellerData>,
    /** screenshot | optional | contact | list */
    val requestMode: String,
    val canRequest: Boolean,
    val screenshotRequired: Boolean,
    val policy: String,
    val creditCurrency: String,
    val minSend: Long,
    val maxSend: Long,
) {
    companion object {
        fun from(json: JSONObject) = SellerListData(
            enabled = json.bool("enabled"),
            sellers = json.objects("sellers").map(PublicSellerData::from),
            requestMode = json.str("request_mode"), canRequest = json.bool("can_request"),
            screenshotRequired = json.bool("screenshot_required"), policy = json.str("policy"),
            creditCurrency = json.str("credit_currency").ifEmpty { "coins" },
            minSend = json.long("min_send"), maxSend = json.long("max_send"),
        )
    }
}

/** One dispute; who may read it is enforced server-side. */
@Immutable
data class SellerDisputeData(
    val id: Long,
    val transferId: Long,
    val sellerId: Long,
    val userId: Long,
    val reason: String,
    /** open | resolved | dismissed */
    val status: String,
    val resolution: String,
    val createdAtMs: Long,
    val username: String,
    val fullName: String,
    val sellerUsername: String,
    val sellerName: String,
) {
    companion object {
        fun from(json: JSONObject) = SellerDisputeData(
            id = json.id("id"), transferId = json.id("transfer_id"),
            sellerId = json.id("seller_id"), userId = json.id("user_id"),
            reason = json.str("reason"), status = json.str("status"), resolution = json.str("resolution"),
            createdAtMs = json.long("created_at_ms"),
            username = json.str("username"), fullName = json.str("full_name"),
            sellerUsername = json.str("seller_username"), sellerName = json.str("seller_name"),
        )
    }
}

/** One line of the dispute chat; [system] lines come from the console. */
@Immutable
data class SellerDisputeMessageData(
    val id: Long,
    val senderId: Long,
    val sender: String,
    val system: Boolean,
    val body: String,
    val createdAtMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = SellerDisputeMessageData(
            id = json.id("id"), senderId = json.id("sender_id"), sender = json.str("sender"),
            system = json.bool("system"), body = json.str("body"), createdAtMs = json.long("created_at_ms"),
        )
    }
}

/** Dispute detail: the header, the thread, and the disputed transfer. */
@Immutable
data class SellerDisputeDetailData(
    val dispute: SellerDisputeData,
    val messages: List<SellerDisputeMessageData>,
    val transfer: SellerTransferData?,
) {
    companion object {
        fun from(json: JSONObject) = SellerDisputeDetailData(
            dispute = SellerDisputeData.from(json.obj("dispute") ?: JSONObject()),
            messages = json.objects("messages").map(SellerDisputeMessageData::from),
            transfer = json.obj("transfer")?.let(SellerTransferData::from),
        )
    }
}

@Immutable
data class SellerDisputePageData(val items: List<SellerDisputeData>, val nextCursor: String) {
    companion object {
        fun from(json: JSONObject) = SellerDisputePageData(
            items = json.objects("items").map(SellerDisputeData::from),
            nextCursor = json.str("next_cursor"),
        )
    }
}

/** Typed coin-seller endpoints bound to the live session. */
class SellerApi internal constructor(private val api: ApiClient) {

    private fun page(beforeId: Long, limit: Int): Map<String, String> {
        val q = HashMap<String, String>(2)
        if (beforeId > 0L) q["before"] = beforeId.toString()
        q["limit"] = limit.toString()
        return q
    }

    // ─── the seller's own screens ─────────────────────────────────────────────

    suspend fun me(): ApiResponse<SellerMeData> =
        api.get("/v1/seller/me", authenticated = true) { SellerMeData.from(it) }

    /** Resolves a receiver by numeric id or exact username. */
    suspend fun lookup(q: String): ApiResponse<SellerLookupData> =
        api.get("/v1/seller/lookup", mapOf("q" to q), authenticated = true) { SellerLookupData.from(it) }

    /** Sends float to one user; [clientId] makes a retry land on the same transfer. */
    suspend fun send(to: String, amount: Long, note: String, clientId: String): ApiResponse<SellerSendResultData> =
        api.post(
            "/v1/seller/send",
            jsonOf("to" to to, "amount" to amount, "note" to note, "client_id" to clientId),
            authenticated = true,
        ) { SellerSendResultData.from(it) }

    suspend fun transfers(beforeId: Long = 0L, limit: Int = 50): ApiResponse<SellerTransferPageData> =
        api.get("/v1/seller/transfers", page(beforeId, limit), authenticated = true) { SellerTransferPageData.from(it) }

    suspend fun team(): ApiResponse<List<SellerTeamData>> =
        api.get("/v1/seller/team", authenticated = true) { it.objects("team").map(SellerTeamData::from) }

    /** Requests addressed to me; [status] "" = all, else pending/approved/rejected. */
    suspend fun requests(status: String = "", beforeId: Long = 0L, limit: Int = 50): ApiResponse<SellerRequestPageData> {
        val q = HashMap(page(beforeId, limit))
        if (status.isNotEmpty()) q["status"] = status
        return api.get("/v1/seller/requests", q, authenticated = true) { SellerRequestPageData.from(it) }
    }

    /** Approving sends the money (idempotent on the request id server-side). */
    suspend fun decideRequest(id: Long, approve: Boolean, note: String = ""): ApiResponse<SellerRequestData> =
        api.post(
            "/v1/seller/requests/$id/${if (approve) "approve" else "reject"}",
            jsonOf("note" to note),
            authenticated = true,
        ) { json -> SellerRequestData.from(json.obj("request") ?: json) }

    // ─── the user side ────────────────────────────────────────────────────────

    suspend fun list(): ApiResponse<SellerListData> =
        api.get("/v1/sellers", authenticated = true) { SellerListData.from(it) }

    /** Uploads the payment screenshot; returns the key to request with. */
    suspend fun uploadProof(bytes: ByteArray, mime: String, ext: String): ApiResponse<String> =
        api.putBytes(
            path = "/v1/sellers/proof?ext=${java.net.URLEncoder.encode(ext, "UTF-8")}",
            bytes = bytes,
            contentType = mime.ifEmpty { "image/jpeg" },
        ) { it.str("key") }

    /** Asks one seller for a recharge; [clientId] makes a retry land once. */
    suspend fun request(sellerId: Long, amount: Long, screenshotKey: String, note: String, clientId: String): ApiResponse<SellerRequestData> =
        api.post(
            "/v1/sellers/$sellerId/request",
            jsonOf("amount" to amount, "screenshot_key" to screenshotKey, "note" to note, "client_id" to clientId),
            authenticated = true,
        ) { SellerRequestData.from(it) }

    suspend fun myRequests(beforeId: Long = 0L, limit: Int = 50): ApiResponse<SellerRequestPageData> =
        api.get("/v1/me/seller-requests", page(beforeId, limit), authenticated = true) { SellerRequestPageData.from(it) }

    // ─── disputes ─────────────────────────────────────────────────────────────

    /** Files a dispute against [sellerId] (or the transfer's seller). */
    suspend fun openDispute(sellerId: Long, transferId: Long, reason: String): ApiResponse<SellerDisputeData> =
        api.post(
            "/v1/seller-disputes",
            jsonOf(
                "seller_id" to sellerId.toString(),
                "transfer_id" to if (transferId > 0L) transferId.toString() else "",
                "reason" to reason,
            ),
            authenticated = true,
        ) { SellerDisputeData.from(it) }

    /** Every dispute I am part of — as the filer or as the seller. */
    suspend fun myDisputes(beforeId: Long = 0L, limit: Int = 50): ApiResponse<SellerDisputePageData> =
        api.get("/v1/seller-disputes", page(beforeId, limit), authenticated = true) { SellerDisputePageData.from(it) }

    suspend fun dispute(id: Long): ApiResponse<SellerDisputeDetailData> =
        api.get("/v1/seller-disputes/$id", authenticated = true) { SellerDisputeDetailData.from(it) }

    suspend fun reply(disputeId: Long, body: String): ApiResponse<SellerDisputeMessageData> =
        api.post("/v1/seller-disputes/$disputeId/messages", jsonOf("body" to body), authenticated = true) {
            SellerDisputeMessageData.from(it)
        }
}
