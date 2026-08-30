package com.pocketpass.app.auth

enum class AuthStep {
    Landing,
    Email,
    Otp,
}

data class AuthUiError(
    val message: String,
    val code: String,
)

data class AuthUiState(
    val step: AuthStep = AuthStep.Landing,
    val email: String = "",
    val otpCode: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthUiError? = null,
    val resendSecondsRemaining: Int = 0,
    val errorShakeNonce: Int = 0,
) {
    val normalizedEmail: String
        get() = normalizePocketPassEmail(email)

    val canContinueWithEmail: Boolean
        get() = isPocketPassEmailValid(email) && !isSubmitting

    val canVerify: Boolean
        get() = otpCode.length == OTP_LENGTH && !isSubmitting

    val canResend: Boolean
        get() = resendSecondsRemaining == 0 && !isSubmitting
}

sealed interface AuthEvent {
    data object ContinueWithEmail : AuthEvent
    data object ContinueWithDiscord : AuthEvent
    data class EmailChanged(val value: String) : AuthEvent
    data object SubmitEmail : AuthEvent
    data class OtpChanged(val value: String) : AuthEvent
    data object VerifyOtp : AuthEvent
    data object ResendOtp : AuthEvent
    data object ChangeEmail : AuthEvent
    data object Back : AuthEvent
    data object RetryInitialization : AuthEvent
}

const val OTP_LENGTH = 6
const val MAX_EMAIL_LENGTH = 254
const val ERROR_INVALID_EMAIL = "PP-AUTH-101"
const val ERROR_INVALID_OTP = "PP-AUTH-201"
const val ERROR_RATE_LIMITED = "PP-AUTH-429"
const val ERROR_OFFLINE = "PP-NET-001"
const val ERROR_SERVICE_UNAVAILABLE = "PP-SVC-001"
const val ERROR_CONFIGURATION = "PP-CFG-001"
const val ERROR_DISCORD_OAUTH = "PP-OAUTH-001"

fun normalizePocketPassEmail(value: String): String =
    value.trim().lowercase()

fun isPocketPassEmailValid(value: String): Boolean {
    val normalized = normalizePocketPassEmail(value)
    return normalized.length in 3..MAX_EMAIL_LENGTH &&
        EMAIL_PATTERN.matches(normalized)
}

fun filterPocketPassOtp(value: String): String =
    value.filter(Char::isDigit).take(OTP_LENGTH)

private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
