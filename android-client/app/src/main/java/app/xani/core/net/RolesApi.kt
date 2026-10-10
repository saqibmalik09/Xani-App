package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** Role identity as the server prints it: permanent code, slug, title. */
@Immutable
data class RoleBrief(val id: Int, val code: String, val slug: String, val title: String, val status: Int) {
    companion object {
        val Empty = RoleBrief(0, "", "", "", 0)
        fun from(json: JSONObject?) = if (json == null) Empty else RoleBrief(
            id = json.int("id"),
            code = json.str("code"),
            slug = json.str("slug"),
            title = json.str("title").ifEmpty { json.str("name") },
            status = json.int("status"),
        )
    }
}

/** Membership statuses agreed with the server (`store.Membership*`). */
object MembershipStatus {
    const val ACTIVE = 2
    const val SUSPENDED = 3
    const val REVOKED = 4
    const val BLOCKED = 5
}

/** Request kinds / statuses agreed with the server (`store.Request*`). */
object RoleRequestKind {
    const val ASSIGN = 1
    const val APPLY = 2
    const val TRANSFER = 3
    const val REVOKE = 4
    const val REACTIVATE = 5
}

object RoleRequestStatus {
    const val PENDING = 2
    const val APPROVED = 3
    const val REJECTED = 4
    const val CANCELLED = 5
    const val EXPIRED = 6
}

/** One person's seat in the tree. Used for own roles, upline and downline rows alike. */
@Immutable
data class MembershipData(
    val id: Long,
    val code: String,
    val userId: Long,
    val parentId: Long,
    val rootId: Long,
    val status: Int,
    val depth: Int,
    val treeDepth: Int,
    val directChildren: Int,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    /** This member's own code — what others enter to join under them. */
    val referralCode: String,
    /** The upline's code this member joined with (blank when placed directly). */
    val joinedCode: String,
    val approvedAt: String,
    val createdAt: String,
    val endedReason: String,
    val role: RoleBrief,
) {
    val displayName: String get() = fullName.ifBlank { "@$username" }
    val isActive: Boolean get() = status == MembershipStatus.ACTIVE

    companion object {
        fun from(json: JSONObject) = MembershipData(
            id = json.id("id"),
            code = json.str("code"),
            userId = json.id("user_id"),
            parentId = json.id("parent_id"),
            rootId = json.id("root_id"),
            status = json.int("status"),
            depth = json.int("depth"),
            treeDepth = json.int("tree_depth"),
            directChildren = json.int("direct_children"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
            referralCode = json.str("referral_code"),
            joinedCode = json.str("joined_code"),
            approvedAt = json.str("approved_at"),
            createdAt = json.str("created_at"),
            endedReason = json.str("ended_reason"),
            role = RoleBrief.from(json.obj("role")),
        )
    }
}

/** What a member sent and received in gifts — read-only upline view. */
@Immutable
data class GiftTotalsData(
    val receivedCoins: Long,
    val receivedDiamonds: Long,
    val receivedCount: Long,
    val sentCoins: Long,
    val sentCount: Long,
) {
    companion object {
        fun from(json: JSONObject?) = GiftTotalsData(
            receivedCoins = json?.long("received_coins") ?: 0L,
            receivedDiamonds = json?.long("received_diamonds") ?: 0L,
            receivedCount = json?.long("received_count") ?: 0L,
            sentCoins = json?.long("sent_coins") ?: 0L,
            sentCount = json?.long("sent_count") ?: 0L,
        )
    }
}

/** One withdrawal request as an upline may see it (account masked server-side). */
@Immutable
data class WithdrawBriefData(
    val id: Long,
    val diamonds: Long,
    val method: String,
    val account: String,
    val status: Int, // 0 pending, 1 approved, 2 rejected
    val note: String,
    val createdAt: String,
    val decidedAt: String,
) {
    companion object {
        fun from(json: JSONObject) = WithdrawBriefData(
            id = json.id("id"),
            diamonds = json.long("diamonds"),
            method = json.str("method"),
            account = json.str("account"),
            status = json.int("status"),
            note = json.str("note"),
            createdAt = json.str("created_at"),
            decidedAt = json.str("decided_at"),
        )
    }
}

/** One recharge (coin purchase) as an upline may see it. */
@Immutable
data class RechargeBriefData(
    val id: Long,
    val packageTitle: String,
    val currency: String,
    val price: String,
    val coins: Long,
    val method: String,
    val status: Int, // 0 pending, 1 approved, 2 rejected
    val note: String,
    val createdAt: String,
    val decidedAt: String,
) {
    companion object {
        fun from(json: JSONObject) = RechargeBriefData(
            id = json.id("id"),
            packageTitle = json.str("package"),
            currency = json.str("currency"),
            price = json.str("price"),
            coins = json.long("coins"),
            method = json.str("method"),
            status = json.int("status"),
            note = json.str("note"),
            createdAt = json.str("created_at"),
            decidedAt = json.str("decided_at"),
        )
    }
}

/** Approved recharge totals for one member. */
@Immutable
data class RechargeTotalsData(val count: Long, val coins: Long, val pendingCount: Long) {
    companion object {
        fun from(json: JSONObject?) = RechargeTotalsData(
            count = json?.long("count") ?: 0L,
            coins = json?.long("coins") ?: 0L,
            pendingCount = json?.long("pending_count") ?: 0L,
        )
    }
}

/** Read-only money picture of one downline member. */
@Immutable
data class MemberFinanceData(
    val membership: MembershipData,
    val gifts: GiftTotalsData,
    val recharge: RechargeTotalsData,
    val recharges: List<RechargeBriefData>,
    val rechargesNext: String,
    val withdrawals: List<WithdrawBriefData>,
    val next: String,
) {
    companion object {
        fun from(data: JSONObject) = MemberFinanceData(
            membership = MembershipData.from(data.obj("membership") ?: JSONObject()),
            gifts = GiftTotalsData.from(data.obj("gifts")),
            recharge = RechargeTotalsData.from(data.obj("recharge")),
            recharges = data.objects("recharges").map(RechargeBriefData::from),
            rechargesNext = data.str("recharges_next_cursor"),
            withdrawals = data.objects("withdrawals").map(WithdrawBriefData::from),
            next = data.str("next_cursor"),
        )
    }
}

@Immutable
data class MembershipPage(val items: List<MembershipData>, val next: String) {
    companion object {
        fun from(data: JSONObject) = MembershipPage(
            items = data.objects("items").map(MembershipData::from),
            next = data.str("next_cursor"),
        )
    }
}

/** Compact person, as embedded in requests and code lookups. */
@Immutable
data class UserBriefData(val id: Long, val username: String, val fullName: String, val avatarUrl: String) {
    val displayName: String get() = fullName.ifBlank { "@$username" }

    companion object {
        val Empty = UserBriefData(0L, "", "", "")
        fun from(json: JSONObject?) = if (json == null) Empty else UserBriefData(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
        )
    }
}

/** Compact membership (id, code, person, role) as embedded in requests. */
@Immutable
data class MembershipBriefData(
    val id: Long,
    val code: String,
    val userId: Long,
    val username: String,
    val fullName: String,
    val status: Int,
    val role: RoleBrief,
    val referralCode: String = "",
) {
    val displayName: String get() = fullName.ifBlank { "@$username" }

    companion object {
        fun from(json: JSONObject?): MembershipBriefData? {
            if (json == null) return null
            val id = json.id("id")
            if (id <= 0L) return null
            return MembershipBriefData(
                id = id,
                code = json.str("code"),
                userId = json.id("user_id"),
                username = json.str("username"),
                fullName = json.str("full_name"),
                status = json.int("status"),
                role = RoleBrief.from(json.obj("role")),
                referralCode = json.str("referral_code"),
            )
        }
    }
}

/** Field types the server's role forms use (`store.Field*`). */
object FormFieldType {
    const val TEXT = "text"
    const val TEXTAREA = "textarea"
    const val NUMBER = "number"
    const val DROPDOWN = "dropdown"
    const val COUNTRY = "country"
    const val PHONE = "phone"
    const val WHATSAPP = "whatsapp"
}

/**
 * One question on a role's application form. The dashboard defines these;
 * the app renders whatever arrives, so new roles and questions never need
 * an app release. [min]/[max] are length for text, value for numbers.
 */
@Immutable
data class FormFieldData(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val hint: String,
    val min: Double?,
    val max: Double?,
    val options: List<String>,
) {
    companion object {
        fun from(json: JSONObject) = FormFieldData(
            key = json.str("key"),
            label = json.str("label"),
            type = json.str("type").ifEmpty { FormFieldType.TEXT },
            required = json.bool("required"),
            hint = json.str("hint"),
            min = if (json.isNull("min")) null else json.optDouble("min"),
            max = if (json.isNull("max")) null else json.optDouble("max"),
            options = json.strings("options"),
        )
    }
}

@Immutable
data class ApplicationFormData(val role: RoleBrief, val fields: List<FormFieldData>) {
    companion object {
        fun from(json: JSONObject) = ApplicationFormData(
            role = RoleBrief.from(json.obj("role")),
            fields = json.objects("fields").map(FormFieldData::from),
        )
    }
}

/** A labelled answer from a submitted application (approver's view). */
@Immutable
data class FormAnswerData(val key: String, val label: String, val value: String)

/** One role a seat may bring people into, and how the caller can do it. */
@Immutable
data class RecruitOptionData(
    val role: RoleBrief,
    val formFields: Int,
    val canAssign: Boolean,
    val approveNow: Boolean,
    val viaCode: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = RecruitOptionData(
            role = RoleBrief.from(json.obj("role")),
            formFields = json.int("form_fields"),
            canAssign = json.bool("can_assign"),
            approveNow = json.bool("approve_now"),
            viaCode = json.bool("via_code"),
        )
    }
}

/** What the caller may do to members under one of their seats (server re-checks on use). */
@Immutable
data class WorkspaceActionsData(
    val assign: Boolean,
    val approve: Boolean,
    val suspend: Boolean,
    val block: Boolean,
    val revoke: Boolean,
    val transfer: Boolean,
    val viewDownline: Boolean,
    val viewUpline: Boolean,
    /** Role-policy self-service: detach from the upline (reason, upline informed). */
    val leaveUpline: Boolean = false,
    /** Role-policy self-service: ask to move under a new upline (they approve). */
    val shiftUpline: Boolean = false,
    /** Role-policy self-service: remove a direct downline (reason, member informed). */
    val removeDownline: Boolean = false,
) {
    val anyMemberAction: Boolean get() = suspend || block || revoke || transfer || removeDownline

    companion object {
        val None = WorkspaceActionsData(false, false, false, false, false, false, false, false)
        fun from(json: JSONObject?) = if (json == null) None else WorkspaceActionsData(
            assign = json.bool("assign"),
            approve = json.bool("approve"),
            suspend = json.bool("suspend"),
            block = json.bool("block"),
            revoke = json.bool("revoke"),
            transfer = json.bool("transfer"),
            viewDownline = json.bool("view_downline"),
            viewUpline = json.bool("view_upline"),
            leaveUpline = json.bool("leave_upline"),
            shiftUpline = json.bool("shift_upline"),
            removeDownline = json.bool("remove_downline"),
        )
    }
}

/** One of the caller's seats with everything its management screen needs. */
@Immutable
data class WorkspaceMembershipData(
    val membership: MembershipData,
    val upline: MembershipBriefData?,
    val recruit: List<RecruitOptionData>,
    val actions: WorkspaceActionsData,
) {
    companion object {
        fun from(json: JSONObject) = WorkspaceMembershipData(
            membership = MembershipData.from(json),
            upline = MembershipBriefData.from(json.obj("upline")),
            recruit = json.objects("recruit").map(RecruitOptionData::from),
            actions = WorkspaceActionsData.from(json.obj("actions")),
        )
    }
}

/** The organisation home: one call, everything the Roles screen renders. */
@Immutable
data class WorkspaceData(
    val user: UserBriefData,
    val memberships: List<WorkspaceMembershipData>,
    val pendingApprovals: Int,
    val permissions: Set<String>,
) {
    companion object {
        fun from(json: JSONObject) = WorkspaceData(
            user = UserBriefData.from(json.obj("user")),
            memberships = json.objects("memberships").map(WorkspaceMembershipData::from),
            pendingApprovals = json.int("pending_approvals"),
            permissions = json.strings("permissions").toSet(),
        )
    }
}

/**
 * A role request: assign / application / transfer / removal / reactivation.
 * [canDecide] and [canCancel] are the server's verdict for the *caller*; the
 * UI shows buttons from them and the server re-checks on tap.
 */
@Immutable
data class RoleRequestData(
    val id: Long,
    val code: String,
    val kind: Int,
    val status: Int,
    val subject: UserBriefData,
    val subjectUserId: Long,
    val role: RoleBrief,
    val parent: MembershipBriefData?,
    val membership: MembershipBriefData?,
    val requestedBy: UserBriefData,
    val requestedById: Long,
    val decidedBy: UserBriefData?,
    val note: String,
    val reason: String,
    val childrenOnly: Boolean,
    val downlineCount: Int,
    val createdAt: String,
    val decidedAt: String,
    val expiresAt: String,
    val canDecide: Boolean,
    val canCancel: Boolean,
    val kindLabel: String,
    val statusLabel: String,
    /** The upline's code the placement was requested with, if any. */
    val referralCode: String = "",
    /** Application answers, labelled from the role's form (empty when none). */
    val answers: List<FormAnswerData> = emptyList(),
) {
    val isPending: Boolean get() = status == RoleRequestStatus.PENDING

    companion object {
        private fun answersOf(json: JSONObject): List<FormAnswerData> {
            val data = json.obj("form_data") ?: return emptyList()
            val fields = json.objects("form").map(FormFieldData::from)
            val labels = fields.associate { it.key to it.label }
            val order = fields.mapIndexed { i, f -> f.key to i }.toMap()
            val out = ArrayList<FormAnswerData>(data.length())
            for (key in data.keys()) {
                if (data.isNull(key)) continue
                out.add(FormAnswerData(key, labels[key] ?: key, data.opt(key).toString()))
            }
            return out.sortedBy { order[it.key] ?: Int.MAX_VALUE }
        }

        fun from(json: JSONObject) = RoleRequestData(
            id = json.id("id"),
            code = json.str("code"),
            kind = json.int("kind"),
            status = json.int("status"),
            subject = UserBriefData.from(json.obj("subject")),
            subjectUserId = json.id("subject_user_id"),
            role = RoleBrief.from(json.obj("role")),
            parent = MembershipBriefData.from(json.obj("parent")),
            membership = MembershipBriefData.from(json.obj("membership")),
            requestedBy = UserBriefData.from(json.obj("requested_by_user")),
            requestedById = json.id("requested_by"),
            decidedBy = json.obj("decided_by_user")?.let(UserBriefData::from),
            note = json.str("note"),
            reason = json.str("reason"),
            childrenOnly = json.bool("children_only"),
            downlineCount = json.int("downline_count"),
            createdAt = json.str("created_at"),
            decidedAt = json.str("decided_at"),
            expiresAt = json.str("expires_at"),
            canDecide = json.bool("can_decide"),
            canCancel = json.bool("can_cancel"),
            kindLabel = json.str("kind_label"),
            statusLabel = json.str("status_label"),
            referralCode = json.str("referral_code"),
            answers = answersOf(json),
        )
    }
}

@Immutable
data class RoleRequestPage(val items: List<RoleRequestData>, val next: String) {
    companion object {
        fun from(data: JSONObject) = RoleRequestPage(
            items = data.objects("items").map(RoleRequestData::from),
            next = data.str("next_cursor"),
        )
    }
}

/** A code the caller can share, and which roles it onboards. */
@Immutable
data class JoinLinkData(val role: RoleBrief, val url: String) {
    companion object {
        fun from(json: JSONObject) = JoinLinkData(
            role = RoleBrief.from(json.obj("role")),
            url = json.str("url"),
        )
    }
}

data class ReferralCodeData(
    val code: String,
    val membershipId: Long,
    val role: RoleBrief,
    val targetRoles: List<RoleBrief>,
    val joinLinks: List<JoinLinkData> = emptyList(),
) {
    companion object {
        fun from(json: JSONObject) = ReferralCodeData(
            code = json.str("code"),
            membershipId = json.id("membership_id"),
            role = RoleBrief.from(json.obj("role")),
            targetRoles = json.objects("target_roles").map { RoleBrief.from(it) },
            joinLinks = json.objects("join_links").map(JoinLinkData::from),
        )
    }

    fun linkFor(role: RoleBrief): String {
        joinLinks.firstOrNull { it.role.slug.equals(role.slug, ignoreCase = true) && it.url.isNotBlank() }
            ?.url?.let { return it }
        return MediaConfig.joinLink(code, role.slug)
    }
}

/** What a code someone typed resolves to, before they commit to applying. */
@Immutable
data class CodeInfoData(val code: String, val owner: UserBriefData, val ownerRole: RoleBrief, val targetRoles: List<RoleBrief>) {
    companion object {
        fun from(json: JSONObject) = CodeInfoData(
            code = json.str("code"),
            owner = UserBriefData.from(json.obj("owner")),
            ownerRole = RoleBrief.from(json.obj("membership")?.obj("role")),
            targetRoles = json.objects("target_roles").map { RoleBrief.from(it) },
        )
    }
}

@Immutable
data class DownlineSummaryRow(val role: RoleBrief, val status: Int, val count: Int) {
    companion object {
        fun from(json: JSONObject) = DownlineSummaryRow(
            role = RoleBrief.from(json.obj("role")),
            status = json.int("status"),
            count = json.int("count"),
        )
    }
}

/**
 * Organisation roles for the signed-in account: what they hold, who is above
 * and below them, codes they can share, requests in flight and — for uplines —
 * the approvals inbox. Every write is pending-by-default on the server; a
 * response with status APPROVED only happens when the caller could approve
 * their own action.
 */
class RolesApi internal constructor(private val api: ApiClient) {
    suspend fun myMemberships(): ApiResponse<List<MembershipData>> =
        api.get("/v1/me/memberships", authenticated = true) { it.objects("items").map(MembershipData::from) }

    /** One call: seats, uplines, roles each may recruit, and the actions the caller holds. */
    suspend fun workspace(): ApiResponse<WorkspaceData> =
        api.get("/v1/me/workspace", authenticated = true, map = WorkspaceData::from)

    /** The questions to render before applying for / assigning [roleRef]. */
    suspend fun applicationForm(roleRef: String): ApiResponse<ApplicationFormData> =
        api.get("/v1/roles/${roleRef.trim()}/application-form", authenticated = true, map = ApplicationFormData::from)

    /**
     * Upline adds a member under their own seat. Pending unless [approveNow]
     * and the caller may approve it, in which case the response is APPROVED.
     */
    suspend fun assign(
        userId: Long,
        roleSlug: String,
        parentMembershipId: Long,
        form: Map<String, String>,
        approveNow: Boolean,
        note: String,
    ): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/requests/assign",
            jsonOf(
                "user_id" to userId.toString(),
                "role" to roleSlug,
                "parent_id" to parentMembershipId.toString(),
                "form" to answersJson(form),
                "approve_now" to approveNow,
                "note" to note.trim(),
            ),
            authenticated = true,
            map = RoleRequestData::from,
        )

    /** [kind] is suspend / reactivate / block / unblock; immediate, audited. */
    suspend fun transition(id: Long, kind: String, reason: String): ApiResponse<MembershipData> =
        api.post("/v1/memberships/$id/$kind", jsonOf("reason" to reason.trim()), authenticated = true, map = MembershipData::from)

    /** Remove from the role. [disposition] 1 keep downline in place · 2 move up · 3 suspend below. */
    suspend fun revoke(id: Long, reason: String, disposition: Int = 1): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/memberships/$id/revoke",
            jsonOf("reason" to reason.trim(), "disposition" to disposition),
            authenticated = true,
            map = RoleRequestData::from,
        )

    /** Move a member (or only their downline) under another upline. */
    suspend fun transfer(id: Long, newParentId: Long, childrenOnly: Boolean, note: String): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/memberships/$id/transfer",
            jsonOf("new_parent_id" to newParentId.toString(), "children_only" to childrenOnly, "note" to note.trim()),
            authenticated = true,
            map = RoleRequestData::from,
        )

    /** Leave my own upline (role kept, reason required, upline informed). Immediate. */
    suspend fun leaveUpline(id: Long, reason: String): ApiResponse<MembershipData> =
        api.post("/v1/memberships/$id/leave-upline", jsonOf("reason" to reason.trim()), authenticated = true, map = MembershipData::from)

    /** Ask to move under a new upline by their referral code; they approve or reject. */
    suspend fun shiftUpline(id: Long, code: String, note: String): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/memberships/$id/shift-upline",
            jsonOf("code" to code.trim(), "note" to note.trim()),
            authenticated = true,
            map = RoleRequestData::from,
        )

    /** Active members of [roleSlug] the caller may see — candidates for a transfer destination. */
    suspend fun members(roleSlug: String, query: String, cursor: String): ApiResponse<MembershipPage> {
        val q = HashMap(page(cursor))
        q["role"] = roleSlug
        q["status"] = MembershipStatus.ACTIVE.toString()
        if (query.isNotBlank()) q["q"] = query.trim()
        return api.get("/v1/memberships", q, authenticated = true, map = MembershipPage::from)
    }

    suspend fun myCodes(): ApiResponse<List<ReferralCodeData>> =
        api.get("/v1/me/referral-codes", authenticated = true) { it.objects("items").map(ReferralCodeData::from) }

    suspend fun myRequests(cursor: String): ApiResponse<RoleRequestPage> =
        api.get("/v1/me/requests", page(cursor), authenticated = true, map = RoleRequestPage::from)

    suspend fun lookupCode(code: String): ApiResponse<CodeInfoData> =
        api.get("/v1/referral/codes/${code.trim()}", authenticated = true, map = CodeInfoData::from)

    /** "I have a code": the code fixes both the role and the upline. [form] = the role's application answers. */
    suspend fun redeem(code: String, note: String, form: Map<String, String> = emptyMap()): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/referral/redeem",
            jsonOf("code" to code.trim(), "note" to note, "form" to answersJson(form)),
            authenticated = true,
            map = RoleRequestData::from,
        )

    /** Apply for one specific role a code opens (codes that onboard several roles). */
    suspend fun apply(code: String, roleSlug: String, note: String, form: Map<String, String> = emptyMap()): ApiResponse<RoleRequestData> =
        api.post(
            "/v1/requests/apply",
            jsonOf("referral_code" to code.trim(), "role" to roleSlug, "note" to note, "form" to answersJson(form)),
            authenticated = true,
            map = RoleRequestData::from,
        )

    private fun answersJson(form: Map<String, String>): JSONObject {
        val answers = JSONObject()
        for ((k, v) in form) if (v.isNotBlank()) answers.put(k, v.trim())
        return answers
    }

    suspend fun membership(id: Long): ApiResponse<MembershipData> =
        api.get("/v1/memberships/$id", authenticated = true, map = MembershipData::from)

    /**
     * Gift totals + recharge and withdrawal history for a downline seat the
     * caller may view. [before] pages withdrawals, [rechargeBefore] pages recharges.
     */
    suspend fun finance(id: Long, before: String = "", rechargeBefore: String = ""): ApiResponse<MemberFinanceData> {
        val q = buildMap {
            if (before.isNotEmpty()) put("before", before)
            if (rechargeBefore.isNotEmpty()) put("rc_before", rechargeBefore)
        }
        return api.get("/v1/memberships/$id/finance", query = q, authenticated = true, map = MemberFinanceData::from)
    }

    suspend fun upline(id: Long): ApiResponse<List<MembershipData>> =
        api.get("/v1/memberships/$id/upline", authenticated = true) { it.objects("items").map(MembershipData::from) }

    suspend fun summary(id: Long): ApiResponse<List<DownlineSummaryRow>> =
        api.get("/v1/memberships/$id/summary", authenticated = true) { it.objects("items").map(DownlineSummaryRow::from) }

    /** [depth] 1 = direct only; 0 = everyone below (server caps the walk). */
    suspend fun downline(id: Long, depth: Int, cursor: String, query: String = ""): ApiResponse<MembershipPage> {
        val q = HashMap(page(cursor))
        if (depth > 0) q["depth"] = depth.toString()
        if (query.isNotBlank()) q["q"] = query.trim()
        return api.get("/v1/memberships/$id/downline", q, authenticated = true, map = MembershipPage::from)
    }

    suspend fun request(id: Long): ApiResponse<RoleRequestData> =
        api.get("/v1/requests/$id", authenticated = true, map = RoleRequestData::from)

    /** Requests waiting on the caller (or visible to them), newest first. */
    suspend fun inbox(status: Int, cursor: String): ApiResponse<RoleRequestPage> {
        val q = HashMap(page(cursor))
        if (status > 0) q["status"] = status.toString()
        return api.get("/v1/requests/inbox", q, authenticated = true, map = RoleRequestPage::from)
    }

    suspend fun inboxCount(): ApiResponse<Int> =
        api.get("/v1/requests/inbox/count", authenticated = true) { it.int("pending") }

    suspend fun approve(id: Long, note: String): ApiResponse<RoleRequestData> =
        api.post("/v1/requests/$id/approve", jsonOf("note" to note), authenticated = true, map = RoleRequestData::from)

    suspend fun reject(id: Long, reason: String): ApiResponse<RoleRequestData> =
        api.post("/v1/requests/$id/reject", jsonOf("reason" to reason), authenticated = true, map = RoleRequestData::from)

    suspend fun cancel(id: Long, reason: String): ApiResponse<RoleRequestData> =
        api.post("/v1/requests/$id/cancel", jsonOf("reason" to reason), authenticated = true, map = RoleRequestData::from)

    private fun page(cursor: String): Map<String, String> =
        if (cursor.isEmpty()) mapOf("limit" to "30") else mapOf("limit" to "30", "cursor" to cursor)
}
