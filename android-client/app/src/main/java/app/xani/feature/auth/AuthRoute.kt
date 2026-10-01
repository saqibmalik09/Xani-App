package app.xani.feature.auth

enum class AuthRoute {
    Splash,
    Onboarding,
    Choice,
    SignUp,
    LogIn,
    ResetPassword,
    VerifyOtp,
    CompleteProfile,
    Authenticated,
}

/** Masks the delivery target so the OTP screen never renders a full address. */
fun maskDestination(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val at = value.indexOf('@')
    if (at <= 0) {
        val tail = value.takeLast(4)
        return "••• ••• $tail"
    }
    val name = value.substring(0, at)
    val domain = value.substring(at)
    val head = name.take(2)
    return "$head${"•".repeat((name.length - 2).coerceAtLeast(2))}$domain"
}
