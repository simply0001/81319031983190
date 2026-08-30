package com.pocketpass.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import com.pocketpass.app.auth.SupabaseAuthRemoteDataSource
import com.pocketpass.app.auth.SupabaseSessionRepository
import com.pocketpass.app.auth.AuthStateHolder
import com.pocketpass.app.data.DataStoreSettingsRepository
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.pretendo.OkHttpPretendoMiiSource
import com.pocketpass.app.data.repository.FixtureRepositoryBundle
import com.pocketpass.app.data.repository.FixtureEncounterRemoteDataSource
import com.pocketpass.app.data.repository.PendingOperationScheduler
import com.pocketpass.app.data.repository.ProductionRepositoryBundle
import com.pocketpass.app.data.repository.RealtimePresenceRepository
import com.pocketpass.app.data.repository.RoomAchievementsRepository
import com.pocketpass.app.data.repository.RoomBingoRepository
import com.pocketpass.app.data.repository.RoomLeaderboardRepository
import com.pocketpass.app.data.repository.RoomWorldTourRepository
import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.data.supabase.PocketPassSupabaseClientFactory
import com.pocketpass.app.data.supabase.SupabaseBackendConfig
import com.pocketpass.app.data.supabase.SupabaseProductionRemoteDataSources
import com.pocketpass.app.data.supabase.realtime.ConversationRealtimeEvent
import com.pocketpass.app.data.supabase.realtime.MessageChangeOperation
import com.pocketpass.app.data.supabase.realtime.PresenceStateDto
import com.pocketpass.app.data.supabase.realtime.SupabaseRealtimeGateway
import com.pocketpass.app.data.supabase.realtime.TokenChannelEvent
import com.pocketpass.app.data.supabase.realtime.NotificationChange
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.audio.SoundEffectPlayer
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.AccountDeleter
import com.pocketpass.app.domain.repository.AchievementsRepository
import com.pocketpass.app.domain.repository.BingoRepository
import com.pocketpass.app.domain.repository.LeaderboardRepository
import com.pocketpass.app.domain.repository.WorldTourRepository
import com.pocketpass.app.domain.repository.ShopRepository
import com.pocketpass.app.domain.repository.ConnectedAppsSource
import com.pocketpass.app.domain.repository.FriendProfileStatsSource
import com.pocketpass.app.feature.ConnectedAppsStateHolder
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.repository.PresenceRepository
import com.pocketpass.app.domain.repository.ProfileRepository
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.repository.SyncRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupStateHolder
import com.pocketpass.app.feature.ActivitiesStateHolder
import com.pocketpass.app.feature.GamesStateHolder
import com.pocketpass.app.feature.AchievementsStateHolder
import com.pocketpass.app.feature.BingoStateHolder
import com.pocketpass.app.feature.LeaderboardStateHolder
import com.pocketpass.app.feature.WorldTourStateHolder
import com.pocketpass.app.feature.ShopStateHolder
import com.pocketpass.app.feature.FriendsStateHolder
import com.pocketpass.app.feature.HomeProfileStateHolder
import com.pocketpass.app.feature.MessagesStateHolder
import com.pocketpass.app.feature.NotificationStateHolder
import com.pocketpass.app.feature.ProfileViewerStateHolder
import com.pocketpass.app.feature.SettingsStateHolder
import com.pocketpass.app.mii.FileMiiEditorPersistence
import com.pocketpass.app.mii.FileMiiProfilePublishQueue
import com.pocketpass.app.mii.LocalOnlyMiiActiveSlotCallback
import com.pocketpass.app.mii.LocalOnlyMiiEditorSaveCallback
import com.pocketpass.app.mii.MII_FIRST_SLOT
import com.pocketpass.app.mii.MII_SLOT_COUNT
import com.pocketpass.app.mii.MiiActiveSlotCallback
import com.pocketpass.app.mii.MiiActiveSlotPublisher
import com.pocketpass.app.mii.MiiSlotDeleter
import com.pocketpass.app.mii.MiiEditorSaveResult
import com.pocketpass.app.mii.MiiEditorStateHolder
import com.pocketpass.app.mii.MiiPersistedEditorSession
import com.pocketpass.app.mii.MiiProfileFetcher
import com.pocketpass.app.mii.MiiProfilePublisher
import com.pocketpass.app.mii.MiiStoredProfile
import com.pocketpass.app.mii.QueuedMiiEditorSaveCallback
import com.pocketpass.app.mii.toSaveRequest
import com.pocketpass.app.mii.withoutLockedHat
import com.pocketpass.app.nearby.NearbyLifecycleController
import com.pocketpass.app.nearby.NearbyCredentialPool
import com.pocketpass.app.nearby.isAynThorDevice
import com.pocketpass.app.nearby.NearbyEncounterProof
import com.pocketpass.app.nearby.NearbyNotifications
import com.pocketpass.app.nearby.NearbyProofOutboxStore
import com.pocketpass.app.nearby.NearbyReceiptOutcome
import com.pocketpass.app.nearby.NearbyReceiptVerdict
import com.pocketpass.app.nearby.NearbyReceiptVerdictBus
import com.pocketpass.app.security.AndroidKeystoreEncryptedStringStore
import com.pocketpass.app.security.AppIntegrity
import com.pocketpass.app.security.AppIntegrityStatus
import com.pocketpass.app.security.KeystoreSupabaseCodeVerifierCache
import com.pocketpass.app.security.KeystoreSupabaseSessionManager
import com.pocketpass.app.sync.DatabaseOutboxWorkRunner
import com.pocketpass.app.sync.OutboxWorkCoordinator
import com.pocketpass.app.sync.OutboxWorkerRuntime
import com.pocketpass.app.update.ApkInstaller
import com.pocketpass.app.update.AppUpdateCheckScheduler
import com.pocketpass.app.update.AppUpdateStateHolder
import com.pocketpass.app.update.UPDATE_MANIFEST_URL
import com.pocketpass.app.update.OkHttpUpdateTransport
import com.pocketpass.app.update.UpdateNotifications
import io.github.jan.supabase.SupabaseClient
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PocketPassRepositoryGraph(
    val session: SessionRepository,
    val profiles: ProfileRepository,
    val friends: MutableFriendsRepository,
    val conversations: MessageRepository,
    val notifications: NotificationRepository,
    val shop: ShopRepository,
    val leaderboard: LeaderboardRepository,
    val achievements: AchievementsRepository,
    val worldTour: WorldTourRepository,
    val bingo: BingoRepository,
    val encounters: EncounterRepository,
    val presence: PresenceRepository,
    val sync: SyncRepository,
)

internal suspend fun clearSignOutData(cleanup: () -> Unit) {
    withContext(NonCancellable + Dispatchers.IO) {
        cleanup()
    }
}

internal data class RealtimeRuntimeGate(
    val accountId: UserId?,
    val appForeground: Boolean,
    val networkAvailable: Boolean,
    val networkGeneration: Long,
) {
    val shouldRun: Boolean
        get() = accountId != null && appForeground && networkAvailable
}

class AppContainer(
    private val context: Context,
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: PocketPassDatabase = PocketPassDatabase.build(context)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    val soundEffects = SoundEffectPlayer(context)
    private val appForeground = MutableStateFlow(false)
    private var lastSeenRefreshJob: Job? = null
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val realtimeNetworkState = MutableStateFlow(
        RealtimeNetworkState(
            available = hasValidatedNetwork(),
            networkHandle = connectivityManager.activeNetwork?.networkHandle,
            generation = 0L,
        ),
    )
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private val nearbySecureStore = AndroidKeystoreEncryptedStringStore(
        context = context,
        preferenceName = "pocketpass_secure_nearby",
        keyAlias = "com.pocketpass.app.nearby.aes.v1",
    )

    private val outboxWorkCoordinator = OutboxWorkCoordinator(context)
    private val postedNearbyNotificationIds =
        ConcurrentHashMap.newKeySet<String>()
    val nearbyProofOutboxStore = NearbyProofOutboxStore(
        outboxDao = database.outboxDao(),
        secureStore = nearbySecureStore,
        scheduler = PendingOperationScheduler(outboxWorkCoordinator::enqueue),
    )
    private val nearbyReceiptVerdicts = NearbyReceiptVerdictBus()

    val integrityStatus: AppIntegrityStatus = AppIntegrity.evaluate(
        context = context,
        expectedCertificateSha256 = BuildConfig.RELEASE_CERT_SHA256,
        isDebugBuild = BuildConfig.DEBUG,
    )

    val integrityCompromised: Boolean =
        integrityStatus == AppIntegrityStatus.Compromised

    val encounterLedSupported: Boolean = isAynThorDevice()

    val fixtureMode: Boolean =
        !BuildConfig.BACKEND_ENABLED || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()

    private val backendComponents: BackendComponents? =
        if (fixtureMode || integrityCompromised) null else createBackendComponents()

    private val productionComponents: ProductionComponents? =
        backendComponents?.let(::createProductionComponents)

    val repositories: PocketPassRepositoryGraph =
        productionComponents?.repositories ?: createFixtureRepositoryGraph()

    val activeAccountId: StateFlow<UserId?> =
        if (fixtureMode) {
            kotlinx.coroutines.flow.MutableStateFlow(FixtureData.CurrentUserId)
        } else {
            repositories.session.sessionState
                .map(::accountIdOrNull)
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.Eagerly,
                    initialValue = null,
                )
        }

    val miiEditorEnabled: Boolean = !fixtureMode
    val pretendoImportEnabled: Boolean = !fixtureMode

    private val miiPublishQueue = FileMiiProfilePublishQueue(context)
    private val miiPersistence = FileMiiEditorPersistence(context)
    private val miiPublishCallback =
        productionComponents?.miiPublisher?.let { publisher ->
            QueuedMiiEditorSaveCallback(
                queue = miiPublishQueue,
                publisher = publisher,
                onPublished = { publication ->
                    repositories.profiles.refreshProfile(publication.accountId)
                },
            )
        }

    private val miiActiveSlotCallback =
        productionComponents?.miiActiveSlotPublisher?.let { publisher ->
            MiiActiveSlotCallback { accountKey, slot ->
                when (
                    publisher.setActiveMiiSlot(
                        accountId = UserId(accountKey),
                        slot = slot,
                    )
                ) {
                    is RepositoryResult.Success -> {
                        repositories.profiles.refreshProfile(UserId(accountKey))
                        MiiEditorSaveResult.Completed
                    }

                    is RepositoryResult.Failure -> MiiEditorSaveResult.QueuedForSync
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ownedHatTypes: Flow<Set<Int>> = activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(emptySet()) else repositories.shop.observeOwnedHatTypes(accountId)
    }

    val miiEditor = MiiEditorStateHolder(
        persistence = miiPersistence,
        scope = applicationScope,
        saveCallback = miiPublishCallback ?: LocalOnlyMiiEditorSaveCallback,
        activeSlotCallback = miiActiveSlotCallback ?: LocalOnlyMiiActiveSlotCallback,
        remoteRestore = productionComponents?.miiFetcher?.let { fetcher ->
            { accountKey -> restoreMiiSessionFromServer(fetcher, accountKey) }
        },
        ownedHatTypes = ownedHatTypes,
        pretendoMiiSource = if (pretendoImportEnabled) {
            OkHttpPretendoMiiSource(BuildConfig.VERSION_NAME)
        } else {
            null
        },
    )

    val nearbyCredentialPool = NearbyCredentialPool(
        dao = database.nearbyEncounterDao(),
        remote = productionComponents?.encounterRemote
            ?: FixtureEncounterRemoteDataSource(),
        secureStore = nearbySecureStore,
    )

    val homeProfile = HomeProfileStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        encounterRepository = repositories.encounters,
        settingsRepository = settingsRepository,
        scope = applicationScope,
    )
    val accountSetup = AccountSetupStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        scope = applicationScope,
    )
    private val pendingConversation = MutableStateFlow<ConversationId?>(null)
    val requestedConversation: StateFlow<ConversationId?> = pendingConversation

    fun consumeRequestedConversation() {
        pendingConversation.value = null
    }

    private val pendingAppUpdate = MutableStateFlow(false)
    val requestedAppUpdate: StateFlow<Boolean> = pendingAppUpdate
    val pendingInstallConfirmation = MutableStateFlow<android.content.Intent?>(null)
    val notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestAppUpdateScreen() {
        pendingAppUpdate.value = true
    }

    fun consumeRequestedAppUpdate() {
        pendingAppUpdate.value = false
    }

    suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
        settings.setUpdateAlertsEnabled(enabled)
        if (enabled) {
            notificationPermissionRequests.tryEmit(Unit)
        } else {
            UpdateNotifications.cancel(context)
        }
    }

    val profileViewer = ProfileViewerStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        friendsRepository = repositories.friends,
        presenceRepository = repositories.presence,
        scope = applicationScope,
        statsSource = productionComponents?.friendStats,
        onOpenConversation = { pendingConversation.value = it },
    )
    val friends = FriendsStateHolder(
        accountId = activeAccountId,
        friendsRepository = repositories.friends,
        presenceRepository = repositories.presence,
        scope = applicationScope,
    )
    val connectedApps = ConnectedAppsStateHolder(
        accountId = activeAccountId,
        source = productionComponents?.connectedApps,
        scope = applicationScope,
    )
    val messages = MessagesStateHolder(
        accountId = activeAccountId,
        conversationRepository = repositories.conversations,
        scope = applicationScope,
        deterministicFixtureMessageCount = if (fixtureMode) 102 else 0,
        presenceRepository = repositories.presence,
        onMessageSent = { soundEffects.play(SoundEffect.MessageSent) },
        onGroupCreated = { pendingConversation.value = it },
    )
    val notifications = NotificationStateHolder(
        accountId = activeAccountId,
        notificationRepository = repositories.notifications,
        friendsRepository = repositories.friends,
        scope = applicationScope,
    )
    val activities = ActivitiesStateHolder(
        accountId = activeAccountId,
        shopRepository = repositories.shop,
        leaderboardRepository = repositories.leaderboard,
        worldTourRepository = repositories.worldTour,
        scope = applicationScope,
    )
    val shop = ShopStateHolder(
        accountId = activeAccountId,
        shopRepository = repositories.shop,
        scope = applicationScope,
    )
    val games = GamesStateHolder()
    val leaderboard = LeaderboardStateHolder(
        accountId = activeAccountId,
        leaderboardRepository = repositories.leaderboard,
        settingsRepository = settingsRepository,
        scope = applicationScope,
    )
    val achievements = AchievementsStateHolder(
        accountId = activeAccountId,
        achievementsRepository = repositories.achievements,
        scope = applicationScope,
    )
    val worldTour = WorldTourStateHolder(
        accountId = activeAccountId,
        worldTourRepository = repositories.worldTour,
        scope = applicationScope,
    )
    val bingo = BingoStateHolder(
        accountId = activeAccountId,
        bingoRepository = repositories.bingo,
        scope = applicationScope,
    )
    val settings = SettingsStateHolder(settingsRepository, applicationScope)
    private val updateTransport = OkHttpUpdateTransport()
    val appUpdate = AppUpdateStateHolder(
        settingsRepository = settingsRepository,
        scope = applicationScope,
        installedVersionCode = BuildConfig.VERSION_CODE,
        enabled = !BuildConfig.DEBUG,
        manifestUrl = UPDATE_MANIFEST_URL,
        downloadDirProvider = { File(context.cacheDir, "updates") },
        manifestFetcher = updateTransport,
        apkDownloader = updateTransport,
        installGate = { context.packageManager.canRequestPackageInstalls() },
        installer = { apk, manifest -> ApkInstaller.commit(context, apk, manifest) },
        notifier = { manifest ->
            UpdateNotifications.postUpdateAvailable(
                context,
                manifest.versionName ?: manifest.versionCode.toString(),
            )
        },
    )
    val auth = AuthStateHolder(
        sessionRepository = repositories.session,
        scope = applicationScope,
    )
    val nearby = NearbyLifecycleController(
        context = context,
        settingsRepository = settingsRepository,
        settings = settings.settings,
        sessionState = repositories.session.sessionState,
        scope = applicationScope,
    )

    val authRemoteDataSource: SupabaseAuthRemoteDataSource?
        get() = backendComponents?.authRemote

    val realtimeGateway: SupabaseRealtimeGateway?
        get() = backendComponents?.realtime

    init {
        applicationScope.launch {
            activeAccountId
                .collectLatest { accountId ->
                    miiEditor.activateAccount(
                        accountId
                            ?.takeIf { miiEditorEnabled }
                            ?.value,
                    )
                    if (accountId == null || !miiEditorEnabled) {
                        return@collectLatest
                    }
                    var publicationVerified = false
                    while (currentCoroutineContext().isActive) {
                        if (!publicationVerified) {
                            publicationVerified =
                                ensureSavedMiiPublication(accountId)
                        }
                        val published = miiPublishCallback?.drain(accountId.value) ?: 0
                        if (published > 0) {
                            publicationVerified = true
                        }
                        delay(MII_PUBLICATION_RETRY_MILLIS)
                    }
                }
        }
        applicationScope.launch {
            repositories.session.initialize()
        }
        appUpdate.checkOnLaunch()
        productionComponents?.let(::startProductionRuntime)
    }

    suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState> =
        repositories.session.handleAuthCallback(callbackUri)

    fun setAppForeground(foreground: Boolean) {
        appForeground.value = foreground
        appUpdate.setForeground(foreground)
        if (foreground && !BuildConfig.DEBUG) AppUpdateCheckScheduler.schedule(context)
    }

    suspend fun submitNearbyProof(
        accountId: UserId,
        proof: NearbyEncounterProof,
    ): NearbyReceiptVerdict {
        if (fixtureMode) return NearbyReceiptVerdict.NotQueued
        if (!nearbyProofOutboxStore.enqueue(accountId, proof)) return NearbyReceiptVerdict.NotQueued
        val production = productionComponents ?: return NearbyReceiptVerdict.Unknown
        applicationScope.launch {
            runCatching { production.bundle.outboxProcessor.drain(accountId) }
        }
        return nearbyReceiptVerdicts.await(EncounterId(proof.encounterId))?.verdict
            ?: NearbyReceiptVerdict.Unknown
    }

    suspend fun resetSettings() {
        settings.resetSettings()
    }

    private suspend fun restoreMiiSessionFromServer(
        fetcher: MiiProfileFetcher,
        accountKey: String,
    ): MiiPersistedEditorSession? {
        repeat(3) { attempt ->
            when (val result = fetcher.fetchMiiProfiles(UserId(accountKey))) {
                is RepositoryResult.Success -> {
                    val snapshots = result.value
                        .filter { it.slot in MII_FIRST_SLOT..MII_SLOT_COUNT }
                    if (snapshots.isEmpty()) return null
                    val profiles = snapshots.associate { snapshot ->
                        snapshot.slot to MiiStoredProfile(
                            appearance = snapshot.appearance,
                            portraitFilePath = snapshot.portraitPng
                                ?.let { writeRestoredMiiPortrait(it) },
                            revision = snapshot.revision,
                            savedAtEpochMillis = snapshot.savedAt.toEpochMilliseconds(),
                        )
                    }
                    return MiiPersistedEditorSession(
                        savedProfiles = profiles,
                        activeSlot = snapshots.firstOrNull { it.isActive }?.slot
                            ?: profiles.keys.min(),
                    )
                }

                is RepositoryResult.Failure ->
                    if (attempt < 2) delay(1_000L * (attempt + 1))
            }
        }
        return null
    }

    private suspend fun writeRestoredMiiPortrait(bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.filesDir, "mii/portraits")
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, "portrait-${UUID.randomUUID()}.png")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }

    private suspend fun ensureSavedMiiPublication(accountId: UserId): Boolean {
        if (miiPublishQueue.pending(accountId.value).isNotEmpty()) return false
        val session = miiPersistence.load(accountId.value) ?: return true
        val stored = session.savedProfiles[session.activeSlot]
            ?: session.savedProfiles.values.firstOrNull()
            ?: return true
        if (stored.portraitFilePath.isNullOrBlank()) return true
        val refresh = repositories.profiles.refreshProfile(accountId)
        if (refresh is RepositoryResult.Failure) return false
        val profile = database.profileDao().get(accountId.value)
        if (!profile?.avatarValue.isNullOrBlank()) return true
        val publishable = stored.appearance.normalized().withoutLockedHat(ownedHatTypes.first())
        miiPublishQueue.enqueue(
            stored.copy(appearance = publishable)
                .toSaveRequest(accountId.value, slot = session.activeSlot),
        )
        return false
    }

    suspend fun deleteMiiSlot(slot: Int): RepositoryResult<Unit> {
        val accountId = activeAccountId.value
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Unauthorized,
                    message = "Sign in again to delete this Mii.",
                    retryable = false,
                ),
            )
        miiPublishQueue.pending(accountId.value)
            .filter { it.slot == slot }
            .forEach { miiPublishQueue.remove(it.queueId) }
        productionComponents?.miiSlotDeleter?.let { deleter ->
            val result = deleter.deleteMiiSlot(accountId, slot)
            if (result is RepositoryResult.Failure) return result
        }
        miiEditor.deleteSlot(slot)
        return RepositoryResult.Success(Unit)
    }

    suspend fun deleteAccount(): RepositoryResult<Unit> {
        val deleter = productionComponents?.accountDeleter
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Misconfigured,
                    message = "Account deletion is unavailable.",
                    retryable = false,
                ),
            )
        val accountId = activeAccountId.value
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Unauthorized,
                    message = "Sign in again to delete your account.",
                    retryable = false,
                ),
            )
        return when (val result = deleter.deleteAccount(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                miiPublishQueue.clearAccount(accountId.value)
                signOut()
            }
        }
    }

    suspend fun signOut(): RepositoryResult<Unit> {
        val accountsWithLocalMutations = database.withTransaction {
            database.openHelper.writableDatabase
                .query("SELECT DISTINCT accountId FROM pending_operations")
                .use { cursor ->
                    buildSet {
                        val accountColumn = cursor.getColumnIndexOrThrow("accountId")
                        while (cursor.moveToNext()) {
                            add(UserId(cursor.getString(accountColumn)))
                        }
                    }
                }
        } + listOfNotNull(activeAccountId.value)
        accountsWithLocalMutations.forEach(outboxWorkCoordinator::cancel)
        val nearbySecureEntryKeys = accountsWithLocalMutations
            .flatMap { accountId ->
                database.nearbyEncounterDao()
                    .getCredentialSecureEntryKeys(accountId.value) +
                    database.outboxDao().getPayloadReferences(
                        accountId.value,
                        NearbyProofOutboxStore.OPERATION_KIND,
                    )
            }
            .distinct()

        var secureCleanupFailures: List<Throwable> = emptyList()
        val result = try {
            repositories.session.signOut()
        } finally {
            secureCleanupFailures = buildList {
                addAll(
                    nearbySecureEntryKeys.mapNotNull { entryKey ->
                        runCatching {
                            nearbySecureStore.remove(entryKey)
                        }.exceptionOrNull()
                    },
                )
                addAll(
                    listOfNotNull(
                runCatching {
                    backendComponents?.sessionManager?.deleteSession()
                }.exceptionOrNull(),
                runCatching {
                    backendComponents?.verifierCache?.deleteCodeVerifier()
                }.exceptionOrNull(),
                    ),
                )
            }
            val residualCleanupFailures = buildList {
                add(runCatching { productionComponents?.presence?.clearAll() }.exceptionOrNull())
                accountsWithLocalMutations.forEach { accountId ->
                    add(
                        runCatching { miiPublishQueue.clearAccount(accountId.value) }
                            .exceptionOrNull(),
                    )
                    add(
                        runCatching { miiPersistence.clear(accountId.value) }
                            .exceptionOrNull(),
                    )
                }
                add(runCatching { postedNearbyNotificationIds.clear() }.exceptionOrNull())
            }.filterNotNull()
            val databaseCleanupFailure = try {
                clearSignOutData(database::clearAllTables)
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error
            }
            secureCleanupFailures = secureCleanupFailures +
                residualCleanupFailures +
                listOfNotNull(databaseCleanupFailure)
        }
        return if (secureCleanupFailures.isEmpty()) {
            result
        } else {
            RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Unknown,
                    message = "Unable to clear protected authentication data",
                    retryable = false,
                ),
            )
        }
    }

    private fun createBackendComponents(): BackendComponents {
        val secureStore = AndroidKeystoreEncryptedStringStore(context)
        val sessionManager = KeystoreSupabaseSessionManager(secureStore)
        val verifierCache = KeystoreSupabaseCodeVerifierCache(secureStore)
        val config = SupabaseBackendConfig(
            baseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            authCallbackUrl = BuildConfig.AUTH_CALLBACK_URL,
        )
        val client = PocketPassSupabaseClientFactory(
            config = config,
            sessionManager = sessionManager,
            codeVerifierCache = verifierCache,
        ).get()
        val authRemote = SupabaseAuthRemoteDataSource(client, config)
        return BackendComponents(
            client = client,
            authRemote = authRemote,
            session = SupabaseSessionRepository(authRemote, applicationScope),
            realtime = SupabaseRealtimeGateway(client),
            sessionManager = sessionManager,
            verifierCache = verifierCache,
        )
    }

    private fun createFixtureRepositoryGraph(): PocketPassRepositoryGraph {
        val fixtures = FixtureRepositoryBundle()
        return PocketPassRepositoryGraph(
            session = fixtures.session,
            profiles = fixtures.profiles,
            friends = fixtures.friends,
            conversations = fixtures.messages,
            notifications = fixtures.notifications,
            shop = fixtures.shop,
            leaderboard = fixtures.leaderboard,
            achievements = fixtures.achievements,
            worldTour = fixtures.worldTour,
            bingo = fixtures.bingo,
            encounters = fixtures.encounters,
            presence = fixtures.presence,
            sync = fixtures.sync,
        )
    }

    private fun createProductionComponents(
        backend: BackendComponents,
    ): ProductionComponents {
        val remote = SupabaseProductionRemoteDataSources(backend.client)
        val bundle = ProductionRepositoryBundle.create(
            database = database,
            remote = remote.sources,
            nearbySecureStore = nearbySecureStore,
            nearbyProofOutboxStore = nearbyProofOutboxStore,
            onEncounterSubmitted = { command, encounter ->
                nearbyReceiptVerdicts.report(
                    NearbyReceiptOutcome(
                        submittedEncounterId = command.encounterId,
                        resolvedEncounterId = encounter?.id,
                    ),
                )
            },
            pendingOperationScheduler = PendingOperationScheduler(
                outboxWorkCoordinator::enqueue,
            ),
        )
        OutboxWorkerRuntime.install(DatabaseOutboxWorkRunner(bundle.outboxProcessor))

        val shop = bundle.shop
        val leaderboard = RoomLeaderboardRepository(
            database.leaderboardDao(),
            remote.sources.leaderboard,
        )
        val achievements = RoomAchievementsRepository(
            database.achievementDao(),
            remote.sources.achievements,
        )
        val worldTour = RoomWorldTourRepository(
            database.worldTourDao(),
            remote.sources.worldTour,
        )
        val bingo = RoomBingoRepository(
            database.bingoDao(),
            remote.sources.bingo,
        )
        val presence = RealtimePresenceRepository()
        val graph = PocketPassRepositoryGraph(
            session = backend.session,
            profiles = bundle.profiles,
            friends = bundle.friends,
            conversations = bundle.messages,
            notifications = bundle.notifications,
            shop = shop,
            leaderboard = leaderboard,
            achievements = achievements,
            worldTour = worldTour,
            bingo = bingo,
            encounters = bundle.encounters,
            presence = presence,
            sync = bundle.sync,
        )
        return ProductionComponents(
            repositories = graph,
            bundle = bundle,
            presence = presence,
            encounterRemote = remote.sources.encounters,
            miiPublisher = remote,
            miiFetcher = remote,
            miiActiveSlotPublisher = remote,
            miiSlotDeleter = remote,
            accountDeleter = remote,
            friendStats = remote,
            connectedApps = remote,
            profileRemote = remote,
        )
    }

    private fun startProductionRuntime(production: ProductionComponents) {
        registerRealtimeNetworkCallback()
        applicationScope.launch {
            combine(
                repositories.session.sessionState
                    .map(::accountIdOrNull)
                    .distinctUntilChanged(),
                appForeground,
                realtimeNetworkState,
            ) { accountId, foreground, network ->
                RealtimeRuntimeGate(
                    accountId = accountId,
                    appForeground = foreground,
                    networkAvailable = network.available,
                    networkGeneration = network.generation,
                )
            }
                .distinctUntilChanged()
                .collectLatest { gate ->
                    production.presence.clearAll()
                    if (!gate.shouldRun) {
                        Log.i(
                            TAG,
                            "Realtime stopped: foreground=${gate.appForeground}, " +
                                "network=${gate.networkAvailable}, " +
                                "authenticated=${gate.accountId != null}",
                        )
                        if (gate.accountId != null && gate.networkAvailable) {
                            touchLastSeen(production)
                        }
                        return@collectLatest
                    }

                    val accountId = requireNotNull(gate.accountId)
                    Log.i(
                        TAG,
                        "Realtime starting for network generation ${gate.networkGeneration}",
                    )
                    try {
                        repositories.sync.synchronize(accountId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Log.w(TAG, "Realtime reconciliation failed before reconnect", error)
                    }
                    coroutineScope {
                        launch { touchLastSeenPeriodically(production) }
                        launch { collectRealtimeConversations(accountId, production) }
                        launch { collectRealtimeNotifications(accountId, production) }
                        launch { collectRealtimeFriends(accountId, production) }
                        launch { collectRealtimeTokenBalance(accountId, production) }
                        launch { collectRealtimeEncounterStats(accountId, production) }
                        launch { collectRealtimeAppUpdates() }
                    }
                }
        }
    }

    private suspend fun collectRealtimeFriends(
        accountId: UserId,
        production: ProductionComponents,
    ) = coroutineScope {
        production.bundle.friends.refreshFriends(accountId)
        launch {
            var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            while (currentCoroutineContext().isActive) {
                try {
                    requireNotNull(backendComponents).realtime
                        .friendInvalidations(accountId.value)
                        .collect {
                            production.bundle.friends.refreshFriends(accountId)
                            production.bundle.notifications.refreshNotifications(accountId)
                        }
                    retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(TAG, "Friend invalidation channel failed; retrying", error)
                }
                delay(retryDelayMillis)
                production.bundle.friends.refreshFriends(accountId)
                retryDelayMillis = (retryDelayMillis * 2)
                    .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
            }
        }
        launch {
            val channelJobs = mutableMapOf<UserId, Job>()
            try {
                database.friendDao()
                    .observeForOwner(accountId.value)
                    .map { rows ->
                        rows.asSequence()
                            .filter {
                                it.friendshipStatus == FriendshipStatus.Accepted.name
                            }
                            .mapTo(linkedSetOf()) { UserId(it.friendUserId) }
                    }
                    .distinctUntilChanged()
                    .collect { friendIds ->
                        val removed = channelJobs.keys - friendIds
                        removed.forEach { friendId ->
                            channelJobs.remove(friendId)?.cancel()
                            production.presence.clearFriendPresence(
                                friendPresencePairKey(accountId, friendId),
                            )
                        }

                        (friendIds - channelJobs.keys).forEach { friendId ->
                            channelJobs[friendId] = launch {
                                collectFriendPresence(
                                    accountId = accountId,
                                    friendId = friendId,
                                    production = production,
                                )
                            }
                        }
                    }
            } finally {
                channelJobs.values.forEach(Job::cancel)
                channelJobs.keys.forEach { friendId ->
                    production.presence.clearFriendPresence(
                        friendPresencePairKey(accountId, friendId),
                    )
                }
            }
        }
    }

    private suspend fun touchLastSeen(production: ProductionComponents) {
        when (val result = production.profileRemote.touchLastSeen()) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Failure ->
                Log.d(TAG, "Last seen touch failed: ${result.error.message}")
        }
    }

    private suspend fun touchLastSeenPeriodically(production: ProductionComponents) {
        while (currentCoroutineContext().isActive) {
            touchLastSeen(production)
            delay(LAST_SEEN_TOUCH_INTERVAL_MILLIS)
        }
    }

    private fun scheduleLastSeenRefresh(accountId: UserId, production: ProductionComponents) {
        lastSeenRefreshJob?.cancel()
        lastSeenRefreshJob = applicationScope.launch {
            delay(LAST_SEEN_REFRESH_DELAY_MILLIS)
            production.bundle.friends.refreshFriends(accountId)
        }
    }

    private suspend fun collectFriendPresence(
        accountId: UserId,
        friendId: UserId,
        production: ProductionComponents,
    ) {
        val pairKey = friendPresencePairKey(accountId, friendId)
        var friendOnline = false
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            try {
                requireNotNull(backendComponents).realtime
                    .friendPresence(
                        accountId = accountId.value,
                        friendUserId = friendId.value,
                    )
                    .collect { presences ->
                        Log.i(
                            TAG,
                            "Friend presence changed: onlineMembers=${presences.size}",
                        )
                        val online = presences.any { it.userId == friendId.value }
                        if (friendOnline && !online) {
                            scheduleLastSeenRefresh(accountId, production)
                        }
                        friendOnline = online
                        production.presence.replaceFriendPresence(
                            pairKey = pairKey,
                            snapshot = presences
                                .mapNotNull { presence ->
                                    runCatching {
                                        UserId(presence.userId) to PresenceStatus.Online
                                    }.getOrNull()
                                }
                                .toMap(),
                        )
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                production.presence.clearFriendPresence(pairKey)
                Log.w(TAG, "Friend presence channel failed; reconciling and retrying", error)
                try {
                    production.bundle.friends.refreshFriends(accountId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (refreshError: Throwable) {
                    Log.w(TAG, "Friend reconciliation failed after Presence error", refreshError)
                }
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private fun friendPresencePairKey(first: UserId, second: UserId): String =
        listOf(first.value, second.value)
            .sorted()
            .joinToString(separator = ":")

    private suspend fun collectRealtimeAppUpdates() {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            try {
                requireNotNull(backendComponents).realtime
                    .appUpdateSignals()
                    .collect { appUpdate.onRemoteManifestChanged() }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "App update channel failed; retrying", error)
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeTokenBalance(
        accountId: UserId,
        production: ProductionComponents,
    ) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            production.repositories.shop.refresh(accountId)
            try {
                requireNotNull(backendComponents).realtime
                    .tokenBalanceInvalidations(accountId.value)
                    .collect { event ->
                        when (event) {
                            TokenChannelEvent.Balance ->
                                production.repositories.shop.refreshTokenBalance(accountId)
                            TokenChannelEvent.Supporter ->
                                production.repositories.shop.refreshSupporterStatus(accountId)
                        }
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Token balance channel failed; retrying", error)
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeEncounterStats(
        accountId: UserId,
        production: ProductionComponents,
    ) {
        suspend fun refreshStats() {
            production.repositories.leaderboard.refresh(accountId, LeaderboardScope.Friends)
            production.repositories.leaderboard.refresh(accountId, LeaderboardScope.Global)
            production.repositories.worldTour.refresh(accountId)
        }
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            refreshStats()
            try {
                requireNotNull(backendComponents).realtime
                    .encounterInvalidations(accountId.value)
                    .collect { refreshStats() }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Encounter stats channel failed; retrying", error)
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeNotifications(
        accountId: UserId,
        production: ProductionComponents,
    ) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            production.bundle.notifications.refreshNotifications(accountId)
            postUnreadNearbyNotifications(accountId)
            try {
                requireNotNull(backendComponents).realtime
                    .notificationInvalidations(accountId.value)
                    .collect { change ->
                        if (change == NotificationChange.Inserted) {
                            soundEffects.play(SoundEffect.Notification)
                        }
                        production.bundle.notifications.refreshNotifications(accountId)
                        postUnreadNearbyNotifications(accountId)
                        refreshConversationsForNotifications(accountId, production)
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Notification invalidation channel failed; retrying", error)
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun postUnreadNearbyNotifications(accountId: UserId) {
        val alertsEnabled = settingsRepository.settings.first().encounterAlertsEnabled
        database.notificationDao()
            .getUnreadNearbyForAccount(accountId.value)
            .forEach { notification ->
                if (
                    postedNearbyNotificationIds.add(notification.notificationId) &&
                    alertsEnabled
                ) {
                    NearbyNotifications.postEncounter(
                        context = context,
                        displayName = notification.actorDisplayName
                            ?.takeIf(String::isNotBlank)
                            ?: "Someone",
                        notificationKey = notification.notificationId,
                    )
                }
            }
    }

    private suspend fun refreshConversationsForNotifications(
        accountId: UserId,
        production: ProductionComponents,
    ) {
        val pending = database.notificationDao().conversationIdsNeedingRefresh(accountId.value)
        if (pending.isNotEmpty()) {
            production.bundle.messages.refreshConversations(accountId)
        }
    }

    private suspend fun collectRealtimeConversations(
        accountId: UserId,
        production: ProductionComponents,
    ) = coroutineScope {
        val channelJobs = mutableMapOf<ConversationId, Job>()
        try {
            database.conversationDao()
                .observeForAccount(accountId.value)
                .map { rows ->
                    rows.mapTo(linkedSetOf()) { row ->
                        ConversationId(row.conversationId)
                    }
                }
                .distinctUntilChanged()
                .collect { conversationIds ->
                    val removed = channelJobs.keys - conversationIds
                    removed.forEach { conversationId ->
                        channelJobs.remove(conversationId)?.cancel()
                        production.presence.clearConversation(conversationId)
                    }

                    (conversationIds - channelJobs.keys).forEach { conversationId ->
                        channelJobs[conversationId] = launch {
                            collectConversationRealtime(
                                accountId = accountId,
                                conversationId = conversationId,
                                production = production,
                            )
                        }
                    }
                }
        } finally {
            channelJobs.values.forEach(Job::cancel)
            channelJobs.keys.forEach(production.presence::clearConversation)
        }
    }

    private suspend fun collectConversationRealtime(
        accountId: UserId,
        conversationId: ConversationId,
        production: ProductionComponents,
    ) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            production.bundle.messages.refreshConversations(accountId)
            production.bundle.messages.refreshMessages(accountId, conversationId)

            try {
                requireNotNull(backendComponents).realtime
                    .conversationEvents(
                        conversationId = conversationId.value,
                        userId = accountId.value,
                        typingUpdates = messages.observeSelfTyping(conversationId),
                    )
                    .collect { event ->
                        when (event) {
                            is ConversationRealtimeEvent.MessageInvalidated -> {
                                val invalidation = event.invalidation
                                if (
                                    invalidation.operation == MessageChangeOperation.Insert &&
                                    invalidation.senderId != null &&
                                    invalidation.senderId != accountId.value
                                ) {
                                    soundEffects.play(SoundEffect.MessageReceived)
                                }
                                production.bundle.messages.refreshMessages(
                                    accountId,
                                    conversationId,
                                )
                                production.bundle.messages.refreshConversations(accountId)
                            }

                            is ConversationRealtimeEvent.ConversationInvalidated -> {
                                production.bundle.messages.refreshConversations(accountId)
                            }

                            is ConversationRealtimeEvent.PresenceChanged -> {
                                production.presence.replaceConversationPresence(
                                    conversationId = conversationId,
                                    snapshot = event.presences
                                        .mapNotNull { presence ->
                                            runCatching {
                                                UserId(presence.userId) to PresenceStatus.Online
                                            }.getOrNull()
                                        }
                                        .toMap(),
                                )
                                production.presence.replaceConversationTyping(
                                    conversationId = conversationId,
                                    typingUserIds = event.presences
                                        .filter(PresenceStateDto::isTyping)
                                        .mapNotNull { presence ->
                                            runCatching {
                                                UserId(presence.userId)
                                            }.getOrNull()
                                        }
                                        .filterNot { it == accountId }
                                        .toSet(),
                                )
                            }
                        }
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                production.presence.clearConversation(conversationId)
                Log.w(TAG, "Conversation Realtime channel failed; retrying", error)
            }

            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private fun accountIdOrNull(session: SessionState): UserId? =
        when (session) {
            is SessionState.Authenticated -> session.userId
            is SessionState.OfflineWithCachedSession -> session.userId
            else -> null
        }

    private fun registerRealtimeNetworkCallback() {
        if (connectivityCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                publishRealtimeNetworkState()
            }

            override fun onLost(network: Network) {
                publishRealtimeNetworkState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                publishRealtimeNetworkState()
            }
        }
        connectivityCallback = callback
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            publishRealtimeNetworkState()
        } catch (error: RuntimeException) {
            connectivityCallback = null
            Log.w(TAG, "Unable to observe network changes for Realtime", error)
        }
    }

    @Synchronized
    private fun publishRealtimeNetworkState() {
        val network = connectivityManager.activeNetwork
        val nextAvailable = hasValidatedNetwork(network)
        val nextHandle = network?.networkHandle
        val current = realtimeNetworkState.value
        if (
            current.available == nextAvailable &&
            current.networkHandle == nextHandle
        ) {
            return
        }
        realtimeNetworkState.value = RealtimeNetworkState(
            available = nextAvailable,
            networkHandle = nextHandle,
            generation = current.generation + 1L,
        )
    }

    private fun hasValidatedNetwork(
        network: Network? = connectivityManager.activeNetwork,
    ): Boolean {
        val capabilities = network
            ?.let(connectivityManager::getNetworkCapabilities)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private data class ProductionComponents(
        val repositories: PocketPassRepositoryGraph,
        val bundle: ProductionRepositoryBundle,
        val presence: RealtimePresenceRepository,
        val encounterRemote: EncounterRemoteDataSource,
        val miiPublisher: MiiProfilePublisher,
        val miiFetcher: MiiProfileFetcher,
        val miiActiveSlotPublisher: MiiActiveSlotPublisher,
        val miiSlotDeleter: MiiSlotDeleter,
        val accountDeleter: AccountDeleter,
        val friendStats: FriendProfileStatsSource,
        val connectedApps: ConnectedAppsSource,
        val profileRemote: ProfileRemoteDataSource,
    )

    private data class BackendComponents(
        val client: SupabaseClient,
        val authRemote: SupabaseAuthRemoteDataSource,
        val session: SessionRepository,
        val realtime: SupabaseRealtimeGateway,
        val sessionManager: KeystoreSupabaseSessionManager,
        val verifierCache: KeystoreSupabaseCodeVerifierCache,
    )

    private data class RealtimeNetworkState(
        val available: Boolean,
        val networkHandle: Long?,
        val generation: Long,
    )

    private companion object {
        const val TAG = "PocketPassRealtime"
        const val INITIAL_REALTIME_RETRY_MILLIS = 1_000L
        const val LAST_SEEN_TOUCH_INTERVAL_MILLIS = 5 * 60_000L
        const val LAST_SEEN_REFRESH_DELAY_MILLIS = 4_000L
        const val MAX_REALTIME_RETRY_MILLIS = 30_000L
        const val MII_PUBLICATION_RETRY_MILLIS = 60_000L
    }
}
