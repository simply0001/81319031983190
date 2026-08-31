package com.pocketpass.app.state

import com.pocketpass.app.PocketPassRepositoryGraph
import com.pocketpass.app.audio.NoSoundEffects
import com.pocketpass.app.auth.AuthStateHolder
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.UserDefaultsSettingsRepository
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.repository.FixtureRepositoryBundle
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
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
import com.pocketpass.app.model.StatusInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import kotlin.math.roundToInt

/**
 * The iOS composition root, currently in the same local demo mode the Android app uses
 * when it is built without a backend: fixture repositories, no sign-in, no street-pass.
 */
class IosAppContainer(
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PocketPassStoreContainer {
    private val settingsRepository: SettingsRepository = UserDefaultsSettingsRepository()

    override val repositories: PocketPassRepositoryGraph = FixtureRepositoryBundle().let { fixtures ->
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

    override val activeAccountId: StateFlow<UserId?> =
        MutableStateFlow(FixtureData.CurrentUserId)

    override val soundEffects = NoSoundEffects
    override val miiEditor = InactiveMiiEditorController
    override val integrityCompromised = false
    override val miiEditorEnabled = false
    override val pretendoImportEnabled = false
    override val encounterLedSupported = false

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
        statsSource = null,
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
        source = null,
        scope = applicationScope,
    )
    override val messages = MessagesStateHolder(
        accountId = activeAccountId,
        conversationRepository = repositories.conversations,
        scope = applicationScope,
        deterministicFixtureMessageCount = 102,
        presenceRepository = repositories.presence,
        onMessageSent = {},
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
    override val nearby = InactiveNearby
    override val appUpdate = DisabledAppUpdate

    init {
        applicationScope.launch {
            repositories.session.initialize()
        }
    }

    override fun consumeRequestedAppUpdate() = Unit

    override fun consumeRequestedConversation() {
        pendingConversation.value = null
    }

    override suspend fun deleteMiiSlot(slot: Int): RepositoryResult<Unit> {
        miiEditor.deleteSlot(slot)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun deleteAccount(): RepositoryResult<Unit> =
        RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Misconfigured,
                message = "Account deletion is unavailable.",
                retryable = false,
            ),
        )

    override suspend fun signOut(): RepositoryResult<Unit> = repositories.session.signOut()

    override suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState> =
        repositories.session.handleAuthCallback(callbackUri)

    override suspend fun resetSettings() {
        settings.resetSettings()
    }

    override suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
        settings.setUpdateAlertsEnabled(enabled)
    }
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
