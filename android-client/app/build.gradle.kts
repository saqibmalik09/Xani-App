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
        ?: syncedBrand[key]?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: fallback

/**
 * What `sync-brand.ps1` last pulled from the dashboard (Settings → Branding):
 * the launcher label. The home-screen name and icon live in the APK manifest,
 * so they follow the dashboard only on a rebuild; everything inside the app
 * follows it live through `/v1/config`.
 */
val syncedBrand: Map<String, String> = run {
    val text = providers.fileContents(layout.projectDirectory.file("brand/brand.properties"))
        .asText.orNull.orEmpty()
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val i = line.indexOf('=')
            line.substring(0, i).trim() to line.substring(i + 1).trim()
                .replace("\\=", "=")
                .replace("\\:", ":")
                .replace("\\\\", "\\")
        }
}

/**
 * The version this build reports and shows: `-PAPP_VERSION=9.1.2`, or
 * `APP_VERSION` in the environment, falling back to gradle.properties.
 *
 * It is one string with three jobs, which is why it is set in one place: the
 * drawer prints it, `X-App-Version` reports it, and the dashboard's App
 * versions table gates on it. A build whose reported version is not the one
 * typed on the dashboard cannot be turned off.
 */
val appVersion = brand("APP_VERSION", "1.0.0").trim()

/**
 * One ascending integer from the version, three digits per segment: 9.1.2 is
 * 9_001_002. Installs are ordered on this, so it has to be monotonic — which it
 * is as long as minor and patch stay under 1000, checked below.
 */
fun versionCodeOf(name: String): Int {
    val p = name.split('.')
    return p[0].toInt() * 1_000_000 + p[1].toInt() * 1_000 + p[2].toInt()
}

// `major.minor.patch` only. Checked here rather than trusted, because a loose
// string would produce a version code that silently goes backwards and an APK
// that then refuses to install over the last one. `matches` spans the whole
// string, so no anchors are needed.
if (!Regex("""\d{1,4}\.\d{1,3}\.\d{1,3}""").matches(appVersion)) {
    throw GradleException(
        "APP_VERSION must be major.minor.patch (for example 9.1.2), got \"$appVersion\". " +
            "Pass it as -PAPP_VERSION=9.1.2 or set it in gradle.properties.",
    )
}

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
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion

        val appName = brand("APP_NAME", "App")
        resValue("string", "app_name", appName)
        buildConfigField("String", "APP_NAME", "\"$appName\"")
        buildConfigField("String", "APP_WORDMARK", "\"${brand("APP_WORDMARK", appName.lowercase())}\"")
        buildConfigField("String", "APP_TAGLINE", "\"${brand("APP_TAGLINE", "")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${brand("API_BASE_URL", "")}\"")
        buildConfigField("long", "SPLASH_MIN_MS", "${brand("SPLASH_MIN_MS", "900").toLong()}L")
        // Overridden per buildType: debug package vs release app.xani.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientIdDebug\"")

        // Custom-scheme deep links (scheme://join/…, scheme://u/…). Domain for
        // https share links comes from /v1/config (dashboard Website URL) — never
        // baked here, so a domain move needs no APK rebuild.
        // Optional DEEP_LINK_HOST enables verified https App Links for one host.
        val deepHost = brand("DEEP_LINK_HOST", "")
            .removePrefix("https://").removePrefix("http://").substringBefore("/")
        manifestPlaceholders["deepLinkHost"] = deepHost.ifEmpty { "app.invalid" }
        manifestPlaceholders["deepLinkScheme"] = brand("APP_WORDMARK", appName.lowercase()).lowercase()
        buildConfigField("String", "APP_LINK_SCHEME", "\"${brand("APP_WORDMARK", appName.lowercase()).lowercase()}\"")
    }

    // Media stack for calls/live. `XANI_RTC=1` (env or gradle.properties) compiles
    // the libwebrtc + SFU client adapter from src/rtc; otherwise src/rtc-stub reports
    // calling as unavailable and the build needs no native artifacts.
    sourceSets["main"].kotlin.srcDir(if (rtcEnabled) "src/rtc/java" else "src/rtc-stub/java")

    // Dashboard launcher icon from sync-brand.ps1. A build-type source set wins
    // the resource merge over main, so the synced foreground replaces the
    // shipped one without a duplicate-resource clash; absent = shipped icon.
    listOf("debug", "release", "benchmark").forEach { sourceSets.maybeCreate(it).res.srcDir("brand/res") }

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

    // Bundled ML Kit: face mesh + selfie mask for Xani Lens. No download, works
    // offline. The GPU pipeline is a no-op without these, never a crash.
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

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
