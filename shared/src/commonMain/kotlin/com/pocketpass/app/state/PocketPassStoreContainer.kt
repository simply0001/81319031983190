package com.pocketpass.app.state

import com.pocketpass.app.PocketPassRepositoryGraph
import com.pocketpass.app.audio.SoundEffectSink
import com.pocketpass.app.auth.AuthStateHolder
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.UserId
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
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.mii.MiiRendererCommand
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.nearby.NearbyFeatureState
import com.pocketpass.app.update.AppUpdateUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Everything the PocketPass state loop needs from its host. Android's AppContainer
 * fulfils this through a thin adapter; the iOS container implements it directly.
 */
interface PocketPassStoreContainer {
    val soundEffects: SoundEffectSink
    val miiEditor: MiiEditorController
    val integrityCompromised: Boolean
    val miiEditorEnabled: Boolean
    val pretendoImportEnabled: Boolean
    val encounterLedSupported: Boolean
    val activeAccountId: StateFlow<UserId?>
    val repositories: PocketPassRepositoryGraph
    val auth: AuthStateHolder
    val accountSetup: AccountSetupStateHolder
    val homeProfile: HomeProfileStateHolder
    val profileViewer: ProfileViewerStateHolder
    val friends: FriendsStateHolder
    val connectedApps: ConnectedAppsStateHolder
    val messages: MessagesStateHolder
    val notifications: NotificationStateHolder
    val activities: ActivitiesStateHolder
    val shop: ShopStateHolder
    val games: GamesStateHolder
    val leaderboard: LeaderboardStateHolder
    val achievements: AchievementsStateHolder
    val worldTour: WorldTourStateHolder
    val bingo: BingoStateHolder
    val settings: SettingsStateHolder
    val nearby: NearbyActions
    val stepRewards: StepRewardsActions
    val appUpdate: AppUpdateActions
    val requestedAppUpdate: StateFlow<Boolean>
    val requestedConversation: StateFlow<ConversationId?>

    fun consumeRequestedAppUpdate()
    fun consumeRequestedConversation()

    suspend fun deleteMiiSlot(slot: Int): RepositoryResult<Unit>
    suspend fun deleteAccount(): RepositoryResult<Unit>
    suspend fun signOut(): RepositoryResult<Unit>
    suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState>
    suspend fun resetSettings()
    suspend fun setUpdateAlertsEnabled(enabled: Boolean)
}

interface NearbyActions {
    val state: StateFlow<NearbyFeatureState>
    fun onNearbyPreferenceChanged(enabled: Boolean)
    fun requestPermissions()
    fun skipOnboarding()
    fun onAppOpened(openRepair: Boolean)
    fun onPermissionResult()
}

// Street-pass is not wired on this platform: the settings toggle flips the preference,
// permission prompts never appear, and no radio runs.
object InactiveNearby : NearbyActions {
    override val state: StateFlow<NearbyFeatureState> = MutableStateFlow(NearbyFeatureState())
    override fun onNearbyPreferenceChanged(enabled: Boolean) = Unit
    override fun requestPermissions() = Unit
    override fun skipOnboarding() = Unit
    override fun onAppOpened(openRepair: Boolean) = Unit
    override fun onPermissionResult() = Unit
}

interface AppUpdateActions {
    val state: StateFlow<AppUpdateUiState>
    fun check()
    fun download()
    fun install()
}

// In-app updates only exist for the sideloaded Android build.
object DisabledAppUpdate : AppUpdateActions {
    override val state: StateFlow<AppUpdateUiState> = MutableStateFlow(AppUpdateUiState())
    override fun check() = Unit
    override fun download() = Unit
    override fun install() = Unit
}

object InactiveMiiEditorController : MiiEditorController {
    override val state: StateFlow<MiiEditorUiState> = MutableStateFlow(MiiEditorUiState())
    override val rendererCommands: SharedFlow<MiiRendererCommand> = MutableSharedFlow()
    override fun activateAccount(accountKey: String?) = Unit
    override fun beginEdit(slot: Int, wearHat: Int?) = Unit
    override fun setActiveSlot(slot: Int) = Unit
    override fun deleteSlot(slot: Int) = Unit
    override fun dispatch(event: MiiEditorEvent) = Unit
}

// The status pills' data feed (clock, battery, connectivity).
fun interface StatusFeed {
    fun status(): Flow<StatusInfo>
}

object FrozenStatusFeed : StatusFeed {
    override fun status(): Flow<StatusInfo> = flowOf(StatusInfo())
}

// Persists the navigation stack across process recreation where the platform supports it.
interface RouteStateStore {
    fun restore(): List<PocketPassRoute>?
    fun persist(routes: List<PocketPassRoute>)
}

object NoRouteStateStore : RouteStateStore {
    override fun restore(): List<PocketPassRoute>? = null
    override fun persist(routes: List<PocketPassRoute>) = Unit
}
