package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Live-time rewards as the host sees them: what was earned for time on air,
 * when the required time was completed, when the balance moved and from
 * what to what. Every figure is the server's — the same ledger row support
 * reads in the dashboard — and every day/time is already in the business
 * timezone the admin picked for live time.
 */

/** How the server cuts reward days: same calendar as live time. */
@Immutable
data class RewardCalendar(
    val tz: String,
    val dayStart: String,
    val weekStart: String,
    /** Today's business day, YYYY-MM-DD in [tz]. */
    val today: String,
    /** Server clock when the reply was built, unix ms. */
    val serverNowMs: Long,
) {
    companion object {
        val Empty = RewardCalendar("UTC", "00:00", "monday", "", 0L)

        fun from(json: JSONObject?): RewardCalendar {
            json ?: return Empty
            return RewardCalendar(
                tz = json.str("tz", "UTC"),
                dayStart = json.str("day_start", "00:00"),
                weekStart = json.str("week_start", "monday"),
                today = json.str("today"),
                serverNowMs = parseRfc3339Ms(json.str("now")),
            )
        }
    }
}

/** The rendered message: title/body plus the local-time facts behind them. */
@Immutable
data class RewardMessage(
    val title: String,
    val body: String,
    val detail: String,
    /** Business day YYYY-MM-DD. */
    val day: String,
    /** Local wall-clock text of the threshold crossing ("Fri 03 Oct 21:14:05"). */
    val reachedAt: String,
    /** Local wall-clock text of the wallet move; empty until added. */
    val addedAt: String,
    val before: Long?,
    val after: Long?,
    val tz: String,
    val status: String,
    val statusLabel: String,
) {
    companion object {
        val Empty = RewardMessage("", "", "", "", "", "", null, null, "", "", "")

        fun from(json: JSONObject?): RewardMessage {
            json ?: return Empty
            return RewardMessage(
                title = json.str("title"),
                body = json.str("body"),
                detail = json.str("detail"),
                day = json.str("day"),
                reachedAt = json.str("reached_at"),
                addedAt = json.str("added_at"),
                before = json.longOrNull("before"),
                after = json.longOrNull("after"),
                tz = json.str("tz"),
                status = json.str("status"),
                statusLabel = json.str("status_label"),
            )
        }
    }
}

/**
 * One reward. [status] is the user-facing word: pending | added | claimable
 * | claimed | expired | cancelled.
 */
@Immutable
data class RewardLedger(
    val id: Long,
    /** earn | manual | reversal */
    val kind: String,
    val campaignId: Long,
    val campaign: String,
    val day: String,
    val periodKind: String,
    /** host | cohost */
    val role: String,
    val modeRule: String,
    val requiredMs: Long,
    val verifiedMs: Long,
    val asset: String,
    val amount: Long,
    val status: String,
    val statusLabel: String,
    /** direct | claim */
    val delivery: String,
    val reachedAtMs: Long,
    val earnedAtMs: Long,
    /** 0 until the balance moved. */
    val issuedAtMs: Long,
    val claimableFromMs: Long,
    val claimableUntilMs: Long,
    val claimedAtMs: Long,
    val balanceBefore: Long?,
    val balanceAfter: Long?,
    /** issued_at − reached_at; how late the credit was. */
    val delayMs: Long,
    val message: RewardMessage,
    val note: String,
) {
    val added: Boolean get() = status == "added" || status == "claimed"

    companion object {
        fun from(json: JSONObject) = RewardLedger(
            id = json.id("id"),
            kind = json.str("kind", "earn"),
            campaignId = json.id("campaign_id"),
            campaign = json.str("campaign"),
            day = json.str("day"),
            periodKind = json.str("period_kind"),
            role = json.str("role", "host"),
            modeRule = json.str("mode_rule"),
            requiredMs = json.long("required_ms"),
            verifiedMs = json.long("verified_ms"),
            asset = json.str("asset", "coins"),
            amount = json.long("amount"),
            status = json.str("status"),
            statusLabel = json.str("status_label"),
            delivery = json.str("delivery", "direct"),
            reachedAtMs = parseRfc3339Ms(json.str("reached_at")),
            earnedAtMs = parseRfc3339Ms(json.str("earned_at")),
            issuedAtMs = parseRfc3339Ms(json.str("issued_at")),
            claimableFromMs = parseRfc3339Ms(json.str("claimable_from")),
            claimableUntilMs = parseRfc3339Ms(json.str("claimable_until")),
            claimedAtMs = parseRfc3339Ms(json.str("claimed_at")),
            balanceBefore = json.longOrNull("balance_before"),
            balanceAfter = json.longOrNull("balance_after"),
            delayMs = json.long("delay_ms"),
            message = RewardMessage.from(json.obj("message")),
            note = json.str("note"),
        )
    }
}

/** asset → amount, in the server's order (coins, diamonds, beans). */
typealias RewardAmounts = Map<String, Long>

private fun JSONObject.amounts(key: String): RewardAmounts {
    val o = obj(key) ?: return emptyMap()
    val out = LinkedHashMap<String, Long>(3)
    for (a in listOf("coins", "diamonds", "beans")) {
        val v = o.optLong(a, 0L)
        if (v != 0L) out[a] = v
    }
    val keys = o.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        if (!out.containsKey(k)) {
            val v = o.optLong(k, 0L)
            if (v != 0L) out[k] = v
        }
    }
    return out
}

/** One business day inside the Today / Week / Month / All summary. */
@Immutable
data class RewardDaySummary(
    val day: String,
    val label: String,
    val liveMs: Long,
    val added: RewardAmounts,
    val count: Int,
) {
    companion object {
        fun from(json: JSONObject) = RewardDaySummary(
            day = json.str("day"),
            label = json.str("label"),
            liveMs = json.long("live_ms"),
            added = json.amounts("added"),
            count = json.int("count"),
        )
    }
}

@Immutable
data class RewardSummary(
    /** today | week | month | all | custom */
    val range: String,
    val from: String,
    val to: String,
    val liveMs: Long,
    val earned: RewardAmounts,
    val added: RewardAmounts,
    val claimable: RewardAmounts,
    val pending: RewardAmounts,
    val count: Int,
    /** Newest first. */
    val days: List<RewardDaySummary>,
) {
    companion object {
        val Empty = RewardSummary("today", "", "", 0L, emptyMap(), emptyMap(), emptyMap(), emptyMap(), 0, emptyList())

        fun from(json: JSONObject?): RewardSummary {
            json ?: return Empty
            return RewardSummary(
                range = json.str("range", "today"),
                from = json.str("from"),
                to = json.str("to"),
                liveMs = json.long("live_ms"),
                earned = json.amounts("earned"),
                added = json.amounts("added"),
                claimable = json.amounts("claimable"),
                pending = json.amounts("pending"),
                count = json.int("count"),
                days = json.objects("days").map(RewardDaySummary::from),
            )
        }
    }
}

/** Per asset: what can be claimed right now and what is still waiting for its window. */
@Immutable
data class RewardClaimable(
    val asset: String,
    val amount: Long,
    val count: Int,
    val waitingAmount: Long,
    val nextOpensAtMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = RewardClaimable(
            asset = json.str("asset", "coins"),
            amount = json.long("amount"),
            count = json.int("count"),
            waitingAmount = json.long("waiting_amount"),
            nextOpensAtMs = parseRfc3339Ms(json.str("next_opens_at")),
        )
    }
}

@Immutable
data class RewardHome(
    val summary: RewardSummary,
    val claimable: List<RewardClaimable>,
    val calendar: RewardCalendar,
    val enabled: Boolean,
)

/** History: rewards grouped by business day with that day's live time beside them. */
@Immutable
data class RewardDayGroup(
    val day: String,
    val label: String,
    val liveMs: Long,
    val audioMs: Long,
    val videoMs: Long,
    val totals: RewardAmounts,
    val rewards: List<RewardLedger>,
) {
    companion object {
        fun from(json: JSONObject) = RewardDayGroup(
            day = json.str("day"),
            label = json.str("label"),
            liveMs = json.long("live_ms"),
            audioMs = json.long("audio_ms"),
            videoMs = json.long("video_ms"),
            totals = json.amounts("totals"),
            rewards = json.objects("rewards").map(RewardLedger::from),
        )
    }
}

@Immutable
data class RewardHistory(val days: List<RewardDayGroup>, val cursor: String, val calendar: RewardCalendar)

@Immutable
data class RewardTimelineStep(val atMs: Long, val local: String, val what: String) {
    companion object {
        fun from(json: JSONObject) = RewardTimelineStep(
            atMs = parseRfc3339Ms(json.str("at")),
            local = json.str("local"),
            what = json.str("what"),
        )
    }
}

/** One reward in full: the row plus its timeline in local business time. */
@Immutable
data class RewardDetail(
    val reward: RewardLedger,
    val timeline: List<RewardTimelineStep>,
    val calendar: RewardCalendar,
) {
    companion object {
        fun from(json: JSONObject) = RewardDetail(
            reward = RewardLedger.from(json),
            timeline = json.objects("timeline").map(RewardTimelineStep::from),
            calendar = RewardCalendar.from(json.obj("calendar")),
        )
    }
}

@Immutable
data class RewardLevel(
    val thresholdMs: Long,
    val asset: String,
    val amount: Long,
    val amountCohost: Long?,
    val label: String,
    val reached: Boolean,
    val paid: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = RewardLevel(
            thresholdMs = json.long("threshold_ms"),
            asset = json.str("asset", "coins"),
            amount = json.long("amount"),
            amountCohost = json.longOrNull("amount_cohost"),
            label = json.str("label"),
            reached = json.bool("reached"),
            paid = json.bool("paid"),
        )
    }
}

/** A threshold crossed but not yet paid: why, and when it is looked at again. */
@Immutable
data class RewardPending(
    val thresholdMs: Long,
    val status: String,
    val reason: String,
    val reachedAtMs: Long,
    val nextEvalAtMs: Long,
    val deadlineMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = RewardPending(
            thresholdMs = json.long("threshold_ms"),
            status = json.str("status"),
            reason = json.str("reason"),
            reachedAtMs = parseRfc3339Ms(json.str("reached_at")),
            nextEvalAtMs = parseRfc3339Ms(json.str("next_eval_at")),
            deadlineMs = parseRfc3339Ms(json.str("deadline")),
        )
    }
}

/** One condition on the campaign and whether this account passes it now. */
@Immutable
data class RewardCondition(val type: String, val required: String, val actual: String, val pass: Boolean, val reason: String) {
    companion object {
        fun from(json: JSONObject) = RewardCondition(
            type = json.str("type"),
            required = json.opt("required")?.toString().orEmpty(),
            actual = json.opt("actual")?.toString().orEmpty(),
            pass = json.bool("pass"),
            reason = json.str("reason"),
        )
    }
}

/** Where I stand on one campaign right now: eligible, how far, what is next and when. */
@Immutable
data class RewardCampaignStatus(
    val campaignId: Long,
    val campaign: String,
    val periodKind: String,
    val eligible: Boolean,
    /** Machine reason when not eligible (ROLE_NOT_ELIGIBLE, CONDITION_FAILED, LIMIT_REACHED, …). */
    val reason: String,
    val verifiedMs: Long,
    val nextThresholdMs: Long,
    val remainingMs: Long,
    val nextAmount: Long,
    val nextAsset: String,
    val nextDueAtMs: Long,
    val rewardsInPeriod: Int,
    val maxTotal: Int,
    val levels: List<RewardLevel>,
    val roles: List<String>,
    val modeRule: String,
    val delivery: String,
    val conditions: List<RewardCondition>,
    val pending: List<RewardPending>,
) {
    /** All paid levels reached for this period: nothing more to earn until it rolls. */
    val complete: Boolean get() = nextThresholdMs <= 0L && eligible

    companion object {
        fun from(json: JSONObject) = RewardCampaignStatus(
            campaignId = json.id("campaign_id"),
            campaign = json.str("campaign"),
            periodKind = json.str("period_kind", "day"),
            eligible = json.bool("eligible"),
            reason = json.str("reason"),
            verifiedMs = json.long("verified_ms"),
            nextThresholdMs = json.long("next_threshold_ms"),
            remainingMs = json.long("remaining_ms"),
            nextAmount = json.long("next_amount"),
            nextAsset = json.str("next_asset", "coins"),
            nextDueAtMs = parseRfc3339Ms(json.str("next_due_at")),
            rewardsInPeriod = json.int("rewards_in_period"),
            maxTotal = json.int("max_total"),
            levels = json.objects("levels").map(RewardLevel::from),
            roles = json.strings("roles"),
            modeRule = json.str("mode_rule"),
            delivery = json.str("delivery", "direct"),
            conditions = json.objects("conditions").map(RewardCondition::from),
            pending = json.objects("pending").map(RewardPending::from),
        )
    }
}

@Immutable
data class RewardEligibility(
    val live: Boolean,
    val counting: Boolean,
    val campaigns: List<RewardCampaignStatus>,
    val calendar: RewardCalendar,
    val claimable: List<RewardClaimable>,
    val enabled: Boolean,
) {
    /** Anything I can earn by going live now? */
    val anyEligible: Boolean get() = enabled && campaigns.any { it.eligible && it.nextThresholdMs > 0L }

    /** The closest next reward across campaigns, for the HUD line. */
    val next: RewardCampaignStatus? get() =
        campaigns.filter { it.eligible && it.nextThresholdMs > 0L }.minByOrNull { it.remainingMs }

    companion object {
        val Empty = RewardEligibility(false, false, emptyList(), RewardCalendar.Empty, emptyList(), false)

        fun from(json: JSONObject): RewardEligibility {
            val e = json.obj("eligibility") ?: JSONObject()
            return RewardEligibility(
                live = e.bool("live"),
                counting = e.bool("counting"),
                campaigns = e.objects("campaigns").map(RewardCampaignStatus::from),
                calendar = RewardCalendar.from(e.obj("calendar")),
                claimable = e.objects("claimable").map(RewardClaimable::from),
                enabled = json.bool("enabled", true),
            )
        }
    }
}

@Immutable
data class RewardClaimResult(
    val claimId: Long,
    val asset: String,
    val amount: Long,
    val count: Int,
    val balance: Balance,
    val duplicate: Boolean,
    val rewards: List<RewardLedger>,
) {
    companion object {
        fun from(json: JSONObject): RewardClaimResult {
            val c = json.obj("claim") ?: JSONObject()
            return RewardClaimResult(
                claimId = c.id("claim_id"),
                asset = c.str("asset", "coins"),
                amount = c.long("amount"),
                count = c.int("count"),
                balance = c.obj("balance")?.let(Balance::from) ?: Balance(),
                duplicate = c.bool("duplicate"),
                rewards = json.objects("rewards").map(RewardLedger::from),
            )
        }
    }
}

/**
 * The `reward_toast` hub event: one reward just landed (or became
 * claimable). Surfaces dedupe on [ledgerId] — the server may send it more
 * than once across reconnects, the host must see it once.
 */
@Immutable
data class RewardToast(
    val ledgerId: Long,
    val asset: String,
    val amount: Long,
    /** added | claimable | pending */
    val status: String,
    val title: String,
    val body: String,
    val requiredMs: Long,
    val day: String,
    val reachedAt: String,
    val addedAt: String,
    val before: Long?,
    val after: Long?,
) {
    companion object {
        fun from(json: JSONObject): RewardToast? {
            val id = json.id("ledger_id")
            if (id <= 0L) return null
            return RewardToast(
                ledgerId = id,
                asset = json.str("asset", "coins"),
                amount = json.long("amount"),
                status = json.str("status", "added"),
                title = json.str("title"),
                body = json.str("body"),
                requiredMs = json.long("required_ms"),
                day = json.str("day"),
                reachedAt = json.str("reached_at"),
                addedAt = json.str("added_at"),
                before = json.longOrNull("before"),
                after = json.longOrNull("after"),
            )
        }
    }
}

/** Nullable long: JSON `null` or absent → null, else the number. */
internal fun JSONObject.longOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key, 0L)

class RewardsApi internal constructor(private val api: ApiClient) {
    /**
     * The Today / Week / Month / All header plus the per-day rows for that
     * range; also what is claimable right now and the business calendar.
     */
    suspend fun me(range: String = "today", from: String = "", to: String = ""): ApiResponse<RewardHome> {
        val q = HashMap<String, String>(3)
        q["range"] = range
        if (from.isNotEmpty()) q["from"] = from
        if (to.isNotEmpty()) q["to"] = to
        return api.get("/v1/me/rewards", q, authenticated = true) { d ->
            RewardHome(
                summary = RewardSummary.from(d.obj("summary")),
                claimable = d.objects("claimable").map(RewardClaimable::from),
                calendar = RewardCalendar.from(d.obj("calendar")),
                enabled = d.bool("enabled", true),
            )
        }
    }

    /**
     * Rewards grouped by business day, newest first, keyset-paged. [status]
     * filters on the ledger word (earned|issued|claimable|claimed|expired),
     * [from]/[to] on business days (YYYY-MM-DD).
     */
    suspend fun history(
        from: String = "",
        to: String = "",
        status: String = "",
        asset: String = "",
        cursor: String = "",
        limit: Int = 50,
    ): ApiResponse<RewardHistory> {
        val q = HashMap<String, String>(6)
        if (from.isNotEmpty()) q["from"] = from
        if (to.isNotEmpty()) q["to"] = to
        if (status.isNotEmpty()) q["status"] = status
        if (asset.isNotEmpty()) q["asset"] = asset
        if (cursor.isNotEmpty()) q["cursor"] = cursor
        q["limit"] = limit.toString()
        return api.get("/v1/me/rewards/history", q, authenticated = true) { d ->
            RewardHistory(
                days = d.objects("days").map(RewardDayGroup::from),
                cursor = d.str("cursor"),
                calendar = RewardCalendar.from(d.obj("calendar")),
            )
        }
    }

    suspend fun detail(id: Long): ApiResponse<RewardDetail> =
        api.get("/v1/me/rewards/$id", authenticated = true) { d -> RewardDetail.from(d) }

    /**
     * Pre-live sheet and HUD: per campaign, am I eligible, what is next and
     * when. [role] (host|cohost) and [mode] (audio|video) are the presumption
     * for a user who is not live yet; a live user's open segment decides.
     */
    suspend fun eligibility(role: String = "", mode: String = ""): ApiResponse<RewardEligibility> {
        val q = HashMap<String, String>(2)
        if (role.isNotEmpty()) q["role"] = role
        if (mode.isNotEmpty()) q["mode"] = mode
        return api.get("/v1/me/rewards/eligibility", q, authenticated = true) { d -> RewardEligibility.from(d) }
    }

    /**
     * Pays every claimable reward of [asset] whose window is open, once.
     * [idemKey] makes a retry after a dropped reply return the first result
     * rather than pay twice.
     */
    suspend fun claim(asset: String, idemKey: String): ApiResponse<RewardClaimResult> =
        api.post("/v1/me/rewards/claim", jsonOf("asset" to asset, "idem_key" to idemKey), authenticated = true) { d ->
            RewardClaimResult.from(d)
        }
}
