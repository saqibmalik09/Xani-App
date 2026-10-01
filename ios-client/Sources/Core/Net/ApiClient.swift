import Foundation

/// Supplies a valid access token and knows how to renew one. Implemented by
/// `SessionManager`; an interface so the transport has no knowledge of where sessions
/// live and can be exercised without one.
/// `Sendable` because the transport is an actor and holds a reference to it; the
/// conformer is a `@MainActor` class, which satisfies this implicitly.
protocol TokenSource: AnyObject, Sendable {
    /// A token believed valid, refreshing proactively if it is about to expire.
    func accessToken() async -> String?

    /// Called after a 401. Returns a new token, or nil when the refresh window is
    /// over. Implementations must collapse concurrent callers into one network call.
    func renew(staleToken: String?) async -> String?
}

/// The HTTP transport. One instance for the whole app.
///
/// Responsibilities, and deliberately nothing else:
///  - build the request, attach the bearer token
///  - decode the fixed envelope into `ApiResponse`
///  - turn transport failures into envelopes rather than thrown errors
///  - on 401, renew once through `TokenSource` and replay the request
actor ApiClient {
    private let baseURL: String
    private let userAgent: String
    private let session: URLSession
    /// Streams need their own session: the request session's 20s idle timeout
    /// would cut a healthy stream between two server heartbeats.
    private let streamSession: URLSession
    /// Part uploads carry up to 16 MB; on a slow cell link one part legitimately
    /// takes minutes, which the request session would call a timeout.
    private let uploadSession: URLSession
    private let decoder: JSONDecoder

    private weak var tokens: TokenSource?

    init(baseURL: String, userAgent: String) {
        self.baseURL = baseURL.hasSuffix("/") ? String(baseURL.dropLast()) : baseURL
        self.userAgent = userAgent

        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 40
        configuration.waitsForConnectivity = false
        // The envelope is the cache story: auth responses must never be replayed from
        // a URL cache, and the two cacheable reads set Cache-Control themselves.
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpAdditionalHeaders = ["Accept": "application/json"]
        self.session = URLSession(configuration: configuration)

        let streaming = URLSessionConfiguration.default
        // Longer than the server heartbeat; no resource limit, the server rotates
        // the connection itself.
        streaming.timeoutIntervalForRequest = 70
        streaming.timeoutIntervalForResource = 0
        streaming.requestCachePolicy = .reloadIgnoringLocalCacheData
        streaming.httpAdditionalHeaders = ["Accept": "text/event-stream"]
        self.streamSession = URLSession(configuration: streaming)

        let uploading = URLSessionConfiguration.default
        uploading.timeoutIntervalForRequest = 120
        uploading.timeoutIntervalForResource = 15 * 60
        uploading.waitsForConnectivity = false
        uploading.requestCachePolicy = .reloadIgnoringLocalCacheData
        uploading.httpAdditionalHeaders = ["Accept": "application/json"]
        self.uploadSession = URLSession(configuration: uploading)

        let decoder = JSONDecoder()
        // The server sends snake_case payload keys and camelCase envelope keys.
        // This strategy leaves keys without underscores untouched, so `statusCode`
        // and `full_name` both decode with one decoder.
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        self.decoder = decoder
    }

    func bind(tokens: TokenSource) {
        self.tokens = tokens
    }

    // MARK: - Requests

    func get<T: Decodable>(
        _ path: String,
        query: [String: String] = [:],
        authenticated: Bool = false,
        as type: T.Type
    ) async -> ApiResponse<T> {
        await send(method: "GET", path: path, query: query, body: nil,
                   authenticated: authenticated, retries: 1, as: type)
    }

    func post<T: Decodable>(
        _ path: String,
        body: [String: Any],
        authenticated: Bool = false,
        as type: T.Type
    ) async -> ApiResponse<T> {
        await send(method: "POST", path: path, query: [:], body: body,
                   authenticated: authenticated, retries: 0, as: type)
    }

    @discardableResult
    func post(
        _ path: String,
        body: [String: Any],
        authenticated: Bool = false
    ) async -> ApiResponse<EmptyPayload> {
        await post(path, body: body, authenticated: authenticated, as: EmptyPayload.self)
    }

    /// Idempotent by definition, so a transport failure is safe to replay once.
    func delete<T: Decodable>(
        _ path: String,
        authenticated: Bool = false,
        as type: T.Type
    ) async -> ApiResponse<T> {
        await send(method: "DELETE", path: path, query: [:], body: nil,
                   authenticated: authenticated, retries: 1, as: type)
    }

    /// PUTs one raw part of a resumable upload. The server verifies the
    /// checksum before it stores a byte, so a damaged part is refused, not kept.
    /// Never retried here: the upload engine owns retry policy per part.
    func putBytes<T: Decodable>(
        _ path: String,
        body: Data,
        sha256Hex: String = "",
        contentType: String = "application/octet-stream",
        as type: T.Type
    ) async -> ApiResponse<T> {
        guard !baseURL.isEmpty, let url = buildURL(path: path, query: [:]) else {
            return .failure(0, "not_configured", "This build has no server configured.")
        }
        var bearer = await tokens?.accessToken()
        guard bearer != nil else { return .sessionExpired }

        var attempt = 0
        while true {
            var request = URLRequest(url: url)
            request.httpMethod = "PUT"
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
            // Only the resumable pipeline checksums its parts; an avatar is one
            // shot with nothing to resume, so there is nothing to verify against.
            if !sha256Hex.isEmpty { request.setValue(sha256Hex, forHTTPHeaderField: "X-Part-Sha256") }
            request.setValue("Bearer \(bearer ?? "")", forHTTPHeaderField: "Authorization")

            let raw: RawResponse
            do {
                let (data, response) = try await uploadSession.upload(for: request, from: body)
                raw = RawResponse(status: (response as? HTTPURLResponse)?.statusCode ?? 0, body: data, timedOut: false)
            } catch let error as URLError {
                raw = RawResponse(status: Self.transportStatus, body: Data(), timedOut: error.code == .timedOut)
            } catch {
                raw = RawResponse(status: Self.transportStatus, body: Data(), timedOut: false)
            }

            if raw.status == 401, attempt == 0 {
                guard let renewed = await tokens?.renew(staleToken: bearer) else { return .sessionExpired }
                bearer = renewed
                attempt += 1
                continue
            }
            return decode(raw, as: type)
        }
    }

    /// PUTs a file straight from disk — a chat attachment — with the message's
    /// fields in the query and headers. URLSession streams the file, so a clip
    /// never sits in memory, and the one-shot semantics match `putBytes`: the
    /// caller owns retries, keyed by its own client id.
    func putFile<T: Decodable>(
        _ path: String,
        query: [String: String],
        headers: [String: String] = [:],
        file: URL,
        contentType: String,
        as type: T.Type
    ) async -> ApiResponse<T> {
        guard !baseURL.isEmpty, let url = buildURL(path: path, query: query) else {
            return .failure(0, "not_configured", "This build has no server configured.")
        }
        var bearer = await tokens?.accessToken()
        guard bearer != nil else { return .sessionExpired }

        var attempt = 0
        while true {
            var request = URLRequest(url: url)
            request.httpMethod = "PUT"
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
            headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
            request.setValue("Bearer \(bearer ?? "")", forHTTPHeaderField: "Authorization")

            let raw: RawResponse
            do {
                let (data, response) = try await uploadSession.upload(for: request, fromFile: file)
                raw = RawResponse(status: (response as? HTTPURLResponse)?.statusCode ?? 0, body: data, timedOut: false)
            } catch let error as URLError {
                raw = RawResponse(status: Self.transportStatus, body: Data(), timedOut: error.code == .timedOut)
            } catch {
                raw = RawResponse(status: Self.transportStatus, body: Data(), timedOut: false)
            }

            if raw.status == 401, attempt == 0 {
                guard let renewed = await tokens?.renew(staleToken: bearer) else { return .sessionExpired }
                bearer = renewed
                attempt += 1
                continue
            }
            return decode(raw, as: type)
        }
    }

    /// Opens a Server-Sent Events stream and reports every frame to `onEvent`,
    /// reconnecting until the calling task is cancelled.
    ///
    /// Realtime is an enhancement, not a dependency: a stream that cannot connect
    /// backs off and retries while the screen keeps serving its cache, so a proxy
    /// that blocks streaming costs freshness and nothing else.
    func stream(
        _ path: String,
        onOpen: @escaping @Sendable () -> Void = {},
        onEvent: @escaping @Sendable (String, Data) -> Void
    ) async {
        guard !baseURL.isEmpty, let url = buildURL(path: path, query: [:]) else { return }
        var backoff = Self.minBackoff
        while !Task.isCancelled {
            guard let bearer = await tokens?.accessToken() else {
                // No token: the session is finished. SessionManager drives re-login;
                // this loop must not spin against a closed door.
                return
            }
            let clean = await read(url: url, bearer: bearer, onOpen: onOpen, onEvent: onEvent)
            if Task.isCancelled { return }
            // A clean end is the server rotating the connection (it does that every
            // ten minutes), so reconnect at once; a failure earns backoff.
            backoff = clean ? Self.minBackoff : min(backoff * 2, Self.maxBackoff)
            let wait = clean ? 0 : backoff + Double.random(in: 0...0.5)
            if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) }
        }
    }

    /// Returns true when the server closed the stream cleanly.
    private func read(
        url: URL,
        bearer: String,
        onOpen: @escaping @Sendable () -> Void,
        onEvent: @escaping @Sendable (String, Data) -> Void
    ) async -> Bool {
        var request = URLRequest(url: url)
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

        do {
            let (bytes, response) = try await streamSession.bytes(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return false }
            onOpen()

            var event = ""
            var payload = ""
            for try await line in bytes.lines {
                if Task.isCancelled { return true }
                if line.hasPrefix(":") { continue } // heartbeat
                if line.hasPrefix("event:") {
                    event = String(line.dropFirst("event:".count)).trimmingCharacters(in: .whitespaces)
                } else if line.hasPrefix("data:") {
                    payload += String(line.dropFirst("data:".count)).trimmingCharacters(in: .whitespaces)
                } else if line.isEmpty {
                    if !event.isEmpty, let data = payload.data(using: .utf8) {
                        onEvent(event, data)
                    }
                    event = ""
                    payload = ""
                }
            }
            return true
        } catch {
            return false
        }
    }

    private func send<T: Decodable>(
        method: String,
        path: String,
        query: [String: String],
        body: [String: Any]?,
        authenticated: Bool,
        retries: Int,
        as type: T.Type
    ) async -> ApiResponse<T> {
        guard !baseURL.isEmpty else {
            return .failure(0, "not_configured", "This build has no server configured.")
        }

        var bearer: String?
        if authenticated {
            bearer = await tokens?.accessToken()
            if bearer == nil { return .sessionExpired }
        }

        var attempt = 0
        while true {
            let raw = await execute(method: method, path: path, query: query,
                                   body: body, bearer: bearer)

            // A 401 on an authenticated call means the access token died early —
            // revoked, or rotated by another device. Renew once and replay; a second
            // 401 is a real dead end, not something to loop on.
            if raw.status == 401, authenticated, attempt == 0 {
                guard let renewed = await tokens?.renew(staleToken: bearer) else {
                    return .sessionExpired
                }
                bearer = renewed
                attempt += 1
                continue
            }

            // Retry idempotent reads once on a transport failure; never a POST, which
            // may already have created an account or consumed a one-time code.
            if raw.status == Self.transportStatus, attempt < retries {
                attempt += 1
                continue
            }

            return decode(raw, as: type)
        }
    }

    private func decode<T: Decodable>(_ raw: RawResponse, as type: T.Type) -> ApiResponse<T> {
        if raw.status == Self.transportStatus {
            return raw.timedOut ? .timedOut : .offline
        }
        guard let envelope = try? decoder.decode(ApiEnvelope.self, from: raw.body) else {
            // Not our envelope: a proxy answered instead of the service (413 on a body
            // it thinks is too large, 502/504 when it gave up). Carry the status in the
            // message — an opaque string here costs hours.
            let suffix = raw.status > 0 ? " (HTTP \(raw.status))" : ""
            return .failure(raw.status, ApiResponse<T>.Code.malformed,
                            "The server sent something unexpected\(suffix). Try again.")
        }

        let statusCode = envelope.statusCode ?? raw.status
        let message = envelope.message ?? ""
        if envelope.error ?? (raw.status >= 400) {
            let code = (envelope.code?.isEmpty == false) ? envelope.code! : "error"
            return .failure(statusCode, code,
                            message.isEmpty ? "Something went wrong. Try again." : message)
        }

        // `data` is always an object, so decoding it separately keeps a payload change
        // from taking down the envelope parse.
        guard let payload = try? decoder.decode(DataWrapper<T>.self, from: raw.body) else {
            return .failure(statusCode, ApiResponse<T>.Code.malformed,
                            "The server sent something unexpected. Try again.")
        }
        return .success(statusCode, message, payload.data)
    }

    private func execute(
        method: String,
        path: String,
        query: [String: String],
        body: [String: Any]?,
        bearer: String?
    ) async -> RawResponse {
        guard let url = buildURL(path: path, query: query) else {
            return RawResponse(status: Self.transportStatus, body: Data(), timedOut: false)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if let bearer {
            request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }

        do {
            let (data, response) = try await session.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            return RawResponse(status: status, body: data, timedOut: false)
        } catch let error as URLError {
            let timedOut = error.code == .timedOut
            return RawResponse(status: Self.transportStatus, body: Data(), timedOut: timedOut)
        } catch {
            return RawResponse(status: Self.transportStatus, body: Data(), timedOut: false)
        }
    }

    private func buildURL(path: String, query: [String: String]) -> URL? {
        let suffix = path.hasPrefix("/") ? path : "/\(path)"
        guard var components = URLComponents(string: baseURL + suffix) else { return nil }
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        return components.url
    }

    private struct RawResponse {
        let status: Int
        let body: Data
        let timedOut: Bool
    }

    private struct DataWrapper<T: Decodable>: Decodable {
        let data: T
    }

    private static let transportStatus = -1
    private static let minBackoff: Double = 1
    private static let maxBackoff: Double = 30
}
