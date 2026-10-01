package app.xani.feature.profile

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.xani.core.isolate.runFeature
import app.xani.core.net.AppForeground
import app.xani.core.net.Xani
import app.xani.feature.call.PushRegistrar
import app.xani.feature.call.PushStatus
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Categories the settings screen shows, in order; the server enforces them. */
private val NotifyCategories = listOf(
    "messages" to Copy.NotifyMessages,
    "audio_calls" to Copy.NotifyAudioCalls,
    "video_calls" to Copy.NotifyVideoCalls,
    "live" to Copy.NotifyLive,
    "comments" to Copy.NotifyComments,
    "replies" to Copy.NotifyReplies,
    "mentions" to Copy.NotifyMentions,
)

/**
 * Per-category push switches. State lives on the server (it decides what to
 * send, so a muted category costs no delivery); a flip is optimistic and
 * rolls back if the save fails.
 */
@Composable
internal fun NotificationSettingsScreen(copy: Copy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { mutableStateMapOf<String, Boolean>() }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val status by PushRegistrar.status.collectAsState()
    var osAllowed by remember { mutableStateOf(PushRegistrar.notificationsAllowed(context)) }
    // Coming back from system settings flips foreground on: re-read the OS switch.
    val foreground by AppForeground.visible.collectAsState()
    LaunchedEffect(foreground) {
        if (foreground) osAllowed = PushRegistrar.notificationsAllowed(context)
    }

    LaunchedEffect(reload) {
        failed = false
        val map = runFeature(null as Map<String, Boolean>?) {
            Xani.api.get("/v1/notifications/prefs", authenticated = true) { json ->
                parseNotifyPrefs(json)
            }.value
        }
        if (map == null) {
            failed = true
        } else {
            prefs.clear()
            prefs.putAll(map)
            loaded = true
        }
    }

    fun flip(category: String, on: Boolean) {
        prefs[category] = on
        scope.launch {
            val r = runFeature(null) {
                Xani.api.post(
                    "/v1/notifications/prefs",
                    JSONObject().put("prefs", JSONObject().put(category, on)),
                    authenticated = true,
                )
            }
            if (r?.ok != true) prefs[category] = !on
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg2)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                ProfileIco.Back(ProfileTokens.Text)
            }
            XaniText(copy.t(Copy.ProfileNotifications), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = rdp(40))) {
            val banner = when {
                !osAllowed -> copy.t(Copy.NotifyOsOff)
                status == PushStatus.Registered -> copy.t(Copy.NotifyPushOn)
                status == PushStatus.Unknown -> ""
                else -> copy.t(Copy.NotifyNotReady)
            }
            if (banner.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(start = rdp(16), end = rdp(16), top = rdp(16))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(Color.White)
                        .padding(horizontal = rdp(16), vertical = rdp(12)),
                ) {
                    XaniText(banner, Type.Label, color = ProfileTokens.Text, fontSizeOverride = 13f)
                    if (!osAllowed) {
                        XaniText(
                            copy.t(Copy.NotifyOpenSystem),
                            Type.LabelBold,
                            color = Dark.Primary,
                            fontSizeOverride = 13f,
                            modifier = Modifier.padding(top = rdp(8)).pressable {
                                runFeature(Unit) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (failed) {
                Column(Modifier.fillMaxWidth().padding(rdp(24)), horizontalAlignment = Alignment.CenterHorizontally) {
                    XaniText(copy.t(Copy.NotifyLoadFailed), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
                    XaniText(
                        copy.t(Copy.NotifyRetry),
                        Type.LabelBold,
                        color = Dark.Primary,
                        fontSizeOverride = 14f,
                        modifier = Modifier.padding(top = rdp(10)).pressable { reload++ },
                    )
                }
            } else {
                Column(
                    Modifier
                        .padding(start = rdp(16), end = rdp(16), top = rdp(16))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Color.White),
                ) {
                    NotifyCategories.forEachIndexed { i, (key, label) ->
                        val on = prefs[key] ?: true
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(pressedScale = 1f) { if (loaded) flip(key, !on) }
                                .padding(horizontal = rdp(18), vertical = rdp(13)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XaniText(copy.t(label), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.weight(1f))
                            NotifySwitch(on = on, enabled = loaded)
                        }
                        if (i < NotifyCategories.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
                        }
                    }
                }
            }
        }
    }
}

private fun parseNotifyPrefs(json: JSONObject): Map<String, Boolean> {
    val items = json.optJSONArray("items") ?: return emptyMap()
    val out = LinkedHashMap<String, Boolean>(items.length())
    for (i in 0 until items.length()) {
        val o = items.optJSONObject(i) ?: continue
        out[o.optString("category")] = o.optBoolean("enabled", true)
    }
    return out
}

@Composable
private fun NotifySwitch(on: Boolean, enabled: Boolean) {
    Box(
        Modifier
            .width(rdp(44))
            .height(rdp(26))
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color(0xFFE5E5EA)
                    on -> Dark.Primary
                    else -> Color(0xFFD1D1D6)
                },
            )
            .padding(rdp(3)),
    ) {
        Box(
            Modifier
                .size(rdp(20))
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
