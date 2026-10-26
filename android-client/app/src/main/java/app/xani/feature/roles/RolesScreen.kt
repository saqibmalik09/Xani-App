package app.xani.feature.roles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xani.core.isolate.runFeature
import app.xani.core.net.ApiResponse
import app.xani.core.net.ApplicationFormData
import app.xani.core.net.AuthState
import app.xani.core.net.CodeInfoData
import app.xani.core.net.FormFieldData
import app.xani.core.net.JoinLinkData
import app.xani.core.net.MediaConfig
import app.xani.core.net.MembershipData
import app.xani.core.net.MembershipStatus
import app.xani.core.net.ReferralCodeData
import app.xani.core.net.RoleBrief
import app.xani.core.net.RoleRequestData
import app.xani.core.net.RoleRequestKind
import app.xani.core.net.RoleRequestPage
import app.xani.core.net.RoleRequestStatus
import app.xani.core.net.WorkspaceData
import app.xani.core.net.WorkspaceMembershipData
import app.xani.core.net.Xani
import app.xani.feature.profile.ProfileIco
import app.xani.feature.profile.ProfileTokens
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RolesTab { Mine, Requests, Approvals }

/**
 * The account's place in the organisation: roles held (with upline and
 * downline), referral codes to share, join-with-code, requests in flight and
 * — for people who can approve — the approvals inbox. Every action here is a
 * request the server may hold for approval; the response says which.
 */
@Composable
fun RolesScreen(copy: Copy, onBack: () -> Unit, startRequestId: Long = 0L) {
    BackHandler(onBack = onBack)
    val authState by Xani.session.state.collectAsState()
    val access = (authState as? AuthState.SignedIn)?.access
    val canApprove = access?.holdsAnywhere("requests.approve") == true || access?.holdsAnywhere("requests.view") == true

    var tab by rememberSaveable { mutableStateOf(RolesTab.Mine) }
    var openMembership by remember { mutableStateOf<WorkspaceMembershipData?>(null) }
    var openRequestId by rememberSaveable { mutableStateOf(startRequestId) }
    var toast by remember { mutableStateOf("") }
    var pendingCount by remember { mutableStateOf(0) }
    // Bumped when a seat ended (leave role): the Mine list under the detail
    // overlay stays composed, so it re-reads the workspace on this key.
    var wsReload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        delay(2200)
        toast = ""
    }
    LaunchedEffect(canApprove) {
        if (canApprove) pendingCount = runFeature(0) { Xani.roles.inboxCount().value ?: 0 }
    }
    // A push or notice tap while this screen is already open.
    val routed by RolesRouter.pending.collectAsState()
    LaunchedEffect(routed) {
        val id = RolesRouter.consume() ?: return@LaunchedEffect
        if (id > 0L) openRequestId = id
    }
    // A referral link tapped: land on "mine" with the join form prefilled.
    var joinPrefill by rememberSaveable { mutableStateOf("") }
    var joinPrefillRole by rememberSaveable { mutableStateOf("") }
    val routedJoin by RolesRouter.pendingJoin.collectAsState()
    LaunchedEffect(routedJoin) {
        val j = RolesRouter.consumeJoin() ?: return@LaunchedEffect
        joinPrefill = j.code
        joinPrefillRole = j.roleSlug
        openMembership = null
        openRequestId = 0L
        tab = RolesTab.Mine
    }

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg2)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(20), vertical = rdp(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                    ProfileIco.Back(ProfileTokens.Text)
                }
                XaniText(copy.t(Copy.RolesTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
            }
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(16), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                TabChip(Modifier.weight(1f), copy.t(Copy.RolesTabMine), tab == RolesTab.Mine) { tab = RolesTab.Mine }
                TabChip(Modifier.weight(1f), copy.t(Copy.RolesTabRequests), tab == RolesTab.Requests) { tab = RolesTab.Requests }
                if (canApprove) {
                    TabChip(
                        Modifier.weight(1f),
                        copy.t(Copy.RolesTabApprovals) + if (pendingCount > 0) " · $pendingCount" else "",
                        tab == RolesTab.Approvals,
                    ) { tab = RolesTab.Approvals }
                }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
            when (tab) {
                RolesTab.Mine -> MineTab(copy, joinPrefill, joinPrefillRole, wsReload, onOpen = { openMembership = it }, onToast = { toast = it })
                RolesTab.Requests -> RequestList(
                    copy = copy,
                    empty = copy.t(Copy.RolesRequestsEmpty),
                    load = { Xani.roles.myRequests(it) },
                    onOpen = { openRequestId = it.id },
                )
                RolesTab.Approvals -> RequestList(
                    copy = copy,
                    empty = copy.t(Copy.RolesApprovalsEmpty),
                    load = { Xani.roles.inbox(RoleRequestStatus.PENDING, it) },
                    onOpen = { openRequestId = it.id },
                )
            }
        }

        openMembership?.let { m ->
            MembershipDetail(
                copy, m,
                onBack = { openMembership = null },
                onToast = { toast = it },
                onLeft = { msg ->
                    toast = msg
                    openMembership = null
                    wsReload++
                },
            )
        }
        if (openRequestId > 0L) {
            RequestDetail(
                copy = copy,
                requestId = openRequestId,
                onBack = { openRequestId = 0L },
                onDecided = { msg ->
                    toast = msg
                    scope.launch { if (canApprove) pendingCount = runFeature(pendingCount) { Xani.roles.inboxCount().value ?: pendingCount } }
                },
            )
        }

        if (toast.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = rdp(24))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0xE6111111))
                    .padding(horizontal = rdp(16), vertical = rdp(8)),
            ) {
                XaniText(toast, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
            }
        }
    }
}

// ─── my roles ────────────────────────────────────────────────────────────────

@Composable
private fun MineTab(copy: Copy, joinPrefill: String, joinPrefillRole: String, extReload: Int, onOpen: (WorkspaceMembershipData) -> Unit, onToast: (String) -> Unit) {
    var memberships by remember { mutableStateOf<List<WorkspaceMembershipData>?>(null) }
    var codes by remember { mutableStateOf<List<ReferralCodeData>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey, extReload) {
        // One call: seats held, what each may recruit, and which actions the
        // server will honour. The UI below is drawn from it, never guessed.
        val ws = runFeature(null as WorkspaceData?) { Xani.roles.workspace().value }
        if (ws == null) {
            failed = memberships == null
            return@LaunchedEffect
        }
        failed = false
        memberships = ws.memberships
        codes = runFeature(emptyList()) { Xani.roles.myCodes().value ?: emptyList() }
    }

    // imePadding: the join form sits at the bottom of this list, so without it
    // the keyboard covers the fields and the Apply button with nothing to
    // scroll to. Shrinking the list instead lets Compose bring the focused
    // field into view.
    LazyColumn(Modifier.fillMaxSize().imePadding()) {
        when {
            failed -> item(key = "failed") { Failed(copy) { reloadKey++ } }
            memberships == null -> item(key = "loading") { Loading() }
            memberships!!.isEmpty() -> item(key = "empty") {
                XaniText(
                    copy.t(Copy.RolesMineEmpty),
                    Type.Caption,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 14f,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(rdp(24)),
                )
            }
        }
        itemsIndexed(memberships.orEmpty(), key = { _, m -> m.membership.id }) { _, m ->
            MembershipRow(copy, m, onClick = { onOpen(m) })
        }
        if (codes.isNotEmpty()) {
            item(key = "codes-title") { SectionTitle(copy.t(Copy.RolesMyCodes)) }
            itemsIndexed(codes, key = { _, c -> c.code }) { _, c -> CodeRow(copy, c, onToast) }
        }
        item(key = "join") {
            JoinWithCode(copy, joinPrefill, joinPrefillRole) { msg ->
                onToast(msg)
                scope.launch { reloadKey++ }
            }
        }
    }
}

@Composable
private fun MembershipRow(copy: Copy, wm: WorkspaceMembershipData, onClick: () -> Unit) {
    val m = wm.membership
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(18), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                XaniText(m.role.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                StatusPill(memberStatusLabel(copy, m.status), memberStatusColor(m.status))
            }
            val code = if (m.referralCode.isNotBlank()) "${copy.t(Copy.RolesYourCode)} ${m.referralCode} · " else ""
            XaniText(
                code + copy.t(Copy.RolesDirectCount, "n" to m.directChildren.toString()),
                Type.Caption,
                color = ProfileTokens.Sub,
                fontSizeOverride = 12f,
            )
            val recruitable = wm.recruit.filter { it.canAssign }
            if (recruitable.isNotEmpty()) {
                XaniText(
                    copy.t(Copy.RolesCanRecruit, "roles" to recruitable.joinToString { it.role.title }),
                    Type.Caption,
                    color = ProfileTokens.Accent,
                    fontSizeOverride = 12f,
                    maxLines = 1,
                    modifier = Modifier.padding(top = rdp(2)),
                )
            }
        }
        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
    }
    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun CodeRow(copy: Copy, c: ReferralCodeData, onToast: (String) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val targets = c.joinLinks.ifEmpty { c.targetRoles.map { JoinLinkData(it, MediaConfig.joinLink(c.code, it.slug)) } }
    fun copyCodeOnly() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("referral", c.code))
        onToast(copy.t(Copy.RolesCopied))
    }
    fun shareFor(link: JoinLinkData) {
        val role = link.role
        val url = link.url.ifBlank { MediaConfig.joinLink(c.code, role.slug) }
        if (url.isBlank()) {
            onToast(copy.t(Copy.RolesShareNoWeb))
            return
        }
        val text = copy.t(Copy.RolesShareTextRole, "role" to role.title, "code" to c.code, "link" to url)
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("referral", url))
        onToast(copy.t(Copy.RolesLinkCopied))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runFeature(Unit) { context.startActivity(Intent.createChooser(send, null)) }
    }
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(18), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                XaniText(c.code, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 18f)
                XaniText(
                    "${c.role.title} · " + copy.t(
                        Copy.RolesCodeOpens,
                        "roles" to targets.joinToString { it.role.title }.ifEmpty { "—" },
                    ),
                    Type.Caption,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 12f,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                SmallButton(copy.t(Copy.RolesCopyCode), ::copyCodeOnly)
                SmallButton(copy.t(Copy.RolesShareLink)) {
                    when {
                        targets.isEmpty() -> onToast(copy.t(Copy.RolesShareNoRoles))
                        targets.size == 1 -> shareFor(targets[0])
                        else -> picking = !picking
                    }
                }
            }
        }
        if (picking && targets.size > 1) {
            XaniText(
                copy.t(Copy.RolesSharePickRole),
                Type.Caption,
                color = ProfileTokens.Sub,
                fontSizeOverride = 12f,
                modifier = Modifier.padding(start = rdp(18), end = rdp(18), bottom = rdp(6)),
            )
            targets.forEach { link ->
                val url = link.url.ifBlank { MediaConfig.joinLink(c.code, link.role.slug) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = {
                            picking = false
                            shareFor(link)
                        })
                        .padding(horizontal = rdp(18), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        XaniText(link.role.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                        if (url.isNotEmpty()) {
                            XaniText(url, Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 11f, maxLines = 1)
                        } else {
                            XaniText(copy.t(Copy.RolesShareNoWeb), Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 11f, maxLines = 2)
                        }
                    }
                    ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
                }
                Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun JoinWithCode(copy: Copy, prefill: String, prefillRole: String, onDone: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<CodeInfoData?>(null) }
    var roleSlug by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<List<FormFieldData>?>(null) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Face verification gate: the role's form says whether it is required;
    // the server enforces it again on apply (error code "face_required").
    var faceRequired by remember { mutableStateOf(false) }
    var faceVerified by remember { mutableStateOf(false) }
    var showFaceVerify by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // A tapped referral link lands here with the code (and often the role) known.
    LaunchedEffect(prefill, prefillRole) {
        if (prefill.isNotEmpty() && prefill != code) code = prefill
        if (prefillRole.isNotEmpty()) roleSlug = prefillRole
    }
    LaunchedEffect(code) {
        info = null
        error = ""
        val keepRole = roleSlug
        val t = code.trim()
        if (t.length < 4) return@LaunchedEffect
        delay(350)
        val r = runFeature(null as ApiResponse<CodeInfoData>?) { Xani.roles.lookupCode(t) } ?: return@LaunchedEffect
        if (r.ok && r.data != null) {
            info = r.data
            val targets = r.data.targetRoles
            roleSlug = when {
                keepRole.isNotEmpty() && targets.any { it.slug.equals(keepRole, ignoreCase = true) } ->
                    targets.first { it.slug.equals(keepRole, ignoreCase = true) }.slug
                targets.size == 1 -> targets[0].slug
                keepRole.isNotEmpty() -> keepRole
                else -> ""
            }
        } else {
            error = r.message
        }
    }
    // The role's own questions; the server rejects an application missing them.
    LaunchedEffect(roleSlug) {
        form = null
        answers.clear()
        fieldErrors = emptyMap()
        faceRequired = false
        if (roleSlug.isEmpty()) return@LaunchedEffect
        val fd = runFeature(null as ApplicationFormData?) { Xani.roles.applicationForm(roleSlug).value }
        form = fd?.fields ?: emptyList()
        faceRequired = fd?.faceRequired == true
        if (faceRequired && !faceVerified) {
            faceVerified = runFeature(false) { Xani.face.status().value?.faceVerified == true }
        }
    }

    Column(Modifier.fillMaxWidth().padding(rdp(18)).padding(top = rdp(6))) {
        XaniText(copy.t(Copy.RolesJoinTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
        XaniText(copy.t(Copy.RolesJoinHint), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(4), bottom = rdp(12)))
        LightField(
            value = code,
            onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' } },
            placeholder = copy.t(Copy.RolesCodePlaceholder),
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
            maxChars = 24,
        )
        if (error.isNotEmpty()) {
            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(8)))
        }
        info?.let { i ->
            XaniText(
                copy.t(Copy.RolesCodeOwner, "name" to i.owner.displayName, "role" to i.ownerRole.title),
                Type.Caption,
                color = ProfileTokens.Ok,
                fontSizeOverride = 12f,
                modifier = Modifier.padding(top = rdp(8)),
            )
            if (i.targetRoles.size > 1 && prefillRole.isEmpty()) {
                XaniText(copy.t(Copy.RolesPickRole), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, modifier = Modifier.padding(top = rdp(10), bottom = rdp(6)))
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    for (t in i.targetRoles) {
                        TabChip(Modifier, t.title, roleSlug == t.slug) { roleSlug = t.slug }
                    }
                }
            } else if (roleSlug.isNotEmpty()) {
                val title = i.targetRoles.firstOrNull { it.slug == roleSlug }?.title ?: roleSlug
                XaniText(
                    copy.t(Copy.RolesApplyingAs, "role" to title),
                    Type.Caption,
                    color = ProfileTokens.Accent,
                    fontSizeOverride = 12f,
                    modifier = Modifier.padding(top = rdp(8)),
                )
            }
            val fields = form
            if (roleSlug.isNotEmpty()) {
                Spacer(Modifier.height(rdp(12)))
                when {
                    fields == null -> Loading()
                    fields.isEmpty() -> Unit
                    else -> DynamicForm(copy, fields, answers, fieldErrors) { k, v ->
                        answers[k] = v
                        if (fieldErrors.containsKey(k)) fieldErrors = fieldErrors - k
                    }
                }
            }
            Spacer(Modifier.height(rdp(10)))
            LightField(
                value = note,
                onValueChange = { note = it },
                placeholder = copy.t(Copy.RolesNotePlaceholder),
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
                maxChars = 300,
            )
            // The face check is its own step with its own button — opening the
            // camera off the Apply tap read as "Apply failed". Apply stays
            // disabled until the check passes.
            if (faceRequired) {
                Spacer(Modifier.height(rdp(12)))
                FaceGateCard(copy, faceVerified) { showFaceVerify = true }
            }
            Spacer(Modifier.height(rdp(12)))
            PrimaryCta(
                text = copy.t(Copy.RolesApply),
                enabled = !busy && roleSlug.isNotEmpty() && fields != null && (!faceRequired || faceVerified),
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
                            Xani.roles.apply(code, roleSlug, note, answers.toMap())
                        }
                        busy = false
                        if (r == null || !r.ok) {
                            // The server is the authority: an un-verified face
                            // bounces here even if the form said otherwise.
                            // Surface the step instead of springing the camera.
                            if (r?.code == "face_required") {
                                faceRequired = true
                                faceVerified = false
                                error = r.message
                                return@launch
                            }
                            error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                            if (r != null && r.fields.isNotEmpty()) fieldErrors = r.fields
                            return@launch
                        }
                        code = ""
                        note = ""
                        info = null
                        onDone(copy.t(Copy.RolesApplied))
                    }
                },
            )
        }
    }
    if (showFaceVerify) {
        // Full-screen capture flow on top of the join form; verifying flips
        // the local flag so Apply unlocks.
        Dialog(
            onDismissRequest = { showFaceVerify = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FaceVerifyScreen(copy) { verified ->
                showFaceVerify = false
                if (verified) faceVerified = true
            }
        }
    }
}

/**
 * The face-check step of a gated application, drawn above the Apply button:
 * a titled card with its own Verify button, flipping to a green verified
 * state once the check passes.
 */
@Composable
private fun FaceGateCard(copy: Copy, verified: Boolean, onVerify: () -> Unit) {
    val shape = RoundedCornerShape(rdp(12))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ProfileTokens.Bg2)
            .border(rdp(1), if (verified) ProfileTokens.Ok else ProfileTokens.Hairline, shape)
            .padding(horizontal = rdp(14), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                XaniText(copy.t(Copy.FaceTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
                if (verified) ProfileIco.Verified(rdp(15))
            }
            XaniText(
                if (verified) copy.t(Copy.FaceOkVerified) else copy.t(Copy.FaceGateHint),
                Type.Caption,
                color = if (verified) ProfileTokens.Ok else ProfileTokens.Sub,
                fontSizeOverride = 12f,
                modifier = Modifier.padding(top = rdp(2)),
            )
        }
        if (!verified) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(17)))
                    .background(ProfileTokens.Accent)
                    .pressable(onClick = onVerify)
                    .padding(horizontal = rdp(14), vertical = rdp(9)),
            ) {
                XaniText(copy.t(Copy.FaceVerifyCta), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }
    }
}

// ─── membership detail: upline + downline ────────────────────────────────────

@Composable
private fun MembershipDetail(copy: Copy, wm: WorkspaceMembershipData, onBack: () -> Unit, onToast: (String) -> Unit, onLeft: (String) -> Unit) {
    val m = wm.membership
    val canAdd = m.isActive && wm.recruit.any { it.canAssign }
    var adding by rememberSaveable(m.id) { mutableStateOf(false) }
    var managing by remember(m.id) { mutableStateOf<MembershipData?>(null) }
    var upline by remember(m.id) { mutableStateOf<List<MembershipData>?>(null) }
    // "" | "leave" | "shift" | "leaveRole": the member's own action, drawn in
    // place of the lists. Leave/shift upline come from role policy (workspace
    // actions); leave role is always offered on an active own seat. The
    // server re-checks everything on submit.
    var selfAct by rememberSaveable(m.id) { mutableStateOf("") }
    var uplineKey by remember(m.id) { mutableStateOf(0) }
    var whole by rememberSaveable(m.id) { mutableStateOf(false) }
    val items = remember(m.id, whole) { mutableStateListOf<MembershipData>() }
    var cursor by remember(m.id, whole) { mutableStateOf("") }
    var loaded by remember(m.id, whole) { mutableStateOf(false) }
    var loading by remember(m.id, whole) { mutableStateOf(false) }
    var failed by remember(m.id, whole) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val back: () -> Unit = { if (selfAct.isNotEmpty()) selfAct = "" else onBack() }
    BackHandler(onBack = back)

    LaunchedEffect(m.id, uplineKey) {
        upline = runFeature(emptyList()) { Xani.roles.upline(m.id).value ?: emptyList() }
    }

    suspend fun fetch(after: String) {
        loading = true
        val page = runFeature(null) { Xani.roles.downline(m.id, if (whole) 0 else 1, after).value }
        if (page == null) {
            failed = after.isEmpty()
        } else {
            val seen = items.mapTo(HashSet()) { it.id }
            if (after.isEmpty()) items.clear()
            items.addAll(page.items.filter { after.isEmpty() || seen.add(it.id) })
            cursor = page.next
            loaded = true
            failed = false
        }
        loading = false
    }
    LaunchedEffect(m.id, whole) { fetch("") }

    Column(Modifier.fillMaxSize().background(ProfileTokens.Bg2).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(40)).pressable(onClick = back), contentAlignment = Alignment.Center) {
                ProfileIco.Back(ProfileTokens.Text)
            }
            Column(Modifier.weight(1f)) {
                XaniText(m.role.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
                if (m.referralCode.isNotBlank()) {
                    XaniText("${copy.t(Copy.RolesYourCode)} ${m.referralCode}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
            }
            if (canAdd && selfAct.isEmpty()) SmallButton("+ " + copy.t(Copy.RolesAddMember)) { adding = true }
        }
        when (selfAct) {
            "leave" -> LeaveUplineForm(copy, m.id, upline?.firstOrNull()) { msg ->
                selfAct = ""
                uplineKey++
                onToast(msg)
            }
            "shift" -> ShiftUplineForm(copy, m.id) { msg ->
                selfAct = ""
                onToast(msg)
            }
            "leaveRole" -> LeaveRoleForm(copy, m.id, m.role.title) { msg ->
                selfAct = ""
                onLeft(msg)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
            item(key = "upline-title") { SectionTitle(copy.t(Copy.RolesUpline)) }
            item(key = "upline") {
                val chain = upline
                when {
                    chain == null -> Loading()
                    chain.isEmpty() -> XaniText(
                        copy.t(Copy.RolesTopOfTree),
                        Type.Caption,
                        color = ProfileTokens.Sub,
                        fontSizeOverride = 13f,
                        modifier = Modifier.padding(horizontal = rdp(18), vertical = rdp(8)),
                    )
                    else -> Column {
                        // Server returns nearest-first; show root → nearest, indented by level.
                        chain.asReversed().forEachIndexed { level, u ->
                            PersonRow(u, copy, indent = level, sub = "${u.role.title} · @${u.username}")
                        }
                    }
                }
            }
            // Role-policy self-service: leave-upline needs an upline to leave;
            // shift is offered whenever the role allows it. Leave role ends the
            // seat itself and is always available while it is active. All
            // re-checked server-side.
            val hasUpline = upline?.isNotEmpty() == true
            if (m.isActive || (wm.actions.leaveUpline && hasUpline) || wm.actions.shiftUpline) {
                item(key = "upline-actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = rdp(18), vertical = rdp(8)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                    ) {
                        if (wm.actions.shiftUpline) SmallButton(copy.t(Copy.RolesShiftUpline)) { selfAct = "shift" }
                        if (wm.actions.leaveUpline && hasUpline) SmallButton(copy.t(Copy.RolesLeaveUpline)) { selfAct = "leave" }
                        if (m.isActive) SmallButton(copy.t(Copy.RolesLeaveRole)) { selfAct = "leaveRole" }
                    }
                }
            }
            item(key = "downline-title") {
                Row(
                    Modifier.fillMaxWidth().padding(end = rdp(16)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(copy.t(Copy.RolesDownline), Modifier.weight(1f))
                    TabChip(Modifier, copy.t(Copy.RolesDirect), !whole) { whole = false }
                    Spacer(Modifier.width(rdp(6)))
                    TabChip(Modifier, copy.t(Copy.RolesWhole), whole) { whole = true }
                }
            }
            when {
                failed -> item(key = "failed") { Failed(copy) { scope.launch { fetch("") } } }
                !loaded -> item(key = "loading") { Loading() }
                items.isEmpty() -> item(key = "empty") {
                    XaniText(
                        copy.t(Copy.RolesDownlineEmpty),
                        Type.Caption,
                        color = ProfileTokens.Sub,
                        fontSizeOverride = 13f,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(rdp(24)),
                    )
                }
            }
            itemsIndexed(items, key = { _, d -> d.id }) { i, d ->
                // Every row opens the member sheet: finance details are visible
                // for the whole chain, while manage actions stay scoped to
                // direct reports (the server re-checks reach either way).
                PersonRow(
                    d,
                    copy,
                    indent = if (whole) (d.treeDepth - 1).coerceAtLeast(0) else 0,
                    sub = "${d.role.title} · ${copy.t(Copy.RolesDirectCount, "n" to d.directChildren.toString())}",
                    status = d.status,
                    onClick = { managing = d },
                )
                if (i == items.lastIndex && cursor.isNotEmpty() && !loading) {
                    LaunchedEffect(d.id) { fetch(cursor) }
                }
            }
            } // LazyColumn (the `else` branch of the selfAct switch)
        }
    }

    if (adding) {
        AddMemberFlow(
            copy = copy,
            owner = wm,
            onClose = { adding = false },
            onDone = { msg ->
                adding = false
                onToast(msg)
                scope.launch { fetch("") }
            },
        )
    }
    managing?.let { target ->
        MemberActionsSheet(
            copy = copy,
            owner = wm,
            target = target,
            manageable = wm.actions.anyMemberAction && target.parentId == m.id && target.status != MembershipStatus.REVOKED,
            onClose = { managing = null },
            onChanged = { msg ->
                managing = null
                onToast(msg)
                scope.launch { fetch("") }
            },
        )
    }
}

/**
 * Leave my upline: the role and my downline stay with me, only the link up is
 * cut — immediately, with a required reason the upline is shown. The policy
 * gate and everything else is re-checked server-side.
 */
@Composable
private fun LeaveUplineForm(copy: Copy, membershipId: Long, upline: MembershipData?, onDone: (String) -> Unit) {
    var reason by rememberSaveable(membershipId) { mutableStateOf("") }
    var error by remember(membershipId) { mutableStateOf("") }
    var busy by remember(membershipId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(rdp(18))) {
        XaniText(copy.t(Copy.RolesLeaveUpline), Type.LabelBold, color = ProfileTokens.Danger, fontSizeOverride = 16f)
        upline?.let {
            XaniText(
                "${it.displayName} · ${it.role.title}",
                Type.Caption,
                color = ProfileTokens.Sub,
                fontSizeOverride = 13f,
                modifier = Modifier.padding(top = rdp(4)),
            )
        }
        XaniText(
            copy.t(Copy.RolesLeaveUplineHint),
            Type.Caption,
            color = ProfileTokens.Sub,
            fontSizeOverride = 12f,
            modifier = Modifier.padding(top = rdp(6), bottom = rdp(12)),
        )
        LightField(
            value = reason,
            onValueChange = { reason = it },
            placeholder = copy.t(Copy.RolesReasonPlaceholder),
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
            maxChars = 300,
        )
        if (error.isNotEmpty()) {
            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(10)))
        }
        Spacer(Modifier.height(rdp(16)))
        OutlineCta(copy.t(Copy.RolesLeaveUpline), ProfileTokens.Danger, busy, !busy) {
            if (reason.isBlank()) {
                error = copy.t(Copy.RolesReasonRequired)
                return@OutlineCta
            }
            busy = true
            error = ""
            scope.launch {
                val r = runFeature(null as ApiResponse<MembershipData>?) { Xani.roles.leaveUpline(membershipId, reason) }
                busy = false
                if (r == null || !r.ok) {
                    error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                    return@launch
                }
                onDone(r.message.ifBlank { copy.t(Copy.RolesActDone) })
            }
        }
    }
}

/**
 * Leave the role entirely: ends the caller's own membership at once (the
 * server completes a self-revoke inline and informs the old upline). After
 * success the session is resynced so profile badges and role-gated UI drop
 * the role immediately instead of waiting for the next notice or cold start.
 */
@Composable
private fun LeaveRoleForm(copy: Copy, membershipId: Long, roleTitle: String, onDone: (String) -> Unit) {
    var reason by rememberSaveable(membershipId) { mutableStateOf("") }
    var error by remember(membershipId) { mutableStateOf("") }
    var busy by remember(membershipId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(rdp(18))) {
        XaniText(copy.t(Copy.RolesLeaveRole), Type.LabelBold, color = ProfileTokens.Danger, fontSizeOverride = 16f)
        XaniText(
            roleTitle,
            Type.Caption,
            color = ProfileTokens.Sub,
            fontSizeOverride = 13f,
            modifier = Modifier.padding(top = rdp(4)),
        )
        XaniText(
            copy.t(Copy.RolesLeaveRoleHint),
            Type.Caption,
            color = ProfileTokens.Sub,
            fontSizeOverride = 12f,
            modifier = Modifier.padding(top = rdp(6), bottom = rdp(12)),
        )
        LightField(
            value = reason,
            onValueChange = { reason = it },
            placeholder = copy.t(Copy.RolesReasonPlaceholder),
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
            maxChars = 300,
        )
        if (error.isNotEmpty()) {
            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(10)))
        }
        Spacer(Modifier.height(rdp(16)))
        OutlineCta(copy.t(Copy.RolesLeaveRole), ProfileTokens.Danger, busy, !busy) {
            if (reason.isBlank()) {
                error = copy.t(Copy.RolesReasonRequired)
                return@OutlineCta
            }
            busy = true
            error = ""
            scope.launch {
                val r = runFeature(null as ApiResponse<RoleRequestData>?) { Xani.roles.revoke(membershipId, reason) }
                busy = false
                if (r == null || !r.ok) {
                    error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                    return@launch
                }
                // The seat is gone: pull fresh authority so every role-gated
                // surface (profile badges, buttons) follows right now.
                runFeature(Unit) { Xani.session.resync() }
                val left = r.data?.status == RoleRequestStatus.APPROVED
                onDone(if (left) copy.t(Copy.RolesLeftRole) else r.message.ifBlank { copy.t(Copy.RolesActDone) })
            }
        }
    }
}

/**
 * Change upline: enter the new upline's referral code; the move happens only
 * after that upline approves the request (the server notifies them).
 */
@Composable
private fun ShiftUplineForm(copy: Copy, membershipId: Long, onDone: (String) -> Unit) {
    var code by rememberSaveable(membershipId) { mutableStateOf("") }
    var note by rememberSaveable(membershipId) { mutableStateOf("") }
    var error by remember(membershipId) { mutableStateOf("") }
    var busy by remember(membershipId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(rdp(18))) {
        XaniText(copy.t(Copy.RolesShiftUpline), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
        XaniText(
            copy.t(Copy.RolesShiftUplineHint),
            Type.Caption,
            color = ProfileTokens.Sub,
            fontSizeOverride = 12f,
            modifier = Modifier.padding(top = rdp(6), bottom = rdp(12)),
        )
        LightField(
            value = code,
            onValueChange = { code = it.uppercase() },
            placeholder = copy.t(Copy.RolesShiftCodePlaceholder),
            imeAction = ImeAction.Next,
            maxChars = 32,
        )
        Spacer(Modifier.height(rdp(10)))
        LightField(
            value = note,
            onValueChange = { note = it },
            placeholder = copy.t(Copy.RolesNotePlaceholder),
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
            maxChars = 300,
        )
        if (error.isNotEmpty()) {
            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(10)))
        }
        Spacer(Modifier.height(rdp(16)))
        PrimaryCta(
            text = copy.t(Copy.RolesShiftUpline),
            enabled = !busy,
            loading = busy,
            onClick = {
                if (code.isBlank()) {
                    error = copy.t(Copy.RolesShiftCodeRequired)
                    return@PrimaryCta
                }
                busy = true
                error = ""
                scope.launch {
                    val r = runFeature(null as ApiResponse<RoleRequestData>?) { Xani.roles.shiftUpline(membershipId, code, note) }
                    busy = false
                    if (r == null || !r.ok) {
                        error = r?.message ?: copy.t(Copy.RolesLoadFailed)
                        return@launch
                    }
                    onDone(r.message.ifBlank { copy.t(Copy.RolesActDone) })
                }
            },
        )
    }
}

@Composable
private fun PersonRow(p: MembershipData, copy: Copy, indent: Int, sub: String, status: Int = 0, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(start = rdp(18 + (indent.coerceAtMost(6) * 14)), end = rdp(18), top = rdp(10), bottom = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(p.userId, p.displayName, p.avatarUrl, rdp(40))
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                XaniText(p.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                if (status != 0 && status != MembershipStatus.ACTIVE) {
                    StatusPill(memberStatusLabel(copy, status), memberStatusColor(status))
                }
            }
            XaniText(sub, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1)
        }
        if (onClick != null) ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
    }
    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
}

// ─── requests + approvals ────────────────────────────────────────────────────

@Composable
private fun RequestList(
    copy: Copy,
    empty: String,
    load: suspend (String) -> ApiResponse<RoleRequestPage>,
    onOpen: (RoleRequestData) -> Unit,
) {
    val items = remember(empty) { mutableStateListOf<RoleRequestData>() }
    var cursor by remember(empty) { mutableStateOf("") }
    var loaded by remember(empty) { mutableStateOf(false) }
    var loading by remember(empty) { mutableStateOf(false) }
    var failed by remember(empty) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(after: String) {
        loading = true
        val page = runFeature(null as RoleRequestPage?) { load(after).value }
        if (page == null) {
            failed = after.isEmpty()
        } else {
            val seen = items.mapTo(HashSet()) { it.id }
            if (after.isEmpty()) items.clear()
            items.addAll(page.items.filter { after.isEmpty() || seen.add(it.id) })
            cursor = page.next
            loaded = true
            failed = false
        }
        loading = false
    }
    LaunchedEffect(empty) { fetch("") }

    LazyColumn(Modifier.fillMaxSize()) {
        when {
            failed -> item(key = "failed") { Failed(copy) { scope.launch { fetch("") } } }
            !loaded -> item(key = "loading") { Loading() }
            items.isEmpty() -> item(key = "empty") {
                XaniText(
                    empty,
                    Type.Caption,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 14f,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(rdp(32)),
                )
            }
        }
        itemsIndexed(items, key = { _, r -> r.id }) { i, r ->
            RequestRow(copy, r, onClick = { onOpen(r) })
            if (i == items.lastIndex && cursor.isNotEmpty() && !loading) {
                LaunchedEffect(r.id) { fetch(cursor) }
            }
        }
    }
}

@Composable
private fun RequestRow(copy: Copy, r: RoleRequestData, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(18), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(r.subject.id, r.subject.displayName, r.subject.avatarUrl, rdp(40))
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                XaniText(r.subject.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                StatusPill(requestStatusLabel(copy, r.status), requestStatusColor(r.status))
            }
            XaniText(describe(copy, r), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 2)
        }
        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
    }
    Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun RequestDetail(copy: Copy, requestId: Long, onBack: () -> Unit, onDecided: (String) -> Unit) {
    BackHandler(onBack = onBack)
    var r by remember(requestId) { mutableStateOf<RoleRequestData?>(null) }
    var error by remember(requestId) { mutableStateOf("") }
    var text by rememberSaveable(requestId) { mutableStateOf("") }
    var busy by remember(requestId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestId) {
        val x = runFeature(null as ApiResponse<RoleRequestData>?) { Xani.roles.request(requestId) }
        if (x == null || !x.ok || x.data == null) error = x?.message ?: copy.t(Copy.RolesLoadFailed) else r = x.data
    }

    fun act(kind: String) {
        val cur = r ?: return
        if (kind != "approve" && text.isBlank()) {
            error = copy.t(Copy.RolesReasonRequired)
            return
        }
        busy = kind
        error = ""
        scope.launch {
            val x = runFeature(null as ApiResponse<RoleRequestData>?) {
                when (kind) {
                    "approve" -> Xani.roles.approve(cur.id, text)
                    "reject" -> Xani.roles.reject(cur.id, text)
                    else -> Xani.roles.cancel(cur.id, text)
                }
            }
            busy = ""
            if (x == null || !x.ok || x.data == null) {
                error = x?.message ?: copy.t(Copy.RolesLoadFailed)
                return@launch
            }
            r = x.data
            // Accepting a role changes this account's memberships: re-read
            // identity so the profile role title updates immediately.
            if (x.data.status == RoleRequestStatus.APPROVED) {
                runFeature(Unit) { Xani.session.resync() }
            }
            onDecided("${cur.code} · ${requestStatusLabel(copy, x.data.status)}")
        }
    }

    Column(Modifier.fillMaxSize().background(ProfileTokens.Bg2).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                ProfileIco.Back(ProfileTokens.Text)
            }
            Column {
                XaniText(r?.let { kindLabel(copy, it.kind) } ?: "", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
                XaniText(r?.code ?: "", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
            }
        }
        val v = r
        if (v == null) {
            if (error.isNotEmpty()) {
                XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(20)))
            } else {
                Loading()
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(rdp(18))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(v.subject.id, v.subject.displayName, v.subject.avatarUrl, rdp(48))
                        Spacer(Modifier.width(rdp(12)))
                        Column(Modifier.weight(1f)) {
                            XaniText(v.subject.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
                            XaniText("@${v.subject.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                        }
                        StatusPill(requestStatusLabel(copy, v.status), requestStatusColor(v.status))
                    }
                    Spacer(Modifier.height(rdp(12)))
                    XaniText(describe(copy, v), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
                    KeyValue(copy.t(Copy.RolesRequestedBy, "name" to v.requestedBy.displayName), relative(v.createdAt))
                    if (v.note.isNotBlank()) KeyValue("“${v.note}”", "")
                    v.decidedBy?.let { KeyValue(copy.t(Copy.RolesDecided, "name" to it.displayName), relative(v.decidedAt)) }
                    if (v.reason.isNotBlank()) KeyValue(v.reason, "")
                    if (v.referralCode.isNotBlank()) KeyValue(copy.t(Copy.RolesJoinedWith, "code" to v.referralCode), "")
                    if (v.isPending && v.expiresAt.isNotBlank()) {
                        KeyValue(copy.t(Copy.RolesExpires, "when" to relative(v.expiresAt)), "")
                    }
                }
            }
            if (v.answers.isNotEmpty()) {
                item(key = "answers") {
                    Column(Modifier.fillMaxWidth().padding(top = rdp(8)).background(Color.White).padding(rdp(18))) {
                        XaniText(copy.t(Copy.RolesAnswers), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                        for (a in v.answers) {
                            XaniText(a.label, Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f, modifier = Modifier.padding(top = rdp(10)))
                            XaniText(a.value, Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
                        }
                    }
                }
            }
            if (v.isPending && (v.canDecide || v.canCancel)) {
                item(key = "actions") {
                    Column(Modifier.fillMaxWidth().padding(rdp(18))) {
                        LightField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = copy.t(Copy.RolesReasonPlaceholder),
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                            maxChars = 300,
                        )
                        if (error.isNotEmpty()) {
                            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(8)))
                        }
                        Spacer(Modifier.height(rdp(12)))
                        if (v.canDecide) {
                            PrimaryCta(text = copy.t(Copy.RolesApprove), enabled = busy.isEmpty(), loading = busy == "approve", onClick = { act("approve") })
                            Spacer(Modifier.height(rdp(10)))
                            OutlineCta(copy.t(Copy.RolesReject), ProfileTokens.Danger, busy == "reject", busy.isEmpty()) { act("reject") }
                        }
                        if (v.canCancel) {
                            Spacer(Modifier.height(rdp(10)))
                            OutlineCta(copy.t(Copy.RolesCancel), ProfileTokens.Text, busy == "cancel", busy.isEmpty()) { act("cancel") }
                        }
                    }
                }
            } else if (v.isPending) {
                item(key = "waiting") {
                    XaniText(copy.t(Copy.RolesWaiting), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(18)))
                }
            }
        }
    }
}

// ─── small pieces ────────────────────────────────────────────────────────────

@Composable
internal fun TabChip(modifier: Modifier, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(rdp(32))
            .clip(RoundedCornerShape(rdp(16)))
            .background(if (on) ProfileTokens.Accent else ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1)
    }
}

@Composable
internal fun SmallButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(rdp(18))
    Box(
        Modifier
            .clip(shape)
            .border(rdp(1), ProfileTokens.Hairline, shape)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(7)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f)
    }
}

@Composable
internal fun OutlineCta(label: String, tint: Color, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(rdp(6))
    Box(
        Modifier
            .fillMaxWidth()
            .height(rdp(48))
            .clip(shape)
            .border(rdp(1), tint, shape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) Spinner(rdp(18), tint) else XaniText(label, Type.LabelBold, color = tint, fontSizeOverride = 14f)
    }
}

@Composable
internal fun StatusPill(label: String, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(8)))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = rdp(7), vertical = rdp(2)),
    ) {
        XaniText(label, Type.Micro, color = tint, fontSizeOverride = 10f)
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    XaniText(
        text.uppercase(),
        Type.Micro,
        color = Color(0xFFAAAAAA),
        fontSizeOverride = 11f,
        modifier = modifier.padding(start = rdp(18), end = rdp(18), top = rdp(16), bottom = rdp(6)),
    )
}

@Composable
internal fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(top = rdp(6)), verticalAlignment = Alignment.CenterVertically) {
        XaniText(k, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
        if (v.isNotEmpty()) XaniText(v, Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f)
    }
}

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) {
        Spinner(rdp(20), ProfileTokens.Accent)
    }
}

@Composable
internal fun Failed(copy: Copy, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(rdp(24)), horizontalAlignment = Alignment.CenterHorizontally) {
        XaniText(copy.t(Copy.RolesLoadFailed), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
        XaniText(
            copy.t(Copy.NotifyRetry),
            Type.LabelBold,
            color = ProfileTokens.Accent,
            fontSizeOverride = 14f,
            modifier = Modifier.padding(top = rdp(10)).pressable(onClick = retry),
        )
    }
}

private fun describe(copy: Copy, r: RoleRequestData): String {
    val under = r.parent?.let { " → ${it.displayName}" } ?: ""
    return when (r.kind) {
        RoleRequestKind.ASSIGN, RoleRequestKind.APPLY -> "${kindLabel(copy, r.kind)} · ${r.role.title}$under"
        RoleRequestKind.TRANSFER -> "${kindLabel(copy, r.kind)} · ${r.role.title}$under" +
            if (r.downlineCount > 0) " · ${r.downlineCount}" else ""
        else -> "${kindLabel(copy, r.kind)} · ${r.role.title}"
    }
}

internal fun kindLabel(copy: Copy, kind: Int): String = copy.t(
    when (kind) {
        RoleRequestKind.ASSIGN -> Copy.RolesKindAssign
        RoleRequestKind.APPLY -> Copy.RolesKindApply
        RoleRequestKind.TRANSFER -> Copy.RolesKindTransfer
        RoleRequestKind.REVOKE -> Copy.RolesKindRevoke
        else -> Copy.RolesKindReactivate
    },
)

internal fun requestStatusLabel(copy: Copy, s: Int): String = copy.t(
    when (s) {
        RoleRequestStatus.APPROVED -> Copy.RolesReqApproved
        RoleRequestStatus.REJECTED -> Copy.RolesReqRejected
        RoleRequestStatus.CANCELLED -> Copy.RolesReqCancelled
        RoleRequestStatus.EXPIRED -> Copy.RolesReqExpired
        else -> Copy.RolesReqPending
    },
)

internal fun requestStatusColor(s: Int): Color = when (s) {
    RoleRequestStatus.APPROVED -> ProfileTokens.Ok
    RoleRequestStatus.REJECTED -> ProfileTokens.Danger
    RoleRequestStatus.PENDING -> Color(0xFFB45309)
    else -> ProfileTokens.Sub
}

internal fun memberStatusLabel(copy: Copy, s: Int): String = copy.t(
    when (s) {
        MembershipStatus.ACTIVE -> Copy.RolesStatusActive
        MembershipStatus.SUSPENDED -> Copy.RolesStatusSuspended
        MembershipStatus.BLOCKED -> Copy.RolesStatusBlocked
        else -> Copy.RolesStatusRevoked
    },
)

internal fun memberStatusColor(s: Int): Color = when (s) {
    MembershipStatus.ACTIVE -> ProfileTokens.Ok
    MembershipStatus.SUSPENDED -> Color(0xFFB45309)
    MembershipStatus.BLOCKED -> ProfileTokens.Danger
    else -> ProfileTokens.Sub
}

/** RFC3339 → "2h" / "3d"; blank when unparsable. */
private fun relative(rfc3339: String): String {
    if (rfc3339.isBlank()) return ""
    val t = runFeature(0L) { java.time.Instant.parse(rfc3339).toEpochMilli() }
    if (t == 0L) return ""
    val d = (System.currentTimeMillis() - t) / 1000
    val a = kotlin.math.abs(d)
    val s = when {
        a < 60 -> "now"
        a < 3600 -> "${a / 60}m"
        a < 86400 -> "${a / 3600}h"
        a < 86400 * 7 -> "${a / 86400}d"
        else -> "${a / (86400 * 7)}w"
    }
    return if (d < 0 && s != "now") "in $s" else s
}
