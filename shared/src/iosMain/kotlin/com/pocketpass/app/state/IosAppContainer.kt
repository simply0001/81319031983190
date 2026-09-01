package com.pocketpass.app.state

import com.pocketpass.app.IosBuildConfig
import com.pocketpass.app.PocketPassRepositoryGraph
import com.pocketpass.app.audio.IosBackgroundMusicPlayer
import com.pocketpass.app.audio.IosSoundEffectPlayer
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.auth.AuthStateHolder
import com.pocketpass.app.auth.SupabaseAuthRemoteDataSource
import com.pocketpass.app.auth.SupabaseSessionRepository
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.UserDefaultsSettingsRepository
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.buildPocketPassDatabase
import com.pocketpass.app.data.local.clearAllPocketPassTables
import com.pocketpass.app.data.pretendo.KtorPretendoMiiSource
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.repository.FixtureRepositoryBundle
import com.pocketpass.app.data.repository.PendingOperationScheduler
import com.pocketpass.app.data.repository.ProductionRepositoryBundle
import com.pocketpass.app.data.repository.RealtimePresenceRepository
import com.pocketpass.app.data.repository.RoomAchievementsRepository
import com.pocketpass.app.data.repository.RoomBingoRepository
import com.pocketpass.app.data.repository.RoomLeaderboardRepository
import com.pocketpass.app.data.repository.RoomWorldTourRepository
import com.pocketpass.app.data.supabase.PocketPassSupabaseClientFactory
import com.pocketpass.app.data.supabase.SupabaseBackendConfig
import com.pocketpass.app.data.supabase.SupabaseProductionRemoteDataSources
import com.pocketpass.app.data.supabase.realtime.SupabaseRealtimeGateway
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.accountIdOrNull
import com.pocketpass.app.feature.AccountSetupStateHolder
import com.pocketpass.app.feature.AchievementsStateHolder
import com.pocketpass.app.feature.ActivitiesStateHolder
import com.pocketpass.app.feature.BingoStateHolder
import com.pocketpass.app.feature.ConnectedAppsStateHolder
import com.pocketpass.app.feature.FriendsStateHolder
import com.pocketpass.app.feature.GamesStateHolder
import com.pocketpass.app.feature.HomeProfileStateHolder
import com.pocketpass.app.feature.LeaderboardStateHolder
import com.pocketpass.app.feature.MessagesStateHolder
import com.pocketpass.app.feature.NotificationStateHolder
import com.pocketpass.app.feature.ProfileViewerStateHolder
import com.pocketpass.app.feature.SettingsStateHolder
import com.pocketpass.app.feature.ShopStateHolder
import com.pocketpass.app.feature.WorldTourStateHolder
import com.pocketpass.app.mii.FixturePretendoMiiSource
import com.pocketpass.app.mii.IosFileMiiEditorPersistence
import com.pocketpass.app.mii.IosFileMiiProfilePublishQueue
import com.pocketpass.app.mii.LocalOnlyMiiActiveSlotCallback
import com.pocketpass.app.mii.LocalOnlyMiiEditorSaveCallback
import com.pocketpass.app.mii.MII_FIRST_SLOT
import com.pocketpass.app.mii.MII_SLOT_COUNT
import com.pocketpass.app.mii.MiiActiveSlotCallback
import com.pocketpass.app.mii.MiiEditorSaveResult
import com.pocketpass.app.mii.MiiEditorStateHolder
import com.pocketpass.app.mii.MiiPersistedEditorSession
import com.pocketpass.app.mii.MiiProfileFetcher
import com.pocketpass.app.mii.MiiStoredProfile
import com.pocketpass.app.mii.QueuedMiiEditorSaveCallback
import com.pocketpass.app.mii.iosDeletePortraitFile
import com.pocketpass.app.mii.iosReadPortraitFile
import com.pocketpass.app.mii.iosWriteRestoredPortrait
import com.pocketpass.app.mii.toSaveRequest
import com.pocketpass.app.mii.withoutLockedHat
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.security.KeychainSecureStringStore
import com.pocketpass.app.security.KeystoreSupabaseCodeVerifierCache
import com.pocketpass.app.security.KeystoreSupabaseSessionManager
import com.pocketpass.app.nearby.IosNearbyController
import com.pocketpass.app.nearby.NearbyCredentialPool
import com.pocketpass.app.nearby.NearbyEncounterProof
import com.pocketpass.app.nearby.NearbyProofOutboxStore
import com.pocketpass.app.nearby.NearbyReceiptOutcome
import com.pocketpass.app.nearby.NearbyReceiptVerdict
import com.pocketpass.app.nearby.NearbyReceiptVerdictBus
import com.pocketpass.app.nearby.postEncounterNotification
import com.pocketpass.app.sync.IosNetworkMonitor
import com.pocketpass.app.sync.OutboxProcessor
import com.pocketpass.app.sync.RealtimeRuntime
import com.pocketpass.app.widget.IosWidgetSnapshotSink
import com.pocketpass.app.widget.WidgetSnapshotPublisher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

/**
 * The iOS composition root. With backend coordinates generated into
 * IosBuildConfig it runs the same production graph as Android (Supabase,
 * Room, realtime); without them it stays in the fixture demo mode.
 */
class IosAppContainer(
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PocketPassStoreContainer {
    private val settingsRepository: SettingsRepository = UserDefaultsSettingsRepository()

    val fixtureMode: Boolean =
        !IosBuildConfig.BACKEND_ENABLED || IosBuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()

    private val backend: IosBackendComponents? =
        if (fixtureMode) null else createBackendComponents()

    override val repositories: PocketPassRepositoryGraph =
        backend?.repositories ?: createFixtureRepositoryGraph()

    override val activeAccountId: StateFlow<UserId?> =
        if (backend == null) {
            MutableStateFlow(FixtureData.CurrentUserId)
        } else {
            repositories.session.sessionState
                .map { it.accountIdOrNull() }
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.Eagerly,
                    initialValue = null,
                )
        }

    override val soundEffects = IosSoundEffectPlayer()
    val backgroundMusic = IosBackgroundMusicPlayer()

    override val integrityCompromised = false
    override val miiEditorEnabled = true
    override val pretendoImportEnabled = true
    override val encounterLedSupported = false

    private val miiPersistence = IosFileMiiEditorPersistence()
    private val miiPublishQueue = IosFileMiiProfilePublishQueue()

    private val miiPublishCallback = backend?.let { components ->
        QueuedMiiEditorSaveCallback(
            queue = miiPublishQueue,
            publisher = components.remote,
            readPortrait = { iosReadPortraitFile(it) },
            onPublished = { publication ->
                repositories.profiles.refreshProfile(publication.accountId)
            },
        )
    }

    private val miiActiveSlotCallback = backend?.let { components ->
        MiiActiveSlotCallback { accountKey, slot ->
            when (
                components.remote.setActiveMiiSlot(
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
        if (accountId == null) {
            flowOf(emptySet())
        } else {
            repositories.shop.observeOwnedHatTypes(accountId)
        }
    }

    override val miiEditor = MiiEditorStateHolder(
        persistence = miiPersistence,
        scope = applicationScope,
        saveCallback = miiPublishCallback ?: LocalOnlyMiiEditorSaveCallback,
        activeSlotCallback = miiActiveSlotCallback ?: LocalOnlyMiiActiveSlotCallback,
        remoteRestore = backend?.let { components ->
            { accountKey -> restoreMiiSessionFromServer(components.remote, accountKey) }
        },
        ownedHatTypes = ownedHatTypes,
        pretendoMiiSource = if (fixtureMode) {
            FixturePretendoMiiSource()
        } else {
            KtorPretendoMiiSource(versionName = iosVersionName(), platform = "iOS")
        },
        deletePortraitFile = { iosDeletePortraitFile(it) },
    )

    override val auth = AuthStateHolder(
        sessionRepository = repositories.session,
        scope = applicationScope,
    )
    override val accountSetup = AccountSetupStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        scope = applicationScope,
    )
    override val homeProfile = HomeProfileStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        encounterRepository = repositories.encounters,
        settingsRepository = settingsRepository,
        scope = applicationScope,
    )

    private val pendingConversation = MutableStateFlow<ConversationId?>(null)
    override val requestedConversation: StateFlow<ConversationId?> = pendingConversation
    override val requestedAppUpdate: StateFlow<Boolean> = MutableStateFlow(false)

    override val profileViewer = ProfileViewerStateHolder(
        accountId = activeAccountId,
        profileRepository = repositories.profiles,
        friendsRepository = repositories.friends,
        presenceRepository = repositories.presence,
        scope = applicationScope,
        statsSource = backend?.remote,
        onOpenConversation = { pendingConversation.value = it },
    )
    override val friends = FriendsStateHolder(
        accountId = activeAccountId,
        friendsRepository = repositories.friends,
        presenceRepository = repositories.presence,
        scope = applicationScope,
    )
    override val connectedApps = ConnectedAppsStateHolder(
        accountId = activeAccountId,
        source = backend?.remote,
        scope = applicationScope,
    )
    override val messages = MessagesStateHolder(
        accountId = activeAccountId,
        conversationRepository = repositories.conversations,
        scope = applicationScope,
        deterministicFixtureMessageCount = if (fixtureMode) 102 else 0,
        presenceRepository = repositories.presence,
        onMessageSent = { soundEffects.play(SoundEffect.MessageSent) },
        onGroupCreated = { pendingConversation.value = it },
    )
    override val notifications = NotificationStateHolder(
        accountId = activeAccountId,
        notificationRepository = repositories.notifications,
        friendsRepository = repositories.friends,
        scope = applicationScope,
    )
    override val activities = ActivitiesStateHolder(
        accountId = activeAccountId,
        shopRepository = repositories.shop,
        leaderboardRepository = repositories.leaderboard,
        worldTourRepository = repositories.worldTour,
        scope = applicationScope,
    )
    override val shop = ShopStateHolder(
        accountId = activeAccountId,
        shopRepository = repositories.shop,
        scope = applicationScope,
    )
    override val games = GamesStateHolder()
    override val leaderboard = LeaderboardStateHolder(
        accountId = activeAccountId,
        leaderboardRepository = repositories.leaderboard,
        settingsRepository = settingsRepository,
        scope = applicationScope,
    )
    override val achievements = AchievementsStateHolder(
        accountId = activeAccountId,
        achievementsRepository = repositories.achievements,
        scope = applicationScope,
    )
    override val worldTour = WorldTourStateHolder(
        accountId = activeAccountId,
        worldTourRepository = repositories.worldTour,
        scope = applicationScope,
    )
    override val bingo = BingoStateHolder(
        accountId = activeAccountId,
        bingoRepository = repositories.bingo,
        scope = applicationScope,
    )
    override val settings = SettingsStateHolder(settingsRepository, applicationScope)
    override val nearby: NearbyActions = backend?.let { components ->
        IosNearbyController(
            scope = applicationScope,
            settingsRepository = settingsRepository,
            settings = settings.settings,
            activeAccountId = activeAccountId,
            credentialPool = components.nearbyCredentialPool,
            submitProof = { accountId, proof -> submitNearbyProof(accountId, proof) },
            onEncounter = {
                applicationScope.launch {
                    soundEffects.play(SoundEffect.Notification)
                    if (settingsRepository.settings.first().encounterAlertsEnabled) {
                        postEncounterNotification()
                    }
                }
            },
        )
    } ?: InactiveNearby
    override val appUpdate = DisabledAppUpdate

    private val widgetPublisher = WidgetSnapshotPublisher(
        scope = applicationScope,
        activeAccountId = activeAccountId,
        homeProfile = homeProfile.state,
        notifications = notifications.state,
        friends = friends.state,
        nearby = nearby.state,
        miiEditor = miiEditor.state,
        settings = settings.settings,
        sink = IosWidgetSnapshotSink(),
    )

    private val appForeground = MutableStateFlow(true)
    private val networkMonitor = IosNetworkMonitor()

    private val realtimeRuntime = backend?.let { components ->
        RealtimeRuntime(
            scope = applicationScope,
            database = components.database,
            repositories = components.repositories,
            bundle = components.bundle,
            presence = components.presence,
            realtime = components.realtimeGateway,
            profileRemote = components.remote,
            soundEffects = soundEffects,
            settingsRepository = settingsRepository,
            appForeground = appForeground,
            networkState = networkMonitor.state,
            observeSelfTyping = { messages.observeSelfTyping(it) },
            onAppUpdateSignal = {},
            onNearbyEncounterNotification = { _, _ -> },
        )
    }

    init {
        applicationScope.launch {
            repositories.session.initialize()
        }
        val components = backend
        if (components == null) {
            miiEditor.activateAccount(FixtureData.CurrentUserId.value)
        } else {
            registerForegroundObservers()
            networkMonitor.start()
            realtimeRuntime?.start()
            applicationScope.launch {
                activeAccountId.collectLatest { accountId ->
                    miiEditor.activateAccount(accountId?.value)
                    if (accountId == null) return@collectLatest
                    var publicationVerified = false
                    while (currentCoroutineContext().isActive) {
                        if (!publicationVerified) {
                            publicationVerified = ensureSavedMiiPublication(accountId)
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
                appForeground.collectLatest { foreground ->
                    if (!foreground) return@collectLatest
                    activeAccountId.value?.let { accountId ->
                        runCatching { components.bundle.outboxProcessor.drain(accountId) }
                    }
                }
            }
        }
        applicationScope.launch {
            settingsRepository.settings.collect { soundEffects.volume = it.sfxLevel }
        }
        widgetPublisher.start()
    }

    override fun consumeRequestedAppUpdate() = Unit

    override fun consumeRequestedConversation() {
        pendingConversation.value = null
    }

    override suspend fun deleteMiiSlot(slot: Int): RepositoryResult<Unit> {
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
        backend?.remote?.let { deleter ->
            val result = deleter.deleteMiiSlot(accountId, slot)
            if (result is RepositoryResult.Failure) return result
        }
        miiEditor.deleteSlot(slot)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun deleteAccount(): RepositoryResult<Unit> {
        val components = backend
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
        return when (val result = components.remote.deleteAccount(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                miiPublishQueue.clearAccount(accountId.value)
                signOut()
            }
        }
    }

    override suspend fun signOut(): RepositoryResult<Unit> {
        val components = backend ?: return repositories.session.signOut()
        val accountId = activeAccountId.value
        var cleanupFailures: List<Throwable> = emptyList()
        val result = try {
            repositories.session.signOut()
        } finally {
            cleanupFailures = buildList {
                add(runCatching { components.sessionManager.deleteSession() }.exceptionOrNull())
                add(runCatching { components.verifierCache.deleteCodeVerifier() }.exceptionOrNull())
                add(runCatching { components.presence.clearAll() }.exceptionOrNull())
                accountId?.let { account ->
                    add(
                        runCatching { miiPublishQueue.clearAccount(account.value) }
                            .exceptionOrNull(),
                    )
                    add(runCatching { miiPersistence.clear(account.value) }.exceptionOrNull())
                }
                add(
                    runCatching { realtimeRuntime?.clearPostedNearbyNotifications() }
                        .exceptionOrNull(),
                )
                add(
                    try {
                        components.database.clearAllPocketPassTables()
                        null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        error
                    },
                )
            }.filterNotNull()
        }
        return if (cleanupFailures.isEmpty()) {
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

    override suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState> =
        repositories.session.handleAuthCallback(callbackUri)

    private suspend fun submitNearbyProof(
        accountId: UserId,
        proof: NearbyEncounterProof,
    ): NearbyReceiptVerdict {
        val components = backend ?: return NearbyReceiptVerdict.NotQueued
        if (!components.nearbyProofOutboxStore.enqueue(accountId, proof)) {
            return NearbyReceiptVerdict.NotQueued
        }
        applicationScope.launch {
            runCatching { components.bundle.outboxProcessor.drain(accountId) }
        }
        return components.nearbyReceiptVerdicts.await(EncounterId(proof.encounterId))?.verdict
            ?: NearbyReceiptVerdict.Unknown
    }

    /** One best-effort reconcile pass for BGTaskScheduler refreshes. */
    internal suspend fun performBackgroundSync(): Boolean {
        val components = backend ?: return true
        val accountId = activeAccountId.value ?: return true
        var healthy = true
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                healthy = false
            }
        }
        attempt { repositories.sync.synchronize(accountId) }
        attempt { components.bundle.outboxProcessor.drain(accountId) }
        attempt { miiPublishCallback?.drain(accountId.value) }
        attempt { widgetPublisher.publishNow() }
        return healthy
    }

    override suspend fun resetSettings() {
        settings.resetSettings()
    }

    override suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
        settings.setUpdateAlertsEnabled(enabled)
    }

    private fun registerForegroundObservers() {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> appForeground.value = true }
        center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> appForeground.value = false }
    }

    private fun createBackendComponents(): IosBackendComponents {
        val secureStore = KeychainSecureStringStore()
        val nearbySecureStore = KeychainSecureStringStore(
            service = "xyz.pocketpass.securestore.nearby",
        )
        val sessionManager = KeystoreSupabaseSessionManager(secureStore)
        val verifierCache = KeystoreSupabaseCodeVerifierCache(secureStore)
        val config = SupabaseBackendConfig(
            baseUrl = IosBuildConfig.SUPABASE_URL,
            publishableKey = IosBuildConfig.SUPABASE_PUBLISHABLE_KEY,
            authCallbackUrl = SupabaseBackendConfig.MOBILE_AUTH_CALLBACK_URL,
        )
        val client = PocketPassSupabaseClientFactory(
            config = config,
            sessionManager = sessionManager,
            codeVerifierCache = verifierCache,
        ).get()
        val authRemote = SupabaseAuthRemoteDataSource(client, config)
        val session = SupabaseSessionRepository(authRemote, applicationScope)
        val database = buildPocketPassDatabase(iosDatabasePath())
        val remote = SupabaseProductionRemoteDataSources(client)

        // The scheduler is needed to create the bundle whose processor it
        // drains, so the reference is bound just after creation.
        var outboxProcessor: OutboxProcessor? = null
        val scheduler = PendingOperationScheduler { accountId ->
            applicationScope.launch {
                runCatching { outboxProcessor?.drain(accountId) }
            }
        }
        val nearbyProofOutboxStore = NearbyProofOutboxStore(
            outboxDao = database.outboxDao(),
            secureStore = nearbySecureStore,
            scheduler = scheduler,
        )
        val nearbyReceiptVerdicts = NearbyReceiptVerdictBus()
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
            pendingOperationScheduler = scheduler,
        )
        outboxProcessor = bundle.outboxProcessor

        val presence = RealtimePresenceRepository()
        val graph = PocketPassRepositoryGraph(
            session = session,
            profiles = bundle.profiles,
            friends = bundle.friends,
            conversations = bundle.messages,
            notifications = bundle.notifications,
            shop = bundle.shop,
            leaderboard = RoomLeaderboardRepository(
                database.leaderboardDao(),
                remote.sources.leaderboard,
            ),
            achievements = RoomAchievementsRepository(
                database.achievementDao(),
                remote.sources.achievements,
            ),
            worldTour = RoomWorldTourRepository(
                database.worldTourDao(),
                remote.sources.worldTour,
            ),
            bingo = RoomBingoRepository(
                database.bingoDao(),
                remote.sources.bingo,
            ),
            encounters = bundle.encounters,
            presence = presence,
            sync = bundle.sync,
        )
        return IosBackendComponents(
            database = database,
            sessionManager = sessionManager,
            verifierCache = verifierCache,
            remote = remote,
            bundle = bundle,
            presence = presence,
            realtimeGateway = SupabaseRealtimeGateway(client),
            repositories = graph,
            nearbyProofOutboxStore = nearbyProofOutboxStore,
            nearbyReceiptVerdicts = nearbyReceiptVerdicts,
            nearbyCredentialPool = NearbyCredentialPool(
                dao = database.nearbyEncounterDao(),
                remote = remote.sources.encounters,
                secureStore = nearbySecureStore,
            ),
        )
    }

    private fun createFixtureRepositoryGraph(): PocketPassRepositoryGraph =
        FixtureRepositoryBundle().let { fixtures ->
            PocketPassRepositoryGraph(
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
                                ?.let { iosWriteRestoredPortrait(it) },
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

    private suspend fun ensureSavedMiiPublication(accountId: UserId): Boolean {
        val components = backend ?: return true
        if (miiPublishQueue.pending(accountId.value).isNotEmpty()) return false
        val session = miiPersistence.load(accountId.value) ?: return true
        val stored = session.savedProfiles[session.activeSlot]
            ?: session.savedProfiles.values.firstOrNull()
            ?: return true
        if (stored.portraitFilePath.isNullOrBlank()) return true
        val refresh = repositories.profiles.refreshProfile(accountId)
        if (refresh is RepositoryResult.Failure) return false
        val profile = components.database.profileDao().get(accountId.value)
        if (!profile?.avatarValue.isNullOrBlank()) return true
        val publishable = stored.appearance.normalized().withoutLockedHat(ownedHatTypes.first())
        miiPublishQueue.enqueue(
            stored.copy(appearance = publishable)
                .toSaveRequest(accountId.value, slot = session.activeSlot),
        )
        return false
    }

    private class IosBackendComponents(
        val database: PocketPassDatabase,
        val sessionManager: KeystoreSupabaseSessionManager,
        val verifierCache: KeystoreSupabaseCodeVerifierCache,
        val remote: SupabaseProductionRemoteDataSources,
        val bundle: ProductionRepositoryBundle,
        val presence: RealtimePresenceRepository,
        val realtimeGateway: SupabaseRealtimeGateway,
        val repositories: PocketPassRepositoryGraph,
        val nearbyProofOutboxStore: NearbyProofOutboxStore,
        val nearbyReceiptVerdicts: NearbyReceiptVerdictBus,
        val nearbyCredentialPool: NearbyCredentialPool,
    )

    private companion object {
        const val MII_PUBLICATION_RETRY_MILLIS = 60_000L
    }
}

private fun iosVersionName(): String =
    platform.Foundation.NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String
        ?: ""

private fun iosDatabasePath(): String {
    val applicationSupport =
        NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
            ?: error("No Application Support directory available")
    val directory = "$applicationSupport/pocketpass".toPath()
    FileSystem.SYSTEM.createDirectories(directory)
    return (directory / "pocketpass.db").toString()
}

/** Clock and battery for the status pills, refreshed every few seconds. */
class IosStatusFeed : StatusFeed {
    private val clockFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }

    override fun status(): Flow<StatusInfo> = flow {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
        while (true) {
            val level = UIDevice.currentDevice.batteryLevel
            emit(
                StatusInfo(
                    time = clockFormatter.stringFromDate(NSDate()),
                    batteryPercent = if (level < 0f) 100 else (level * 100f).roundToInt(),
                    batteryCharging = UIDevice.currentDevice.batteryState ==
                        UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                        UIDevice.currentDevice.batteryState ==
                        UIDeviceBatteryState.UIDeviceBatteryStateFull,
                    wifiConnected = true,
                    wifiSignalLevel = 2,
                ),
            )
            delay(5_000)
        }
    }
}
