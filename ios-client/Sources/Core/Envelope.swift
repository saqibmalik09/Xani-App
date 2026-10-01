import Foundation

/// `{ ok, code, message, data, cursor }` — the only response shape the UI consumes.
struct Envelope<T> {
    let ok: Bool
    let code: String
    let message: String
    let data: T?
    let cursor: String?

    static func ok(_ data: T, cursor: String? = nil) -> Envelope<T> {
        Envelope(ok: true, code: "ok", message: "", data: data, cursor: cursor)
    }

    static func empty(cursor: String? = nil) -> Envelope<T> {
        Envelope(ok: true, code: "empty", message: "", data: nil, cursor: cursor)
    }

    static func fail(_ code: String, _ message: String) -> Envelope<T> {
        Envelope(ok: false, code: code, message: message, data: nil, cursor: nil)
    }
}

enum FeatureFailures {
    private static var count: Int = 0

    static func record() {
        count += 1
    }
}

func runFeature<T>(_ fallback: T, _ block: () throws -> T) -> T {
    do {
        return try block()
    } catch {
        FeatureFailures.record()
        return fallback
    }
}
