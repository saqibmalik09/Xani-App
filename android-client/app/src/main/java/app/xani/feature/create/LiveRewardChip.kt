package app.xani.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.LiveFeed
import app.xani.core.net.RewardCampaignStatus
import app.xani.core.net.RewardCondition
import app.xani.core.net.RewardEligibility
import app.xani.core.net.RewardToast
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

/**
 * "Am I earning for this?" before and during a live. The setup form asks
 * the server once per broadcast type (audio/video); the HUD asks on entry,
 * again whenever a reward lands, and every minute so the "next reward in"
 * line never drifts far from the server's own projection.
 */
@Composable
internal fun rememberRewardEligibility(isVideo: Boolean, live: Boolean): RewardEligibility? {
    var el by remember { mutableStateOf<RewardEligibility?>(null) }
    var key by remember { mutableStateOf(0) }
    LaunchedEffect(isVideo, key) {
        val r = Xani.rewards.eligibility(role = "host", mode = if (isVideo) "video" else "audio")
        r.value?.let { el = it }
    }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        // The hub says a reward landed: re-read the projection, never patch it.
        LiveFeed.rewards.collect { key++ }
    }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            delay(60_000)
            key++
        }
    }
    return el
}

/** One line under the Go Live button: eligible (what / when) or why not. */
@Composable
internal fun RewardEligibilityChip(copy: Copy, el: RewardEligibility?) {
    el ?: return
    if (!el.enabled || el.campaigns.isEmpty()) return
    val next = el.next
    val (tint, text) = when {
        next != null -> Color(0xFF16A34A) to copy.t(Copy.RewardsEligible) + " · " + copy.t(
            Copy.RewardsEligibleNext,
            "amt" to "${next.nextAmount} ${next.nextAsset}",
            "t" to fmtShort(next.nextThresholdMs),
        )
        el.campaigns.all { it.complete } -> Color(0xFF16A34A) to copy.t(Copy.RewardsEligibleDone)
        else -> {
            val bad = whyNotCampaign(el)
            val policy = bad?.let { qualifiedDaysPolicy(copy, it) }
            Color(0xFFD97706) to (policy ?: (copy.t(Copy.RewardsNotEligible) + " · " + notEligibleWhy(copy, bad)))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = rdp(8)).clip(RoundedCornerShape(rdp(12)))
            .background(tint.copy(alpha = 0.1f)).padding(horizontal = rdp(12), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(7)).clip(CircleShape).background(tint))
        Spacer(Modifier.width(rdp(8)))
        Column(Modifier.weight(1f)) {
            XaniText(text, Type.LabelBold, color = tint, fontSizeOverride = 12f, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (next != null && next.levels.size > 1) {
                val ladder = next.levels.joinToString("  ") { l ->
                    (if (l.paid) "✓ " else "") + fmtShort(l.thresholdMs) + " → " + l.amount
                }
                XaniText(ladder, Type.Micro, color = tint.copy(alpha = 0.85f), fontSizeOverride = 10.5f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * HUD line beside the clock while live: "100 coins in 12:30" counting down
 * on the verified figure, or "100 coins earned" once the ledger says so.
 */
internal fun rewardHudText(copy: Copy, el: RewardEligibility?, verifiedMs: Long): String? {
    el ?: return null
    if (!el.enabled) return null
    val next = el.next ?: run {
        faceBlockedHudText(copy, el, verifiedMs)?.let { return it }
        // Nothing to earn right now: say why (cap reached, level not unlocked,
        // group off, …) instead of going silent — the host asked the screen.
        val bad = whyNotCampaign(el) ?: return null
        qualifiedDaysPolicy(copy, bad)?.let { return it }
        return copy.t(Copy.RewardsNotEligible) + " · " + notEligibleWhy(copy, bad)
    }
    val amt = "${next.nextAmount} ${next.nextAsset}"
    // Progress is measured on the server's period total, not this session:
    // remaining = next threshold − verified so far in the period, advanced
    // locally by what the HUD clock gained since the projection landed.
    val gained = (verifiedMs - next.verifiedMs).coerceAtLeast(0L)
    val remaining = (next.remainingMs - gained).coerceAtLeast(0L)
    return if (remaining == 0L) copy.t(Copy.RewardsHudEarned, "amt" to amt)
    else copy.t(Copy.RewardsHudNext, "amt" to amt, "t" to fmtCountdown(remaining))
}

/**
 * The ineligible campaign this user actually belongs to. Campaigns are
 * independent (a Star host and a Host/Agency account can each run their own),
 * so when several are ineligible the one worth explaining is the one whose
 * role the user holds — only a progress rule (time, gifts, qualifying days)
 * is holding them back, not USER_ROLE/LIVE_ROLE. A role mismatch on every
 * campaign (account truly has no eligible role anywhere) falls back to the
 * first, same as before.
 */
private fun whyNotCampaign(el: RewardEligibility): RewardCampaignStatus? {
    val bad = el.campaigns.filter { !it.eligible }
    return bad.firstOrNull { c ->
        c.reason != "USER_ROLE" &&
            c.conditions.none { !it.pass && (it.type == "USER_ROLE" || it.type == "LIVE_ROLE") }
    } ?: bad.firstOrNull()
}

/** The campaign is held back only by the FACE_VERIFIED rule (server reason or the failed leaf). */
private fun RewardCampaignStatus.blockedByFace(): Boolean =
    !eligible && (reason == "FACE_NOT_VERIFIED" || conditions.any { !it.pass && it.type == "FACE_VERIFIED" })

/**
 * HUD line when nothing is eligible because the face is not verified:
 * before the level is reached, a warning that rewards will not be added;
 * once the time is complete (or the server already blocked the reached
 * hour), "Face not verified · {amt} not added". Null when face is not the
 * reason, so every other case keeps its current (empty) HUD.
 */
private fun faceBlockedHudText(copy: Copy, el: RewardEligibility, verifiedMs: Long): String? {
    val c = el.campaigns.filter { it.blockedByFace() }.minByOrNull { it.remainingMs } ?: return null
    val amt = "${c.nextAmount} ${c.nextAsset}"
    val gained = (verifiedMs - c.verifiedMs).coerceAtLeast(0L)
    val reached = c.pending.any { it.reason == "FACE_NOT_VERIFIED" } ||
        (c.nextThresholdMs > 0L && c.remainingMs - gained <= 0L)
    return if (reached && c.nextAmount > 0L) copy.t(Copy.RewardsHudFaceNotAdded, "amt" to amt)
    else copy.t(Copy.RewardsHudFaceNeeded)
}

/**
 * The in-live toast: one reward landed. Dedupes on ledger id across
 * reconnect replays; a second toast within the display window queues behind
 * the first rather than replacing it mid-read.
 */
@Composable
internal fun rememberRewardToasts(live: Boolean): RewardToast? {
    var current by remember { mutableStateOf<RewardToast?>(null) }
    val seen = remember { HashSet<Long>() }
    val queue = remember { ArrayDeque<RewardToast>() }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        LiveFeed.rewards.collect { t ->
            if (!seen.add(t.ledgerId)) return@collect
            if (seen.size > 512) {
                seen.clear()
                seen.add(t.ledgerId)
            }
            if (current == null) current = t else if (queue.size < 8) queue.addLast(t)
        }
    }
    LaunchedEffect(current) {
        current ?: return@LaunchedEffect
        delay(4_500)
        current = queue.removeFirstOrNull()
    }
    return current
}

@Composable
internal fun RewardToastView(copy: Copy, t: RewardToast, modifier: Modifier = Modifier) {
    val amt = "${t.amount} ${t.asset}"
    val line = when (t.status) {
        "claimable" -> copy.t(Copy.RewardsToastClaimable, "amt" to amt)
        "pending" -> copy.t(Copy.RewardsToastPending, "amt" to amt)
        else -> if (t.before != null && t.after != null) copy.t(Copy.RewardsToastAdded, "amt" to amt, "b" to t.before.toString(), "a" to t.after.toString())
        else "+$amt"
    }
    Row(
        modifier.clip(RoundedCornerShape(rdp(16))).background(Color(0xE6111111)).padding(horizontal = rdp(14), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(28)).clip(CircleShape).background(Color(0xFFF59E0B)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(rdp(14)).clip(CircleShape).background(Color(0xFFFCD34D)))
        }
        Spacer(Modifier.width(rdp(10)))
        Column {
            XaniText(line, Type.LabelBold, color = Color.White, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                if (t.requiredMs > 0L) append(fmtShort(t.requiredMs)).append(" live")
                if (t.addedAt.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(t.addedAt) }
                else if (t.reachedAt.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(t.reachedAt) }
            }
            if (sub.isNotEmpty()) XaniText(sub, Type.Micro, color = Color.White.copy(alpha = 0.75f), fontSizeOverride = 10.5f, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * "Valid days" campaigns (QUALIFIED_DAYS): while the cycle is open the rule
 * itself is the whole message — go live {target} a day, {need} valid days,
 * then the reward. No live counters here on purpose: the day tally and
 * today's hours refresh too slowly to show truthfully in real time, and a
 * number that lags behind the clock reads as broken, not merely stale.
 * Null unless QUALIFIED_DAYS is the only thing holding the campaign back,
 * so every other reason keeps its existing "Not eligible" message.
 */
private fun qualifiedDaysPolicy(copy: Copy, c: RewardCampaignStatus): String? {
    val failed = c.conditions.firstOrNull { !it.pass } ?: return null
    if (failed.type != "QUALIFIED_DAYS") return null
    val need = failed.required.toIntOrNull() ?: return null
    val target = c.levels.firstOrNull()?.thresholdMs?.takeIf { it > 0L } ?: return null
    val base = copy.t(Copy.RewardsQualifyingPolicy, "target" to fmtShort(target), "need" to need.toString())
    if (c.nextAmount <= 0L) return base
    return base + " · " + copy.t(Copy.RewardsQualifyingReward, "amt" to "${c.nextAmount} ${c.nextAsset}")
}

/** Prefer the first failed condition (type + required vs actual); else the reason code. */
private fun notEligibleWhy(copy: Copy, c: RewardCampaignStatus?): String {
    c ?: return copy.t(Copy.RewardsNotEligibleNone)
    val failed = c.conditions.firstOrNull { !it.pass }
    if (failed != null) {
        if (failed.type == "FACE_VERIFIED") return copy.t(Copy.RewardsNotEligibleFace)
        return copy.t(Copy.RewardsNotEligibleNeed, "which" to failedConditionWhich(failed))
    }
    return when (c.reason) {
        "ROLE_NOT_ALLOWED", "MODE_NOT_ALLOWED" -> copy.t(Copy.RewardsNotEligibleRole)
        "FACE_NOT_VERIFIED" -> copy.t(Copy.RewardsNotEligibleFace)
        "USER_ROLE" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "platform role")
        "TIME_NOT_REACHED" -> copy.t(Copy.RewardsNotEligibleTime, "t" to fmtShort(c.remainingMs.coerceAtLeast(0L)))
        "MAX_TOTAL", "MAX_PER_DAY", "MAX_PER_WEEK", "MAX_PER_MONTH", "MIN_INTERVAL",
        "TIER_MAX_HOURS", "LEVEL_MAX_TOTAL", "LEVEL_MAX_PER_DAY", "LEVEL_MAX_PER_WEEK", "LEVEL_MAX_PER_MONTH",
        -> copy.t(Copy.RewardsNotEligibleLimit)
        // Gift-level ladder: no tier reached yet / the paying group is off.
        "RATE_NOT_UNLOCKED" -> copy.t(Copy.RewardsNotEligibleUnlock)
        "LADDER_OFF" -> copy.t(Copy.RewardsNotEligibleOff)
        "MIN_SESSION_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "a longer single live session")
        "ACCOUNT_AGE" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "account age")
        "GIFT_VALUE_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "more gift value received")
        "GIFT_COUNT_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "more gifts received")
        "UNIQUE_GIFTERS_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "more different gifters")
        "SENT_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "more gifts sent")
        "QUALIFIED_DAYS_NOT_MET" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "more qualifying days")
        "LEVEL_SCHEDULE" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "a scheduled day for this level")
        "LEVEL_PREVIOUS_UNPAID" -> copy.t(Copy.RewardsNotEligibleNeed, "which" to "the previous level first")
        "POLICY_DISABLED", "" -> copy.t(Copy.RewardsNotEligibleNone)
        // Never a mystery: an unmapped code still names itself.
        else -> copy.t(Copy.RewardsNotEligibleNeed, "which" to c.reason.lowercase().replace('_', ' '))
    }
}

/** "platform role host (yours: moderator)" — short enough for the chip. */
private fun failedConditionWhich(c: RewardCondition): String {
    val need = prettyVal(c.required)
    val have = prettyVal(c.actual)
    val label = when (c.type) {
        "USER_ROLE" -> "platform role"
        "LIVE_ROLE" -> "live role"
        "LIVE_MODE" -> "live type"
        "SESSION_TIME" -> "session live time"
        "ACCOUNT_AGE" -> "account age (days)"
        "FACE_VERIFIED" -> "face verification"
        "RECEIVED_GIFT_VALUE" -> "gift value received"
        "RECEIVED_GIFT_COUNT" -> "gifts received"
        "UNIQUE_GIFTERS" -> "different gifters"
        "SENT_GIFT_VALUE" -> "gift value sent"
        "SENT_GIFT_COUNT" -> "gifts sent"
        "UNIQUE_RECIPIENTS" -> "different recipients"
        "QUALIFIED_DAYS" -> "qualifying days"
        else -> c.type.lowercase().replace('_', ' ')
    }
    val needPart = if (need.isEmpty()) label else "$label $need"
    return if (have.isEmpty() || have == need) needPart else "$needPart (yours: $have)"
}

/** JSON arrays / quotes → "host, cohost". */
private fun prettyVal(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty() || s == "null" || s == "[]") return ""
    if (s.startsWith("[")) s = s.removePrefix("[").removeSuffix("]")
    return s.split(',').map { it.trim().trim('"').trim('\'') }.filter { it.isNotEmpty() && it != "null" }.joinToString(", ")
}

/** "30m" / "1h 30m" for thresholds. */
internal fun fmtShort(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0L)
    val h = m / 60L
    return when {
        h > 0L && m % 60L == 0L -> "${h}h"
        h > 0L -> "${h}h ${m % 60L}m"
        else -> "${m}m"
    }
}

/** "12:30" / "1:02:05" for the countdown. */
internal fun fmtCountdown(ms: Long): String {
    val s = (ms + 999L) / 1000L
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
