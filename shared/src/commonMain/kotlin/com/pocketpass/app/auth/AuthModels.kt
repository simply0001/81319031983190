package com.pocketpass.app.auth

enum class AuthStep {
    Landing,
    Method,
    Email,
    Otp,
    Credentials,
}

enum class AuthIntent {
    SignIn,
    SignUp,
}

enum class CredentialsMode {
    SignIn,
    Create,
}

data class AuthUiError(
    val message: String,
    val code: String,
)

data class AuthUiState(
    val step: AuthStep = AuthStep.Landing,
    val intent: AuthIntent = AuthIntent.SignIn,
    val email: String = "",
    val otpCode: String = "",
    val identifier: String = "",
    val password: String = "",
    val passwordRepeat: String = "",
    val credentialsMode: CredentialsMode = CredentialsMode.SignIn,
    val showPassword: Boolean = false,
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

    val normalizedIdentifier: String
        get() = normalizePocketPassIdentifier(identifier)

    val isCreatingAccount: Boolean
        get() = credentialsMode == CredentialsMode.Create

    val canSubmitCredentials: Boolean
        get() = !isSubmitting &&
            normalizedIdentifier.isNotEmpty() &&
            password.isNotEmpty() &&
            (!isCreatingAccount || passwordRepeat.isNotEmpty())
}

sealed interface AuthEvent {
    data object ChooseSignIn : AuthEvent
    data object ChooseSignUp : AuthEvent
    data object ContinueWithEmail : AuthEvent
    data object ContinueWithDiscord : AuthEvent
    data object ContinueWithCredentials : AuthEvent
    data class EmailChanged(val value: String) : AuthEvent
    data object SubmitEmail : AuthEvent
    data class OtpChanged(val value: String) : AuthEvent
    data object VerifyOtp : AuthEvent
    data object ResendOtp : AuthEvent
    data object ChangeEmail : AuthEvent
    data class IdentifierChanged(val value: String) : AuthEvent
    data class PasswordChanged(val value: String) : AuthEvent
    data class PasswordRepeatChanged(val value: String) : AuthEvent
    data object ToggleCredentialsMode : AuthEvent
    data object TogglePasswordVisibility : AuthEvent
    data object SubmitCredentials : AuthEvent
    data object Back : AuthEvent
    data object RetryInitialization : AuthEvent
}

const val OTP_LENGTH = 6
const val MAX_EMAIL_LENGTH = 254
const val PASSWORD_MIN_LENGTH = 8
const val PASSWORD_MAX_LENGTH = 72
const val LOGIN_USERNAME_MIN_LENGTH = 3
const val LOGIN_USERNAME_MAX_LENGTH = 12
const val LOGIN_EMAIL_DOMAIN = "users.pocketpass.xyz"
const val ERROR_INVALID_EMAIL = "PP-AUTH-101"
const val ERROR_INVALID_OTP = "PP-AUTH-201"
const val ERROR_INVALID_USERNAME = "PP-AUTH-301"
const val ERROR_WEAK_PASSWORD = "PP-AUTH-302"
const val ERROR_PASSWORD_MISMATCH = "PP-AUTH-303"
const val ERROR_INVALID_CREDENTIALS = "PP-AUTH-401"
const val ERROR_SIGN_UP_BANNED = "PP-AUTH-403"
const val ERROR_USERNAME_TAKEN = "PP-AUTH-409"
const val ERROR_RATE_LIMITED = "PP-AUTH-429"
const val ERROR_OFFLINE = "PP-NET-001"
const val ERROR_SERVICE_UNAVAILABLE = "PP-SVC-001"
const val ERROR_CONFIGURATION = "PP-CFG-001"
const val ERROR_DISCORD_OAUTH = "PP-OAUTH-001"

const val USERNAME_RULE_MESSAGE = "Usernames are 3-12 lowercase letters, numbers or dots."
const val PASSWORD_RULE_MESSAGE = "Passwords need at least 8 characters."
const val PASSWORD_MISMATCH_MESSAGE = "The passwords do not match."
const val USERNAME_TAKEN_MESSAGE = "That username is taken."
const val SIGN_UP_BANNED_MESSAGE = "This sign-up is blocked because of a ban."
const val INVALID_CREDENTIALS_MESSAGE =
    "Wrong username or password. If you linked an email address to this account, sign in with that email."
const val FORGOT_PASSWORD_MESSAGE =
    "Forgot your password? There is no reset. If you linked an email address, go back and continue with email instead."
const val NO_PASSWORD_RESET_MESSAGE =
    "There is no password reset. If you forget this password you cannot get back in unless you link an email address in Settings first."

fun normalizePocketPassEmail(value: String): String =
    value.trim().lowercase()

fun isPocketPassEmailValid(value: String): Boolean {
    val normalized = normalizePocketPassEmail(value)
    return normalized.length in 3..MAX_EMAIL_LENGTH &&
        EMAIL_PATTERN.matches(normalized)
}

fun filterPocketPassOtp(value: String): String =
    value.filter(Char::isDigit).take(OTP_LENGTH)

fun normalizePocketPassIdentifier(value: String): String =
    value.trim().lowercase()

fun filterPocketPassUsername(value: String): String =
    value.lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' }
        .take(LOGIN_USERNAME_MAX_LENGTH)

fun isPocketPassLoginUsernameValid(value: String): Boolean =
    value.length in LOGIN_USERNAME_MIN_LENGTH..LOGIN_USERNAME_MAX_LENGTH &&
        LOGIN_USERNAME_PATTERN.matches(value)

fun isPocketPassPasswordValid(value: String): Boolean =
    value.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH

fun loginEmailFor(identifier: String): String {
    val normalized = normalizePocketPassIdentifier(identifier)
    return if ('@' in normalized) normalized else "$normalized@$LOGIN_EMAIL_DOMAIN"
}

fun isLoginDomainEmail(email: String?): Boolean =
    email?.trim()?.lowercase()?.endsWith("@$LOGIN_EMAIL_DOMAIN") == true

fun loginUsernameFromEmail(email: String?): String? =
    if (isLoginDomainEmail(email)) email!!.trim().lowercase().substringBefore('@') else null

private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
private val LOGIN_USERNAME_PATTERN = Regex("""^[a-z0-9]+(\.[a-z0-9]+)*$""")
