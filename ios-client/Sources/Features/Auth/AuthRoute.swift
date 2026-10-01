import Foundation

enum AuthRoute: String {
    case splash
    case onboarding
    case choice
    case signUp
    case logIn
    case resetPassword
    case verifyOtp
    case completeProfile
    case authenticated
}

/// Masks the destination shown on the OTP screen. Never log or display the full
/// address; a shoulder-surfer should not learn the account from the code screen.
func maskDestination(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespaces)
    guard let atIndex = trimmed.firstIndex(of: "@") else {
        guard trimmed.count > 4 else { return trimmed.isEmpty ? "" : "•••" }
        return "••• " + String(trimmed.suffix(4))
    }

    let local = String(trimmed[trimmed.startIndex..<atIndex])
    let domain = String(trimmed[atIndex...])
    guard local.count > 2 else { return "•••" + domain }
    return "\(local.prefix(1))•••\(local.suffix(1))\(domain)"
}
