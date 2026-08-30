package com.pocketpass.app.domain.state

import com.pocketpass.app.domain.model.UserId
import kotlin.time.Instant

enum class RepositoryFailureKind {
    Offline,
    Unauthorized,
    Forbidden,
    NotFound,
    Conflict,
    Validation,
    RateLimited,
    Unavailable,
    Misconfigured,
    Unknown,
}

data class RepositoryFailure(
    val kind: RepositoryFailureKind,
    val message: String? = null,
    val retryable: Boolean = kind == RepositoryFailureKind.Offline ||
        kind == RepositoryFailureKind.RateLimited ||
        kind == RepositoryFailureKind.Unavailable,
)

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val error: RepositoryFailure) : RepositoryResult<Nothing>
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Data<T>(val value: T, val isRefreshing: Boolean = false) : LoadState<T>
    data class Error<T>(
        val failure: RepositoryFailure,
        val cachedValue: T? = null,
    ) : LoadState<T>
}

sealed interface SessionState {
    data object Initializing : SessionState
    data object SignedOut : SessionState
    data class Authenticated(val userId: UserId) : SessionState

    data class OfflineWithCachedSession(
        val userId: UserId,
        val failure: RepositoryFailure,
    ) : SessionState

    data class ReauthenticationRequired(
        val failure: RepositoryFailure? = null,
    ) : SessionState

    data class ConfigurationError(val message: String) : SessionState
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val startedAt: Instant) : SyncState
    data class Succeeded(val completedAt: Instant) : SyncState

    data class Failed(
        val failure: RepositoryFailure,
        val nextRetryAt: Instant?,
    ) : SyncState

    data class Paused(val reason: String) : SyncState
}

sealed interface PendingState {
    data object Synced : PendingState
    data class Queued(val operationId: String) : PendingState
    data class Sending(val operationId: String, val attempt: Int) : PendingState

    data class Failed(
        val operationId: String,
        val retryable: Boolean,
        val message: String?,
    ) : PendingState
}

typealias PendingMutationState = PendingState
