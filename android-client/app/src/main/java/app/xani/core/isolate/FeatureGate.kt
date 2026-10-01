package app.xani.core.isolate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.xani.i18n.Copy
import app.xani.i18n.rememberCopy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.util.concurrent.atomic.AtomicInteger

/**
 * One failing feature must not take down the process. Compose has no error
 * boundary, so features report through [FeatureHost.fail] and wrap every
 * non-composable call in [runFeature]. The tab bar stays up either way.
 */
@Stable
class FeatureHost(
    private val onFail: () -> Unit,
) {
    fun fail() = onFail()

    fun <T> run(fallback: T, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        FeatureFailures.record(t)
        onFail()
        fallback
    }
}

val LocalFeatureHost = compositionLocalOf { FeatureHost { } }

object FeatureFailures {
    private val count = AtomicInteger(0)

    fun record(t: Throwable) {
        count.incrementAndGet()
        t.javaClass.simpleName
    }

    val total: Int get() = count.get()
}

@Composable
fun FeatureGate(
    name: String,
    light: Boolean = false,
    content: @Composable () -> Unit,
) {
    var failed by rememberSaveable(name) { mutableStateOf(false) }
    val host = remember(name) { FeatureHost { failed = true } }
    if (failed) {
        FeatureFallback(light = light)
        return
    }
    CompositionLocalProvider(LocalFeatureHost provides host) {
        content()
    }
}

@Composable
private fun FeatureFallback(light: Boolean) {
    val copy = rememberCopy()
    val bg = if (light) Colors.Background else Dark.Background
    val fg = if (light) Colors.Sub else Dark.MutedFg
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = rdp(32)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            XaniText(
                text = copy.t(Copy.FeatureUnavailable),
                style = Type.Label,
                color = fg,
                align = TextAlign.Center,
            )
        }
    }
}

inline fun <T> runFeature(fallback: T, block: () -> T): T = try {
    block()
} catch (t: Throwable) {
    FeatureFailures.record(t)
    fallback
}
