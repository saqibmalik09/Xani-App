package app.xani.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Account id with a copy control. Support and ops ask for this number; putting
 * it next to the handle (profile + drawer) means it is one tap away and never
 * needs a separate screen.
 */
@Composable
internal fun CopyableUserId(
    copy: Copy,
    userId: Long,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (userId <= 0L) return
    val context = LocalContext.current
    val idText = userId.toString()
    var flash by remember(userId) { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        kotlinx.coroutines.delay(1_400)
        flash = false
    }
    fun copyId() {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("user_id", idText))
        flash = true
        onCopied()
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(rdp(10)))
            .background(ProfileTokens.Bg2)
            .pressable(onClick = ::copyId)
            .padding(start = rdp(10), end = rdp(6), top = rdp(4), bottom = rdp(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(4)),
    ) {
        XaniText(
            copy.t(Copy.ProfileUserId, "n" to idText),
            Type.Caption,
            color = ProfileTokens.Sub,
            fontSizeOverride = 12f,
            maxLines = 1,
        )
        Box(
            Modifier.size(rdp(22)),
            contentAlignment = Alignment.Center,
        ) {
            if (flash) ProfileIco.Check(Color(0xFF22C55E), rdp(14))
            else ProfileIco.CopyLink(ProfileTokens.Muted, rdp(14))
        }
    }
}
