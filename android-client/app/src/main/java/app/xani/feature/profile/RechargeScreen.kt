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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.media.PickedImage
import app.xani.core.net.RechargeHomeData
import app.xani.core.net.RechargeMethodData
import app.xani.core.net.RechargePackageData
import app.xani.core.net.RechargeRequestData
import app.xani.core.net.Xani
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
 * Buy coins in three steps: pick a package (price → coins), pick where to pay
 * (one of the platform's receiving accounts), then pay and attach the
 * screenshot. The claim joins "My requests" as pending; approval credits the
 * coins and a notice + push tells the user. Nothing moves until then.
 */
@Composable
internal fun RechargeScreen(copy: Copy, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var home by remember { mutableStateOf<RechargeHomeData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var homeKey by remember { mutableStateOf(0) }
    var pkg by remember { mutableStateOf<RechargePackageData?>(null) }
    var method by remember { mutableStateOf<RechargeMethodData?>(null) }
    var banner by remember { mutableStateOf("") }
    var requestsKey by remember { mutableStateOf(0) }

    LaunchedEffect(homeKey) {
        loading = true
        val r = Xani.wallet.rechargeHome()
        val got = r.value
        if (got != null) {
            home = got
            error = ""
        } else {
            error = r.message
        }
        loading = false
    }

    fun pop() {
        when {
            method != null -> method = null
            pkg != null -> pkg = null
            else -> onBack()
        }
    }
    BackHandler(onBack = ::pop)

    val step = when {
        method != null -> 3
        pkg != null -> 2
        else -> 1
    }

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = ::pop),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(
                        method?.name ?: pkg?.let { "${it.currency} ${it.price}" } ?: copy.t(Copy.RechargeTitle),
                        Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (pkg != null) {
                        XaniText(copy.t(Copy.RechargeStep, "n" to step.toString()), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f)
                    }
                }
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
            }

            val p = pkg
            val m = method
            when {
                p != null && m != null -> PayForm(
                    copy = copy, pkg = p, method = m,
                    onDone = { message ->
                        banner = message
                        method = null
                        pkg = null
                        tab = 1
                        homeKey++
                        requestsKey++
                    },
                )
                p != null -> MethodsStep(copy, p, home, loading) { method = it }
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(10)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                    ) {
                        RcTab(copy.t(Copy.RechargeTabPackages), tab == 0, Modifier.weight(1f)) { tab = 0 }
                        RcTab(copy.t(Copy.RechargeTabRequests), tab == 1, Modifier.weight(1f)) { tab = 1 }
                    }
                    if (banner.isNotEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(8))
                                .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Ok.copy(alpha = 0.10f))
                                .pressable(onClick = { banner = "" }).padding(rdp(12)),
                        ) { XaniText(banner, Type.Caption, color = ProfileTokens.Ok, fontSizeOverride = 12f) }
                    }
                    if (error.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(8))
                                .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { homeKey++ })
                                    .padding(horizontal = rdp(12), vertical = rdp(6)),
                            ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                        }
                    }
                    when (tab) {
                        0 -> PackagesStep(copy, home, loading) { pkg = it }
                        else -> RechargeRequestsTab(copy, requestsKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun RcTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(rdp(12))).background(if (active) ProfileTokens.Text else ProfileTokens.Bg2)
            .pressable(onClick = onClick).padding(vertical = rdp(10)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (active) Color.White else ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1)
    }
}

// ─── step 1: packages ────────────────────────────────────────────────────────

@Composable
private fun PackagesStep(copy: Copy, home: RechargeHomeData?, loading: Boolean, onPick: (RechargePackageData) -> Unit) {
    val packages = home?.packages.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "head") {
            XaniText(
                copy.t(Copy.RechargePickPackage) + (home?.country?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                modifier = Modifier.padding(horizontal = rdp(20)).padding(bottom = rdp(8)),
            )
        }
        if (packages.isEmpty()) {
            item(key = "empty") {
                if (!loading) {
                    XaniText(
                        copy.t(Copy.RechargeNoPackages), Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(24), horizontal = rdp(24)),
                    )
                }
            }
        } else {
            items(packages, key = { it.id }) { p -> PackageCard(copy, p) { onPick(p) } }
        }
    }
}

@Composable
private fun PackageCard(copy: Copy, p: RechargePackageData, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(16), vertical = rdp(5))
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(rdp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(rdp(46)).clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.AccentWash),
            contentAlignment = Alignment.Center,
        ) { ProfileIco.Coin(ProfileTokens.AccentDeep, rdp(24)) }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(compact(p.coins), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, maxLines = 1)
                Spacer(Modifier.width(rdp(6)))
                XaniText(copy.t(Copy.BalanceCoins).lowercase(), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                if (p.bonusCoins > 0L) {
                    Spacer(Modifier.width(rdp(8)))
                    Box(Modifier.clip(RoundedCornerShape(rdp(8))).background(ProfileTokens.Ok.copy(alpha = 0.12f)).padding(horizontal = rdp(7), vertical = rdp(3))) {
                        XaniText(copy.t(Copy.RechargeBonus, "n" to compact(p.bonusCoins)), Type.Micro, color = ProfileTokens.Ok, fontSizeOverride = 10f)
                    }
                }
            }
            if (p.title.isNotEmpty()) {
                XaniText(p.title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(rdp(8)))
        Box(Modifier.clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent).padding(horizontal = rdp(12), vertical = rdp(8))) {
            XaniText("${p.currency} ${p.price}", Type.LabelBold, color = Color.White, fontSizeOverride = 13f, maxLines = 1)
        }
    }
}

// ─── step 2: methods ─────────────────────────────────────────────────────────

@Composable
private fun MethodsStep(copy: Copy, pkg: RechargePackageData, home: RechargeHomeData?, loading: Boolean, onPick: (RechargeMethodData) -> Unit) {
    val methods = home?.methods.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "pkg") { PackageSummary(copy, pkg) }
        item(key = "head") {
            XaniText(
                copy.t(Copy.RechargePickMethod), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                modifier = Modifier.padding(horizontal = rdp(20)).padding(top = rdp(14), bottom = rdp(8)),
            )
        }
        if (methods.isEmpty()) {
            item(key = "empty") {
                if (!loading) {
                    XaniText(
                        copy.t(Copy.RechargeNoMethods), Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(24), horizontal = rdp(24)),
                    )
                }
            }
        } else {
            items(methods, key = { it.id }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(16), vertical = rdp(5))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(ProfileTokens.Bg2)
                        .pressable(onClick = { onPick(m) })
                        .padding(rdp(14)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(rdp(44)).clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { XaniText(m.name.take(1).uppercase(), Type.H2, color = ProfileTokens.Accent, fontSizeOverride = 18f) }
                    Spacer(Modifier.width(rdp(12)))
                    Column(Modifier.weight(1f)) {
                        XaniText(m.name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        XaniText(
                            m.title.ifEmpty { m.accountTitle }, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(rdp(8)))
                    ProfileIco.Chevron(ProfileTokens.Muted, rdp(16))
                }
            }
        }
    }
}

@Composable
private fun PackageSummary(copy: Copy, pkg: RechargePackageData) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(16)).clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Accent).padding(rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(copy.t(Copy.RechargeSendExactly), Type.Caption, color = Color.White.copy(alpha = 0.8f), fontSizeOverride = 11f)
            XaniText("${pkg.currency} ${pkg.price}", Type.H2, color = Color.White, fontSizeOverride = 24f, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            XaniText(copy.t(Copy.RechargeYouGet), Type.Caption, color = Color.White.copy(alpha = 0.8f), fontSizeOverride = 11f)
            XaniText("🪙 ${compact(pkg.totalCoins)}", Type.H2, color = Color.White, fontSizeOverride = 20f, maxLines = 1)
        }
    }
}

// ─── step 3: pay + proof ─────────────────────────────────────────────────────

@Composable
private fun PayForm(copy: Copy, pkg: RechargePackageData, method: RechargeMethodData, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable(method.id) { mutableStateOf("") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf("") }
    // One id per form: a retry after a dropped reply lands on the same claim.
    val clientId = remember(pkg.id, method.id) { UUID.randomUUID().toString() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        error = ""
        scope.launch {
            // Decode + re-encode off the main thread: a receipt stays readable
            // at 1600px and uploads in one short PUT.
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

    fun copyText(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        copied = label
    }
    LaunchedEffect(copied) {
        if (copied.isNotEmpty()) {
            kotlinx.coroutines.delay(1400)
            copied = ""
        }
    }

    val canSubmit = bytes != null && !busy && !preparing

    fun submit() {
        val proof = bytes
        if (busy) return
        if (proof == null) {
            error = copy.t(Copy.RechargeNeedProof)
            return
        }
        busy = true
        error = ""
        scope.launch {
            val up = Xani.wallet.uploadRechargeProof(proof, "image/jpeg", "jpg")
            val key = up.value
            if (key.isNullOrEmpty()) {
                busy = false
                error = up.message
                return@launch
            }
            val r = Xani.wallet.recharge(pkg.id, method.id, key, note.trim(), clientId)
            busy = false
            if (r.value != null) onDone(copy.t(Copy.RechargeDone)) else error = r.message
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = rdp(32)),
    ) {
        PackageSummary(copy, pkg)
        Spacer(Modifier.height(rdp(12)))

        Column(Modifier.padding(horizontal = rdp(16))) {
            // Where to send it — every value one tap to copy.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(14))) {
                XaniText(method.name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                if (method.title.isNotEmpty()) {
                    XaniText(method.title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
                Spacer(Modifier.height(rdp(10)))
                CopyRow(copy.t(Copy.RechargeAccountTitle), method.accountTitle, copied == "title", copy) { copyText("title", method.accountTitle) }
                Spacer(Modifier.height(rdp(8)))
                CopyRow(copy.t(Copy.RechargeAccountNo), method.accountNo, copied == "no", copy) { copyText("no", method.accountNo) }
                Spacer(Modifier.height(rdp(8)))
                CopyRow(copy.t(Copy.RechargeSendExactly), "${pkg.currency} ${pkg.price}", copied == "amt", copy) { copyText("amt", pkg.price) }
                if (method.instructions.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(10)))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Accent.copy(alpha = 0.10f)).padding(rdp(10))) {
                        Column {
                            XaniText(copy.t(Copy.RechargeInstructions), Type.Micro, color = ProfileTokens.Accent, fontSizeOverride = 10f)
                            Spacer(Modifier.height(rdp(2)))
                            XaniText(method.instructions, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f)
                        }
                    }
                }
            }

            Spacer(Modifier.height(rdp(16)))
            XaniText(copy.t(Copy.RechargeProof), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.padding(start = rdp(4)))
            XaniText(copy.t(Copy.RechargeProofHint), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(start = rdp(4), top = rdp(2), bottom = rdp(8)))
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
            XaniText(copy.t(Copy.RechargeNote), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
            LightField(
                value = note, onValueChange = { note = it.take(300) }, placeholder = copy.t(Copy.RechargeNoteHint),
                modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences, maxChars = 300,
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(rdp(10)))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12))) {
                    XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
                }
            }

            Spacer(Modifier.height(rdp(20)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(if (canSubmit) ProfileTokens.Accent else ProfileTokens.Bg3)
                    .pressable(enabled = canSubmit, onClick = ::submit)
                    .padding(vertical = rdp(15)),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Spinner(rdp(18), Color.White)
                } else {
                    XaniText(
                        copy.t(Copy.RechargeSubmit), Type.LabelBold,
                        color = if (canSubmit) Color.White else ProfileTokens.Muted, fontSizeOverride = 15f,
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, justCopied: Boolean, copy: Copy, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(Color.White).pressable(onClick = onCopy).padding(horizontal = rdp(12), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(label, Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
            XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(rdp(10)))
        Box(
            Modifier.clip(RoundedCornerShape(rdp(8))).background(if (justCopied) ProfileTokens.Ok.copy(alpha = 0.12f) else ProfileTokens.AccentWash)
                .padding(horizontal = rdp(10), vertical = rdp(6)),
        ) {
            XaniText(
                copy.t(if (justCopied) Copy.RechargeCopied else Copy.RechargeCopy), Type.Micro,
                color = if (justCopied) ProfileTokens.Ok else ProfileTokens.Accent, fontSizeOverride = 11f,
            )
        }
    }
}

// ─── my requests ─────────────────────────────────────────────────────────────

@Composable
private fun RechargeRequestsTab(copy: Copy, reloadKey: Int) {
    val rows = remember { mutableStateListOf<RechargeRequestData>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retryKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(beforeId: Long) {
        loading = true
        val r = Xani.wallet.recharges(beforeId)
        val got = r.value
        if (got != null) {
            if (beforeId == 0L) rows.clear()
            rows.addAll(got.items)
            cursor = got.nextCursor
            error = ""
        } else {
            error = r.message
        }
        loading = false
    }
    LaunchedEffect(reloadKey, retryKey) { load(0L) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = rdp(16), end = rdp(16), bottom = rdp(32))) {
        if (loading && rows.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = rdp(20)), contentAlignment = Alignment.Center) { Spinner(rdp(18), ProfileTokens.Accent) }
            }
        }
        if (error.isNotEmpty()) {
            item(key = "error") {
                Row(Modifier.fillMaxWidth().padding(vertical = rdp(8)), verticalAlignment = Alignment.CenterVertically) {
                    XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { retryKey++ })
                            .padding(horizontal = rdp(12), vertical = rdp(6)),
                    ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                if (!loading && error.isEmpty()) {
                    XaniText(
                        copy.t(Copy.RechargeNoRequests), Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(24)),
                    )
                }
            }
        } else {
            items(rows, key = { it.id }) { r -> RechargeRequestRow(copy, r) }
        }
        if (cursor.isNotEmpty() || (loading && rows.isNotEmpty())) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(vertical = rdp(14)), contentAlignment = Alignment.Center) {
                    if (loading) {
                        Spinner(rdp(18), ProfileTokens.Accent)
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2)
                                .pressable(onClick = { scope.launch { load(cursor.toLongOrNull() ?: 0L) } })
                                .padding(horizontal = rdp(18), vertical = rdp(10)),
                        ) { XaniText(copy.t(Copy.TxnLoadMore), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f) }
                    }
                }
            }
        }
    }
}
