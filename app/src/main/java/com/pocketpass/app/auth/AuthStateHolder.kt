package com.pocketpass.app.auth

import android.os.SystemClock
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
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

class AuthStateHolder(
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private var resendDeadlineMillis = 0L
    private var resendCountdownJob: Job? = null

    fun dispatch(event: AuthEvent) {
        when (event) {
            AuthEvent.ContinueWithEmail -> mutableState.update {
                it.copy(step = AuthStep.Email, error = null)
            }

            AuthEvent.ContinueWithDiscord -> startDiscord()
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
            AuthEvent.Back -> goBack()
            AuthEvent.RetryInitialization -> retryInitialization()
        }
    }

    fun clearTemporaryStateAfterAuthentication() {
        resendCountdownJob?.cancel()
        resendDeadlineMillis = 0L
        mutableState.value = AuthUiState()
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
            AuthStep.Email -> mutableState.update {
                it.copy(step = AuthStep.Landing, error = null)
            }

            AuthStep.Otp -> changeEmail()
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

    private fun RepositoryFailure.discordError(): AuthUiError =
        when (kind) {
            RepositoryFailureKind.Offline -> offlineError()
            RepositoryFailureKind.Misconfigured -> configurationError()
            RepositoryFailureKind.RateLimited -> rateLimitedError()
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
