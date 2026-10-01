# iOS client

Swift + SwiftUI. Auth surfaces only (splash, onboarding, auth choice, sign up, log
in, OTP), rendered from the same Figma Make source and the same design tokens as the
Android client.

## Build

From `client-apps` on a Mac, with the iPhone plugged in and trusted:

```bash
chmod +x ios.sh   # once
./ios.sh
```

Or:

```bash
brew install xcodegen        # once
cd client-apps/ios-client
xcodegen                    # writes App.xcodeproj
open App.xcodeproj
```

The `.xcodeproj` is generated and gitignored on purpose. A committed `project.pbxproj`
is the single worst merge-conflict source on an iOS team, and generating it makes the
target settings reviewable in `project.yml`.

If you would rather not install XcodeGen: create an iOS App target in Xcode, drag in
`Sources/` and `Resources/`, set `Config/Brand.xcconfig` as the target config file,
and set the deployment target to iOS 15.0.

## Dependency policy

**Zero packages.** No SPM, no CocoaPods, no Carthage, no embedded frameworks.

This is a deliberate constraint, not laziness:

- Nothing third-party can break on a new iOS release, so an OS update is never a
  release blocker.
- No dynamic framework loading at launch, which is measurable startup time.
- No supply-chain surface in an app that will hold payment and identity flows.
- The binary stays small, so App Store cellular-download limits are a non-issue.

Consequences that were handled rather than papered over:

| Need | Bought in a library elsewhere | What we do |
| --- | --- | --- |
| SVG path rendering | SVGKit / Macaw | `Sources/Vector/PathData.swift`, a ~200-line parser feeding SwiftUI `Canvas` |
| Logo | PNG/PDF asset set | `BrandMark`, drawn from geometry, crisp at any size, no decode |
| Date wheel | Third-party pickers | System `DatePicker` with `.wheel` style |
| Navigation | Coordinators | One `AuthRoute` enum in `@SceneStorage` |

## Version support

Deployment target is **iOS 15.0**, which covers effectively the entire active install
base while allowing `@FocusState`, `Canvas`, `foregroundStyle` and structured
concurrency.

Nothing in the client is gated on a newer SDK. Two APIs that only exist later are
feature-detected rather than required:

- `scrollDismissesKeyboard` (iOS 16) — `InteractiveKeyboardDismiss` in `AuthScaffold`
- `Path` boolean operators (iOS 16) — `BrandMark` fills one non-zero path instead

No deprecated API is used, so the project also builds clean against future SDKs:
`foregroundStyle` not `foregroundColor`, `kerning` not `tracking`, `Task.sleep` not
`Timer.publish`, no `NavigationView`.

## Brand configuration

Nothing user-visible is hardcoded. `Config/Brand.xcconfig` supplies `APP_NAME`,
`APP_WORDMARK`, `APP_TAGLINE`, `APP_BUNDLE_ID` and `SPLASH_MIN_MS`; those flow into
`Info.plist` and are read once by `Brand.current`.

Rebranding is that file plus `Sources/Brand/BrandMark.swift`. See
`../brand.env` for the canonical cross-platform values.

`xcconfig` treats `//` as a comment, so URLs must be escaped:
`API_BASE_URL = https:$()/$()/api.example.com`.

## Performance notes

- **Launch**: `UILaunchScreen` is a solid `LaunchBackground` colour identical to the
  first rendered frame, so the handover from the system launch image is invisible.
  Nothing runs between process start and first frame — no network, no disk, no
  analytics init.
- **Splash duration** is `SPLASH_MIN_MS` (900 by default) and can be set to 0. It is
  the only intentional delay in the app.
- **Animations** only touch opacity, offset and scale, all of which stay on the
  render server and never trigger a layout pass.
- **Icons** are single-pass `Canvas` draws. No asset catalogue lookups, no image
  decode, crisp at every scale.
- **Responsive scale** is computed once at the root and read from the environment;
  screens never nest a second `GeometryReader` to re-derive it.
- Measure on a **Release** build. Debug is `-Onone` with no LTO, and its numbers are
  meaningless.

## Not implemented yet

Feed, Inbox, Live, Profile, Create, and every network call. `AuthFlow` ends at
`AuthenticatedPlaceholderView`.
