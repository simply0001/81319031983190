package com.pocketpass.app.sync

import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.data.repository.MutationAcknowledgementReconciler
import com.pocketpass.app.data.repository.ProductionOperationKinds
import com.pocketpass.app.data.repository.ProductionOperationPayloadCodec
import com.pocketpass.app.data.repository.remote.FriendsRemoteDataSource
import com.pocketpass.app.data.repository.remote.MessageRemoteDataSource
import com.pocketpass.app.data.repository.remote.ProductionRemoteDataSources
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.data.repository.remote.EmptyShopRemoteDataSource
import com.pocketpass.app.data.repository.remote.ShopRemoteDataSource
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopPurchaseOutcome
import com.pocketpass.app.domain.model.ShopPurchaseRejection
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePendingOperationExecutorTest {
    @Test
    fun acknowledgedProfileUpdateReconcilesCanonicalServerValue() = runTest {
        val canonical = profile().copy(displayName = "Canonical")
        val profileRemote = RecordingProfileRemote(canonical)
        val reconciler = RecordingReconciler()
        val executor = executor(profileRemote, reconciler)
        val command = UpdateProfileCommand(
            accountId = ACCOUNT,
            profile = profile(),
            clientOperationId = OPERATION,
            changedAt = NOW,
        )

        val result = executor.execute(
            operation(
                kind = ProductionOperationKinds.UPDATE_PROFILE,
                aggregateId = ACCOUNT.value,
                payload = ProductionOperationPayloadCodec.encode(command),
            ),
        )

        assertSame(OutboxExecutionResult.Acknowledged, result)
        assertEquals(command, profileRemote.lastUpdate)
        assertEquals(canonical, reconciler.profile)
    }

    @Test
    fun mismatchedDurableIdentityIsRejectedBeforeRemoteCall() = runTest {
        val profileRemote = RecordingProfileRemote(profile())
        val executor = executor(profileRemote, RecordingReconciler())
        val command = UpdateProfileCommand(
            accountId = ACCOUNT,
            profile = profile(),
            clientOperationId = OPERATION,
            changedAt = NOW,
        )

        val result = executor.execute(
            operation(
                kind = ProductionOperationKinds.UPDATE_PROFILE,
                aggregateId = "another-profile",
                payload = ProductionOperationPayloadCodec.encode(command),
            ),
        )

        assertEquals("INVALID_PAYLOAD", (result as OutboxExecutionResult.PermanentFailure).code)
        assertEquals(null, profileRemote.lastUpdate)
    }

    @Test
    fun completedPurchaseMarksOwnershipSyncedWithReceiptBalance() = runTest {
        val shopRemote = RecordingShopRemote(
            RepositoryResult.Success(
                ShopPurchaseOutcome.Completed(itemId = ITEM, balance = 80, purchasedAt = NOW),
            ),
        )
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, shopRemote)
        val command = purchaseCommand()

        val result = executor.execute(purchaseOperation(command))

        assertSame(OutboxExecutionResult.Acknowledged, result)
        assertEquals(command, shopRemote.lastPurchase)
        assertEquals(
            listOf(AcknowledgedPurchase(ACCOUNT, ITEM, 80, NOW)),
            reconciler.acknowledgedPurchases,
        )
        assertTrue(reconciler.rejectedPurchases.isEmpty())
    }

    @Test
    fun insufficientTokensRollsBackTheOptimisticPurchase() = runTest {
        val shopRemote = RecordingShopRemote(
            RepositoryResult.Success(
                ShopPurchaseOutcome.Rejected(ShopPurchaseRejection.InsufficientTokens),
            ),
        )
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, shopRemote)

        val result = executor.execute(purchaseOperation(purchaseCommand()))

        assertEquals(
            OutboxExecutionResult.PermanentFailure(
                code = "INSUFFICIENT_TOKENS",
                message = "InsufficientTokens",
            ),
            result,
        )
        assertEquals(
            listOf(RejectedPurchase(ACCOUNT, ITEM, OPERATION.value)),
            reconciler.rejectedPurchases,
        )
        assertTrue(reconciler.acknowledgedPurchases.isEmpty())
    }

    @Test
    fun alreadyOwnedPurchaseIsAcknowledgedWithoutAReceipt() = runTest {
        val shopRemote = RecordingShopRemote(
            RepositoryResult.Success(ShopPurchaseOutcome.AlreadyOwned),
        )
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, shopRemote)

        val result = executor.execute(purchaseOperation(purchaseCommand()))

        assertSame(OutboxExecutionResult.Acknowledged, result)
        assertEquals(
            listOf(AcknowledgedPurchase(ACCOUNT, ITEM, null, null)),
            reconciler.acknowledgedPurchases,
        )
    }

    @Test
    fun offlinePurchaseStaysQueuedWithoutRollback() = runTest {
        val shopRemote = RecordingShopRemote(
            RepositoryResult.Failure(
                RepositoryFailure(kind = RepositoryFailureKind.Offline, retryable = true),
            ),
        )
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, shopRemote)

        val result = executor.execute(purchaseOperation(purchaseCommand()))

        assertTrue(result is OutboxExecutionResult.RetryableFailure)
        assertTrue(reconciler.acknowledgedPurchases.isEmpty())
        assertTrue(reconciler.rejectedPurchases.isEmpty())
    }

    @Test
    fun purchaseIdentityMismatchIsInvalidPayloadAndRollsBack() = runTest {
        val shopRemote = RecordingShopRemote(
            RepositoryResult.Success(ShopPurchaseOutcome.AlreadyOwned),
        )
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, shopRemote)

        val result = executor.execute(
            purchaseOperation(purchaseCommand(), aggregateId = "item-other"),
        )

        assertEquals("INVALID_PAYLOAD", (result as OutboxExecutionResult.PermanentFailure).code)
        assertEquals(null, shopRemote.lastPurchase)
        assertEquals(
            listOf(RejectedPurchase(ACCOUNT, "item-other", OPERATION.value)),
            reconciler.rejectedPurchases,
        )
    }

    @Test
    fun acknowledgedEditReconcilesTheCanonicalMessage() = runTest {
        val canonical = message(body = "Canonical", editedAt = NOW)
        val messageRemote = RecordingMessageRemote(edit = RepositoryResult.Success(canonical))
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, messageRemote = messageRemote)
        val command = editCommand()

        val result = executor.execute(editOperation(command))

        assertSame(OutboxExecutionResult.Acknowledged, result)
        assertEquals(command, messageRemote.lastEdit)
        assertEquals(canonical, reconciler.message)
    }

    @Test
    fun editIdentityMismatchIsInvalidPayloadBeforeRemoteCall() = runTest {
        val messageRemote = RecordingMessageRemote(edit = RepositoryResult.Success(message()))
        val executor = executor(
            RecordingProfileRemote(profile()),
            RecordingReconciler(),
            messageRemote = messageRemote,
        )

        val result = executor.execute(editOperation(editCommand(), aggregateId = "another-message"))

        assertEquals("INVALID_PAYLOAD", (result as OutboxExecutionResult.PermanentFailure).code)
        assertEquals(null, messageRemote.lastEdit)
    }

    @Test
    fun deleteResponseWithoutDeletedAtIsAPermanentMismatch() = runTest {
        val messageRemote = RecordingMessageRemote(delete = RepositoryResult.Success(message()))
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, messageRemote = messageRemote)

        val result = executor.execute(deleteOperation(deleteCommand()))

        assertEquals("REMOTE_RESPONSE_MISMATCH", (result as OutboxExecutionResult.PermanentFailure).code)
        assertEquals(null, reconciler.message)
    }

    @Test
    fun acknowledgedDeleteReconcilesTheSoftDeletedRow() = runTest {
        val canonical = message(body = "Message deleted", deletedAt = NOW)
        val messageRemote = RecordingMessageRemote(delete = RepositoryResult.Success(canonical))
        val reconciler = RecordingReconciler()
        val executor = executor(RecordingProfileRemote(profile()), reconciler, messageRemote = messageRemote)

        val result = executor.execute(deleteOperation(deleteCommand()))

        assertSame(OutboxExecutionResult.Acknowledged, result)
        assertEquals(canonical, reconciler.message)
    }

    @Test
    fun offlineEditStaysRetryable() = runTest {
        val executor = executor(RecordingProfileRemote(profile()), RecordingReconciler())

        val result = executor.execute(editOperation(editCommand()))

        assertTrue(result is OutboxExecutionResult.RetryableFailure)
    }

    private fun executor(
        profileRemote: ProfileRemoteDataSource,
        reconciler: MutationAcknowledgementReconciler,
        shopRemote: ShopRemoteDataSource = EmptyShopRemoteDataSource,
        messageRemote: MessageRemoteDataSource = NoOpMessageRemote,
    ) = RemotePendingOperationExecutor(
        remote = ProductionRemoteDataSources(
            profiles = profileRemote,
            friends = NoOpFriendsRemote,
            messages = messageRemote,
            shop = shopRemote,
        ),
        reconciler = reconciler,
    )

    private fun editCommand() = EditMessageCommand(
        accountId = ACCOUNT,
        conversationId = CONVERSATION,
        messageId = MESSAGE,
        clientOperationId = OPERATION,
        body = "Edited",
        editedAt = NOW,
    )

    private fun deleteCommand() = DeleteMessageCommand(
        accountId = ACCOUNT,
        conversationId = CONVERSATION,
        messageId = MESSAGE,
        clientOperationId = OPERATION,
        deletedAt = NOW,
    )

    private fun editOperation(
        command: EditMessageCommand,
        aggregateId: String = command.messageId.value,
    ) = operation(
        kind = LocalOperationKinds.EDIT_MESSAGE,
        aggregateId = aggregateId,
        payload = BinaryMessageEditPayloadCodec.encode(
            MessageEditPayload(
                accountId = command.accountId,
                conversationId = command.conversationId,
                messageId = command.messageId,
                body = command.body,
                editedAt = command.editedAt,
            ),
        ),
        payloadVersion = BinaryMessageEditPayloadCodec.version,
    )

    private fun deleteOperation(command: DeleteMessageCommand) = operation(
        kind = LocalOperationKinds.DELETE_MESSAGE,
        aggregateId = command.messageId.value,
        payload = BinaryMessageDeletePayloadCodec.encode(
            MessageDeletePayload(
                accountId = command.accountId,
                conversationId = command.conversationId,
                messageId = command.messageId,
                deletedAt = command.deletedAt,
            ),
        ),
        payloadVersion = BinaryMessageDeletePayloadCodec.version,
    )

    private fun message(
        body: String = "Edited",
        editedAt: Instant? = null,
        deletedAt: Instant? = null,
    ) = Message(
        id = MESSAGE,
        conversationId = CONVERSATION,
        senderId = ACCOUNT,
        clientOperationId = OPERATION,
        body = body,
        createdAt = NOW,
        editedAt = editedAt,
        deletedAt = deletedAt,
    )

    private fun purchaseCommand() = PurchaseShopItemCommand(
        accountId = ACCOUNT,
        itemId = ITEM,
        priceTokens = 20,
        clientOperationId = OPERATION,
        requestedAt = NOW,
    )

    private fun purchaseOperation(
        command: PurchaseShopItemCommand,
        aggregateId: String = command.itemId,
    ) = operation(
        kind = ProductionOperationKinds.PURCHASE_SHOP_ITEM,
        aggregateId = aggregateId,
        payload = ProductionOperationPayloadCodec.encode(command),
    )

    private fun operation(
        kind: String,
        aggregateId: String,
        payload: String,
        payloadVersion: Int = ProductionOperationPayloadCodec.VERSION,
    ) = PendingOperationEntity(
        operationId = OPERATION.value,
        accountId = ACCOUNT.value,
        idempotencyKey = OPERATION.value,
        kind = kind,
        aggregateId = aggregateId,
        payload = payload,
        payloadVersion = payloadVersion,
        state = LocalOutboxStates.PENDING,
        attemptCount = 0,
        createdAtEpochMillis = NOW.toEpochMilliseconds(),
        nextAttemptAtEpochMillis = NOW.toEpochMilliseconds(),
        leaseUntilEpochMillis = null,
        leaseToken = null,
        completedAtEpochMillis = null,
        lastErrorCode = null,
        lastErrorMessage = null,
    )

    private fun profile() = UserProfile(
        userId = ACCOUNT,
        displayName = "Local",
        avatar = null,
        updatedAt = NOW,
    )

    private class RecordingProfileRemote(
        private val canonical: UserProfile,
    ) : ProfileRemoteDataSource {
        var lastUpdate: UpdateProfileCommand? = null

        override suspend fun fetchProfile(
            userId: UserId,
        ): RepositoryResult<UserProfile?> = RepositoryResult.Success(canonical)

        override suspend fun updateProfile(
            command: UpdateProfileCommand,
        ): RepositoryResult<UserProfile> {
            lastUpdate = command
            return RepositoryResult.Success(canonical)
        }

        override suspend fun completeAccountSetup(
            command: com.pocketpass.app.domain.model.AccountSetupCommand,
        ): RepositoryResult<UserProfile> = RepositoryResult.Success(canonical)

        override suspend fun renameProfile(
            command: com.pocketpass.app.domain.model.RenameProfileCommand,
        ): RepositoryResult<UserProfile> = RepositoryResult.Success(canonical)

        override suspend fun touchLastSeen(): RepositoryResult<Instant> =
            RepositoryResult.Success(Instant.fromEpochSeconds(0))
    }

    private class RecordingReconciler : MutationAcknowledgementReconciler {
        var profile: UserProfile? = null
        var message: Message? = null
        val acknowledgedPurchases = mutableListOf<AcknowledgedPurchase>()
        val rejectedPurchases = mutableListOf<RejectedPurchase>()

        override suspend fun reconcileAcknowledgedProfile(profile: UserProfile) {
            this.profile = profile
        }

        override suspend fun reconcileAcknowledgedMessage(
            accountId: UserId,
            message: Message,
        ) {
            this.message = message
        }

        override suspend fun reconcileAcknowledgedPurchase(
            accountId: UserId,
            itemId: String,
            balance: Int?,
            purchasedAt: Instant?,
        ) {
            acknowledgedPurchases += AcknowledgedPurchase(accountId, itemId, balance, purchasedAt)
        }

        override suspend fun reconcileRejectedPurchase(
            accountId: UserId,
            itemId: String,
            operationId: String,
        ) {
            rejectedPurchases += RejectedPurchase(accountId, itemId, operationId)
        }
    }

    private data class AcknowledgedPurchase(
        val accountId: UserId,
        val itemId: String,
        val balance: Int?,
        val purchasedAt: Instant?,
    )

    private data class RejectedPurchase(
        val accountId: UserId,
        val itemId: String,
        val operationId: String,
    )

    private class RecordingShopRemote(
        private val purchaseResult: RepositoryResult<ShopPurchaseOutcome>,
    ) : ShopRemoteDataSource {
        var lastPurchase: PurchaseShopItemCommand? = null

        override suspend fun fetchCatalog(): RepositoryResult<List<ShopCategory>> =
            RepositoryResult.Success(emptyList())

        override suspend fun fetchTokenBalance(accountId: UserId): RepositoryResult<Int> =
            RepositoryResult.Success(0)

        override suspend fun fetchOwnedItems(
            accountId: UserId,
        ): RepositoryResult<List<OwnedShopItem>> = RepositoryResult.Success(emptyList())

        override suspend fun fetchSupporterStatus(
            accountId: UserId,
        ): RepositoryResult<Instant?> = RepositoryResult.Success(null)

        override suspend fun purchaseItem(
            command: PurchaseShopItemCommand,
        ): RepositoryResult<ShopPurchaseOutcome> {
            lastPurchase = command
            return purchaseResult
        }
    }

    private object NoOpFriendsRemote : FriendsRemoteDataSource {
        override suspend fun fetchFriends(
            accountId: UserId,
        ): RepositoryResult<List<Friend>> = RepositoryResult.Success(emptyList())

        override suspend fun sendFriendRequest(
            command: SendFriendRequestCommand,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun respondToFriendRequest(
            command: RespondToFriendRequestCommand,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun removeFriend(
            command: RemoveFriendCommand,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun setUserBlocked(
            command: SetUserBlockCommand,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private object NoOpMessageRemote : MessageRemoteDataSource {
        override suspend fun fetchConversations(
            accountId: UserId,
        ): RepositoryResult<List<ConversationSummary>> =
            RepositoryResult.Success(emptyList())

        override suspend fun fetchMessages(
            accountId: UserId,
            conversationId: ConversationId,
        ): RepositoryResult<List<Message>> = RepositoryResult.Success(emptyList())

        override suspend fun sendMessage(
            command: SendMessageCommand,
        ): RepositoryResult<Message> = unavailableMessage()

        override suspend fun editMessage(
            command: EditMessageCommand,
        ): RepositoryResult<Message> = unavailableMessage()

        override suspend fun deleteMessage(
            command: DeleteMessageCommand,
        ): RepositoryResult<Message> = unavailableMessage()
    }

    private class RecordingMessageRemote(
        private val edit: RepositoryResult<Message> = unavailableMessage(),
        private val delete: RepositoryResult<Message> = unavailableMessage(),
    ) : MessageRemoteDataSource {
        var lastEdit: EditMessageCommand? = null
        var lastDelete: DeleteMessageCommand? = null

        override suspend fun fetchConversations(
            accountId: UserId,
        ): RepositoryResult<List<ConversationSummary>> =
            RepositoryResult.Success(emptyList())

        override suspend fun fetchMessages(
            accountId: UserId,
            conversationId: ConversationId,
        ): RepositoryResult<List<Message>> = RepositoryResult.Success(emptyList())

        override suspend fun sendMessage(
            command: SendMessageCommand,
        ): RepositoryResult<Message> = unavailableMessage()

        override suspend fun editMessage(
            command: EditMessageCommand,
        ): RepositoryResult<Message> {
            lastEdit = command
            return edit
        }

        override suspend fun deleteMessage(
            command: DeleteMessageCommand,
        ): RepositoryResult<Message> {
            lastDelete = command
            return delete
        }
    }

    private companion object {
        const val ITEM = "item-baseball-cap"
        val ACCOUNT = UserId("account-one")
        val CONVERSATION = ConversationId("conversation-one")
        val MESSAGE = MessageId("message-one")

        fun unavailableMessage(): RepositoryResult<Message> = RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Unavailable,
                retryable = true,
            ),
        )
        val OPERATION = ClientOperationId("operation-one")
        val NOW: Instant = Instant.parse("2026-07-27T12:46:00Z")
    }
}
