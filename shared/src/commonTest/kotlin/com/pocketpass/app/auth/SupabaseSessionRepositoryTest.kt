package com.pocketpass.app.auth

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseSessionRepositoryTest {
    @Test
    fun initializesAuthenticatedSession() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.Authenticated(USER_ID),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)

        val result = repository.initialize()
        runCurrent()

        assertEquals(
            RepositoryResult.Success(SessionState.Authenticated(UserId(USER_ID))),
            result,
        )
        assertEquals(SessionState.Authenticated(UserId(USER_ID)), repository.sessionState.value)
    }

    @Test
    fun transientInitializingNeverDropsAnAuthenticatedAccount() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.Authenticated(USER_ID),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)
        repository.initialize()
        runCurrent()

        remote.emit(RemoteAuthStatus.Initializing)
        runCurrent()
        assertEquals(SessionState.Authenticated(UserId(USER_ID)), repository.sessionState.value)

        remote.emit(
            RemoteAuthStatus.RefreshFailed(
                error = IOException("network unavailable"),
                isNetworkFailure = true,
            ),
        )
        runCurrent()
        remote.emit(RemoteAuthStatus.Initializing)
        runCurrent()
        val state = repository.sessionState.value
        assertTrue(state is SessionState.OfflineWithCachedSession)
        assertEquals(UserId(USER_ID), (state as SessionState.OfflineWithCachedSession).userId)

        remote.emit(RemoteAuthStatus.SignedOut)
        runCurrent()
        remote.emit(RemoteAuthStatus.Initializing)
        runCurrent()
        assertEquals(SessionState.Initializing, repository.sessionState.value)
    }

    @Test
    fun networkRefreshFailureKeepsCachedIdentityOffline() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.Authenticated(USER_ID),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)
        repository.initialize()
        runCurrent()

        remote.emit(
            RemoteAuthStatus.RefreshFailed(
                error = IOException("network unavailable"),
                isNetworkFailure = true,
            ),
        )
        runCurrent()

        val state = repository.sessionState.value
        assertTrue(state is SessionState.OfflineWithCachedSession)
        state as SessionState.OfflineWithCachedSession
        assertEquals(UserId(USER_ID), state.userId)
        assertTrue(state.failure.retryable)
    }

    @Test
    fun coldStartRefreshFailureUsesStoredIdentityOffline() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.RefreshFailed(
                error = IOException("network unavailable"),
                isNetworkFailure = true,
                cachedUserId = USER_ID,
            ),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)

        val result = repository.initialize()
        runCurrent()

        val state = repository.sessionState.value
        assertTrue(state is SessionState.OfflineWithCachedSession)
        state as SessionState.OfflineWithCachedSession
        assertEquals(RepositoryResult.Success(state), result)
        assertEquals(UserId(USER_ID), state.userId)
        assertEquals(RepositoryFailureKind.Offline, state.failure.kind)
        assertTrue(state.failure.retryable)
    }

    @Test
    fun coldStartRefreshFailureWithoutStoredSessionRequiresReauthentication() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.RefreshFailed(
                error = IOException("network unavailable"),
                isNetworkFailure = true,
            ),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)

        repository.initialize()
        runCurrent()

        assertTrue(repository.sessionState.value is SessionState.ReauthenticationRequired)
    }

    @Test
    fun signOutMovesRepositoryToSignedOut() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.Authenticated(USER_ID),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)
        repository.initialize()

        val result = repository.signOut()

        assertEquals(RepositoryResult.Success(Unit), result)
        assertEquals(SessionState.SignedOut, repository.sessionState.value)
        assertTrue(remote.didSignOut)
    }

    @Test
    fun failedRemoteSignOutStillClearsLocalSessionState() = runTest {
        val remote = FakeAuthRemoteDataSource(
            initial = RemoteAuthStatus.Authenticated(USER_ID),
            signOutError = IOException("offline"),
        )
        val repository = SupabaseSessionRepository(remote, backgroundScope)
        repository.initialize()

        val result = repository.signOut()

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(SessionState.SignedOut, repository.sessionState.value)
        assertTrue(remote.didSignOut)
    }

    private class FakeAuthRemoteDataSource(
        initial: RemoteAuthStatus,
        private val signOutError: Throwable? = null,
    ) : AuthRemoteDataSource {
        private val mutableStatus = MutableStateFlow(initial)
        override val status: Flow<RemoteAuthStatus> = mutableStatus
        var didSignOut = false

        override suspend fun initialize(): RemoteAuthStatus = mutableStatus.value

        override suspend fun handleAuthCallback(
            callbackUri: String,
        ): RemoteAuthStatus.Authenticated {
            val authenticated = RemoteAuthStatus.Authenticated(USER_ID)
            mutableStatus.value = authenticated
            return authenticated
        }

        override suspend fun signInWithDiscord() = Unit

        override suspend fun requestEmailOtp(
            email: String,
            createUser: Boolean,
        ) = Unit

        override suspend fun verifyEmailOtp(
            email: String,
            code: String,
        ): RemoteAuthStatus.Authenticated {
            val authenticated = RemoteAuthStatus.Authenticated(USER_ID)
            mutableStatus.value = authenticated
            return authenticated
        }

        override suspend fun signOut() {
            didSignOut = true
            signOutError?.let { throw it }
            mutableStatus.value = RemoteAuthStatus.SignedOut
        }

        fun emit(status: RemoteAuthStatus) {
            mutableStatus.value = status
        }
    }

    companion object {
        private const val USER_ID = "f343f8bc-34e7-477e-a0f3-fc796fbb9d7b"
    }
}
