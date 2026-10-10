package app.xani.ui.brand

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.xani.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything the UI knows about the brand. No screen holds a literal product name.
 * The dashboard (Settings → Branding) is the source of truth: [BrandStore] carries
 * its name, tagline and logo from `/v1/config`; the build's values are only the
 * first-launch fallback before the server has answered once.
 */
@Immutable
data class Brand(
    /** Shown everywhere the product is named, including the splash wordmark. */
    val name: String,
    val tagline: String,
    /** Absolute URL of the dashboard logo; empty = draw the built-in mark. */
    val logoUrl: String = "",
    /**
     * Lowercase, url-safe handle for links and file names. Comes from the build
     * and never from the dashboard: it is baked into shared links and the deep
     * link scheme, and a rename must not break links already in the wild.
     */
    val slug: String = "app",
) {
    companion object {
        fun fromBuildConfig(): Brand {
            val name = BuildConfig.APP_NAME.ifBlank { "App" }
            return Brand(
                name = name,
                tagline = BuildConfig.APP_TAGLINE,
                slug = BuildConfig.APP_WORDMARK.ifBlank { name }.lowercase(),
            )
        }
    }
}

/**
 * The live brand. Seeded from the build, then from the cached config on attach,
 * then from every config refresh — so a dashboard rename or new logo reaches
 * the splash, sign-in screens and notifications without a release.
 */
object BrandStore {
    private val build = Brand.fromBuildConfig()
    private val _brand = MutableStateFlow(build)
    val brand: StateFlow<Brand> = _brand.asStateFlow()

    private val _fromServer = MutableStateFlow(false)

    /**
     * False until the dashboard brand has been read at least once — on a fresh
     * install that is a network round trip, and the splash must not flash the
     * build's own name and tagline in the meantime.
     */
    val fromServer: StateFlow<Boolean> = _fromServer.asStateFlow()

    /** The current brand, for code outside composition (notifications, services). */
    val current: Brand get() = _brand.value

    fun learn(name: String, tagline: String, logoUrl: String) {
        val n = name.trim()
        val next = Brand(
            // Spelled exactly as the dashboard typed it: a white-label owner's
            // capitals are part of their brand, and the wordmark on the splash
            // is that same name, not a second spelling of it.
            name = n.ifBlank { build.name },
            tagline = tagline.trim(),
            logoUrl = logoUrl,
            slug = build.slug,
        )
        if (next != _brand.value) _brand.value = next
        _fromServer.value = true
    }

    /**
     * Waits for the dashboard brand, up to [timeoutMs]. Returns either way: a
     * splash that cannot reach the server still has to open the app, with the
     * build's brand, rather than hang on a blank screen.
     */
    suspend fun awaitFromServer(timeoutMs: Long) {
        if (_fromServer.value) return
        withTimeoutOrNull(timeoutMs) { _fromServer.first { it } }
    }
}

val LocalBrand = staticCompositionLocalOf { Brand("App", "") }

object LaunchTiming {
    /** Branding time is dead time; the build can shorten it to zero. */
    val splashMinMillis: Long get() = BuildConfig.SPLASH_MIN_MS
}
