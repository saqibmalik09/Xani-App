package app.xani.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.theme.responsive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ProfileEditScreen(
    copy: Copy,
    snapshot: ProfileSnapshot,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val brand = LocalBrand.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(snapshot.displayName) }
    var username by remember { mutableStateOf(snapshot.username) }
    var bio by remember { mutableStateOf(snapshot.bio) }
    var gender by remember { mutableStateOf(snapshot.gender) }
    var genderSheet by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var handleState by remember { mutableStateOf(UsernameState.Idle) }
    var photoSheet by remember { mutableStateOf(false) }
    val usernameLocked = !ProfileStore.canChangeUsername()
    val daysLeft = ProfileStore.usernameCooldownDays()

    // Availability while typing. A new keystroke cancels this coroutine, so only
    // the handle the user stopped on is ever asked about, and names already
    // answered this session come back from memory with no request at all.
    LaunchedEffect(username) {
        if (usernameLocked || username == snapshot.username) {
            handleState = UsernameState.Idle
            return@LaunchedEffect
        }
        delay(200)
        handleState = UsernameState.Checking
        handleState = ProfileStore.checkUsername(username)
    }
    val handleBlocks = handleState == UsernameState.Taken ||
        handleState == UsernameState.Reserved ||
        handleState == UsernameState.Invalid

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(20), vertical = rdp(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(40)).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text) }
                XaniText(copy.t(Copy.ProfileEditTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = rdp(24)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = rdp(28)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The same picture as the profile header: the stored photo,
                    // the one uploading right now, or the initials on the account
                    // tint — never a placeholder mark. Tap opens the picker.
                    MyAvatar(size = rdp(90), onPick = { photoSheet = true })
                    XaniText(
                        copy.t(Copy.ProfileChangePhoto),
                        Type.LabelBold,
                        color = ProfileTokens.Accent,
                        fontSizeOverride = 14f,
                        modifier = Modifier
                            .padding(top = rdp(10))
                            .pressable(onClick = { photoSheet = true }),
                    )
                }

                Column(
                    Modifier.padding(horizontal = rdp(20)),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(rdp(20)),
                ) {
                    FieldLabel(copy.t(Copy.ProfileNameLabel))
                    FieldBox {
                        BasicTextField(
                            value = name,
                            onValueChange = {
                                error = ""
                                name = it.take(40)
                            },
                            textStyle = Type.Label.responsive().copy(color = ProfileTokens.Text),
                            cursorBrush = SolidColor(ProfileTokens.Accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FieldLabel(copy.t(Copy.ProfileUsername))
                    FieldBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            XaniText("@", Type.Label, color = Color(0xFFAAAAAA), fontSizeOverride = 15f)
                            Spacer(Modifier.size(rdp(4)))
                            BasicTextField(
                                value = username,
                                onValueChange = {
                                    if (usernameLocked) return@BasicTextField
                                    error = ""
                                    username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24)
                                },
                                enabled = !usernameLocked,
                                textStyle = Type.Label.responsive().copy(
                                    color = if (usernameLocked) ProfileTokens.Muted else ProfileTokens.Text,
                                ),
                                cursorBrush = SolidColor(ProfileTokens.Accent),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val (hint, hintColor) = usernameHint(copy, handleState, username, usernameLocked, daysLeft)
                    XaniText(
                        hint,
                        Type.Caption,
                        color = hintColor,
                        fontSizeOverride = 12f,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(10)))
                            .background(ProfileTokens.Bg2)
                            .padding(horizontal = rdp(14), vertical = rdp(10)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(
                            "${brand.wordmark.lowercase()}.app/@$username",
                            Type.Caption,
                            color = ProfileTokens.Sub,
                            fontSizeOverride = 13f,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(rdp(32))
                                .pressable(onClick = {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "link",
                                            "${brand.wordmark.lowercase()}.app/@$username",
                                        ),
                                    )
                                    copied = true
                                    scope.launch {
                                        delay(1800)
                                        copied = false
                                    }
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (copied) ProfileIco.Check(Color(0xFF22C55E), rdp(16))
                            else ProfileIco.CopyLink(ProfileTokens.Sub, rdp(16))
                        }
                    }

                    FieldLabel(copy.t(Copy.ProfileGender))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(12)))
                            .border(rdp(1.5f), Color(0xFFE8E8E8), RoundedCornerShape(rdp(12)))
                            .pressable(onClick = { genderSheet = true })
                            .padding(horizontal = rdp(16), vertical = rdp(13)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(genderLabel(copy, gender), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.weight(1f))
                        ProfileIco.Path("M4 6l4 4 4-4", Color(0xFFAAAAAA), rdp(16))
                    }

                    FieldLabel(copy.t(Copy.ProfileBio))
                    FieldBox(minHeight = rdp(110)) {
                        BasicTextField(
                            value = bio,
                            onValueChange = {
                                error = ""
                                bio = it.take(150)
                            },
                            textStyle = Type.Label.responsive().copy(color = ProfileTokens.Text),
                            cursorBrush = SolidColor(ProfileTokens.Accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    XaniText(
                        "${bio.length}/150",
                        Type.Micro,
                        color = Color(0xFFCCCCCC),
                        fontSizeOverride = 12f,
                        modifier = Modifier.align(Alignment.End),
                    )

                    if (error.isNotEmpty()) {
                        XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 13f)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
            Box(
                Modifier
                    .padding(horizontal = rdp(20), vertical = rdp(12))
                    .fillMaxWidth()
                    .height(rdp(52))
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(if (handleBlocks) SolidColor(ProfileTokens.Muted) else ProfileTokens.Cta)
                    // Saving closes the screen at once: the store applies the edit to
                    // the snapshot and sends it, so there is nothing to wait for.
                    .pressable(enabled = !handleBlocks, onClick = {
                        when (
                            val result = ProfileStore.save(
                                ProfileDraft(
                                    displayName = name.ifBlank { snapshot.displayName },
                                    username = username.ifBlank { snapshot.username },
                                    bio = bio,
                                    gender = gender,
                                    status = snapshot.status,
                                ),
                            )
                        ) {
                            is ProfileSaveResult.Ok -> onSaved()
                            is ProfileSaveResult.UsernameLocked -> {
                                error = copy.t(
                                    Copy.ProfileUsernameAgainIn,
                                    "n" to result.daysLeft.toString(),
                                )
                            }
                        }
                    }),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(copy.t(Copy.ProfileSave), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
            }
        }

        if (genderSheet) {
            GenderSheet(
                copy = copy,
                selected = gender,
                onSelect = {
                    gender = it
                    genderSheet = false
                },
                onDismiss = { genderSheet = false },
            )
        }
        if (photoSheet) {
            AvatarPickerSheet(copy = copy, onClose = { photoSheet = false })
        }
    }
}

@Composable
private fun GenderSheet(
    copy: Copy,
    selected: ProfileGender,
    onSelect: (ProfileGender) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ProfileTokens.SheetScrim)
                .pressable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(bottom = rdp(20)),
        ) {
            Box(
                Modifier
                    .padding(top = rdp(12), bottom = rdp(16))
                    .align(Alignment.CenterHorizontally)
                    .size(rdp(36), rdp(4))
                    .clip(RoundedCornerShape(rdp(2)))
                    .background(Color(0xFFE0E0E0)),
            )
            XaniText(
                copy.t(Copy.ProfileGender),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 16f,
                modifier = Modifier.padding(horizontal = rdp(20)).padding(bottom = rdp(12)),
            )
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
            listOf(
                ProfileGender.Male to Copy.ProfileGenderMale,
                ProfileGender.Female to Copy.ProfileGenderFemale,
                ProfileGender.PreferNot to Copy.ProfileGenderPreferNot,
            ).forEachIndexed { i, (g, key) ->
                val on = selected == g
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { onSelect(g) })
                        .padding(horizontal = rdp(20), vertical = rdp(16)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(
                        copy.t(key),
                        if (on) Type.LabelBold else Type.Label,
                        color = if (on) ProfileTokens.Accent else ProfileTokens.Text,
                        fontSizeOverride = 15f,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) ProfileIco.Check(ProfileTokens.Accent, rdp(18))
                }
                if (i < 2) Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    XaniText(
        text.uppercase(),
        Type.Micro,
        color = ProfileTokens.Sub,
        fontSizeOverride = 12f,
        modifier = Modifier.padding(bottom = rdp(8)),
    )
}

@Composable
private fun FieldBox(
    minHeight: Dp = rdp(48),
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(rdp(12)))
            .border(rdp(1.5f), Color(0xFFE8E8E8), RoundedCornerShape(rdp(12)))
            .padding(horizontal = rdp(16), vertical = rdp(13)),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

/**
 * The line under the username field. It answers with the rule when the field is
 * untouched, the outcome when it has been checked, and the remaining days when
 * the change window is closed — never a spinner the user has to wait out.
 */
private fun usernameHint(
    copy: Copy,
    state: UsernameState,
    username: String,
    locked: Boolean,
    daysLeft: Int,
): Pair<String, Color> = when {
    locked -> copy.t(Copy.ProfileUsernameAgainIn, "n" to daysLeft.toString()) to ProfileTokens.Danger
    state == UsernameState.Available ->
        copy.t(Copy.ProfileUsernameAvailable, "u" to username) to ProfileTokens.Ok

    state == UsernameState.Taken || state == UsernameState.Reserved ->
        copy.t(Copy.ProfileUsernameTaken, "u" to username) to ProfileTokens.Danger

    state == UsernameState.Invalid -> copy.t(Copy.ProfileUsernameRule) to ProfileTokens.Danger
    state == UsernameState.Cooldown ->
        copy.t(Copy.ProfileUsernameAgainIn, "n" to daysLeft.toString()) to ProfileTokens.Danger

    state == UsernameState.Checking -> copy.t(Copy.ProfileUsernameChecking) to ProfileTokens.Sub
    else -> copy.t(Copy.ProfileUsernameLocked) to ProfileTokens.Sub
}

private fun genderLabel(copy: Copy, g: ProfileGender): String = copy.t(
    when (g) {
        ProfileGender.Male -> Copy.ProfileGenderMale
        ProfileGender.Female -> Copy.ProfileGenderFemale
        ProfileGender.PreferNot -> Copy.ProfileGenderPreferNot
    },
)
