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
import org.junit.Assert.assertNull
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

    @Test
    fun creatingAnAccountValidatesBeforeCallingTheBackend() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        holder.dispatch(AuthEvent.ToggleCredentialsMode)
        assertTrue(holder.state.value.isCreatingAccount)

        holder.dispatch(AuthEvent.IdentifierChanged("Bad Name!"))
        assertEquals("badname", holder.state.value.identifier)
        holder.dispatch(AuthEvent.IdentifierChanged("ab"))
        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        holder.dispatch(AuthEvent.PasswordRepeatChanged("hunter22"))
        holder.dispatch(AuthEvent.SubmitCredentials)
        assertEquals(ERROR_INVALID_USERNAME, holder.state.value.error?.code)

        holder.dispatch(AuthEvent.IdentifierChanged("simply"))
        holder.dispatch(AuthEvent.PasswordChanged("short"))
        holder.dispatch(AuthEvent.PasswordRepeatChanged("short"))
        holder.dispatch(AuthEvent.SubmitCredentials)
        assertEquals(ERROR_WEAK_PASSWORD, holder.state.value.error?.code)

        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        holder.dispatch(AuthEvent.PasswordRepeatChanged("hunter23"))
        holder.dispatch(AuthEvent.SubmitCredentials)
        assertEquals(ERROR_PASSWORD_MISMATCH, holder.state.value.error?.code)

        runCurrent()
        assertEquals(0, repository.availabilityChecks)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun creatingAnAccountUsesTheLoginAddressAndMarksSetupPending() = runTest {
        val repository = FakeSessionRepository()
        var pendingUser: UserId? = null
        val holder = AuthStateHolder(
            sessionRepository = repository,
            scope = backgroundScope,
            onPasswordAccountCreated = { pendingUser = it },
        ) { 0L }
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        holder.dispatch(AuthEvent.ToggleCredentialsMode)
        holder.dispatch(AuthEvent.IdentifierChanged("Simply.01"))
        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        holder.dispatch(AuthEvent.PasswordRepeatChanged("hunter22"))
        holder.dispatch(AuthEvent.SubmitCredentials)
        assertTrue(holder.state.value.isSubmitting)
        runCurrent()

        assertEquals(listOf("simply.01"), repository.availabilityQueries)
        assertEquals(1, repository.signUpCalls)
        assertEquals("simply.01@users.pocketpass.xyz", repository.signUpEmail)
        assertEquals("hunter22", repository.signUpPassword)
        assertEquals("simply.01", repository.signUpUsername)
        assertEquals(TEST_USER, pendingUser)
        assertEquals(SessionState.Authenticated(TEST_USER), repository.sessionState.value)
        assertEquals(AuthUiState(), holder.state.value)
    }

    @Test
    fun takenUsernamesAreReportedWithoutSigningUp() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        holder.dispatch(AuthEvent.ToggleCredentialsMode)
        holder.dispatch(AuthEvent.IdentifierChanged("simply"))
        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        holder.dispatch(AuthEvent.PasswordRepeatChanged("hunter22"))

        repository.availability = RepositoryResult.Success(false)
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals(ERROR_USERNAME_TAKEN, holder.state.value.error?.code)
        assertEquals(0, repository.signUpCalls)

        repository.availability = RepositoryResult.Success(true)
        repository.signUpResult = RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Conflict))
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals(ERROR_USERNAME_TAKEN, holder.state.value.error?.code)
        assertEquals(1, repository.signUpCalls)

        repository.signUpResult = RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Unavailable))
        repository.availabilityAfterSignUp = RepositoryResult.Success(false)
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals(ERROR_USERNAME_TAKEN, holder.state.value.error?.code)

        repository.availabilityAfterSignUp = RepositoryResult.Success(true)
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals(ERROR_SERVICE_UNAVAILABLE, holder.state.value.error?.code)
        assertFalse(holder.state.value.isSubmitting)
    }

    @Test
    fun signingInMapsUsernamesAndPassesEmailsThrough() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        assertEquals(AuthStep.Credentials, holder.state.value.step)
        assertFalse(holder.state.value.isCreatingAccount)

        holder.dispatch(AuthEvent.IdentifierChanged(" Person@Example.com "))
        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        repository.signInResult = RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Unauthorized))
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals("person@example.com", repository.signInEmail)
        assertEquals(ERROR_INVALID_CREDENTIALS, holder.state.value.error?.code)
        assertEquals(AuthStep.Credentials, holder.state.value.step)

        repository.signInResult = null
        holder.dispatch(AuthEvent.IdentifierChanged("Simply"))
        holder.dispatch(AuthEvent.SubmitCredentials)
        runCurrent()
        assertEquals("simply@users.pocketpass.xyz", repository.signInEmail)
        assertEquals("hunter22", repository.signInPassword)
        assertEquals(SessionState.Authenticated(TEST_USER), repository.sessionState.value)
        assertEquals(AuthUiState(), holder.state.value)
    }

    @Test
    fun leavingTheCredentialsScreenForgetsPasswords() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        holder.dispatch(AuthEvent.IdentifierChanged("simply"))
        holder.dispatch(AuthEvent.PasswordChanged("hunter22"))
        holder.dispatch(AuthEvent.TogglePasswordVisibility)
        assertTrue(holder.state.value.showPassword)

        holder.dispatch(AuthEvent.Back)

        assertEquals(AuthStep.Method, holder.state.value.step)
        assertEquals("", holder.state.value.password)
        assertFalse(holder.state.value.showPassword)
        assertEquals("simply", holder.state.value.identifier)
        assertNull(holder.state.value.error)
    }

    @Test
    fun landingPicksTheIntentBeforeTheMethod() = runTest {
        val repository = FakeSessionRepository()
        val holder = AuthStateHolder(repository, backgroundScope) { 0L }

        holder.dispatch(AuthEvent.ChooseSignUp)
        assertEquals(AuthStep.Method, holder.state.value.step)
        assertEquals(AuthIntent.SignUp, holder.state.value.intent)

        holder.dispatch(AuthEvent.IdentifierChanged("Spob.Jr"))
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        assertEquals(AuthStep.Credentials, holder.state.value.step)
        assertTrue(holder.state.value.isCreatingAccount)
        assertEquals("spob.jr", holder.state.value.identifier)

        holder.dispatch(AuthEvent.Back)
        assertEquals(AuthStep.Method, holder.state.value.step)
        holder.dispatch(AuthEvent.Back)
        assertEquals(AuthStep.Landing, holder.state.value.step)

        holder.dispatch(AuthEvent.ChooseSignIn)
        assertEquals(AuthIntent.SignIn, holder.state.value.intent)
        holder.dispatch(AuthEvent.ContinueWithCredentials)
        assertFalse(holder.state.value.isCreatingAccount)

        holder.dispatch(AuthEvent.Back)
        holder.dispatch(AuthEvent.ContinueWithEmail)
        assertEquals(AuthStep.Email, holder.state.value.step)
        holder.dispatch(AuthEvent.Back)
        assertEquals(AuthStep.Method, holder.state.value.step)
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
        var availability: RepositoryResult<Boolean> = RepositoryResult.Success(true)
        var availabilityAfterSignUp: RepositoryResult<Boolean>? = null
        var availabilityChecks = 0
        val availabilityQueries = mutableListOf<String>()
        var signUpCalls = 0
        var signUpEmail: String? = null
        var signUpPassword: String? = null
        var signUpUsername: String? = null
        var signUpResult: RepositoryResult<SessionState>? = null
        var signInEmail: String? = null
        var signInPassword: String? = null
        var signInResult: RepositoryResult<SessionState>? = null

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
            return authenticate()
        }

        override suspend fun isUsernameAvailable(username: String): RepositoryResult<Boolean> {
            availabilityChecks += 1
            availabilityQueries += username
            val afterSignUp = availabilityAfterSignUp
            return if (signUpCalls > 0 && afterSignUp != null) afterSignUp else availability
        }

        override suspend fun signUpWithPassword(
            loginEmail: String,
            password: String,
            username: String,
        ): RepositoryResult<SessionState> {
            signUpCalls += 1
            signUpEmail = loginEmail
            signUpPassword = password
            signUpUsername = username
            signUpResult?.let { return it }
            return authenticate()
        }

        override suspend fun signInWithPassword(
            loginEmail: String,
            password: String,
        ): RepositoryResult<SessionState> {
            signInEmail = loginEmail
            signInPassword = password
            signInResult?.let { return it }
            return authenticate()
        }

        override suspend fun signOut(): RepositoryResult<Unit> {
            mutableSessionState.value = SessionState.SignedOut
            return RepositoryResult.Success(Unit)
        }

        private fun authenticate(): RepositoryResult<SessionState> {
            val authenticated = SessionState.Authenticated(TEST_USER)
            mutableSessionState.value = authenticated
            return RepositoryResult.Success(authenticated)
        }
    }

    private companion object {
        val TEST_USER = UserId("f343f8bc-34e7-477e-a0f3-fc796fbb9d7b")
    }
}
