package app.xani.feature.system

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.AppGate
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * Wraps the whole app and drops an unmissable wall over it when this build has
 * been turned off from the dashboard.
 *
 * It cannot be dismissed or navigated past — Back is swallowed and taps never
 * reach the app underneath — so an administrator can retire an old build and be
 * certain nobody keeps using it. The message is the admin's own; the WhatsApp
 * button (shown only when a number is set) opens a chat to request the update.
 * The gate is live: turning the version back on removes the wall without a
 * restart.
 */
@Composable
fun VersionWall(content: @Composable () -> Unit) {
    content()

    val block by AppGate.state.collectAsState()
    block?.let { BlockSheet(message = it.message, whatsapp = it.whatsapp) }
}

@Composable
private fun BlockSheet(message: String, whatsapp: String) {
    val context = LocalContext.current
    val digits = remember(whatsapp) { whatsapp.filter { it.isDigit() } }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(24))
                .clip(RoundedCornerShape(rdp(20)))
                .background(Colors.Background)
                .padding(horizontal = rdp(24), vertical = rdp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            XaniMark(size = rdp(48))

            XaniText(
                text = "Update required",
                style = Type.H2,
                color = Colors.Text,
                align = TextAlign.Center,
            )

            XaniText(
                text = message.ifBlank { "A new version is available. Please contact us to update." },
                style = Type.Body,
                color = Colors.Hint,
                align = TextAlign.Center,
            )

            // The build that was turned off. Support asks for it first, and a
            // walled user cannot reach the drawer to read it anywhere else.
            XaniText(
                text = "Version ${AppGate.version}",
                style = Type.Micro,
                color = Colors.Hint,
                align = TextAlign.Center,
            )

            if (digits.isNotEmpty()) {
                Spacer(Modifier.height(rdp(4)))
                PrimaryCta(
                    text = "Contact on WhatsApp",
                    enabled = true,
                    onClick = {
                        val uri = Uri.parse("https://wa.me/$digits")
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }
    }
}
