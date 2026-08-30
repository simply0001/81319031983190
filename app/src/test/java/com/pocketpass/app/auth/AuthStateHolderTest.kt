package com.pocketpass.app.auth

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStateHolderTest {
    @Test
    fun emailRequestsOtpDirectlyAndStartsResendCountdown() = runTest {
        val repository = FakeSessionRepository()
        var elapsed = 1_000L
        val holder = AuthStateHolder(repository, backgroundScope) { elapsed }

        holder.dispatch(AuthEvent.ContinueWithEmail)
        holder.dispatch(AuthEvent.EmailChanged("  Person@Example.COM "))
        holder.dispatch(AuthEvent.SubmitEmail)

        assertTrue(holder.state.value.isSubmitting)
        assertEquals(AuthStep.Email, holder.state.value.step)

        runCurrent()

        assertEquals(AuthStep.Otp, holder.state.value.step)
        assertEquals("person@example.com", repository.requestedEmail)
        assertTrue(repository.createUser)
        assertEquals(60, holder.state.value.resendSecondsRemaining)

        elapsed += 30_200L
        advanceTimeBy(500L)
        assertEquals(30, holder.state.value.resendSecondsRemaining)
    }

    @Test
    fun duplicateSubmitIsIgnoredWhileRequestIsActive() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }

        holder.dispatch(AuthEvent.ContinueWithEmail)
        holder.dispatch(AuthEvent.EmailChanged("person@example.com"))
        holder.dispatch(AuthEvent.SubmitEmail)
        holder.dispatch(AuthEvent.SubmitEmail)
        runCurrent()

        assertEquals(1, repository.requestCalls)
    }

    @Test
    fun otpFiltersInputAndOnlyVerifyButtonSubmits() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        advanceToOtp(holder)

        holder.dispatch(AuthEvent.OtpChanged("a12 34-567"))
        assertEquals("123456", holder.state.value.otpCode)
        assertTrue(holder.state.value.canVerify)
        assertEquals(0, repository.verifyCalls)

        holder.dispatch(AuthEvent.VerifyOtp)
        runCurrent()

        assertEquals(1, repository.verifyCalls)
        assertEquals("123456", repository.verifiedCode)
        assertEquals(SessionState.Authenticated(TEST_USER), repository.sessionState.value)
        assertEquals(AuthUiState(), holder.state.value)
    }

    @Test
    fun directResendPreservesCodeAndRestartsCountdown() = runTest {
        val repository = FakeSessionRepository()
        var elapsed = 0L
        val holder = AuthStateHolder(repository, backgroundScope) { elapsed }
        advanceToOtp(holder)
        holder.dispatch(AuthEvent.OtpChanged("123456"))

        elapsed = 60_000L
        advanceTimeBy(500L)
        assertTrue(holder.state.value.canResend)

        holder.dispatch(AuthEvent.ResendOtp)
        runCurrent()

        assertEquals(2, repository.requestCalls)
        assertEquals("123456", holder.state.value.otpCode)
        assertEquals(60, holder.state.value.resendSecondsRemaining)
    }

    @Test
    fun changingEmailClearsOtpErrorsAndResendState() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        advanceToOtp(holder)
        holder.dispatch(AuthEvent.OtpChanged("123456"))

        holder.dispatch(AuthEvent.ChangeEmail)

        assertEquals(AuthStep.Email, holder.state.value.step)
        assertEquals("", holder.state.value.otpCode)
        assertEquals(0, holder.state.value.resendSecondsRemaining)
        assertFalse(holder.state.value.isSubmitting)
        assertEquals(null, holder.state.value.error)
    }

    @Test
    fun failuresUseStablePocketPassErrorCodes() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }

        holder.dispatch(AuthEvent.ContinueWithEmail)
        holder.dispatch(AuthEvent.EmailChanged("invalid"))
        holder.dispatch(AuthEvent.SubmitEmail)
        assertEquals(ERROR_INVALID_EMAIL, holder.state.value.error?.code)

        val requestCases = listOf(
            RepositoryFailureKind.RateLimited to ERROR_RATE_LIMITED,
            RepositoryFailureKind.Offline to ERROR_OFFLINE,
            RepositoryFailureKind.Unavailable to ERROR_SERVICE_UNAVAILABLE,
            RepositoryFailureKind.Misconfigured to ERROR_CONFIGURATION,
        )
        for ((kind, expectedCode) in requestCases) {
            repository.requestResult = RepositoryResult.Failure(RepositoryFailure(kind))
            holder.dispatch(AuthEvent.EmailChanged("person@example.com"))
            holder.dispatch(AuthEvent.SubmitEmail)
            runCurrent()
            assertEquals(expectedCode, holder.state.value.error?.code)
        }

        repository.requestResult = RepositoryResult.Success(Unit)
        holder.dispatch(AuthEvent.SubmitEmail)
        runCurrent()
        repository.verifyResult = RepositoryResult.Failure(
            RepositoryFailure(RepositoryFailureKind.Validation),
        )
        holder.dispatch(AuthEvent.OtpChanged("123456"))
        holder.dispatch(AuthEvent.VerifyOtp)
        runCurrent()
        assertEquals(ERROR_INVALID_OTP, holder.state.value.error?.code)

        holder.dispatch(AuthEvent.ChangeEmail)
        holder.dispatch(AuthEvent.Back)
        repository.discordResult = RepositoryResult.Failure(
            RepositoryFailure(RepositoryFailureKind.Unknown),
        )
        holder.dispatch(AuthEvent.ContinueWithDiscord)
        runCurrent()
        assertEquals(ERROR_DISCORD_OAUTH, holder.state.value.error?.code)
    }

    private suspend fun advanceToOtp(holder: AuthStateHolder) {
        holder.dispatch(AuthEvent.ContinueWithEmail)
        holder.dispatch(AuthEvent.EmailChanged("person@example.com"))
        holder.dispatch(AuthEvent.SubmitEmail)
        kotlinx.coroutines.yield()
        assertEquals(AuthStep.Otp, holder.state.value.step)
    }

    private class FakeSessionRepository : SessionRepository {
        private val mutableSessionState =
            MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val sessionState: StateFlow<SessionState> = mutableSessionState

        var requestedEmail: String? = null
        var createUser = false
        var verifiedCode: String? = null
        var requestCalls = 0
        var verifyCalls = 0
        var requestResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var verifyResult: RepositoryResult<SessionState>? = null
        var discordResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun initialize(): RepositoryResult<SessionState> =
            RepositoryResult.Success(mutableSessionState.value)

        override suspend fun handleAuthCallback(
            callbackUri: String,
        ): RepositoryResult<SessionState> =
            RepositoryResult.Success(mutableSessionState.value)

        override suspend fun signInWithDiscord(): RepositoryResult<Unit> = discordResult

        override suspend fun requestEmailOtp(
            email: String,
            createUser: Boolean,
        ): RepositoryResult<Unit> {
            requestCalls += 1
            requestedEmail = email
            this.createUser = createUser
            return requestResult
        }

        override suspend fun verifyEmailOtp(
            email: String,
            sixDigitCode: String,
        ): RepositoryResult<SessionState> {
            verifyCalls += 1
            verifiedCode = sixDigitCode
            verifyResult?.let { return it }
            val authenticated = SessionState.Authenticated(TEST_USER)
            mutableSessionState.value = authenticated
            return RepositoryResult.Success(authenticated)
        }

        override suspend fun signOut(): RepositoryResult<Unit> {
            mutableSessionState.value = SessionState.SignedOut
            return RepositoryResult.Success(Unit)
        }
    }

    private companion object {
        val TEST_USER = UserId("f343f8bc-34e7-477e-a0f3-fc796fbb9d7b")
    }
}
