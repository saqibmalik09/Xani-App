import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/// Google Sign-In via `ASWebAuthenticationSession` + PKCE.
///
/// No Google SDK — keeps the zero-package policy. The ID token's `aud` is the
/// **iOS** OAuth client id; put that same value in the server's
/// `GOOGLE_CLIENT_IDS` / `auth.google_client_ids` (alongside the Android web client id).
///
/// Required Info.plist keys (from Brand.xcconfig):
///  - `GoogleIosClientId` — iOS OAuth client (`….apps.googleusercontent.com`)
///  - URL scheme `com.googleusercontent.apps.<prefix>` (auto-derived)
@MainActor
final class WebGoogleSignIn: NSObject, GoogleIdTokenProvider {
    private let clientId: String
    private var session: ASWebAuthenticationSession?

    init(clientId: String = Bundle.main.object(forInfoDictionaryKey: "GoogleIosClientId") as? String ?? "") {
        self.clientId = clientId.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var available: Bool { !clientId.isEmpty && clientId.contains(".apps.googleusercontent.com") }

    /// Reverse-DNS scheme Google registers for iOS OAuth clients.
    private var callbackScheme: String {
        let prefix = clientId.replacingOccurrences(of: ".apps.googleusercontent.com", with: "")
        return "com.googleusercontent.apps.\(prefix)"
    }

    private var redirectURI: String { "\(callbackScheme):/oauth2redirect" }

    func idToken() async -> String? {
        guard available else { return nil }
        let verifier = Self.randomURLSafe(32)
        let challenge = Self.sha256Base64URL(verifier)
        let state = Self.randomURLSafe(16)
        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        guard let authURL = components.url else { return nil }

        do {
            let callback = try await present(authURL: authURL, scheme: callbackScheme)
            guard let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems else {
                return nil
            }
            let returnedState = items.first(where: { $0.name == "state" })?.value
            guard returnedState == state else { return nil }
            if items.contains(where: { $0.name == "error" }) { return nil }
            guard let code = items.first(where: { $0.name == "code" })?.value else { return nil }
            return try await exchange(code: code, verifier: verifier)
        } catch {
            return nil
        }
    }

    private func present(authURL: URL, scheme: String) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: scheme
            ) { url, error in
                self.session = nil
                if let error {
                    continuation.resume(throwing: error)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(throwing: CancellationError())
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            if !session.start() {
                continuation.resume(throwing: CancellationError())
            }
        }
    }

    private func exchange(code: String, verifier: String) async throws -> String? {
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = [
            "code": code,
            "client_id": clientId,
            "redirect_uri": redirectURI,
            "grant_type": "authorization_code",
            "code_verifier": verifier,
        ]
        request.httpBody = body
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
            .joined(separator: "&")
            .data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            return nil
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return json?["id_token"] as? String
    }

    private static func randomURLSafe(_ bytes: Int) -> String {
        var buf = [UInt8](repeating: 0, count: bytes)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes, &buf)
        return Data(buf).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func sha256Base64URL(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

extension WebGoogleSignIn: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }
}
