package com.pocketpass.app.feature

import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSecurityStateHolderTest {
    @Test
    fun usernameAccountsLinkAnEmailWithACode() = runTest {
        val repository = FakeSessionRepository("simply@users.pocketpass.xyz")
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()

        assertTrue(holder.state.value.isUsernameAccount)
        assertEquals("simply", holder.state.value.username)
        assertFalse(holder.state.value.canChangePassword)

        holder.dispatch(AccountSecurityEvent.OpenLinkEmail)
        holder.dispatch(AccountSecurityEvent.EmailChanged(" Person@Example.com "))
        holder.dispatch(AccountSecurityEvent.SubmitEmail)
        runCurrent()

        assertEquals(listOf("person@example.com"), repository.linkRequests)
        assertEquals(AccountSecurityStep.LinkEmailCode, holder.state.value.step)
        assertEquals(60, holder.state.value.resendSecondsRemaining)

        holder.dispatch(AccountSecurityEvent.CodeChanged("12 34 56"))
        holder.dispatch(AccountSecurityEvent.ContinueWithCode)
        runCurrent()

        assertEquals("person@example.com" to "123456", repository.verifiedLink)
        assertEquals(AccountSecurityStep.Overview, holder.state.value.step)
        assertNotNull(holder.state.value.notice)
        assertEquals("person@example.com", holder.state.value.accountEmail)
        assertFalse(holder.state.value.isUsernameAccount)
        assertTrue(holder.state.value.canChangePassword)
    }

    @Test
    fun wrongLinkCodesStayOnTheCodeStep() = runTest {
        val repository = FakeSessionRepository("simply@users.pocketpass.xyz")
        repository.verifyLinkResult = RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Unauthorized))
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()
        holder.dispatch(AccountSecurityEvent.OpenLinkEmail)
        holder.dispatch(AccountSecurityEvent.EmailChanged("person@example.com"))
        holder.dispatch(AccountSecurityEvent.SubmitEmail)
        runCurrent()

        holder.dispatch(AccountSecurityEvent.CodeChanged("123456"))
        holder.dispatch(AccountSecurityEvent.ContinueWithCode)
        runCurrent()

        assertEquals(AccountSecurityStep.LinkEmailCode, holder.state.value.step)
        assertEquals("That code is incorrect or expired.", holder.state.value.error)
        assertEquals(1, holder.state.value.errorShakeNonce)
    }

    @Test
    fun changingThePasswordNeedsAnEmailFirst() = runTest {
        val repository = FakeSessionRepository("simply@users.pocketpass.xyz")
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()

        holder.dispatch(AccountSecurityEvent.OpenChangePassword)
        runCurrent()

        assertEquals(AccountSecurityStep.Overview, holder.state.value.step)
        assertEquals("Link an email address first.", holder.state.value.error)
        assertEquals(0, repository.reauthenticationRequests)
    }

    @Test
    fun emailAccountsChangeThePasswordAfterReauthentication() = runTest {
        val repository = FakeSessionRepository("person@example.com")
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()
        assertTrue(holder.state.value.canChangePassword)

        holder.dispatch(AccountSecurityEvent.OpenChangePassword)
        runCurrent()
        assertEquals(1, repository.reauthenticationRequests)
        assertEquals(AccountSecurityStep.ChangePasswordCode, holder.state.value.step)

        holder.dispatch(AccountSecurityEvent.CodeChanged("654321"))
        holder.dispatch(AccountSecurityEvent.ContinueWithCode)
        assertEquals(AccountSecurityStep.ChangePasswordEntry, holder.state.value.step)

        holder.dispatch(AccountSecurityEvent.NewPasswordChanged("short"))
        holder.dispatch(AccountSecurityEvent.NewPasswordRepeatChanged("short"))
        holder.dispatch(AccountSecurityEvent.SubmitNewPassword)
        assertEquals("Passwords need at least 8 characters.", holder.state.value.error)

        holder.dispatch(AccountSecurityEvent.NewPasswordChanged("hunter22"))
        holder.dispatch(AccountSecurityEvent.NewPasswordRepeatChanged("hunter23"))
        holder.dispatch(AccountSecurityEvent.SubmitNewPassword)
        assertEquals("The passwords do not match.", holder.state.value.error)

        holder.dispatch(AccountSecurityEvent.NewPasswordRepeatChanged("hunter22"))
        holder.dispatch(AccountSecurityEvent.SubmitNewPassword)
        runCurrent()

        assertEquals("hunter22" to "654321", repository.changedPassword)
        assertEquals(AccountSecurityStep.Overview, holder.state.value.step)
        assertEquals("Password changed.", holder.state.value.notice)
        assertEquals("", holder.state.value.newPassword)
    }

    @Test
    fun expiredNoncesReturnToTheCodeStep() = runTest {
        val repository = FakeSessionRepository("person@example.com")
        repository.changePasswordResult = RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Unauthorized))
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()
        holder.dispatch(AccountSecurityEvent.OpenChangePassword)
        runCurrent()
        holder.dispatch(AccountSecurityEvent.CodeChanged("654321"))
        holder.dispatch(AccountSecurityEvent.ContinueWithCode)
        holder.dispatch(AccountSecurityEvent.NewPasswordChanged("hunter22"))
        holder.dispatch(AccountSecurityEvent.NewPasswordRepeatChanged("hunter22"))
        holder.dispatch(AccountSecurityEvent.SubmitNewPassword)
        runCurrent()

        assertEquals(AccountSecurityStep.ChangePasswordCode, holder.state.value.step)
        assertEquals("", holder.state.value.codeDraft)
        assertNotNull(holder.state.value.error)
    }

    @Test
    fun backWalksThroughTheStepsAndReportsWhenAtTheOverview() = runTest {
        val repository = FakeSessionRepository("simply@users.pocketpass.xyz")
        val holder = AccountSecurityStateHolder(repository, backgroundScope) { 0L }
        runCurrent()
        assertFalse(holder.backStep())

        holder.dispatch(AccountSecurityEvent.OpenLinkEmail)
        holder.dispatch(AccountSecurityEvent.EmailChanged("person@example.com"))
        holder.dispatch(AccountSecurityEvent.SubmitEmail)
        runCurrent()
        assertEquals(AccountSecurityStep.LinkEmailCode, holder.state.value.step)

        assertTrue(holder.backStep())
        assertEquals(AccountSecurityStep.LinkEmail, holder.state.value.step)
        assertTrue(holder.backStep())
        assertEquals(AccountSecurityStep.Overview, holder.state.value.step)
        assertEquals(0, holder.state.value.resendSecondsRemaining)
        assertFalse(holder.backStep())
        assertNull(holder.state.value.error)
    }

    private class FakeSessionRepository(initialEmail: String?) : SessionRepository {
        override val sessionState: StateFlow<SessionState> =
            MutableStateFlow(SessionState.SignedOut)
        private val mutableEmail = MutableStateFlow(initialEmail)
        override val accountEmail: StateFlow<String?> = mutableEmail

        val linkRequests = mutableListOf<String>()
        var verifiedLink: Pair<String, String>? = null
        var verifyLinkResult: RepositoryResult<Unit>? = null
        var reauthenticationRequests = 0
        var changedPassword: Pair<String, String>? = null
        var changePasswordResult: RepositoryResult<Unit>? = null

        override suspend fun initialize(): RepositoryResult<SessionState> =
            RepositoryResult.Success(sessionState.value)

        override suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState> =
            RepositoryResult.Success(sessionState.value)

        override suspend fun signInWithDiscord(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun requestEmailOtp(email: String, createUser: Boolean): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun verifyEmailOtp(email: String, sixDigitCode: String): RepositoryResult<SessionState> =
            RepositoryResult.Success(sessionState.value)

        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun requestEmailLink(email: String): RepositoryResult<Unit> {
            linkRequests += email
            return RepositoryResult.Success(Unit)
        }

        override suspend fun verifyEmailLink(email: String, sixDigitCode: String): RepositoryResult<Unit> {
            verifiedLink = email to sixDigitCode
            verifyLinkResult?.let { return it }
            mutableEmail.value = email
            return RepositoryResult.Success(Unit)
        }

        override suspend fun requestReauthentication(): RepositoryResult<Unit> {
            reauthenticationRequests += 1
            return RepositoryResult.Success(Unit)
        }

        override suspend fun changePassword(newPassword: String, nonce: String): RepositoryResult<Unit> {
            changedPassword = newPassword to nonce
            return changePasswordResult ?: RepositoryResult.Success(Unit)
        }
    }
}
