package com.pocketpass.app.feature

import com.pocketpass.app.auth.MAX_EMAIL_LENGTH
import com.pocketpass.app.auth.OTP_LENGTH
import com.pocketpass.app.auth.PASSWORD_MAX_LENGTH
import com.pocketpass.app.auth.PASSWORD_MISMATCH_MESSAGE
import com.pocketpass.app.auth.PASSWORD_RULE_MESSAGE
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.auth.isLoginDomainEmail
import com.pocketpass.app.auth.isPocketPassEmailValid
import com.pocketpass.app.auth.isPocketPassPasswordValid
import com.pocketpass.app.auth.loginUsernameFromEmail
import com.pocketpass.app.auth.normalizePocketPassEmail
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
import kotlin.time.TimeSource

enum class AccountSecurityStep {
    Overview,
    LinkEmail,
    LinkEmailCode,
    ChangePasswordCode,
    ChangePasswordEntry,
}

data class AccountSecurityUiState(
    val step: AccountSecurityStep = AccountSecurityStep.Overview,
    val accountEmail: String? = null,
    val emailDraft: String = "",
    val codeDraft: String = "",
    val newPassword: String = "",
    val newPasswordRepeat: String = "",
    val showPassword: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val resendSecondsRemaining: Int = 0,
    val errorShakeNonce: Int = 0,
) {
    val isUsernameAccount: Boolean
        get() = isLoginDomainEmail(accountEmail)

    val username: String?
        get() = loginUsernameFromEmail(accountEmail)

    val canChangePassword: Boolean
        get() = accountEmail != null && !isUsernameAccount

    val normalizedEmail: String
        get() = normalizePocketPassEmail(emailDraft)

    val canSubmitEmail: Boolean
        get() = isPocketPassEmailValid(emailDraft) && !submitting

    val canContinueWithCode: Boolean
        get() = codeDraft.length == OTP_LENGTH && !submitting

    val canResend: Boolean
        get() = resendSecondsRemaining == 0 && !submitting

    val canSubmitPassword: Boolean
        get() = newPassword.isNotEmpty() && newPasswordRepeat.isNotEmpty() && !submitting
}

sealed interface AccountSecurityEvent {
    data object OpenLinkEmail : AccountSecurityEvent
    data class EmailChanged(val value: String) : AccountSecurityEvent
    data object SubmitEmail : AccountSecurityEvent
    data class CodeChanged(val value: String) : AccountSecurityEvent
    data object ContinueWithCode : AccountSecurityEvent
    data object ResendCode : AccountSecurityEvent
    data object OpenChangePassword : AccountSecurityEvent
    data class NewPasswordChanged(val value: String) : AccountSecurityEvent
    data class NewPasswordRepeatChanged(val value: String) : AccountSecurityEvent
    data object TogglePasswordVisibility : AccountSecurityEvent
    data object SubmitNewPassword : AccountSecurityEvent
    data object DismissNotice : AccountSecurityEvent
    data object Back : AccountSecurityEvent
}

class AccountSecurityStateHolder(
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
    private val elapsedRealtimeMillis: () -> Long = monotonicMillis(),
) {
    private val mutableState = MutableStateFlow(AccountSecurityUiState())
    val state: StateFlow<AccountSecurityUiState> = mutableState.asStateFlow()

    private var resendDeadlineMillis = 0L
    private var resendCountdownJob: Job? = null

    init {
        scope.launch {
            sessionRepository.accountEmail.collect { email ->
                mutableState.update { it.copy(accountEmail = email) }
            }
        }
    }

    fun dispatch(event: AccountSecurityEvent) {
        when (event) {
            AccountSecurityEvent.OpenLinkEmail -> openLinkEmail()
            is AccountSecurityEvent.EmailChanged -> mutableState.update {
                it.copy(emailDraft = event.value.take(MAX_EMAIL_LENGTH), error = null)
            }

            AccountSecurityEvent.SubmitEmail -> submitEmail()
            is AccountSecurityEvent.CodeChanged -> mutableState.update {
                it.copy(codeDraft = filterPocketPassOtp(event.value), error = null)
            }

            AccountSecurityEvent.ContinueWithCode -> continueWithCode()
            AccountSecurityEvent.ResendCode -> resendCode()
            AccountSecurityEvent.OpenChangePassword -> openChangePassword()
            is AccountSecurityEvent.NewPasswordChanged -> mutableState.update {
                it.copy(newPassword = event.value.take(PASSWORD_MAX_LENGTH), error = null)
            }

            is AccountSecurityEvent.NewPasswordRepeatChanged -> mutableState.update {
                it.copy(newPasswordRepeat = event.value.take(PASSWORD_MAX_LENGTH), error = null)
            }

            AccountSecurityEvent.TogglePasswordVisibility -> mutableState.update {
                it.copy(showPassword = !it.showPassword)
            }

            AccountSecurityEvent.SubmitNewPassword -> submitNewPassword()
            AccountSecurityEvent.DismissNotice -> mutableState.update { it.copy(notice = null) }
            AccountSecurityEvent.Back -> backStep()
        }
    }

    fun reset() {
        stopCountdown()
        mutableState.update { AccountSecurityUiState(accountEmail = it.accountEmail) }
    }

    fun backStep(): Boolean {
        val current = mutableState.value
        if (current.submitting) return true
        val previous = when (current.step) {
            AccountSecurityStep.Overview -> return false
            AccountSecurityStep.LinkEmail -> AccountSecurityStep.Overview
            AccountSecurityStep.LinkEmailCode -> AccountSecurityStep.LinkEmail
            AccountSecurityStep.ChangePasswordCode -> AccountSecurityStep.Overview
            AccountSecurityStep.ChangePasswordEntry -> AccountSecurityStep.ChangePasswordCode
        }
        if (previous == AccountSecurityStep.Overview) stopCountdown()
        mutableState.update {
            it.copy(
                step = previous,
                error = null,
                codeDraft = if (previous == AccountSecurityStep.Overview) "" else it.codeDraft,
                newPassword = "",
                newPasswordRepeat = "",
                showPassword = false,
                resendSecondsRemaining =
                    if (previous == AccountSecurityStep.Overview) 0 else it.resendSecondsRemaining,
            )
        }
        return true
    }

    private fun openLinkEmail() {
        val current = mutableState.value
        if (current.submitting || !current.isUsernameAccount) return
        mutableState.update {
            it.copy(step = AccountSecurityStep.LinkEmail, emailDraft = "", codeDraft = "", error = null, notice = null)
        }
    }

    private fun submitEmail() {
        val current = mutableState.value
        if (current.submitting) return
        if (!isPocketPassEmailValid(current.emailDraft)) {
            fail("Enter a valid email address.")
            return
        }
        mutableState.update { it.copy(submitting = true, error = null) }
        scope.launch {
            when (val result = sessionRepository.requestEmailLink(current.normalizedEmail)) {
                is RepositoryResult.Success -> {
                    mutableState.update {
                        it.copy(step = AccountSecurityStep.LinkEmailCode, codeDraft = "", submitting = false)
                    }
                    startResendCountdown()
                }

                is RepositoryResult.Failure -> fail(result.error.linkEmailMessage())
            }
        }
    }

    private fun continueWithCode() {
        val current = mutableState.value
        if (!current.canContinueWithCode) return
        when (current.step) {
            AccountSecurityStep.LinkEmailCode -> verifyLinkCode(current)
            AccountSecurityStep.ChangePasswordCode -> mutableState.update {
                it.copy(
                    step = AccountSecurityStep.ChangePasswordEntry,
                    newPassword = "",
                    newPasswordRepeat = "",
                    showPassword = false,
                    error = null,
                )
            }

            else -> Unit
        }
    }

    private fun verifyLinkCode(current: AccountSecurityUiState) {
        mutableState.update { it.copy(submitting = true, error = null) }
        scope.launch {
            when (
                val result = sessionRepository.verifyEmailLink(
                    email = current.normalizedEmail,
                    sixDigitCode = current.codeDraft,
                )
            ) {
                is RepositoryResult.Success -> {
                    stopCountdown()
                    mutableState.update {
                        it.copy(
                            step = AccountSecurityStep.Overview,
                            emailDraft = "",
                            codeDraft = "",
                            submitting = false,
                            resendSecondsRemaining = 0,
                            notice = "Email linked. You can sign in with it from now on.",
                        )
                    }
                }

                is RepositoryResult.Failure -> fail(result.error.codeMessage())
            }
        }
    }

    private fun resendCode() {
        val current = mutableState.value
        if (!current.canResend) return
        when (current.step) {
            AccountSecurityStep.LinkEmailCode -> {
                mutableState.update { it.copy(submitting = true, error = null) }
                scope.launch {
                    when (val result = sessionRepository.requestEmailLink(current.normalizedEmail)) {
                        is RepositoryResult.Success -> {
                            mutableState.update { it.copy(submitting = false) }
                            startResendCountdown()
                        }

                        is RepositoryResult.Failure -> fail(result.error.linkEmailMessage())
                    }
                }
            }

            AccountSecurityStep.ChangePasswordCode -> requestReauthentication()
            else -> Unit
        }
    }

    private fun openChangePassword() {
        val current = mutableState.value
        if (current.submitting) return
        if (!current.canChangePassword) {
            fail("Link an email address first.")
            return
        }
        mutableState.update { it.copy(notice = null) }
        requestReauthentication()
    }

    private fun requestReauthentication() {
        mutableState.update { it.copy(submitting = true, error = null) }
        scope.launch {
            when (val result = sessionRepository.requestReauthentication()) {
                is RepositoryResult.Success -> {
                    mutableState.update {
                        it.copy(step = AccountSecurityStep.ChangePasswordCode, codeDraft = "", submitting = false)
                    }
                    startResendCountdown()
                }

                is RepositoryResult.Failure -> fail(result.error.linkEmailMessage())
            }
        }
    }

    private fun submitNewPassword() {
        val current = mutableState.value
        if (!current.canSubmitPassword || current.step != AccountSecurityStep.ChangePasswordEntry) return
        if (!isPocketPassPasswordValid(current.newPassword)) {
            fail(PASSWORD_RULE_MESSAGE)
            return
        }
        if (current.newPassword != current.newPasswordRepeat) {
            fail(PASSWORD_MISMATCH_MESSAGE)
            return
        }
        mutableState.update { it.copy(submitting = true, error = null) }
        scope.launch {
            when (
                val result = sessionRepository.changePassword(
                    newPassword = current.newPassword,
                    nonce = current.codeDraft,
                )
            ) {
                is RepositoryResult.Success -> {
                    stopCountdown()
                    mutableState.update {
                        it.copy(
                            step = AccountSecurityStep.Overview,
                            codeDraft = "",
                            newPassword = "",
                            newPasswordRepeat = "",
                            showPassword = false,
                            submitting = false,
                            resendSecondsRemaining = 0,
                            notice = "Password changed.",
                        )
                    }
                }

                is RepositoryResult.Failure -> when (result.error.kind) {
                    RepositoryFailureKind.Unauthorized,
                    RepositoryFailureKind.Forbidden,
                    -> mutableState.update {
                        it.copy(
                            step = AccountSecurityStep.ChangePasswordCode,
                            codeDraft = "",
                            newPassword = "",
                            newPasswordRepeat = "",
                            submitting = false,
                            error = "That code is incorrect or expired. Request a new one.",
                            errorShakeNonce = it.errorShakeNonce + 1,
                        )
                    }

                    RepositoryFailureKind.Validation -> fail(PASSWORD_RULE_MESSAGE)
                    else -> fail(result.error.linkEmailMessage())
                }
            }
        }
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(
                submitting = false,
                error = message,
                errorShakeNonce = it.errorShakeNonce + 1,
            )
        }
    }

    private fun RepositoryFailure.linkEmailMessage(): String =
        when (kind) {
            RepositoryFailureKind.Conflict -> "That email address is already used by another PocketPass account."
            RepositoryFailureKind.Validation -> "Enter a valid email address."
            RepositoryFailureKind.RateLimited -> "Too many attempts. Please wait and try again."
            RepositoryFailureKind.Offline -> "You're offline. Check your connection and try again."
            RepositoryFailureKind.Unauthorized -> "Sign in again before changing your account."
            RepositoryFailureKind.Misconfigured -> "This build cannot change account details."
            else -> "PocketPass could not send the code. Try again."
        }

    private fun RepositoryFailure.codeMessage(): String =
        when (kind) {
            RepositoryFailureKind.RateLimited -> "Too many attempts. Please wait and try again."
            RepositoryFailureKind.Offline -> "You're offline. Check your connection and try again."
            RepositoryFailureKind.Conflict -> "That email address is already used by another PocketPass account."
            RepositoryFailureKind.Unavailable,
            RepositoryFailureKind.Unknown,
            -> "PocketPass could not verify the code. Try again."

            else -> "That code is incorrect or expired."
        }

    private fun startResendCountdown() {
        resendCountdownJob?.cancel()
        resendDeadlineMillis = elapsedRealtimeMillis() + RESEND_DELAY_MILLIS
        resendCountdownJob = scope.launch {
            while (isActive) {
                val remainingMillis =
                    (resendDeadlineMillis - elapsedRealtimeMillis()).coerceAtLeast(0L)
                mutableState.update {
                    it.copy(resendSecondsRemaining = ceil(remainingMillis / 1_000.0).toInt())
                }
                if (remainingMillis == 0L) break
                delay(COUNTDOWN_TICK_MILLIS)
            }
        }
    }

    private fun stopCountdown() {
        resendCountdownJob?.cancel()
        resendCountdownJob = null
        resendDeadlineMillis = 0L
    }

    private companion object {
        const val RESEND_DELAY_MILLIS = 60_000L
        const val COUNTDOWN_TICK_MILLIS = 250L
    }
}

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
