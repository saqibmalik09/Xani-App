package app.xani.feature.profile

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.media.PickedImage
import app.xani.core.net.PublicSellerData
import app.xani.core.net.SellerDisputeData
import app.xani.core.net.SellerListData
import app.xani.core.net.SellerRequestData
import app.xani.core.net.Xani
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The user side of coin sellers: the browsable list (whose fields and actions
 * follow the console's request mode), a request to one seller with an
 * optional / required payment screenshot, my requests with their status, and
 * my disputes with their chat threads.
 */
@Composable
internal fun CoinSellersScreen(copy: Copy, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var list by remember { mutableStateOf<SellerListData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var listKey by remember { mutableStateOf(0) }
    var requestFor by remember { mutableStateOf<PublicSellerData?>(null) }
    var disputeFor by remember { mutableStateOf<PublicSellerData?>(null) }
    var disputeId by rememberSaveable { mutableStateOf(0L) }
    var banner by remember { mutableStateOf("") }

    LaunchedEffect(listKey) {
        loading = true
        val r = Xani.sellers.list()
        r.value?.let { list = it; error = "" } ?: run { error = r.message }
        loading = false
    }

    if (disputeId > 0L) {
        SellerDisputeThread(copy, disputeId, onBack = { disputeId = 0L })
        return
    }
    val reqSeller = requestFor
    if (reqSeller != null && list != null) {
        SellerRequestForm(
            copy = copy, seller = reqSeller, list = list!!,
            onBack = { requestFor = null },
            onDone = {
                requestFor = null
                banner = copy.t(Copy.SellerRequestDone)
                tab = 1
            },
        )
        return
    }
    val dispSeller = disputeFor
    if (dispSeller != null) {
        SellerDisputeForm(
            copy = copy, sellerId = dispSeller.userId, sellerUsername = dispSeller.username,
            onBack = { disputeFor = null },
            onDone = { id ->
                disputeFor = null
                disputeId = id
            },
        )
        return
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                XaniText(copy.t(Copy.SellerListTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, modifier = Modifier.weight(1f))
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(10)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                listOf(copy.t(Copy.SellerTabSellers), copy.t(Copy.SellerTabMyRequests), copy.t(Copy.SellerTabDisputes))
                    .forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(rdp(12)))
                                .background(if (tab == i) ProfileTokens.Text else ProfileTokens.Bg2)
                                .pressable(onClick = { tab = i }).padding(vertical = rdp(10)),
                            contentAlignment = Alignment.Center,
                        ) {
                            XaniText(label, Type.LabelBold, color = if (tab == i) Color.White else ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 1)
                        }
                    }
            }

            if (banner.isNotEmpty()) SellerBanner(banner, onClick = { banner = "" })
            if (error.isNotEmpty()) SellerBanner(error, danger = true, onClick = { listKey++ })

            when (tab) {
                0 -> SellersListTab(copy, list, loading, onRequest = { requestFor = it }, onDispute = { disputeFor = it })
                1 -> MySellerRequestsTab(copy, onDispute = { sellerId, username ->
                    disputeFor = PublicSellerData(sellerId, username, "", "", "", "", 0L)
                })
                else -> MyDisputesTab(copy, onOpen = { disputeId = it })
            }
        }
    }
}

// ─── sellers list ────────────────────────────────────────────────────────────

@Composable
private fun SellersListTab(
    copy: Copy,
    list: SellerListData?,
    loading: Boolean,
    onRequest: (PublicSellerData) -> Unit,
    onDispute: (PublicSellerData) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        if (list != null && !list.enabled) {
            item { SellerBanner(copy.t(Copy.SellerListOff), danger = true) }
            return@LazyColumn
        }
        if (list != null && list.policy.isNotEmpty()) {
            item {
                Box(
                    Modifier.padding(horizontal = rdp(16)).padding(bottom = rdp(8)).fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent.copy(alpha = 0.08f)).padding(rdp(12)),
                ) { XaniText(list.policy, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f) }
            }
        }
        if (loading && (list == null || list.sellers.isEmpty())) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (list != null && list.sellers.isEmpty()) {
            item { XaniText(copy.t(Copy.SellerListEmpty), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24))) }
        }
        if (list != null) {
            items(list.sellers, key = { it.userId }) { s ->
                Column(
                    Modifier.padding(horizontal = rdp(16), vertical = rdp(5)).fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(12)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(s.userId, s.fullName.ifEmpty { s.username }, s.avatarUrl, rdp(44))
                        Spacer(Modifier.width(rdp(10)))
                        Column(Modifier.weight(1f)) {
                            XaniText(s.fullName.ifEmpty { "@${s.username}" }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            XaniText(
                                "@${s.username} · #${s.userId}" + if (s.sentCount > 0) " · ${copy.t(Copy.SellerSends, "n" to s.sentCount.toString())}" else "",
                                Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f,
                            )
                        }
                    }
                    if (s.whatsapp.isNotEmpty() || s.accountNo.isNotEmpty()) {
                        Spacer(Modifier.height(rdp(8)))
                        Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                            if (s.whatsapp.isNotEmpty()) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(rdp(8))).background(ProfileTokens.Ok.copy(alpha = 0.10f))
                                        .pressable(onClick = { uriHandler.openUri("https://wa.me/${s.whatsapp.filter { it.isDigit() }}") })
                                        .padding(horizontal = rdp(10), vertical = rdp(5)),
                                ) { XaniText("${copy.t(Copy.SellerContactWhatsapp)} ${s.whatsapp}", Type.Micro, color = ProfileTokens.Ok, fontSizeOverride = 11f) }
                            }
                            if (s.accountNo.isNotEmpty()) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(rdp(8))).background(ProfileTokens.Bg3)
                                        .padding(horizontal = rdp(10), vertical = rdp(5)),
                                ) { XaniText("${copy.t(Copy.SellerAccountNo)}: ${s.accountNo}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f) }
                            }
                        }
                    }
                    Spacer(Modifier.height(rdp(10)))
                    Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                        if (list.canRequest) {
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Accent)
                                    .pressable(onClick = { onRequest(s) }).padding(vertical = rdp(10)),
                                contentAlignment = Alignment.Center,
                            ) { XaniText(copy.t(Copy.SellerRequestBtn), Type.LabelBold, color = Color.White, fontSizeOverride = 12f) }
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg3)
                                .pressable(onClick = { onDispute(s) }).padding(horizontal = rdp(12), vertical = rdp(10)),
                            contentAlignment = Alignment.Center,
                        ) { XaniText(copy.t(Copy.SellerDispute), Type.LabelBold, color = ProfileTokens.Sub, fontSizeOverride = 12f) }
                    }
                }
            }
        }
    }
}

// ─── request form ────────────────────────────────────────────────────────────

@Composable
private fun SellerRequestForm(
    copy: Copy,
    seller: PublicSellerData,
    list: SellerListData,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val clientId = remember(seller.userId) { UUID.randomUUID().toString() }

    BackHandler(onBack = onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        error = ""
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                val bmp = PickedImage.decodeUpright(context, uri, 1600) ?: return@withContext null
                val enc = PickedImage.encodeJpeg(bmp, 85) ?: return@withContext null
                bmp to enc
            }
            preparing = false
            if (out == null) {
                error = copy.t(Copy.RechargeNeedProof)
                return@launch
            }
            preview = out.first
            bytes = out.second
        }
    }

    val amountN = amount.toLongOrNull() ?: 0L
    val canSubmit = amountN > 0L && !busy && !preparing && (!list.screenshotRequired || bytes != null)

    fun submit() {
        if (!canSubmit) return
        busy = true
        error = ""
        scope.launch {
            var key = ""
            val proof = bytes
            if (proof != null) {
                val up = Xani.sellers.uploadProof(proof, "image/jpeg", "jpg")
                key = up.value.orEmpty()
                if (key.isEmpty()) {
                    busy = false
                    error = up.message
                    return@launch
                }
            }
            val r = Xani.sellers.request(seller.userId, amountN, key, note.trim(), clientId)
            busy = false
            if (r.ok) onDone() else error = r.message
        }
    }

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = rdp(32))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                XaniText(
                    copy.t(Copy.SellerRequestTitle, "name" to seller.username), Type.H2,
                    color = ProfileTokens.Text, fontSizeOverride = 17f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            Column(Modifier.padding(horizontal = rdp(16))) {
                if (list.policy.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent.copy(alpha = 0.08f)).padding(rdp(12)),
                    ) { XaniText(list.policy, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f) }
                    Spacer(Modifier.height(rdp(12)))
                }

                XaniText(copy.t(Copy.SellerAmount), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
                LightField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                    placeholder = copy.t(Copy.SellerAmountHint), modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number, maxChars = 9,
                )
                XaniText(
                    copy.t(Copy.SellerLimits, "min" to list.minSend.toString(), "max" to if (list.maxSend > 0) list.maxSend.toString() else "∞"),
                    Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f,
                    modifier = Modifier.padding(start = rdp(4), top = rdp(4)),
                )

                Spacer(Modifier.height(rdp(14)))
                XaniText(
                    copy.t(if (list.screenshotRequired) Copy.RechargeProof else Copy.SellerProofOptional),
                    Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f,
                    modifier = Modifier.padding(start = rdp(4), bottom = rdp(8)),
                )
                val shot = preview
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (shot != null) rdp(260) else rdp(120))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(ProfileTokens.Bg2)
                        .border(rdp(1), if (shot != null) ProfileTokens.Accent.copy(alpha = 0.4f) else ProfileTokens.Border, RoundedCornerShape(rdp(16)))
                        .pressable(enabled = !preparing && !busy, onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        preparing -> Spinner(rdp(20), ProfileTokens.Accent)
                        shot != null -> {
                            Image(shot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(rdp(10)).clip(RoundedCornerShape(rdp(10)))
                                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = rdp(10), vertical = rdp(6)),
                            ) { XaniText(copy.t(Copy.RechargeChange), Type.Micro, color = Color.White, fontSizeOverride = 11f) }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileIco.Path("M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z", ProfileTokens.Accent)
                            Spacer(Modifier.height(rdp(6)))
                            XaniText(copy.t(Copy.RechargeAttach), Type.LabelBold, color = ProfileTokens.Accent, fontSizeOverride = 13f)
                        }
                    }
                }

                Spacer(Modifier.height(rdp(14)))
                XaniText(copy.t(Copy.SellerNote), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
                LightField(
                    value = note, onValueChange = { note = it.take(300) }, placeholder = copy.t(Copy.RechargeNoteHint),
                    modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences, maxChars = 300,
                )

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(10)))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12))) {
                        XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
                    }
                }

                Spacer(Modifier.height(rdp(20)))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14)))
                        .background(if (canSubmit) ProfileTokens.Accent else ProfileTokens.Bg3)
                        .pressable(enabled = canSubmit, onClick = ::submit).padding(vertical = rdp(15)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) Spinner(rdp(18), Color.White)
                    else XaniText(copy.t(Copy.SellerRequestBtn), Type.LabelBold, color = if (canSubmit) Color.White else ProfileTokens.Muted, fontSizeOverride = 15f)
                }
            }
        }
    }
}

// ─── my requests ─────────────────────────────────────────────────────────────

@Composable
private fun MySellerRequestsTab(copy: Copy, onDispute: (Long, String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var items by remember { mutableStateOf<List<SellerRequestData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Xani.sellers.myRequests().value?.let { items = it.items }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (items.isEmpty()) {
            item { XaniText(copy.t(Copy.SellerNoRequests), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24))) }
        }
        items(items, key = { it.id }) { req ->
            Column(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(5)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(12)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        XaniText("@${req.sellerUsername}", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
                        XaniText(sellerWhen(req.createdAtMs), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                    }
                    Spacer(Modifier.width(rdp(8)))
                    Column(horizontalAlignment = Alignment.End) {
                        XaniText(req.amount.toString(), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                        SellerStatusChip(copy, req.status)
                    }
                }
                if (req.decisionNote.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(6)))
                    XaniText(req.decisionNote, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
                Row(Modifier.padding(top = rdp(8)), horizontalArrangement = Arrangement.spacedBy(rdp(12))) {
                    if (req.screenshotUrl.isNotEmpty()) {
                        XaniText(
                            copy.t(Copy.SellerViewProof), Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 12f,
                            modifier = Modifier.pressable(onClick = { uriHandler.openUri(req.screenshotUrl) }),
                        )
                    }
                    XaniText(
                        copy.t(Copy.SellerDispute), Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f,
                        modifier = Modifier.pressable(onClick = { onDispute(req.sellerId, req.sellerUsername) }),
                    )
                }
            }
        }
    }
}

// ─── disputes ────────────────────────────────────────────────────────────────

@Composable
private fun MyDisputesTab(copy: Copy, onOpen: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<SellerDisputeData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Xani.sellers.myDisputes().value?.let { items = it.items }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (items.isEmpty()) {
            item { XaniText(copy.t(Copy.SellerDisputeEmpty), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24))) }
        }
        items(items, key = { it.id }) { d ->
            Row(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(4)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2)
                    .pressable(onClick = { onOpen(d.id) }).padding(rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    XaniText(
                        copy.t(Copy.SellerDisputeAgainst, "name" to d.sellerUsername) + " · " + sellerWhen(d.createdAtMs),
                        Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f,
                    )
                    XaniText(d.reason, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(rdp(8)))
                SellerStatusChip(copy, d.status)
            }
        }
    }
}

@Composable
private fun SellerDisputeForm(
    copy: Copy,
    sellerId: Long,
    sellerUsername: String,
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    fun submit() {
        if (reason.isBlank() || busy) return
        busy = true
        error = ""
        scope.launch {
            val r = Xani.sellers.openDispute(sellerId, 0L, reason.trim())
            busy = false
            r.value?.let { onDone(it.id) } ?: run { error = r.message }
        }
    }

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                Column {
                    XaniText(copy.t(Copy.SellerDispute), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 17f)
                    XaniText(copy.t(Copy.SellerDisputeAgainst, "name" to sellerUsername), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f)
                }
            }
            Column(Modifier.padding(horizontal = rdp(16))) {
                XaniText(copy.t(Copy.SellerDisputeReason), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
                LightField(
                    value = reason, onValueChange = { reason = it.take(1000) }, placeholder = copy.t(Copy.SellerDisputeReason),
                    modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences, maxChars = 1000,
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(10)))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12))) {
                        XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
                    }
                }
                Spacer(Modifier.height(rdp(16)))
                val can = reason.isNotBlank() && !busy
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14)))
                        .background(if (can) ProfileTokens.Accent else ProfileTokens.Bg3)
                        .pressable(enabled = can, onClick = ::submit).padding(vertical = rdp(15)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) Spinner(rdp(18), Color.White)
                    else XaniText(copy.t(Copy.SellerDisputeOpenBtn), Type.LabelBold, color = if (can) Color.White else ProfileTokens.Muted, fontSizeOverride = 15f)
                }
            }
        }
    }
}
