package app.xani.feature.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.FeatureGate
import app.xani.i18n.Copy

/**
 * Asks for mic (and camera for video) then runs the action. Both dial and
 * accept go through here so the controller can assume permissions are granted
 * and fail fast when they are not.
 */
@Composable
fun rememberCallPermissions(): (video: Boolean, then: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val next = pending
        pending = null
        if (result.values.all { it }) next?.invoke()
    }
    return remember(launcher) {
        { video, then ->
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
            }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (wanted.isEmpty()) then() else {
                pending = then
                launcher.launch(wanted.toTypedArray())
            }
        }
    }
}

/**
 * The one call surface for the whole app. Sits above every tab so a call that
 * rings in on Home, or was minimized from Inbox, renders the same way.
 */
@Composable
fun CallHost(copy: Copy, device: DeviceClass, modifier: Modifier = Modifier) {
    val ui by CallController.state.collectAsState()
    val current = ui ?: return
    val permissions = rememberCallPermissions()

    FeatureGate(name = "call") {
        when {
            current.phase == CallPhase.Incoming -> Box(modifier.fillMaxSize().zIndex(10f)) {
                IncomingCallScreen(current, copy, onAccept = { permissions(current.video) { CallController.accept() } })
            }
            current.minimized && current.active -> Box(modifier.fillMaxSize().zIndex(9f), contentAlignment = Alignment.TopCenter) {
                CallPill(current, copy)
            }
            current.video -> Box(modifier.fillMaxSize().zIndex(10f)) { VideoCallScreen(current, copy) }
            else -> Box(modifier.fillMaxSize().zIndex(10f)) { AudioCallScreen(current, copy, device) }
        }
    }
}
