package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.NotificationAction
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationFeatureState(
    val notifications: LoadState<List<PocketPassNotification>> = LoadState.Loading,
    val operatingIds: Set<NotificationId> = emptySet(),
    val error: String? = null,
) {
    val unreadCount: Int
        get() = (notifications as? LoadState.Data)
            ?.value
            ?.count(PocketPassNotification::isUnread)
            ?: 0
}

class NotificationStateHolder(
    accountId: Flow<UserId?>,
    private val notificationRepository: NotificationRepository,
    private val friendsRepository: MutableFriendsRepository,
    private val scope: CoroutineScope,
) {
    private val activeAccountId = accountId.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    private val notifications = MutableStateFlow<LoadState<List<PocketPassNotification>>>(
        LoadState.Loading,
    )
    private val operatingIds = MutableStateFlow<Set<NotificationId>>(emptySet())
    private val localRequestStatuses = MutableStateFlow<
        Map<NotificationId, FriendRequestNotificationStatus>
    >(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<NotificationFeatureState> = combine(
        notifications,
        operatingIds,
        localRequestStatuses,
        error,
    ) { rows, pending, localStatuses, operationError ->
        val visible = when (rows) {
            is LoadState.Data -> rows.copy(
                value = rows.value.map { notification ->
                    localStatuses[notification.id]?.let { status ->
                        notification.copy(
                            friendRequestStatus = status,
                            readAt = notification.readAt ?: Clock.System.now(),
                        )
                    } ?: notification
                },
            )
            is LoadState.Error -> rows
            LoadState.Loading -> LoadState.Loading
        }
        NotificationFeatureState(
            notifications = visible,
            operatingIds = pending,
            error = operationError,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = NotificationFeatureState(),
    )

    init {
        scope.launch {
            activeAccountId.collectLatest { account ->
                operatingIds.value = emptySet()
                localRequestStatuses.value = emptyMap()
                error.value = null
                if (account == null) {
                    notifications.value = LoadState.Data(emptyList())
                    return@collectLatest
                }
                launch {
                    notificationRepository.observeNotifications(account).collect { rows ->
                        notifications.value = LoadState.Data(rows)
                    }
                }
                notificationRepository.refreshNotifications(account)
            }
        }
    }

    fun refresh() {
        val account = activeAccountId.value ?: return
        scope.launch { notificationRepository.refreshNotifications(account) }
    }

    fun markRead(notificationId: NotificationId) {
        val account = activeAccountId.value ?: return
        val notification = currentNotifications().firstOrNull { it.id == notificationId }
            ?: return
        if (!notification.isUnread || notificationId in operatingIds.value) return
        operate(notificationId) {
            notificationRepository.markRead(
                MarkNotificationReadCommand(
                    accountId = account,
                    notificationId = notificationId,
                    readAt = Clock.System.now(),
                ),
            )
        }
    }

    fun markAllRead() {
        val account = activeAccountId.value ?: return
        scope.launch {
            when (
                val result = notificationRepository.markAllRead(
                    MarkAllNotificationsReadCommand(
                        accountId = account,
                        readAt = Clock.System.now(),
                    ),
                )
            ) {
                is RepositoryResult.Success -> error.value = null
                is RepositoryResult.Failure -> {
                    error.value = result.error.message ?: "Notifications could not be updated."
                }
            }
        }
    }

    fun delete(notificationId: NotificationId) {
        val account = activeAccountId.value ?: return
        val notification = currentNotifications().firstOrNull { it.id == notificationId }
            ?: return
        if (!notification.canDelete) {
            error.value = "Respond to this friend request before deleting it."
            return
        }
        operate(notificationId) {
            notificationRepository.delete(
                DeleteNotificationCommand(
                    accountId = account,
                    notificationId = notificationId,
                    deletedAt = Clock.System.now(),
                ),
            )
        }
    }

    fun clearAll() {
        currentNotifications()
            .asSequence()
            .filter(PocketPassNotification::canDelete)
            .map(PocketPassNotification::id)
            .forEach(::delete)
    }

    fun respondToFriendRequest(notificationId: NotificationId, accept: Boolean) {
        val account = activeAccountId.value ?: return
        val notification = currentNotifications().firstOrNull { it.id == notificationId }
            ?: return
        val action = notification.action as? NotificationAction.RespondToFriendRequest
            ?: return
        operate(notificationId) {
            when (
                val result = friendsRepository.respondToFriendRequest(
                    RespondToFriendRequestCommand(
                        accountId = account,
                        requestId = action.requestId,
                        requester = action.requester,
                        accept = accept,
                        respondedAt = Clock.System.now(),
                    ),
                )
            ) {
                is RepositoryResult.Failure -> result
                is RepositoryResult.Success -> {
                    val respondedAt = Clock.System.now()
                    notificationRepository.recordFriendRequestResponse(
                        accountId = account,
                        requestId = action.requestId,
                        accepted = accept,
                        respondedAt = respondedAt,
                    )
                    localRequestStatuses.update {
                        it + (
                            notificationId to if (accept) {
                                FriendRequestNotificationStatus.Accepted
                            } else {
                                FriendRequestNotificationStatus.Declined
                            }
                        )
                    }
                    notificationRepository.markRead(
                        MarkNotificationReadCommand(
                            accountId = account,
                            notificationId = notificationId,
                            readAt = respondedAt,
                        ),
                    )
                }
            }
        }
    }

    private fun operate(
        notificationId: NotificationId,
        operation: suspend () -> RepositoryResult<*>,
    ) {
        if (notificationId in operatingIds.value) return
        operatingIds.update { it + notificationId }
        error.value = null
        scope.launch {
            when (val result = operation()) {
                is RepositoryResult.Success -> error.value = null
                is RepositoryResult.Failure -> {
                    error.value = result.error.message ?: "Notification action failed."
                }
            }
            operatingIds.update { it - notificationId }
        }
    }

    private fun currentNotifications(): List<PocketPassNotification> =
        (state.value.notifications as? LoadState.Data)?.value.orEmpty()
}
