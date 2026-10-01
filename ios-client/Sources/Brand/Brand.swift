import SwiftUI

/// Everything the UI knows about the brand, resolved from the bundle at launch and
/// fed by `Config/Brand.xcconfig`. No view holds a literal product name, so a
/// rebrand is a config change plus `BrandMark`.
struct Brand {
    let name: String
    let wordmark: String
    let tagline: String

    static let current: Brand = {
        let name = Self.string("CFBundleDisplayName")
            ?? Self.string("CFBundleName")
            ?? "App"
        return Brand(
            name: name,
            wordmark: Self.string("BrandWordmark") ?? name.lowercased(),
            tagline: Self.string("BrandTagline") ?? ""
        )
    }()

    private static func string(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.trimmingCharacters(in: .whitespaces).isEmpty
        else { return nil }
        return value
    }
}

enum LaunchTiming {
    /// Branding time is dead time; the build can shorten it to zero.
    static let splashMinimumMillis: Int = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "SplashMinimumMilliseconds")
        switch raw {
        case let value as Int: return max(0, value)
        case let value as String: return max(0, Int(value) ?? 900)
        default: return 900
        }
    }()
}

/// `Task.sleep(for:)` needs iOS 16, so the flow sleeps in nanoseconds instead.
func sleepMillis(_ millis: Int) async {
    guard millis > 0 else { return }
    try? await Task.sleep(nanoseconds: UInt64(millis) * 1_000_000)
}

private struct BrandKey: EnvironmentKey {
    static let defaultValue: Brand = .current
}

extension EnvironmentValues {
    var brand: Brand {
        get { self[BrandKey.self] }
        set { self[BrandKey.self] = newValue }
    }
}
