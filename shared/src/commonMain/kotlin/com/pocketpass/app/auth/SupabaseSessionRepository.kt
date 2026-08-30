package com.pocketpass.app.auth

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SupabaseSessionRepository(
    private val remote: AuthRemoteDataSource,
    applicationScope: CoroutineScope,
) : SessionRepository {
    private val operationMutex = Mutex()
    private val mutableSessionState = MutableStateFlow<SessionState>(SessionState.Initializing)

    override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()

    init {
        applicationScope.launch {
            remote.status.collect { status ->
                mutableSessionState.update { current ->
                    status.toDomainState(previous = current)
                }
            }
        }
    }

    override suspend fun initialize(): RepositoryResult<SessionState> =
        operationMutex.withLock {
            try {
                val state = remote.initialize().toDomainState(mutableSessionState.value)
                mutableSessionState.value = state
                RepositoryResult.Success(state)
            } catch (error: Throwable) {
                val failure = error.toRepositoryFailure()
                val state = failure.toTerminalSessionState()
                mutableSessionState.value = state
                RepositoryResult.Failure(failure)
            }
        }

    override suspend fun handleAuthCallback(
        callbackUri: String,
    ): RepositoryResult<SessionState> =
        operationMutex.withLock {
            try {
                val authenticated = remote.handleAuthCallback(callbackUri)
                val state = authenticated.toDomainState(mutableSessionState.value)
                mutableSessionState.value = state
                RepositoryResult.Success(state)
            } catch (error: Throwable) {
                RepositoryResult.Failure(error.toRepositoryFailure())
            }
        }

    override suspend fun signInWithDiscord(): RepositoryResult<Unit> =
        operationMutex.withLock {
            try {
                remote.signInWithDiscord()
                RepositoryResult.Success(Unit)
            } catch (error: Throwable) {
                RepositoryResult.Failure(error.toRepositoryFailure())
            }
        }

    override suspend fun requestEmailOtp(
        email: String,
        createUser: Boolean,
    ): RepositoryResult<Unit> =
        operationMutex.withLock {
            try {
                remote.requestEmailOtp(
                    email = email,
                    createUser = createUser,
                )
                RepositoryResult.Success(Unit)
            } catch (error: Throwable) {
                RepositoryResult.Failure(error.toRepositoryFailure())
            }
        }

    override suspend fun verifyEmailOtp(
        email: String,
        sixDigitCode: String,
    ): RepositoryResult<SessionState> =
        operationMutex.withLock {
            try {
                val authenticated = remote.verifyEmailOtp(email, sixDigitCode)
                val state = authenticated.toDomainState(mutableSessionState.value)
                mutableSessionState.value = state
                RepositoryResult.Success(state)
            } catch (error: Throwable) {
                RepositoryResult.Failure(error.toRepositoryFailure())
            }
        }

    override suspend fun signOut(): RepositoryResult<Unit> =
        operationMutex.withLock {
            try {
                remote.signOut()
                RepositoryResult.Success(Unit)
            } catch (error: Throwable) {
                RepositoryResult.Failure(error.toRepositoryFailure())
            } finally {
                mutableSessionState.value = SessionState.SignedOut
            }
        }

    private fun RemoteAuthStatus.toDomainState(previous: SessionState): SessionState =
        when (this) {
            RemoteAuthStatus.Initializing -> when (previous) {
                is SessionState.Authenticated,
                is SessionState.OfflineWithCachedSession,
                -> previous

                else -> SessionState.Initializing
            }
            RemoteAuthStatus.SignedOut -> SessionState.SignedOut
            is RemoteAuthStatus.Authenticated -> SessionState.Authenticated(UserId(userId))
            is RemoteAuthStatus.RefreshFailed -> {
                val failure = error.toRepositoryFailure().let {
                    if (isNetworkFailure) {
                        it.copy(
                            kind = RepositoryFailureKind.Offline,
                            retryable = true,
                        )
                    } else {
                        it
                    }
                }
                val cachedUserId = when (previous) {
                    is SessionState.Authenticated -> previous.userId
                    is SessionState.OfflineWithCachedSession -> previous.userId
                    else -> this.cachedUserId?.let(::UserId)
                }
                if (cachedUserId != null) {
                    SessionState.OfflineWithCachedSession(cachedUserId, failure)
                } else {
                    SessionState.ReauthenticationRequired(failure)
                }
            }
        }

    private fun RepositoryFailure.toTerminalSessionState(): SessionState =
        if (kind == RepositoryFailureKind.Misconfigured) {
            SessionState.ConfigurationError(
                message ?: "PocketPass authentication is not configured",
            )
        } else {
            SessionState.ReauthenticationRequired(this)
        }
}
