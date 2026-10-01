import Foundation

/// The one shape every API answer takes, success or failure:
///
/// ```
/// { "statusCode": 200, "error": false, "message": "Signed in.", "data": { … } }
/// { "statusCode": 409, "error": true,  "message": "That username is taken.",
///   "code": "duplicate_username", "data": {} }
/// ```
///
/// Callers branch on `code`, never on `message`: codes are a contract, messages are
/// copy that product can reword or localise at any time.
///
/// Transport failures are folded into the same type with a synthetic code, so a
/// caller handles "server said no" and "there is no network" through one path rather
/// than a `do`/`catch` around every call site.
struct ApiResponse<T> {
    let statusCode: Int
    let error: Bool
    let message: String
    let code: String
    let data: T?

    var ok: Bool { !error }

    /// Data on success, nil otherwise.
    var value: T? { error ? nil : data }

    /// True when the caller should offer a "check your connection" affordance.
    var isTransport: Bool { code == Code.network || code == Code.timeout }

    /// True when the session is finished and the user must sign in again.
    var isSessionExpired: Bool { code == Code.sessionExpired }

    enum Code {
        static let ok = "ok"
        static let network = "network"
        static let timeout = "timeout"
        static let malformed = "malformed_response"

        /// Emitted when refresh failed: the 72-hour window is over.
        static let sessionExpired = "session_expired"
    }

    static func success(_ statusCode: Int, _ message: String, _ data: T?) -> ApiResponse<T> {
        ApiResponse(statusCode: statusCode, error: false, message: message, code: Code.ok, data: data)
    }

    static func failure(_ statusCode: Int, _ code: String, _ message: String) -> ApiResponse<T> {
        ApiResponse(statusCode: statusCode, error: true, message: message, code: code, data: nil)
    }

    static var offline: ApiResponse<T> {
        .failure(0, Code.network, "No connection. Check your internet and try again.")
    }

    static var timedOut: ApiResponse<T> {
        .failure(0, Code.timeout, "The server took too long to respond. Try again.")
    }

    static var sessionExpired: ApiResponse<T> {
        .failure(401, Code.sessionExpired, "Your session ended. Sign in to continue.")
    }

    func map<R>(_ transform: (T) -> R) -> ApiResponse<R> {
        ApiResponse<R>(
            statusCode: statusCode,
            error: error,
            message: message,
            code: code,
            data: data.map(transform)
        )
    }
}

/// The decodable half of the envelope. `data` is decoded separately so a payload the
/// client cannot read degrades into a `malformed_response` instead of throwing past
/// the call site.
struct ApiEnvelope: Decodable {
    let statusCode: Int?
    let error: Bool?
    let message: String?
    let code: String?
}

/// Endpoints whose payload the caller does not need.
struct EmptyPayload: Decodable {}
