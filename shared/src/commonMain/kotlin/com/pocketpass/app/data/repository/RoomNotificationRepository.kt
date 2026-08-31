package com.pocketpass.app.data.repository

import com.pocketpass.app.data.local.dao.NotificationDao
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.data.repository.remote.NotificationRemoteDataSource
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomNotificationRepository(
    private val notificationDao: NotificationDao,
    private val remote: NotificationRemoteDataSource,
    private val mutationStore: ProductionMutationStore,
    private val pendingOperationScheduler: PendingOperationScheduler =
        PendingOperationScheduler.None,
) : NotificationRepository {
    override fun observeNotifications(
        accountId: UserId,
    ): Flow<List<PocketPassNotification>> =
        notificationDao.observeForAccount(accountId.value)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun refreshNotifications(
        accountId: UserId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchNotifications(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                notificationDao.replaceFromRemote(
                    accountId = accountId.value,
                    remoteRows = result.value.map(PocketPassNotification::toEntity),
                )
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun markRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        mutationStore.enqueueMarkNotificationRead(command)
            .toNotificationRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun markAllRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        mutationStore.enqueueMarkAllNotificationsRead(command)
            .toNotificationRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun delete(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        val notification = notificationDao.get(
            accountId = command.accountId.value,
            notificationId = command.notificationId.value,
        )?.toDomain()
        if (notification == null) return@repositoryCall RepositoryResult.Success(Unit)
        if (!notification.canDelete) {
            return@repositoryCall RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Respond to this friend request before deleting it",
                    retryable = false,
                ),
            )
        }
        mutationStore.enqueueDeleteNotification(command)
            .toNotificationRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun recordFriendRequestResponse(
        accountId: UserId,
        requestId: String,
        accepted: Boolean,
        respondedAt: Instant,
    ): RepositoryResult<Unit> = repositoryCall {
        notificationDao.recordFriendRequestResponse(
            accountId = accountId.value,
            requestId = requestId,
            status = if (accepted) {
                com.pocketpass.app.domain.model.FriendRequestNotificationStatus.Accepted.name
            } else {
                com.pocketpass.app.domain.model.FriendRequestNotificationStatus.Declined.name
            },
            respondedAtEpochMillis = respondedAt.toEpochMilliseconds(),
        )
        RepositoryResult.Success(Unit)
    }
}

private fun OptimisticMutationResult<Unit>.toNotificationRepositoryResult():
    RepositoryResult<Unit> = when (this) {
    is OptimisticMutationResult.Enqueued -> RepositoryResult.Success(value)
    is OptimisticMutationResult.AlreadyEnqueued -> RepositoryResult.Success(value)
    is OptimisticMutationResult.Conflict -> RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Conflict,
            message = reason,
            retryable = false,
        ),
    )
}
