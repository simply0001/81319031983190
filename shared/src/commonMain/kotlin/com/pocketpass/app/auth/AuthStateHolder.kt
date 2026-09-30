package com.pocketpass.app.auth

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.TimeSource

class AuthStateHolder(
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
    private val onPasswordAccountCreated: suspend (UserId) -> Unit = {},
    private val elapsedRealtimeMillis: () -> Long = monotonicMillis(),
) {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private var resendDeadlineMillis = 0L
    private var resendCountdownJob: Job? = null

    fun dispatch(event: AuthEvent) {
        when (event) {
            AuthEvent.ChooseSignIn -> mutableState.update {
                it.copy(step = AuthStep.Method, intent = AuthIntent.SignIn, error = null)
            }

            AuthEvent.ChooseSignUp -> mutableState.update {
                it.copy(step = AuthStep.Method, intent = AuthIntent.SignUp, error = null)
            }

            AuthEvent.ContinueWithEmail -> mutableState.update {
                it.copy(step = AuthStep.Email, error = null)
            }

            AuthEvent.ContinueWithDiscord -> startDiscord()
            AuthEvent.ContinueWithCredentials -> mutableState.update {
                val creating = it.intent == AuthIntent.SignUp
                it.copy(
                    step = AuthStep.Credentials,
                    credentialsMode = if (creating) CredentialsMode.Create else CredentialsMode.SignIn,
                    identifier = if (creating) filterPocketPassUsername(it.identifier) else it.identifier,
                    passwordRepeat = "",
                    error = null,
                )
            }

            is AuthEvent.EmailChanged -> mutableState.update {
                it.copy(
                    email = event.value.take(MAX_EMAIL_LENGTH),
                    error = null,
                )
            }

            AuthEvent.SubmitEmail -> submitEmail()
            is AuthEvent.OtpChanged -> mutableState.update {
                it.copy(
                    otpCode = filterPocketPassOtp(event.value),
                    error = null,
                )
            }

            AuthEvent.VerifyOtp -> verifyOtp()
            AuthEvent.ResendOtp -> resendOtp()
            AuthEvent.ChangeEmail -> changeEmail()
            is AuthEvent.IdentifierChanged -> mutableState.update {
                it.copy(identifier = it.acceptIdentifier(event.value), error = null)
            }

            is AuthEvent.PasswordChanged -> mutableState.update {
                it.copy(password = event.value.take(PASSWORD_MAX_LENGTH), error = null)
            }

            is AuthEvent.PasswordRepeatChanged -> mutableState.update {
                it.copy(passwordRepeat = event.value.take(PASSWORD_MAX_LENGTH), error = null)
            }

            AuthEvent.ToggleCredentialsMode -> toggleCredentialsMode()
            AuthEvent.TogglePasswordVisibility -> mutableState.update {
                it.copy(showPassword = !it.showPassword)
            }

            AuthEvent.SubmitCredentials -> submitCredentials()
            AuthEvent.Back -> goBack()
            AuthEvent.RetryInitialization -> retryInitialization()
        }
    }

    fun showAuthCallbackFailure(failure: RepositoryFailure) {
        if (!failure.isSignUpBan()) return
        mutableState.update {
            val landing = it.step == AuthStep.Landing
            it.copy(
                step = if (landing) AuthStep.Method else it.step,
                intent = if (landing) AuthIntent.SignUp else it.intent,
                isSubmitting = false,
                error = signUpBannedError(),
            )
        }
    }

    fun clearTemporaryStateAfterAuthentication() {
        resendCountdownJob?.cancel()
        resendDeadlineMillis = 0L
        mutableState.value = AuthUiState()
    }

    private fun AuthUiState.acceptIdentifier(value: String): String =
        if (isCreatingAccount) filterPocketPassUsername(value) else value.take(MAX_EMAIL_LENGTH)

    private fun toggleCredentialsMode() {
        mutableState.update { current ->
            if (current.isSubmitting) return@update current
            val mode = if (current.isCreatingAccount) CredentialsMode.SignIn else CredentialsMode.Create
            current.copy(
                credentialsMode = mode,
                identifier = if (mode == CredentialsMode.Create) {
                    filterPocketPassUsername(current.identifier)
                } else {
                    current.identifier
                },
                passwordRepeat = "",
                error = null,
            )
        }
    }

    private fun submitEmail() {
        val current = mutableState.value
        if (current.isSubmitting) return
        if (!isPocketPassEmailValid(current.email)) {
            mutableState.update {
                it.copy(
                    error = AuthUiError(
                        message = "Enter a valid email address.",
                        code = ERROR_INVALID_EMAIL,
                    ),
                )
            }
            return
        }
        requestOtp(preserveEnteredCode = false)
    }

    private fun requestOtp(preserveEnteredCode: Boolean) {
        val current = mutableState.value
        if (current.isSubmitting) return

        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            when (
                val result = sessionRepository.requestEmailOtp(
                    email = current.normalizedEmail,
                    createUser = true,
                )
            ) {
                is RepositoryResult.Success -> {
                    mutableState.update {
                        it.copy(
                            step = AuthStep.Otp,
                            otpCode = if (preserveEnteredCode) it.otpCode else "",
                            isSubmitting = false,
                            error = null,
                        )
                    }
                    startResendCountdown()
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        error = result.error.requestOtpError(),
                    )
                }
            }
        }
    }

    private fun verifyOtp() {
        val current = mutableState.value
        if (!current.canVerify) return
        mutableState.update { it.copy(isSubmitting = true, error = null) }

        scope.launch {
            when (
                val result = sessionRepository.verifyEmailOtp(
                    email = current.normalizedEmail,
                    sixDigitCode = current.otpCode,
                )
            ) {
                is RepositoryResult.Success -> clearTemporaryStateAfterAuthentication()
                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        error = result.error.verifyOtpError(),
                        errorShakeNonce = it.errorShakeNonce + 1,
                    )
                }
            }
        }
    }

    private fun resendOtp() {
        val current = mutableState.value
        if (current.step != AuthStep.Otp || !current.canResend) return
        requestOtp(preserveEnteredCode = true)
    }

    private fun startDiscord() {
        if (mutableState.value.isSubmitting) return
        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            when (val result = sessionRepository.signInWithDiscord()) {
                is RepositoryResult.Success -> mutableState.update {
                    it.copy(isSubmitting = false)
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        error = result.error.discordError(),
                    )
                }
            }
        }
    }

    private fun submitCredentials() {
        val current = mutableState.value
        if (!current.canSubmitCredentials) return
        if (current.isCreatingAccount) {
            createPasswordAccount(current)
        } else {
            signInWithPassword(current)
        }
    }

    private fun signInWithPassword(current: AuthUiState) {
        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            when (
                val result = sessionRepository.signInWithPassword(
                    loginEmail = loginEmailFor(current.identifier),
                    password = current.password,
                )
            ) {
                is RepositoryResult.Success -> clearTemporaryStateAfterAuthentication()
                is RepositoryResult.Failure -> failCredentials(result.error.passwordSignInError())
            }
        }
    }

    private fun createPasswordAccount(current: AuthUiState) {
        val username = current.normalizedIdentifier
        val validationError = when {
            !isPocketPassLoginUsernameValid(username) ->
                AuthUiError(USERNAME_RULE_MESSAGE, ERROR_INVALID_USERNAME)

            !isPocketPassPasswordValid(current.password) ->
                AuthUiError(PASSWORD_RULE_MESSAGE, ERROR_WEAK_PASSWORD)

            current.password != current.passwordRepeat ->
                AuthUiError(PASSWORD_MISMATCH_MESSAGE, ERROR_PASSWORD_MISMATCH)

            else -> null
        }
        if (validationError != null) {
            failCredentials(validationError)
            return
        }
        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            when (val availability = sessionRepository.isUsernameAvailable(username)) {
                is RepositoryResult.Failure -> {
                    failCredentials(availability.error.credentialsServiceError())
                    return@launch
                }

                is RepositoryResult.Success -> if (!availability.value) {
                    failCredentials(usernameTakenError())
                    return@launch
                }
            }
            when (
                val result = sessionRepository.signUpWithPassword(
                    loginEmail = loginEmailFor(username),
                    password = current.password,
                    username = username,
                )
            ) {
                is RepositoryResult.Success -> {
                    (result.value as? SessionState.Authenticated)?.let { authenticated ->
                        onPasswordAccountCreated(authenticated.userId)
                    }
                    clearTemporaryStateAfterAuthentication()
                }

                is RepositoryResult.Failure -> failCredentials(
                    result.error.signUpError(username),
                )
            }
        }
    }

    private suspend fun RepositoryFailure.signUpError(username: String): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Conflict -> usernameTakenError()
            RepositoryFailureKind.Validation -> AuthUiError(PASSWORD_RULE_MESSAGE, ERROR_WEAK_PASSWORD)
            RepositoryFailureKind.Forbidden if isSignUpBan() -> signUpBannedError()
            RepositoryFailureKind.Unavailable,
            RepositoryFailureKind.Unknown,
            -> {
                val recheck = sessionRepository.isUsernameAvailable(username)
                if (recheck is RepositoryResult.Success && !recheck.value) {
                    usernameTakenError()
                } else {
                    serviceUnavailableError()
                }
            }

            else -> credentialsServiceError()
        }

    private fun failCredentials(error: AuthUiError) {
        mutableState.update {
            it.copy(
                isSubmitting = false,
                error = error,
                errorShakeNonce = it.errorShakeNonce + 1,
            )
        }
    }

    private fun changeEmail() {
        resendCountdownJob?.cancel()
        resendDeadlineMillis = 0L
        mutableState.update {
            it.copy(
                step = AuthStep.Email,
                otpCode = "",
                error = null,
                isSubmitting = false,
                resendSecondsRemaining = 0,
            )
        }
    }

    private fun goBack() {
        when (mutableState.value.step) {
            AuthStep.Landing -> Unit
            AuthStep.Method -> mutableState.update {
                it.copy(step = AuthStep.Landing, error = null)
            }

            AuthStep.Email -> mutableState.update {
                it.copy(step = AuthStep.Method, error = null)
            }

            AuthStep.Otp -> changeEmail()
            AuthStep.Credentials -> mutableState.update {
                it.copy(
                    step = AuthStep.Method,
                    password = "",
                    passwordRepeat = "",
                    showPassword = false,
                    error = null,
                )
            }
        }
    }

    private fun retryInitialization() {
        if (mutableState.value.isSubmitting) return
        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            when (val result = sessionRepository.initialize()) {
                is RepositoryResult.Success -> mutableState.update {
                    it.copy(isSubmitting = false, error = null)
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        error = result.error.initializationError(),
                    )
                }
            }
        }
    }

    private fun startResendCountdown() {
        resendCountdownJob?.cancel()
        resendDeadlineMillis = elapsedRealtimeMillis() + RESEND_DELAY_MILLIS
        resendCountdownJob = scope.launch {
            while (isActive) {
                val remainingMillis =
                    (resendDeadlineMillis - elapsedRealtimeMillis()).coerceAtLeast(0L)
                mutableState.update {
                    it.copy(
                        resendSecondsRemaining = ceil(remainingMillis / 1_000.0)
                            .toInt(),
                    )
                }
                if (remainingMillis == 0L) break
                delay(COUNTDOWN_TICK_MILLIS)
            }
        }
    }

    private fun RepositoryFailure.requestOtpError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Validation -> AuthUiError(
                message = "Enter a valid email address.",
                code = ERROR_INVALID_EMAIL,
            )

            RepositoryFailureKind.RateLimited -> rateLimitedError()
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            RepositoryFailureKind.Forbidden if isSignUpBan() -> signUpBannedError()
            else -> serviceUnavailableError()
        }

    private fun RepositoryFailure.verifyOtpError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.RateLimited -> rateLimitedError()
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            RepositoryFailureKind.Unavailable,
            RepositoryFailureKind.Unknown,
            -> serviceUnavailableError()

            else -> AuthUiError(
                message = "That code is incorrect or expired.",
                code = ERROR_INVALID_OTP,
            )
        }

    private fun RepositoryFailure.passwordSignInError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Unauthorized,
            RepositoryFailureKind.Validation,
            RepositoryFailureKind.NotFound,
            RepositoryFailureKind.Forbidden,
            -> AuthUiError(INVALID_CREDENTIALS_MESSAGE, ERROR_INVALID_CREDENTIALS)

            else -> credentialsServiceError()
        }

    private fun RepositoryFailure.credentialsServiceError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.RateLimited -> rateLimitedError()
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            else -> serviceUnavailableError()
        }

    private fun RepositoryFailure.discordError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            RepositoryFailureKind.RateLimited -> rateLimitedError()
            RepositoryFailureKind.Forbidden if isSignUpBan() -> signUpBannedError()
            else -> AuthUiError(
                message = "Discord sign-in could not start. Please try again.",
                code = ERROR_DISCORD_OAUTH,
            )
        }

    private fun RepositoryFailure.initializationError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            else -> serviceUnavailableError()
        }

    private fun signUpBannedError() = AuthUiError(
        message = SIGN_UP_BANNED_MESSAGE,
        code = ERROR_SIGN_UP_BANNED,
    )

    private fun usernameTakenError() = AuthUiError(
        message = USERNAME_TAKEN_MESSAGE,
        code = ERROR_USERNAME_TAKEN,
    )

    private fun offlineError() = AuthUiError(
        message = "You're offline. Check your connection and try again.",
        code = ERROR_OFFLINE,
    )

    private fun rateLimitedError() = AuthUiError(
        message = "Too many attempts. Please wait and try again.",
        code = ERROR_RATE_LIMITED,
    )

    private fun serviceUnavailableError() = AuthUiError(
        message = "PocketPass sign-in is temporarily unavailable.",
        code = ERROR_SERVICE_UNAVAILABLE,
    )

    private fun configurationError() = AuthUiError(
        message = "PocketPass sign-in isn't configured correctly.",
        code = ERROR_CONFIGURATION,
    )

    private companion object {
        const val RESEND_DELAY_MILLIS = 60_000L
        const val COUNTDOWN_TICK_MILLIS = 250L
    }
}

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
