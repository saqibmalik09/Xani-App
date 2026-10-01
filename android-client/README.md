# Android client

Kotlin + Jetpack Compose. Auth surfaces only (splash, onboarding, auth choice, sign
up, log in, OTP), built from the Figma Make source in `app-ui/Prepare Figma prompts/`
and the shared tokens in `../design-tokens.json`.

## Build

From `client-apps`:

```powershell
.\android.bat
```

That installs a debug APK on the USB phone. `JAVA_HOME` defaults to Android Studio's JBR.

Or, from this folder:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:installDebug
```

Toolchain: Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10, compileSdk 37, minSdk 26.

`JAVA_HOME` must point at a JDK the Gradle version accepts. The system JDK on this
machine is 26, which Gradle rejects; Android Studio's bundled JBR works.

Verified locally: `assembleDebug` and `assembleRelease` both succeed, with R8,
resource shrinking and baseline-profile compilation all running clean.

| Artifact | Size |
| --- | --- |
| `app-release-unsigned.apk` | **1.06 MB** |
| `app-debug.apk` | 8.6 MB |

That 8× gap is the whole reason **you must never judge performance from a debug
build**. Debug is unoptimised, unshrunk, and carries the Compose tooling and layout
inspector hooks. Use the `benchmark` build type, which is release-shaped (minified,
non-debuggable, full R8) but signed with the debug key so it installs like a dev
build.

## Brand configuration

Nothing user-visible is hardcoded. Values resolve **environment variable → Gradle
property → default**:

| Key | Reaches the UI as |
| --- | --- |
| `APP_NAME` | launcher label, `Brand.name` |
| `APP_WORDMARK` | the lowercase logotype on splash and lockup |
| `APP_TAGLINE` | splash tagline (blank hides the line) |
| `APP_ID` | `applicationId` |
| `SPLASH_MIN_MS` | how long the splash is held; `0` skips it |
| `API_BASE_URL` | `BuildConfig.API_BASE_URL` |

Defaults live in `gradle.properties`; the canonical cross-platform set is
`../brand.env`. Confirmed working end to end — building with `APP_NAME=Nova` produces
a launcher labelled Nova with `applicationId app.nova`, no source edits.

Rebranding is those values plus one file, `ui/brand/BrandMark.kt`. Every screen reads
`LocalBrand`, so no screen signature changes when the brand does.

The Kotlin package is still `app.xani`. That is an internal namespace with no brand
meaning and it is invisible to users; the store identity comes from `APP_ID`. Renaming
it is a mechanical refactor worth doing in one atomic pass — say the word.

## Dependency policy

Five artifacts, all first-party AndroidX:

```
compose-ui, compose-ui-graphics, compose-foundation, activity-compose, core-ktx,
profileinstaller
```

Deliberately **not** used, with what replaced each:

| Not used | Why | Instead |
| --- | --- | --- |
| Material3 | 400 KB of a design system we override anyway | tokens from Figma, drawn directly |
| material-icons-extended | ~4 MB of vector XML for ~10 glyphs | `ui/vector` + path data shared with iOS |
| navigation-compose | a linear 6-step flow does not need a graph | one saveable `AuthRoute` |
| Coil / Glide | there are no remote images in auth | — |
| Accompanist | its features are all in the platform now | `enableEdgeToEdge`, `WindowInsets` |
| Hilt / Dagger | nothing to inject yet | constructor parameters |

`profileinstaller` is the one addition, and it earns its ~40 KB: see below.

## Startup: what was wrong and what changed

Reviewed as if 100M users were about to install this. Findings, in order of how much
they hurt:

1. **The splash burned 2.6 seconds of dead time.** This was the single largest
   contributor to "the app takes forever to reach home". Now 900 ms, driven by
   `SPLASH_MIN_MS`, and settable to 0. The animation phases finish at 380 ms, so the
   remainder is pure brand hold — the only intentional delay in the app.
2. **No baseline profile**, so the first run after every install interpreted and
   JIT-ed the entire startup path. Added `src/main/baseline-prof.txt` plus
   `profileinstaller`, which is what installs profiles on API 26–30. Typically 20–30%
   off cold start. The committed rules are wildcards over our own packages; replace
   them with a measured profile from a Macrobenchmark module when a device farm
   exists.
3. **`reportFullyDrawn` was never called.** Android vitals was inferring
   time-to-fully-drawn from the first frame, which is the splash, so our own metrics
   would have looked worse than reality. `MainActivity` now reports once, when the
   first interactive screen composes.
4. **`ui-tooling-preview` shipped in release.** Moved to `debugImplementation`.
5. **An empty `Application` subclass** was declared in the manifest, costing a class
   load and a reflective instantiation before the first activity. Deleted.
6. **Dependency metadata was signed into the artifact.** `dependenciesInfo` off,
   plus `packaging.resources.excludes` for Kotlin tooling metadata and
   `DebugProbesKt.bin`.
7. **App Bundle splits** enabled for language, density and ABI, so a user downloads
   only their slice.
8. **Debug and release could not coexist** on one device, which reads as "the APK
   won't install". Debug now carries `.debug` on the application ID.
9. **Illustration labels were scaled twice** — `rdp()` once and again inside
   `fontSizeOverride` — so the "LIVE" badge and the ¥ glyph were up to 39% oversized
   on large screens. Illustrations now take baseline numbers and scale once.
10. **Inner-loop build time.** Configuration cache is on (47 s → 2 s on a repeat
    build), which required reading brand values through `providers` rather than
    `System.getenv` so Gradle can track them as cache inputs.

Already correct and kept: `windowBackground` matches the first Compose frame exactly
so the launch window hands over invisibly; no `largeHeap`; hardware acceleration on;
`windowAnimationStyle` null.

## Responsiveness

The Figma frame is 390dp. Every dimension is a baseline number multiplied by
`LocalUiScale`, clamped to 0.82–1.18, and content is capped at 460dp wide so tablets
and unfolded foldables keep phone-like line lengths instead of stretching. The scale
is derived once in `XaniTheme` from the configuration — not from a
`BoxWithConstraints`, which would force a subcomposition of the whole app at launch.

## Performance rules held to

- No blocking work on the UI thread: no IO, no JSON, no image decode, no `sleep`.
- Animations only touch `alpha`, `translation` and `scale` via `graphicsLayer`, so
  they stay on the GPU and never trigger recomposition or layout.
- Icons and the logo are geometry, drawn in one `Canvas` pass. No drawable decode, no
  density buckets, crisp everywhere.
- System fonts, so no font file is read before first frame.
- No fake loading states. The login screen calls through immediately on valid input
  instead of spinning for effect.

## Not implemented yet

Feed, Inbox, Live, Profile, Create, and every network call. `AuthFlow` ends at
`AuthenticatedPlaceholder`.
