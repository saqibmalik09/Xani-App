plugins {
    // AGP 9 has built-in Kotlin support and registers the `kotlin` extension itself,
    // so `org.jetbrains.kotlin.android` must not be applied alongside it.
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
