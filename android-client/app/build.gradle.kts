plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// FCM: apply only when the Firebase config file is present so a clean checkout
// still builds. Drop `app/google-services.json` from the Firebase console to
// turn background call rings on.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * Environment first, then Gradle property, then default. Read through `providers` so
 * Gradle tracks them as configuration-cache inputs: a plain `System.getenv` would
 * either break the cache or silently serve a stale brand.
 */
fun brand(key: String, fallback: String): String =
    providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: fallback

val rtcEnabled = brand("XANI_RTC", "0") == "1"
val googleWebClientIdDebug = brand("GOOGLE_WEB_CLIENT_ID", "")
val googleWebClientIdRelease = brand("GOOGLE_WEB_CLIENT_ID_RELEASE", googleWebClientIdDebug)

android {
    namespace = "app.xani"
    compileSdk = 37

    defaultConfig {
        applicationId = brand("APP_ID", "app.xani")
        minSdk = 26
        targetSdk = 37
        versionCode = 30
        versionName = "30.0.0"

        val appName = brand("APP_NAME", "App")
        resValue("string", "app_name", appName)
        buildConfigField("String", "APP_NAME", "\"$appName\"")
        buildConfigField("String", "APP_WORDMARK", "\"${brand("APP_WORDMARK", appName.lowercase())}\"")
        buildConfigField("String", "APP_TAGLINE", "\"${brand("APP_TAGLINE", "")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${brand("API_BASE_URL", "")}\"")
        buildConfigField("long", "SPLASH_MIN_MS", "${brand("SPLASH_MIN_MS", "900").toLong()}L")
        // Overridden per buildType: debug package vs release app.xani.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientIdDebug\"")

        // Deep links (https://host/join/CODE and wordmark://join/CODE). The
        // host comes from the brand web URL, falling back to the API host.
        val webHost = brand("WEB_BASE_URL", brand("API_BASE_URL", ""))
            .removePrefix("https://").removePrefix("http://").substringBefore("/")
        manifestPlaceholders["deepLinkHost"] = webHost.ifEmpty { "app.invalid" }
        manifestPlaceholders["deepLinkScheme"] = brand("APP_WORDMARK", appName.lowercase()).lowercase()
    }

    // Media stack for calls/live. `XANI_RTC=1` (env or gradle.properties) compiles
    // the libwebrtc + SFU client adapter from src/rtc; otherwise src/rtc-stub reports
    // calling as unavailable and the build needs no native artifacts.
    sourceSets["main"].kotlin.srcDir(if (rtcEnabled) "src/rtc/java" else "src/rtc-stub/java")

    buildTypes {
        debug {
            // Lets a debug build sit alongside a store build on the same device, which
            // is the usual cause of "the APK won't install".
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = false
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientIdDebug\"")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Installable release APK for sideload/share. Swap to a Play upload
            // keystore before store submission (keystore.properties + signingConfigs.release).
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientIdRelease\"")
        }

        /**
         * Measure here, never on debug. Debug builds are unoptimised and unshrunk, so
         * their startup and scroll numbers are meaningless; this type is release-shaped
         * but installable with debug signing.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applicationIdSuffix = ".benchmark"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9 disables generated resource values by default; the launcher label is
        // the one place a resource has to carry the env-supplied app name.
        resValues = true
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    // Strips the dependency-metadata blob that is otherwise signed into the artifact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(bom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")

    // Google Sign-In (Credential Manager). Only used when GOOGLE_WEB_CLIENT_ID is set.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Lottie for gift animations uploaded as JSON (SVGA has its own player).
    implementation("com.airbnb.android:lottie-compose:6.5.2")

    // Installs the merged baseline profile (ours from src/main/baseline-prof.txt plus
    // the ones shipped inside the Compose artifacts) on API 26-30, where the platform
    // will not do it itself. Without this, the first run of every install interprets
    // and JITs the entire startup path.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // FCM for killed/background call invites. Needs app/google-services.json to
    // initialise at runtime; without it PushRegistrar is a quiet no-op.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    if (rtcEnabled) {
        // Self-hosted SFU (infra/rtc) + libwebrtc via LiveKit's client AAR.
        // Optional local AARs in app/libs still win for a custom webrtc build.
        implementation("io.livekit:livekit-android:2.28.1")
        implementation(fileTree("libs") { include("*.aar") })
    }

    debugImplementation(bom)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}
