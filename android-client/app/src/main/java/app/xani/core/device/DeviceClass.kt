package app.xani.core.device

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * First-launch classing. Drives pager prefetch, infinite animations, and whether
 * Home stays composed under another tab. Low-RAM devices must still scroll; they
 * just keep fewer neighbours alive.
 */
@Immutable
enum class DeviceClass {
    Low,
    Mid,
    High,
    ;

    val pagerNeighbours: Int
        get() = if (this == Low) 0 else 1

    val pinHomeOffTab: Boolean
        get() = this != Low

    companion object {
        fun read(context: Context): DeviceClass {
            val am = context.getSystemService(ActivityManager::class.java)
            val lowRam = am?.isLowRamDevice == true
            val megabytes = am?.memoryClass ?: 128
            return when {
                lowRam || megabytes <= 128 -> Low
                megabytes <= 256 -> Mid
                else -> High
            }
        }
    }
}

@Composable
fun rememberDeviceClass(): DeviceClass {
    val context = LocalContext.current
    return remember(context) { DeviceClass.read(context) }
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
