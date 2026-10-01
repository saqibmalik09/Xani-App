package app.xani.ui.brand

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.xani.BuildConfig

/**
 * Everything the UI knows about the brand. No screen holds a literal product name,
 * so rebranding is a build-time value change plus [BrandMark].
 */
@Immutable
data class Brand(
    val name: String,
    val wordmark: String,
    val tagline: String,
) {
    companion object {
        fun fromBuildConfig(): Brand {
            val name = BuildConfig.APP_NAME.ifBlank { "App" }
            return Brand(
                name = name,
                wordmark = BuildConfig.APP_WORDMARK.ifBlank { name.lowercase() },
                tagline = BuildConfig.APP_TAGLINE,
            )
        }
    }
}

val LocalBrand = staticCompositionLocalOf { Brand("App", "app", "") }

object LaunchTiming {
    /** Branding time is dead time; the build can shorten it to zero. */
    val splashMinMillis: Long get() = BuildConfig.SPLASH_MIN_MS
}
