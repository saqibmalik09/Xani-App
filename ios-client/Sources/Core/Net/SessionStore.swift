import Foundation
import UIKit

/// The device key the server uses for "max accounts per device" and device bans.
///
/// `identifierForVendor` is not used directly: it resets when the last app from the
/// vendor is deleted, and it is shared across our own apps. A random UUID in the
/// keychain survives app deletion and reinstall on the same device, which is exactly
/// the lifetime a device limit needs, and the server hashes it before storing.
enum DeviceIdentity {
    static let platform = "ios"

    static var key: String {
        if let existing = Keychain.string(for: keychainKey), !existing.isEmpty {
            return existing
        }
        let fresh = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        Keychain.set(fresh, for: keychainKey)
        return fresh
    }

    /// Coarse model string for the admin audit log; no serial, no UDID.
    static var model: String {
        let device = UIDevice.current
        return "Apple \(device.model) (iOS \(device.systemVersion))"
    }

    private static let keychainKey = "device.key"
}

/// Minimal keychain wrapper.
///
/// Tokens go here rather than `UserDefaults` because `UserDefaults` is a plist in the
/// app container, readable from a backup. `afterFirstUnlock` is the right class: a
/// background refresh must work while the screen is locked, but nothing should be
/// readable before the user has unlocked the device once since boot.
enum Keychain {
    private static let service = "app.xani.identity"

    static func string(for account: String) -> String? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func set(_ value: String, for account: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query = baseQuery(account)

        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert.merge(attributes) { _, new in new }
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    static func remove(_ account: String) {
        SecItemDelete(baseQuery(account) as CFDictionary)
    }

    private static func baseQuery(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

/// One saved login on this device. Tokens stay local so switching accounts is a
/// cache swap + server check, not a full password form.
struct SavedAccount: Equatable, Identifiable {
    var id: String { userId }
    let userId: String
    let username: String
    let fullName: String
    let avatarURL: String
    let email: String
    let passwordLoginActive: Bool
    let tokens: Tokens
    let access: Access
    let lastUsedAt: Date
}

/// Persists the active session and every other account saved on this device.
///
/// Layout: `accounts` is a JSON array of slots in the keychain; `active_user_id`
/// points at the one [SessionManager] is using. Cold-start migration keeps a
/// single legacy session as the first (and only) slot.
struct SessionStore {
    private enum Key {
        static let accounts = "accounts_v1"
        static let activeId = "active_user_id"
        // Legacy single-session keys — mirrored for the active slot and used once
        // to migrate installs that predate multi-account.
        static let access = "token.access"
        static let refresh = "token.refresh"
        static let accessExpires = "token.access.expires"
        static let refreshExpires = "token.refresh.expires"
        static let user = "session.user"
    }

    private let defaults = UserDefaults.standard

    func loadAccounts() -> [SavedAccount] {
        migrateLegacyIfNeeded()
        guard let raw = Keychain.string(for: Key.accounts),
              let data = raw.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.compactMap(Self.fromDict)
    }

    func activeUserId() -> String {
        defaults.string(forKey: Key.activeId) ?? ""
    }

    func saveAccounts(_ accounts: [SavedAccount], activeUserId: String) {
        let encoded = accounts.map { $0.toDict() }
        if let data = try? JSONSerialization.data(withJSONObject: encoded),
           let raw = String(data: data, encoding: .utf8) {
            Keychain.set(raw, for: Key.accounts)
        }
        defaults.set(activeUserId, forKey: Key.activeId)
        if let active = accounts.first(where: { $0.userId == activeUserId }) {
            mirrorActive(active)
        } else {
            clearLegacy()
        }
    }

    func loadActiveTokens() -> Tokens? {
        loadAccounts().first(where: { $0.userId == activeUserId() })?.tokens
    }

    func loadActiveUser() -> (AccountUser, Access)? {
        guard let a = loadAccounts().first(where: { $0.userId == activeUserId() }) else { return nil }
        let user = AccountUser(
            id: a.userId,
            username: a.username,
            fullName: a.fullName,
            email: a.email,
            emailVerified: false,
            avatarURL: a.avatarURL,
            passwordLoginActive: a.passwordLoginActive
        )
        return (user, a.access)
    }

    func clearAll() {
        Keychain.remove(Key.accounts)
        Keychain.remove(Key.access)
        Keychain.remove(Key.refresh)
        defaults.removeObject(forKey: Key.activeId)
        defaults.removeObject(forKey: Key.accessExpires)
        defaults.removeObject(forKey: Key.refreshExpires)
        defaults.removeObject(forKey: Key.user)
    }

    // MARK: - Legacy API (active slot only)

    func loadTokens() -> Tokens? { loadActiveTokens() }

    func save(_ tokens: Tokens) {
        guard var accounts = Optional(loadAccounts()), !accounts.isEmpty else {
            // No slots yet — only possible mid-migration; write legacy keys so the
            // next adopt can pick them up.
            Keychain.set(tokens.accessToken, for: Key.access)
            Keychain.set(tokens.refreshToken, for: Key.refresh)
            defaults.set(tokens.accessExpiresAt.timeIntervalSince1970, forKey: Key.accessExpires)
            defaults.set(tokens.refreshExpiresAt.timeIntervalSince1970, forKey: Key.refreshExpires)
            return
        }
        let activeId = activeUserId()
        guard let idx = accounts.firstIndex(where: { $0.userId == activeId }) else { return }
        let old = accounts[idx]
        accounts[idx] = SavedAccount(
            userId: old.userId,
            username: old.username,
            fullName: old.fullName,
            avatarURL: old.avatarURL,
            email: old.email,
            passwordLoginActive: old.passwordLoginActive,
            tokens: tokens,
            access: old.access,
            lastUsedAt: old.lastUsedAt
        )
        saveAccounts(accounts, activeUserId: activeId)
    }

    func save(user: AccountUser, access: Access) {
        var accounts = loadAccounts()
        let activeId = user.id.isEmpty ? activeUserId() : user.id
        let tok = accounts.first(where: { $0.userId == activeId })?.tokens
            ?? loadLegacyTokens()
            ?? .empty
        let slot = SavedAccount(
            userId: user.id,
            username: user.username,
            fullName: user.fullName,
            avatarURL: user.avatarURL,
            email: user.email,
            passwordLoginActive: user.passwordLoginActive,
            tokens: tok,
            access: access,
            lastUsedAt: Date()
        )
        accounts = accounts.filter { $0.userId != user.id } + [slot]
        saveAccounts(accounts, activeUserId: user.id)
    }

    func loadUser() -> (AccountUser, Access)? { loadActiveUser() }

    func clear() { clearAll() }

    // MARK: - Private

    private func migrateLegacyIfNeeded() {
        if Keychain.string(for: Key.accounts) != nil { return }
        guard let tokens = loadLegacyTokens(),
              let raw = defaults.dictionary(forKey: Key.user),
              let id = raw["id"] as? String, !id.isEmpty
        else { return }
        let slot = SavedAccount(
            userId: id,
            username: raw["username"] as? String ?? "",
            fullName: raw["full_name"] as? String ?? "",
            avatarURL: raw["avatar_url"] as? String ?? "",
            email: raw["email"] as? String ?? "",
            passwordLoginActive: raw["password_login_active"] as? Bool ?? false,
            tokens: tokens,
            access: Access(
                roles: raw["roles"] as? [String] ?? [],
                permissions: raw["permissions"] as? [String] ?? []
            ),
            lastUsedAt: Date()
        )
        saveAccounts([slot], activeUserId: id)
    }

    private func loadLegacyTokens() -> Tokens? {
        guard let access = Keychain.string(for: Key.access),
              let refresh = Keychain.string(for: Key.refresh),
              !access.isEmpty, !refresh.isEmpty
        else { return nil }
        return Tokens(
            accessToken: access,
            refreshToken: refresh,
            accessExpiresAt: Date(timeIntervalSince1970: defaults.double(forKey: Key.accessExpires)),
            refreshExpiresAt: Date(timeIntervalSince1970: defaults.double(forKey: Key.refreshExpires))
        )
    }

    private func mirrorActive(_ a: SavedAccount) {
        Keychain.set(a.tokens.accessToken, for: Key.access)
        Keychain.set(a.tokens.refreshToken, for: Key.refresh)
        defaults.set(a.tokens.accessExpiresAt.timeIntervalSince1970, forKey: Key.accessExpires)
        defaults.set(a.tokens.refreshExpiresAt.timeIntervalSince1970, forKey: Key.refreshExpires)
        defaults.set(
            [
                "id": a.userId,
                "username": a.username,
                "full_name": a.fullName,
                "email": a.email,
                "email_verified": false,
                "avatar_url": a.avatarURL,
                "password_login_active": a.passwordLoginActive,
                "roles": a.access.roles,
                "permissions": a.access.permissions,
            ],
            forKey: Key.user
        )
    }

    private func clearLegacy() {
        Keychain.remove(Key.access)
        Keychain.remove(Key.refresh)
        defaults.removeObject(forKey: Key.accessExpires)
        defaults.removeObject(forKey: Key.refreshExpires)
        defaults.removeObject(forKey: Key.user)
    }

    private static func fromDict(_ o: [String: Any]) -> SavedAccount? {
        let id = (o["user_id"] as? String)
            ?? (o["user_id"] as? NSNumber).map(String.init(describing:))
            ?? ""
        guard !id.isEmpty else { return nil }
        let accessTok = o["access_token"] as? String ?? ""
        let refreshTok = o["refresh_token"] as? String ?? ""
        guard !accessTok.isEmpty, !refreshTok.isEmpty else { return nil }
        let accessExp = (o["access_expires_at"] as? Double)
            ?? (o["access_expires_at"] as? NSNumber)?.doubleValue
            ?? 0
        let refreshExp = (o["refresh_expires_at"] as? Double)
            ?? (o["refresh_expires_at"] as? NSNumber)?.doubleValue
            ?? 0
        let roles = o["roles"] as? [String] ?? []
        let perms = o["permissions"] as? [String] ?? []
        let memberships = (o["memberships"] as? [[String: Any]] ?? []).compactMap(MembershipContext.fromDict)
        let lastUsed = (o["last_used_at"] as? Double)
            ?? (o["last_used_at"] as? NSNumber)?.doubleValue
            ?? 0
        return SavedAccount(
            userId: id,
            username: o["username"] as? String ?? "",
            fullName: o["full_name"] as? String ?? "",
            avatarURL: o["avatar_url"] as? String ?? "",
            email: o["email"] as? String ?? "",
            passwordLoginActive: o["password_login_active"] as? Bool ?? false,
            tokens: Tokens(
                accessToken: accessTok,
                refreshToken: refreshTok,
                accessExpiresAt: Date(timeIntervalSince1970: accessExp),
                refreshExpiresAt: Date(timeIntervalSince1970: refreshExp)
            ),
            access: Access(roles: roles, permissions: perms, memberships: memberships),
            lastUsedAt: Date(timeIntervalSince1970: lastUsed)
        )
    }
}

private extension SavedAccount {
    func toDict() -> [String: Any] {
        [
            "user_id": userId,
            "username": username,
            "full_name": fullName,
            "avatar_url": avatarURL,
            "email": email,
            "password_login_active": passwordLoginActive,
            "access_token": tokens.accessToken,
            "refresh_token": tokens.refreshToken,
            "access_expires_at": tokens.accessExpiresAt.timeIntervalSince1970,
            "refresh_expires_at": tokens.refreshExpiresAt.timeIntervalSince1970,
            "roles": access.roles,
            "permissions": access.permissions,
            "memberships": access.memberships.map { $0.toDict() },
            "last_used_at": lastUsedAt.timeIntervalSince1970,
        ]
    }
}

extension AccountUser {
    /// Rehydrates the cached subset. Fields the cache does not keep stay empty until
    /// the next `/v1/auth/me`.
    init(
        id: String,
        username: String,
        fullName: String,
        email: String,
        emailVerified: Bool,
        avatarURL: String,
        passwordLoginActive: Bool = false
    ) {
        self.init(
            id: id, username: username, fullName: fullName, email: email,
            phone: "", emailVerified: emailVerified, avatarURL: avatarURL,
            location: "", bio: "", birthday: "", country: nil,
            passwordLoginActive: passwordLoginActive
        )
    }

    init(
        id: String, username: String, fullName: String, email: String, phone: String,
        emailVerified: Bool, avatarURL: String, location: String, bio: String,
        birthday: String = "", country: Country?, passwordLoginActive: Bool = false
    ) {
        self.id = id
        self.username = username
        self.fullName = fullName
        self.email = email
        self.phone = phone
        self.emailVerified = emailVerified
        self.avatarURL = avatarURL
        self.location = location
        self.bio = bio
        self.birthday = birthday
        self.country = country
        self.passwordLoginActive = passwordLoginActive
    }

    func withPasswordLoginActive(_ active: Bool) -> AccountUser {
        AccountUser(
            id: id, username: username, fullName: fullName, email: email, phone: phone,
            emailVerified: emailVerified, avatarURL: avatarURL, location: location,
            bio: bio, birthday: birthday, country: country, passwordLoginActive: active
        )
    }
}
