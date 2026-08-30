package com.pocketpass.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.dao.ConversationDao
import com.pocketpass.app.data.local.dao.ConversationMemberDao
import com.pocketpass.app.data.local.dao.FriendDao
import com.pocketpass.app.data.local.dao.FriendCodeDao
import com.pocketpass.app.data.local.dao.MessageDao
import com.pocketpass.app.data.local.dao.OutboxEnqueueResult
import com.pocketpass.app.data.local.dao.ProfileDao
import com.pocketpass.app.data.local.dao.SyncCursorDao
import com.pocketpass.app.data.local.entity.SyncCursorEntity
import com.pocketpass.app.data.local.entity.FriendCodeEntity
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.data.repository.remote.FriendsRemoteDataSource
import com.pocketpass.app.data.repository.remote.MessageRemoteDataSource
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.data.local.entity.LocalConversationKinds
import com.pocketpass.app.data.local.entity.LocalDeliveryStates
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.domain.model.groupMessagePreview
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.repository.SyncRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SyncState
import com.pocketpass.app.sync.MessageOutboxStore
import com.pocketpass.app.sync.OutboxProcessor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface PendingOperationScheduler {
    fun schedule(accountId: UserId)

    companion object {
        val None = PendingOperationScheduler { }
    }
}

class RoomProfileRepository(
    private val profileDao: ProfileDao,
    private val remote: ProfileRemoteDataSource,
    private val mutationStore: ProductionMutationStore,
    private val reconciler: RoomRepositoryReconciler,
    private val pendingOperationScheduler: PendingOperationScheduler =
        PendingOperationScheduler.None,
) : MutableProfileRepository {
    override fun observeProfile(userId: UserId): Flow<UserProfile?> =
        profileDao.observe(userId.value)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override suspend fun refreshProfile(
        userId: UserId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchProfile(userId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                reconciler.reconcileProfile(userId, result.value)
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile> = repositoryCall {
        mutationStore.enqueueProfileUpdate(command)
            .toRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile> = repositoryCall {
        when (val result = remote.completeAccountSetup(command)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                profileDao.upsert(result.value.toEntity())
                result
            }
        }
    }

    override suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile> = repositoryCall {
        when (val result = remote.renameProfile(command)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                profileDao.upsert(result.value.toEntity())
                result
            }
        }
    }
}

class RoomFriendsRepository(
    private val friendDao: FriendDao,
    private val friendCodeDao: FriendCodeDao,
    private val remote: FriendsRemoteDataSource,
    private val mutationStore: ProductionMutationStore,
    private val reconciler: RoomRepositoryReconciler,
    private val pendingOperationScheduler: PendingOperationScheduler =
        PendingOperationScheduler.None,
) : MutableFriendsRepository {
    override fun observeFriends(accountId: UserId): Flow<List<Friend>> =
        friendDao.observeForOwner(accountId.value)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeMyFriendCode(accountId: UserId): Flow<FriendCode?> =
        friendCodeDao.observe(accountId.value)
            .map { row -> row?.code?.let(::FriendCode) }
            .distinctUntilChanged()

    override suspend fun refreshFriends(
        accountId: UserId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchFriends(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                reconciler.reconcileFriends(accountId, result.value)
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun refreshMyFriendCode(
        accountId: UserId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchMyFriendCode(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                friendCodeDao.upsert(
                    FriendCodeEntity(
                        accountId = accountId.value,
                        code = result.value.value,
                        updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun resolveFriendCode(
        accountId: UserId,
        friendCode: FriendCode,
    ): RepositoryResult<UserProfile> = repositoryCall {
        remote.resolveFriendCode(accountId, friendCode)
    }

    override suspend fun sendFriendRequest(
        command: SendFriendRequestCommand,
    ): RepositoryResult<Friend> = repositoryCall {
        mutationStore.enqueueFriendRequest(command)
            .toRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun respondToFriendRequest(
        command: RespondToFriendRequestCommand,
    ): RepositoryResult<Friend?> = repositoryCall {
        mutationStore.enqueueFriendRequestResponse(command)
            .toRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun removeFriend(
        command: RemoveFriendCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        mutationStore.enqueueFriendRemoval(command)
            .toRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }

    override suspend fun setUserBlocked(
        command: SetUserBlockCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        mutationStore.enqueueUserBlock(command)
            .toRepositoryResult()
            .also { result ->
                if (result is RepositoryResult.Success) {
                    pendingOperationScheduler.schedule(command.accountId)
                }
            }
    }
}

private sealed interface LocalMessageMutationResult {
    data class Success(val message: Message) : LocalMessageMutationResult

    data object Missing : LocalMessageMutationResult

    data object Conflict : LocalMessageMutationResult
}

class RoomMessageRepository(
    private val database: PocketPassDatabase,
    private val conversationDao: ConversationDao,
    private val conversationMemberDao: ConversationMemberDao,
    private val messageDao: MessageDao,
    private val remote: MessageRemoteDataSource,
    private val messageOutboxStore: MessageOutboxStore,
    private val reconciler: RoomRepositoryReconciler,
    private val pendingOperationScheduler: PendingOperationScheduler =
        PendingOperationScheduler.None,
) : MessageRepository {
    override fun observeConversations(
        accountId: UserId,
    ): Flow<List<ConversationSummary>> =
        combine(
            conversationDao.observeForAccount(accountId.value),
            conversationMemberDao.observeForAccount(accountId.value),
        ) { rows, members ->
            val membersByConversation = members.groupBy { it.conversationId }
            rows.map { row ->
                row.toDomain(
                    membersByConversation[row.conversationId].orEmpty().map { it.toDomain() },
                )
            }
        }.distinctUntilChanged()

    override suspend fun createGroupConversation(
        command: CreateGroupConversationCommand,
    ): RepositoryResult<ConversationId> = repositoryCall {
        when (val result = remote.createGroupConversation(command)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                refreshConversations(command.accountId)
                RepositoryResult.Success(result.value)
            }
        }
    }

    override suspend fun addGroupMembers(
        command: AddGroupMembersCommand,
    ): RepositoryResult<Unit> = refreshAfter(command.accountId) {
        remote.addGroupMembers(command)
    }

    override suspend fun removeGroupMember(
        command: RemoveGroupMemberCommand,
    ): RepositoryResult<Unit> = refreshAfter(command.accountId) {
        remote.removeGroupMember(command)
    }

    override suspend fun renameGroupConversation(
        command: RenameGroupConversationCommand,
    ): RepositoryResult<Unit> = refreshAfter(command.accountId) {
        remote.renameGroupConversation(command)
    }

    override suspend fun leaveGroupConversation(
        command: LeaveGroupConversationCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.leaveGroupConversation(command)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                database.withTransaction {
                    conversationDao.delete(command.accountId.value, command.conversationId.value)
                    conversationMemberDao.deleteForConversation(
                        command.accountId.value,
                        command.conversationId.value,
                    )
                    messageDao.deleteForConversation(
                        command.accountId.value,
                        command.conversationId.value,
                    )
                }
                RepositoryResult.Success(Unit)
            }
        }
    }

    private suspend fun refreshAfter(
        accountId: UserId,
        operation: suspend () -> RepositoryResult<Unit>,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = operation()) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                refreshConversations(accountId)
                RepositoryResult.Success(Unit)
            }
        }
    }

    override fun observeMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): Flow<List<Message>> =
        messageDao.observeForConversation(
            accountId = accountId.value,
            conversationId = conversationId.value,
        )
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun refreshConversations(
        accountId: UserId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchConversations(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                reconciler.upsertConversations(accountId, result.value)
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun refreshMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): RepositoryResult<Unit> = repositoryCall {
        when (val result = remote.fetchMessages(accountId, conversationId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                require(result.value.all { it.conversationId == conversationId }) {
                    "Remote message page contains another conversation"
                }
                reconciler.upsertMessages(accountId, result.value)
                reconciler.refreshConversationPreview(accountId, conversationId)
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun sendMessage(
        command: SendMessageCommand,
    ): RepositoryResult<Message> = repositoryCall {
        val localResult = database.withTransaction {
            val conversation = conversationDao.get(
                accountId = command.accountId.value,
                conversationId = command.conversationId.value,
            ) ?: return@withTransaction LocalMessageEnqueueResult.MissingConversation

            when (val enqueue = messageOutboxStore.enqueue(command)) {
                is OutboxEnqueueResult.Conflict ->
                    LocalMessageEnqueueResult.Conflict(enqueue.reason)

                is OutboxEnqueueResult.Enqueued,
                is OutboxEnqueueResult.AlreadyEnqueued,
                -> {
                    conversationDao.updateLatestMessage(
                        accountId = command.accountId.value,
                        conversationId = command.conversationId.value,
                        preview = if (conversation.kind == LocalConversationKinds.GROUP) {
                            groupMessagePreview(
                                body = command.body,
                                senderId = command.accountId,
                                accountId = command.accountId,
                                members = emptyList(),
                            )
                        } else {
                            command.body
                        },
                        createdAtEpochMillis = command.clientCreatedAt.toEpochMilliseconds(),
                    )
                    val message = messageDao.get(
                        accountId = command.accountId.value,
                        messageId = command.messageId.value,
                    ) ?: error("Outbox enqueue completed without an optimistic message")
                    LocalMessageEnqueueResult.Success(message.toDomain())
                }
            }
        }
        when (localResult) {
            is LocalMessageEnqueueResult.Success -> {
                pendingOperationScheduler.schedule(command.accountId)
                RepositoryResult.Success(localResult.message)
            }

            LocalMessageEnqueueResult.MissingConversation -> RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Conversation does not exist in the local cache",
                    retryable = false,
                ),
            )

            is LocalMessageEnqueueResult.Conflict -> RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = localResult.reason,
                    retryable = false,
                ),
            )
        }
    }

    override suspend fun markConversationRead(
        command: MarkConversationReadCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        val exists = database.withTransaction {
            if (
                conversationDao.get(
                    accountId = command.accountId.value,
                    conversationId = command.conversationId.value,
                ) == null
            ) {
                false
            } else {
                conversationDao.markRead(
                    accountId = command.accountId.value,
                    conversationId = command.conversationId.value,
                )
                messageOutboxStore.enqueueRead(command)
                true
            }
        }
        if (exists) {
            pendingOperationScheduler.schedule(command.accountId)
            RepositoryResult.Success(Unit)
        } else {
            RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Conversation does not exist in the local cache",
                    retryable = false,
                ),
            )
        }
    }

    override suspend fun editMessage(
        command: EditMessageCommand,
    ): RepositoryResult<Message> = mutateOwnMessage(
        accountId = command.accountId,
        conversationId = command.conversationId,
        messageId = command.messageId,
    ) {
        messageDao.applyEdit(
            accountId = command.accountId.value,
            messageId = command.messageId.value,
            body = command.body,
            editedAtEpochMillis = command.editedAt.toEpochMilliseconds(),
        )
        messageOutboxStore.enqueueEdit(command)
    }

    override suspend fun deleteMessage(
        command: DeleteMessageCommand,
    ): RepositoryResult<Message> = mutateOwnMessage(
        accountId = command.accountId,
        conversationId = command.conversationId,
        messageId = command.messageId,
    ) {
        messageDao.markDeleted(
            accountId = command.accountId.value,
            messageId = command.messageId.value,
            deletedAtEpochMillis = command.deletedAt.toEpochMilliseconds(),
        )
        messageOutboxStore.enqueueDelete(command)
    }

    private suspend fun mutateOwnMessage(
        accountId: UserId,
        conversationId: ConversationId,
        messageId: MessageId,
        apply: suspend () -> Boolean,
    ): RepositoryResult<Message> = repositoryCall {
        val outcome = database.withTransaction {
            val existing = messageDao.get(accountId.value, messageId.value)
                ?: return@withTransaction LocalMessageMutationResult.Missing
            if (
                existing.conversationId != conversationId.value ||
                existing.senderId != accountId.value ||
                existing.deliveryState != LocalDeliveryStates.SYNCED ||
                existing.deletedAtEpochMillis != null ||
                !apply()
            ) {
                return@withTransaction LocalMessageMutationResult.Conflict
            }
            reconciler.refreshConversationPreview(accountId, conversationId)
            val updated = messageDao.get(accountId.value, messageId.value)
                ?: error("Message vanished while it was being changed")
            LocalMessageMutationResult.Success(updated.toDomain())
        }
        when (outcome) {
            is LocalMessageMutationResult.Success -> {
                pendingOperationScheduler.schedule(accountId)
                RepositoryResult.Success(outcome.message)
            }

            LocalMessageMutationResult.Missing -> RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Message does not exist in the local cache",
                    retryable = false,
                ),
            )

            LocalMessageMutationResult.Conflict -> RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Only your delivered messages can be changed",
                    retryable = false,
                ),
            )
        }
    }

    suspend fun refreshAllMessages(
        accountId: UserId,
    ): RepositoryResult<Unit> {
        val conversations = observeConversations(accountId).first()
        conversations.forEach { conversation ->
            val result = refreshMessages(accountId, conversation.id)
            if (result is RepositoryResult.Failure) return result
        }
        return RepositoryResult.Success(Unit)
    }
}

class RoomSyncRepository(
    private val profiles: RoomProfileRepository,
    private val friends: RoomFriendsRepository,
    private val messages: RoomMessageRepository,
    private val notifications: NotificationRepository,
    private val encounters: RoomEncounterRepository,
    private val outboxProcessor: OutboxProcessor,
    private val syncCursorDao: SyncCursorDao,
    private val clock: Clock = Clock.System,
) : SyncRepository {
    private val synchronizationMutex = Mutex()
    private val mutableSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = mutableSyncState.asStateFlow()

    override suspend fun synchronize(
        accountId: UserId,
    ): RepositoryResult<Unit> = synchronizationMutex.withLock {
        val startedAt = clock.now()
        mutableSyncState.value = SyncState.Running(startedAt)
        try {
            var firstFailure: RepositoryFailure? = null
            val drain = outboxProcessor.drain(accountId)
            if (drain.retryableFailures > 0 || drain.reachedBatchLimit) {
                firstFailure = RepositoryFailure(
                    kind = RepositoryFailureKind.Unavailable,
                    message = "Some pending changes still need to be uploaded",
                    retryable = true,
                )
            } else if (drain.permanentFailures > 0) {
                firstFailure = RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Some pending changes were rejected",
                    retryable = false,
                )
            }

            suspend fun record(result: RepositoryResult<Unit>) {
                if (firstFailure == null && result is RepositoryResult.Failure) {
                    firstFailure = result.error
                }
            }

            record(profiles.refreshProfile(accountId))
            record(friends.refreshFriends(accountId))
            record(friends.refreshMyFriendCode(accountId))
            record(messages.refreshConversations(accountId))
            record(messages.refreshAllMessages(accountId))
            record(notifications.refreshNotifications(accountId))
            record(encounters.refresh(accountId))

            val failure = firstFailure
            if (failure == null) {
                val completedAt = clock.now()
                syncCursorDao.upsert(
                    SyncCursorEntity(
                        accountId = accountId.value,
                        stream = FULL_SYNC_STREAM,
                        cursor = completedAt.toString(),
                        updatedAtEpochMillis = completedAt.toEpochMilliseconds(),
                    ),
                )
                mutableSyncState.value = SyncState.Succeeded(completedAt)
                RepositoryResult.Success(Unit)
            } else {
                mutableSyncState.value = SyncState.Failed(
                    failure = failure,
                    nextRetryAt = if (failure.retryable) {
                        clock.now().plus((RETRY_HINT_SECONDS).seconds)
                    } else {
                        null
                    },
                )
                RepositoryResult.Failure(failure)
            }
        } catch (cancelled: CancellationException) {
            mutableSyncState.value = SyncState.Idle
            throw cancelled
        } catch (error: Throwable) {
            val failure = error.toLocalRepositoryFailure()
            mutableSyncState.value = SyncState.Failed(
                failure = failure,
                nextRetryAt = if (failure.retryable) {
                    clock.now().plus((RETRY_HINT_SECONDS).seconds)
                } else {
                    null
                },
            )
            RepositoryResult.Failure(failure)
        }
    }

    private companion object {
        const val FULL_SYNC_STREAM = "full_sync"
        const val RETRY_HINT_SECONDS = 10L
    }
}

private sealed interface LocalMessageEnqueueResult {
    data class Success(val message: Message) : LocalMessageEnqueueResult
    data object MissingConversation : LocalMessageEnqueueResult
    data class Conflict(val reason: String) : LocalMessageEnqueueResult
}

private fun <T> OptimisticMutationResult<T>.toRepositoryResult(): RepositoryResult<T> =
    when (this) {
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

internal suspend fun <T> repositoryCall(
    operation: suspend () -> RepositoryResult<T>,
): RepositoryResult<T> = try {
    operation()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    RepositoryResult.Failure(error.toLocalRepositoryFailure())
}

private fun Throwable.toLocalRepositoryFailure(): RepositoryFailure {
    val kind = when (this) {
        is SQLiteConstraintException -> RepositoryFailureKind.Conflict
        is IllegalArgumentException -> RepositoryFailureKind.Validation
        else -> RepositoryFailureKind.Unknown
    }
    return RepositoryFailure(
        kind = kind,
        message = message ?: "The local cache operation failed",
        retryable = false,
    )
}
