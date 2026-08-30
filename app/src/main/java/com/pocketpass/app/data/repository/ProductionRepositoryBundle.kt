package com.pocketpass.app.data.repository

import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.repository.remote.ProductionRemoteDataSources
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.repository.SyncRepository
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.nearby.NearbyProofOutboxStore
import com.pocketpass.app.security.SecureStringStore
import com.pocketpass.app.sync.MessageOutboxStore
import com.pocketpass.app.sync.OutboxProcessor
import com.pocketpass.app.sync.PendingOperationExecutor
import com.pocketpass.app.sync.RemotePendingOperationExecutor
import kotlin.time.Clock

data class ProductionRepositoryBundle(
    val profiles: MutableProfileRepository,
    val friends: MutableFriendsRepository,
    val messages: MessageRepository,
    val notifications: NotificationRepository,
    val encounters: RoomEncounterRepository,
    val nearbyProofOutboxStore: NearbyProofOutboxStore,
    val sync: SyncRepository,
    val outboxProcessor: OutboxProcessor,
    val outboxExecutor: PendingOperationExecutor,
    val reconciler: RoomRepositoryReconciler,
    val shop: RoomShopRepository,
) {
    companion object {
        fun create(
            database: PocketPassDatabase,
            remote: ProductionRemoteDataSources,
            nearbySecureStore: SecureStringStore,
            nearbyProofOutboxStore: NearbyProofOutboxStore,
            onEncounterResolved: (NearbyEncounter) -> Unit = {},
            onEncounterSubmitted: (SubmitNearbyEncounterCommand, NearbyEncounter?) -> Unit = { _, _ -> },
            pendingOperationScheduler: PendingOperationScheduler =
                PendingOperationScheduler.None,
            clock: Clock = Clock.System,
        ): ProductionRepositoryBundle {
            val reconciler = RoomRepositoryReconciler(database)
            val mutationStore = ProductionMutationStore(database)
            val messageOutboxStore = MessageOutboxStore(database.outboxDao())
            val profileRepository = RoomProfileRepository(
                profileDao = database.profileDao(),
                remote = remote.profiles,
                mutationStore = mutationStore,
                reconciler = reconciler,
                pendingOperationScheduler = pendingOperationScheduler,
            )
            val friendsRepository = RoomFriendsRepository(
                friendDao = database.friendDao(),
                friendCodeDao = database.friendCodeDao(),
                remote = remote.friends,
                mutationStore = mutationStore,
                reconciler = reconciler,
                pendingOperationScheduler = pendingOperationScheduler,
            )
            val messageRepository = RoomMessageRepository(
                database = database,
                conversationDao = database.conversationDao(),
                conversationMemberDao = database.conversationMemberDao(),
                messageDao = database.messageDao(),
                remote = remote.messages,
                messageOutboxStore = messageOutboxStore,
                reconciler = reconciler,
                pendingOperationScheduler = pendingOperationScheduler,
            )
            val notificationRepository = RoomNotificationRepository(
                notificationDao = database.notificationDao(),
                remote = remote.notifications,
                mutationStore = mutationStore,
                pendingOperationScheduler = pendingOperationScheduler,
            )
            val encounterRepository = RoomEncounterRepository(
                dao = database.nearbyEncounterDao(),
                remote = remote.encounters,
            )
            val shopRepository = RoomShopRepository(
                shopDao = database.shopDao(),
                remote = remote.shop,
                mutationStore = mutationStore,
                pendingOperationScheduler = pendingOperationScheduler,
            )
            val outboxExecutor = RemotePendingOperationExecutor(
                remote = remote,
                reconciler = reconciler,
                nearbySecureStore = nearbySecureStore,
                encounterRepository = encounterRepository,
                onEncounterResolved = onEncounterResolved,
                onEncounterSubmitted = onEncounterSubmitted,
                shopRepository = shopRepository,
            )
            val outboxProcessor = OutboxProcessor(
                outboxDao = database.outboxDao(),
                executor = outboxExecutor,
                clock = clock,
            )
            val syncRepository = RoomSyncRepository(
                profiles = profileRepository,
                friends = friendsRepository,
                messages = messageRepository,
                notifications = notificationRepository,
                encounters = encounterRepository,
                outboxProcessor = outboxProcessor,
                syncCursorDao = database.syncCursorDao(),
                clock = clock,
            )
            return ProductionRepositoryBundle(
                profiles = profileRepository,
                friends = friendsRepository,
                messages = messageRepository,
                notifications = notificationRepository,
                encounters = encounterRepository,
                nearbyProofOutboxStore = nearbyProofOutboxStore,
                sync = syncRepository,
                outboxProcessor = outboxProcessor,
                outboxExecutor = outboxExecutor,
                reconciler = reconciler,
                shop = shopRepository,
            )
        }
    }
}
