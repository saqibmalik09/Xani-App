package app.xani.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.xani.core.isolate.runFeature
import app.xani.core.net.MediaConfig
import app.xani.i18n.Copy
import app.xani.ui.brand.Brand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Builds the public live URL (same shape as a profile share: dashboard web
 * base + path), then either copies it or opens the system share sheet.
 * Refreshes config once when the base is still empty so a cold start that
 * has not yet read `/v1/config` does not share a blank address.
 */
internal object LiveShare {
    suspend fun ensureLink(roomId: Long): String {
        if (roomId <= 0L) return ""
        var u = MediaConfig.liveLink(roomId)
        if (u.isEmpty()) {
            runFeature(false) { MediaConfig.refresh() }
            u = MediaConfig.liveLink(roomId)
        }
        return u
    }

    fun copy(
        context: Context,
        scope: CoroutineScope,
        copy: Copy,
        roomId: Long,
        onDone: (() -> Unit)? = null,
    ) {
        scope.launch {
            val u = ensureLink(roomId)
            if (u.isEmpty()) {
                Toast.makeText(context, copy.t(Copy.LiveShareLinkPending), Toast.LENGTH_SHORT).show()
            } else {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("live", u))
                Toast.makeText(context, copy.t(Copy.LiveShareLinkCopied), Toast.LENGTH_SHORT).show()
            }
            onDone?.invoke()
        }
    }

    fun share(
        context: Context,
        scope: CoroutineScope,
        copy: Copy,
        brand: Brand,
        roomId: Long,
        onDone: (() -> Unit)? = null,
    ) {
        scope.launch {
            val u = ensureLink(roomId)
            if (u.isEmpty()) {
                Toast.makeText(context, copy.t(Copy.LiveShareLinkPending), Toast.LENGTH_SHORT).show()
                onDone?.invoke()
                return@launch
            }
            // Copy first so a share cancel still leaves the link on the clipboard,
            // matching the "link copied" expectation from the three-dot action.
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("live", u))
            Toast.makeText(context, copy.t(Copy.LiveShareLinkCopied), Toast.LENGTH_SHORT).show()
            val body = "${copy.t(Copy.LiveShareBody, "app" to brand.name)}\n$u"
            runFeature(Unit) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, copy.t(Copy.LiveShareBody, "app" to brand.name))
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
            onDone?.invoke()
        }
    }
}
