package app.xani.feature.roles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.isolate.runFeature
import app.xani.core.net.ApiResponse
import app.xani.core.net.FormFieldData
import app.xani.core.net.MemberFinanceData
import app.xani.core.net.MembershipData
import app.xani.core.net.MembershipStatus
import app.xani.core.net.PersonData
import app.xani.core.net.RecruitOptionData
import app.xani.core.net.RoleRequestData
import app.xani.core.net.RoleRequestStatus
import app.xani.core.net.WithdrawBriefData
import app.xani.core.net.WorkspaceMembershipData
import app.xani.core.net.Xani
import app.xani.feature.profile.ProfileIco
import app.xani.feature.profile.ProfileTokens
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PersonData.displayName: String get() = fullName.ifBlank { "@$username" }

// ─── add a member under my seat ──────────────────────────────────────────────

private enum class AddStep { Role, Person, Form }

/**
 * Role → person → that role's form → submit. Every option shown comes from
 * `/v1/me/workspace`; the server re-checks placement and permissions on submit,
 * so a stale screen gets a clear refusal rather than a silent success.
 */
@Composable
internal fun AddMemberFlow(
    copy: Copy,
    owner: WorkspaceMembershipData,
    onClose: () -> Unit,
    onDone: (String) -> Unit,
) {
    val options = remember(owner) { owner.recruit.filter { it.canAssign } }
    var step by rememberSaveable { mutableStateOf(if (options.size == 1) AddStep.Person else AddStep.Role) }
    var option by remember { mutableStateOf(options.singleOrNull()) }
    var person by remember { mutableStateOf<PersonData?>(null) }

    val back: () -> Unit = {
        when (step) {
            AddStep.Role -> onClose()
            AddStep.Person -> if (options.size == 1) onClose() else step = AddStep.Role
            AddStep.Form -> step = AddStep.Person
        }
    }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().background(ProfileTokens.Bg2).statusBarsPadding().navigationBarsPadding()) {
        FlowHeader(
            title = copy.t(Copy.RolesAddMember),
            sub = when (step) {
                AddStep.Role -> owner.membership.role.title
                AddStep.Person -> option?.role?.title ?: ""
                AddStep.Form -> listOfNotNull(option?.role?.title, person?.displayName).joinToString(" · ")
            },
            onBack = back,
        )
        when (step) {
            AddStep.Role -> RolePick(copy, owner, options) {
                option = it
                step = AddStep.Person
            }
            AddStep.Person -> PersonPick(copy) {
                person = it
                step = AddStep.Form
            }
            AddStep.Form -> {
                val o = option
                val p = person
                if (o != null && p != null) AssignForm(copy, owner, o, p, onDone)
            }
        }
    }
}

@Composable
private fun FlowHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(20), vertical = rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
            ProfileIco.Back(ProfileTokens.Text)
        }
        Column(Modifier.weight(1f)) {
            XaniText(title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
            if (sub.isNotBlank()) XaniText(sub, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1)
        }
    }
}

@Composable
private fun RolePick(copy: Copy, owner: WorkspaceMembershipData, options: List<RecruitOptionData>, onPick: (RecruitOptionData) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "t") { SectionTitle(copy.t(Copy.RolesAddMemberAs)) }
        items(options, key = { it.role.slug }) { o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .pressable(onClick = { onPick(o) })
                    .padding(horizontal = rdp(18), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    XaniText(o.role.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                    val bits = ArrayList<String>(2)
                    if (o.formFields > 0) bits += copy.t(Copy.RolesQuestions, "n" to o.formFields.toString())
                    bits += if (o.approveNow) copy.t(Copy.RolesAssignNow) else copy.t(Copy.RolesSendForApproval)
                    XaniText(bits.joinToString(" · "), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
                ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
        }
        val viaCode = owner.recruit.filter { !it.canAssign && it.viaCode }
        if (viaCode.isNotEmpty()) {
            item(key = "via") {
                XaniText(
                    copy.t(Copy.RolesRecruitViaCode) + ": " + viaCode.joinToString { it.role.title },
                    Type.Caption,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 12f,
                    modifier = Modifier.padding(rdp(18)),
                )
            }
        }
    }
}

@Composable
private fun PersonPick(copy: Copy, onPick: (PersonData) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PersonData>?>(emptyList()) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = null
        val r = runFeature(null as ApiResponse<List<PersonData>>?) { Xani.people.search(q, 20) }
        results = r?.value ?: emptyList()
        error = if (r != null && !r.ok) r.message else ""
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18))) {
            XaniText(copy.t(Copy.RolesPickPerson), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.padding(bottom = rdp(10)))
            LightField(
                value = query,
                onValueChange = { query = it },
                placeholder = copy.t(Copy.RolesSearchPeople),
                imeAction = ImeAction.Search,
                maxChars = 60,
            )
            if (error.isNotEmpty()) XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(8)))
        }
        val list = results
        when {
            list == null -> Loading()
            list.isEmpty() && query.trim().length >= 2 -> XaniText(
                copy.t(Copy.RolesNoPeople),
                Type.Caption,
                color = ProfileTokens.Sub,
                fontSizeOverride = 13f,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(rdp(24)),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .pressable(onClick = { onPick(p) })
                            .padding(horizontal = rdp(18), vertical = rdp(10)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(p.id, p.displayName, p.avatarUrl, rdp(40))
                        Spacer(Modifier.width(rdp(12)))
                        Column(Modifier.weight(1f)) {
                            XaniText(p.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                            XaniText("@${p.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1)
                        }
                        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
                    }
                    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
                }
            }
        }
    }
}

@Composable
private fun AssignForm(
    copy: Copy,
    owner: WorkspaceMembershipData,
    option: RecruitOptionData,
    person: PersonData,
    onDone: (String) -> Unit,
) {
    var fields by remember(option.role.slug) { mutableStateOf<List<FormFieldData>?>(null) }
    val answers = remember(option.role.slug) { mutableStateMapOf<String, String>() }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var note by rememberSaveable { mutableStateOf("") }
    var approveNow by rememberSaveable { mutableStateOf(option.approveNow) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(option.role.slug) {
        fields = if (option.formFields == 0) {
            emptyList()
        } else {
            runFeature(null as List<FormFieldData>?) { Xani.roles.applicationForm(option.role.slug).value?.fields }
        }
        if (fields == null) error = copy.t(Copy.RolesLoadFailed)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18)), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(person.id, person.displayName, person.avatarUrl, rdp(44))
            Spacer(Modifier.width(rdp(12)))
            Column(Modifier.weight(1f)) {
                XaniText(person.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                XaniText("@${person.username} → ${option.role.title}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
            }
        }
        Column(Modifier.fillMaxWidth().padding(rdp(18))) {
            val f = fields
            when {
                f == null && error.isEmpty() -> Loading()
                f != null && f.isEmpty() -> XaniText(copy.t(Copy.RolesFormNone), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f)
                f != null -> {
                    XaniText(
                        copy.t(Copy.RolesFormTitle, "role" to option.role.title),
                        Type.LabelBold,
                        color = ProfileTokens.Text,
                        fontSizeOverride = 15f,
                        modifier = Modifier.padding(bottom = rdp(12)),
                    )
                    DynamicForm(copy, f, answers, fieldErrors) { k, v ->
                        answers[k] = v
                        if (fieldErrors.containsKey(k)) fieldErrors = fieldErrors - k
                    }
                }
            }
            Spacer(Modifier.height(rdp(14)))
            LightField(
                value = note,
                onValueChange = { note = it },
                placeholder = copy.t(Copy.RolesNotePlaceholder),
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
                maxChars = 300,
            )
            if (option.approveNow) {
                Spacer(Modifier.height(rdp(14)))
                CheckRow(
                    checked = approveNow,
                    title = copy.t(Copy.RolesApproveNow),
                    sub = copy.t(Copy.RolesApproveNowHint),
                ) { approveNow = !approveNow }
            }
            if (error.isNotEmpty()) {
                XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(10)))
            }
            Spacer(Modifier.height(rdp(16)))
            PrimaryCta(
                text = if (approveNow) copy.t(Copy.RolesAssignNow) else copy.t(Copy.RolesSendForApproval),
                enabled = !busy && fields != null,
                loading = busy,
                onClick = {
                    val local = validateAnswers(copy, fields.orEmpty(), answers)
                    if (local.isNotEmpty()) {
                        fieldErrors = local
                        error = copy.t(Copy.RolesFormFix)
                        return@PrimaryCta
                    }
                    busy = true
                    error = ""
                    scope.launch {
                        val r = runFeature(null as ApiResponse<RoleRequestData>?) {
                            Xani.roles.assign(
                                userId = person.id,
                                roleSlug = option.role.slug,
                                parentMembershipId = owner.membership.id,
                                form = answers.toMap(),
                                approveNow = approveNow,
                                note = note,
                            )
                        }
                        busy = false
                        if (r == null || !r.ok || r.data == null) {
                            error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                            if (r != null && r.fields.isNotEmpty()) fieldErrors = r.fields
                            return@launch
                        }
                        onDone(
                            if (r.data.status == RoleRequestStatus.APPROVED) {
                                copy.t(Copy.RolesAssigned, "name" to person.displayName, "role" to option.role.title)
                            } else {
                                copy.t(Copy.RolesAssignSent)
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(rdp(24)))
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, sub: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(8)))
            .background(Color.White)
            .pressable(onClick = onToggle)
            .padding(horizontal = rdp(14), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(rdp(22))
                .clip(CircleShape)
                .background(if (checked) ProfileTokens.Accent else ProfileTokens.Border),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) XaniText("✓", Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
            if (sub.isNotBlank()) XaniText(sub, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
        }
    }
}

// ─── manage a direct report ──────────────────────────────────────────────────

private enum class MemberAct { Suspend, Reactivate, Block, Unblock, Remove, Transfer }

/**
 * One downline member, opened from the tree. Always shows the read-only money
 * picture (gift totals, withdrawal requests) the server lets this viewer see.
 * Suspend / block / remove / move are offered only for [manageable] direct
 * reports; the server re-checks reach on each call.
 */
@Composable
internal fun MemberActionsSheet(
    copy: Copy,
    owner: WorkspaceMembershipData,
    target: MembershipData,
    manageable: Boolean,
    onClose: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var act by remember(target.id) { mutableStateOf<MemberAct?>(null) }
    val back: () -> Unit = { if (act == null) onClose() else act = null }
    BackHandler(onBack = back)

    val acts = remember(owner, target, manageable) {
        if (!manageable) {
            emptyList()
        } else buildList {
            if (owner.actions.suspend) {
                if (target.status == MembershipStatus.ACTIVE) add(MemberAct.Suspend)
                if (target.status == MembershipStatus.SUSPENDED) add(MemberAct.Reactivate)
            }
            if (owner.actions.block) {
                if (target.status == MembershipStatus.BLOCKED) add(MemberAct.Unblock) else add(MemberAct.Block)
            }
            if (owner.actions.transfer && target.status == MembershipStatus.ACTIVE) add(MemberAct.Transfer)
            if (owner.actions.revoke) add(MemberAct.Remove)
        }
    }

    // Read-only finance: gift totals once, withdrawals paged newest-first.
    var fin by remember(target.id) { mutableStateOf<MemberFinanceData?>(null) }
    var finFailed by remember(target.id) { mutableStateOf(false) }
    var finKey by remember(target.id) { mutableStateOf(0) }
    val withdrawals = remember(target.id) { mutableStateListOf<WithdrawBriefData>() }
    var wdCursor by remember(target.id) { mutableStateOf("") }
    var wdLoading by remember(target.id) { mutableStateOf(false) }
    val finScope = rememberCoroutineScope()

    LaunchedEffect(target.id, finKey) {
        finFailed = false
        val r = runFeature(null as MemberFinanceData?) { Xani.roles.finance(target.id).value }
        if (r == null) {
            finFailed = fin == null
            return@LaunchedEffect
        }
        fin = r
        withdrawals.clear()
        withdrawals.addAll(r.withdrawals)
        wdCursor = r.next
    }

    fun moreWithdrawals() {
        if (wdLoading || wdCursor.isEmpty()) return
        wdLoading = true
        finScope.launch {
            val r = runFeature(null as MemberFinanceData?) { Xani.roles.finance(target.id, wdCursor).value }
            if (r != null) {
                val seen = withdrawals.mapTo(HashSet()) { it.id }
                withdrawals.addAll(r.withdrawals.filter { seen.add(it.id) })
                wdCursor = r.next
            }
            wdLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(ProfileTokens.Bg2).statusBarsPadding().navigationBarsPadding()) {
        FlowHeader(
            title = copy.t(Copy.RolesManage, "name" to target.displayName),
            sub = "${target.role.title} · ${memberStatusLabel(copy, target.status)}",
            onBack = back,
        )
        when (val a = act) {
            null -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "head") {
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18)), verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(target.userId, target.displayName, target.avatarUrl, rdp(48))
                        Spacer(Modifier.width(rdp(12)))
                        Column(Modifier.weight(1f)) {
                            XaniText(target.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
                            XaniText("@${target.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                            XaniText(
                                copy.t(Copy.RolesDirectCount, "n" to target.directChildren.toString()) +
                                    if (target.referralCode.isNotBlank()) " · ${target.referralCode}" else "",
                                Type.Caption,
                                color = ProfileTokens.Sub,
                                fontSizeOverride = 12f,
                            )
                        }
                        StatusPill(memberStatusLabel(copy, target.status), memberStatusColor(target.status))
                    }
                }
                // Gifts: what this member received and sent, summed server-side.
                item(key = "fin-title") { SectionTitle(copy.t(Copy.RolesFinance)) }
                when {
                    finFailed -> item(key = "fin-failed") { Failed(copy) { finKey++ } }
                    fin == null -> item(key = "fin-loading") { Loading() }
                    else -> item(key = "fin") {
                        val g = fin!!.gifts
                        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(18), vertical = rdp(10))) {
                            KeyValue(
                                copy.t(Copy.RolesGiftsReceived),
                                copy.t(
                                    Copy.RolesReceivedLine,
                                    "coins" to g.receivedCoins.toString(),
                                    "diamonds" to g.receivedDiamonds.toString(),
                                    "n" to g.receivedCount.toString(),
                                ),
                            )
                            KeyValue(
                                copy.t(Copy.RolesGiftsSent),
                                copy.t(Copy.RolesSentLine, "coins" to g.sentCoins.toString(), "n" to g.sentCount.toString()),
                            )
                        }
                    }
                }
                // Withdrawal requests, newest first, read-only.
                if (fin != null) {
                    item(key = "wd-title") { SectionTitle(copy.t(Copy.RolesWithdrawals)) }
                    if (withdrawals.isEmpty()) {
                        item(key = "wd-empty") {
                            XaniText(
                                copy.t(Copy.RolesNoWithdrawals),
                                Type.Caption,
                                color = ProfileTokens.Sub,
                                fontSizeOverride = 13f,
                                modifier = Modifier.padding(horizontal = rdp(18), vertical = rdp(8)),
                            )
                        }
                    }
                    itemsIndexed(withdrawals, key = { _, w -> "wd-${w.id}" }) { i, w ->
                        WithdrawRow(copy, w)
                        if (i == withdrawals.lastIndex && wdCursor.isNotEmpty() && !wdLoading) {
                            LaunchedEffect("wd-${w.id}") { moreWithdrawals() }
                        }
                    }
                    if (wdLoading) item(key = "wd-more") { Loading() }
                }
                if (acts.isNotEmpty()) item(key = "t") { SectionTitle(copy.t(Copy.RolesActions)) }
                items(acts, key = { it.name }) { x ->
                    val danger = x == MemberAct.Block || x == MemberAct.Remove
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .pressable(onClick = { act = x })
                            .padding(horizontal = rdp(18), vertical = rdp(14)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(
                            actLabel(copy, x),
                            Type.LabelBold,
                            color = if (danger) ProfileTokens.Danger else ProfileTokens.Text,
                            fontSizeOverride = 15f,
                            modifier = Modifier.weight(1f),
                        )
                        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
                    }
                    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
                }
            }
            MemberAct.Transfer -> TransferForm(copy, owner, target, onChanged)
            else -> ReasonForm(copy, a, target, onChanged)
        }
    }
}

private fun actLabel(copy: Copy, a: MemberAct): String = copy.t(
    when (a) {
        MemberAct.Suspend -> Copy.RolesActSuspend
        MemberAct.Reactivate -> Copy.RolesActReactivate
        MemberAct.Block -> Copy.RolesActBlock
        MemberAct.Unblock -> Copy.RolesActUnblock
        MemberAct.Remove -> Copy.RolesActRemove
        MemberAct.Transfer -> Copy.RolesActTransfer
    },
)

@Composable
private fun WithdrawRow(copy: Copy, w: WithdrawBriefData) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(18), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(
                copy.t(Copy.RolesWdAmount, "n" to w.diamonds.toString()),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 14f,
            )
            val sub = listOf(w.method, w.account, w.createdAt.take(10)).filter { it.isNotBlank() }.joinToString(" · ")
            XaniText(sub, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
            if (w.note.isNotBlank()) {
                XaniText(w.note, Type.Caption, color = ProfileTokens.Muted, fontSizeOverride = 11f, maxLines = 2)
            }
        }
        StatusPill(withdrawStatusLabel(copy, w.status), withdrawStatusColor(w.status))
    }
    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
}

private fun withdrawStatusLabel(copy: Copy, s: Int): String = copy.t(
    when (s) {
        1 -> Copy.RolesWdApproved
        2 -> Copy.RolesWdRejected
        else -> Copy.RolesWdPending
    },
)

private fun withdrawStatusColor(s: Int) = when (s) {
    1 -> ProfileTokens.Ok
    2 -> ProfileTokens.Danger
    else -> ProfileTokens.Sub
}

@Composable
private fun ReasonForm(copy: Copy, a: MemberAct, target: MembershipData, onChanged: (String) -> Unit) {
    var reason by rememberSaveable(a) { mutableStateOf("") }
    var disposition by rememberSaveable(a) { mutableStateOf(1) }
    var error by remember(a) { mutableStateOf("") }
    var busy by remember(a) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val danger = a == MemberAct.Block || a == MemberAct.Remove

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(rdp(18))) {
        XaniText(actLabel(copy, a), Type.LabelBold, color = if (danger) ProfileTokens.Danger else ProfileTokens.Text, fontSizeOverride = 16f)
        Spacer(Modifier.height(rdp(12)))
        LightField(
            value = reason,
            onValueChange = { reason = it },
            placeholder = copy.t(Copy.RolesReasonPlaceholder),
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
            maxChars = 300,
        )
        if (a == MemberAct.Remove && target.directChildren > 0) {
            Spacer(Modifier.height(rdp(14)))
            for ((id, key) in listOf(1 to Copy.RolesRemoveKeep, 2 to Copy.RolesRemoveMoveUp, 3 to Copy.RolesRemoveSuspend)) {
                CheckRow(checked = disposition == id, title = copy.t(key), sub = "") { disposition = id }
                Spacer(Modifier.height(rdp(6)))
            }
        }
        if (error.isNotEmpty()) XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(10)))
        Spacer(Modifier.height(rdp(16)))
        if (danger) {
            OutlineCta(actLabel(copy, a), ProfileTokens.Danger, busy, !busy) {
                submitReason(copy, a, target, reason, disposition, scope, { busy = it }, { error = it }, onChanged)
            }
        } else {
            PrimaryCta(
                text = actLabel(copy, a),
                enabled = !busy,
                loading = busy,
                onClick = { submitReason(copy, a, target, reason, disposition, scope, { busy = it }, { error = it }, onChanged) },
            )
        }
    }
}

private fun submitReason(
    copy: Copy,
    a: MemberAct,
    target: MembershipData,
    reason: String,
    disposition: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    setError: (String) -> Unit,
    onChanged: (String) -> Unit,
) {
    val needsReason = a != MemberAct.Reactivate && a != MemberAct.Unblock
    if (needsReason && reason.isBlank()) {
        setError(copy.t(Copy.RolesReasonRequired))
        return
    }
    setBusy(true)
    setError("")
    scope.launch {
        val r: ApiResponse<*>? = runFeature(null as ApiResponse<*>?) {
            when (a) {
                MemberAct.Suspend -> Xani.roles.transition(target.id, "suspend", reason)
                MemberAct.Reactivate -> Xani.roles.transition(target.id, "reactivate", reason)
                MemberAct.Block -> Xani.roles.transition(target.id, "block", reason)
                MemberAct.Unblock -> Xani.roles.transition(target.id, "unblock", reason)
                MemberAct.Remove -> Xani.roles.revoke(target.id, reason, disposition)
                MemberAct.Transfer -> null
            }
        }
        setBusy(false)
        if (r == null || !r.ok) {
            setError(r?.message ?: copy.t(Copy.RolesLoadFailed))
            return@launch
        }
        onChanged(r.message.ifBlank { copy.t(Copy.RolesActDone) })
    }
}

@Composable
private fun TransferForm(copy: Copy, owner: WorkspaceMembershipData, target: MembershipData, onChanged: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<MembershipData>?>(emptyList()) }
    var chosen by remember { mutableStateOf<MembershipData?>(null) }
    var childrenOnly by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        delay(300)
        candidates = null
        val page = runFeature(null) { Xani.roles.members(owner.membership.role.slug, query, "").value }
        candidates = page?.items.orEmpty().filter { it.id != target.id && it.id != target.parentId && it.userId != target.userId }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18))) {
            XaniText(copy.t(Copy.RolesTransferTo), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
            XaniText(copy.t(Copy.RolesTransferHint), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(4), bottom = rdp(10)))
            LightField(value = query, onValueChange = { query = it }, placeholder = copy.t(Copy.RolesSearchPeople), imeAction = ImeAction.Search, maxChars = 60)
        }
        val list = candidates
        LazyColumn(Modifier.weight(1f)) {
            when {
                list == null -> item(key = "l") { Loading() }
                list.isEmpty() -> item(key = "e") {
                    XaniText(copy.t(Copy.RolesNoPeople), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(rdp(24)))
                }
            }
            items(list.orEmpty(), key = { it.id }) { c ->
                val on = chosen?.id == c.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (on) ProfileTokens.AccentSoft else Color.White)
                        .pressable(onClick = { chosen = c })
                        .padding(horizontal = rdp(18), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(c.userId, c.displayName, c.avatarUrl, rdp(40))
                    Spacer(Modifier.width(rdp(12)))
                    Column(Modifier.weight(1f)) {
                        XaniText(c.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                        XaniText("${c.role.title} · @${c.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1)
                    }
                    if (on) XaniText("✓", Type.LabelBold, color = ProfileTokens.Accent)
                }
                Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18))) {
            if (target.directChildren > 0) {
                CheckRow(checked = childrenOnly, title = copy.t(Copy.RolesTransferChildrenOnly), sub = "") { childrenOnly = !childrenOnly }
                Spacer(Modifier.height(rdp(10)))
            }
            LightField(value = note, onValueChange = { note = it }, placeholder = copy.t(Copy.RolesNotePlaceholder), capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done, maxChars = 300)
            if (error.isNotEmpty()) XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(8)))
            Spacer(Modifier.height(rdp(12)))
            PrimaryCta(
                text = copy.t(Copy.RolesActTransfer),
                enabled = !busy && chosen != null,
                loading = busy,
                onClick = {
                    val dest = chosen ?: return@PrimaryCta
                    busy = true
                    error = ""
                    scope.launch {
                        val r = runFeature(null as ApiResponse<RoleRequestData>?) { Xani.roles.transfer(target.id, dest.id, childrenOnly, note) }
                        busy = false
                        if (r == null || !r.ok) {
                            error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                            return@launch
                        }
                        onChanged(r.message.ifBlank { copy.t(Copy.RolesActDone) })
                    }
                },
            )
        }
    }
}
