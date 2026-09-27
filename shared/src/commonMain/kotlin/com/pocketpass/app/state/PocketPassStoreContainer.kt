package com.pocketpass.app.state

import com.pocketpass.app.PocketPassRepositoryGraph
import com.pocketpass.app.audio.SoundEffectSink
import com.pocketpass.app.auth.AuthStateHolder
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSecurityStateHolder
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
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.nearby.NearbyFeatureState
import com.pocketpass.app.update.AppUpdateUiState
import com.pocketpass.app.widget.WidgetDesignsStateHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.pocketpass.app.feature.PuzzleStateHolder

interface PocketPassStoreContainer {
    val soundEffects: SoundEffectSink
    val miiEditor: MiiEditorController
    val integrityCompromised: Boolean
    val miiEditorEnabled: Boolean
    val pretendoImportEnabled: Boolean
    val encounterLedSupported: Boolean
    val messagePushSupported: Boolean get() = false
    val activeAccountId: StateFlow<UserId?>
    val appForeground: StateFlow<Boolean> get() = AlwaysForeground
    val repositories: PocketPassRepositoryGraph
    val auth: AuthStateHolder
    val accountSetup: AccountSetupStateHolder
    val accountSecurity: AccountSecurityStateHolder
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
    val puzzle: PuzzleStateHolder
    val settings: SettingsStateHolder
    val nearby: NearbyActions
    val stepRewards: StepRewardsActions
    val appUpdate: AppUpdateActions
    val widgetDesigns: WidgetDesignsStateHolder
    val widgetPlatform: WidgetPlatformActions
    val requestedAppUpdate: StateFlow<Boolean>
    val requestedConversation: StateFlow<ConversationId?>
    val requestedBoard: StateFlow<com.pocketpass.app.boards.BoardDestination?> get() = NoRequestedBoard
    fun consumeRequestedBoard() {}

    val requestedWidgetAssignment: StateFlow<Int?>

    fun consumeRequestedAppUpdate()
    fun consumeRequestedConversation()
    fun consumeRequestedWidgetAssignment()

    suspend fun deleteMiiSlot(slot: Int): RepositoryResult<Unit>
    suspend fun deleteAccount(): RepositoryResult<Unit>
    suspend fun signOut(): RepositoryResult<Unit>
    suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState>
    suspend fun resetSettings()
    suspend fun setUpdateAlertsEnabled(enabled: Boolean)
    suspend fun setMessageAlertsEnabled(enabled: Boolean)
}

private val NoRequestedBoard = MutableStateFlow<com.pocketpass.app.boards.BoardDestination?>(null)
private val AlwaysForeground: StateFlow<Boolean> = MutableStateFlow(true)

interface NearbyActions {
    val state: StateFlow<NearbyFeatureState>
    fun onNearbyPreferenceChanged(enabled: Boolean)
    fun requestPermissions()
    fun onAppOpened(openRepair: Boolean)
    fun onPermissionResult()
}

object InactiveNearby : NearbyActions {
    override val state: StateFlow<NearbyFeatureState> = MutableStateFlow(NearbyFeatureState())
    override fun onNearbyPreferenceChanged(enabled: Boolean) = Unit
    override fun requestPermissions() = Unit
    override fun onAppOpened(openRepair: Boolean) = Unit
    override fun onPermissionResult() = Unit
}

interface AppUpdateActions {
    val state: StateFlow<AppUpdateUiState>
    fun check()
    fun download()
    fun install()
}

object DisabledAppUpdate : AppUpdateActions {
    override val state: StateFlow<AppUpdateUiState> = MutableStateFlow(AppUpdateUiState())
    override fun check() = Unit
    override fun download() = Unit
    override fun install() = Unit
}

fun interface StatusFeed {
    fun status(): Flow<StatusInfo>
}

interface RouteStateStore {
    fun restore(): List<PocketPassRoute>?
    fun persist(routes: List<PocketPassRoute>)
}

object NoRouteStateStore : RouteStateStore {
    override fun restore(): List<PocketPassRoute>? = null
    override fun persist(routes: List<PocketPassRoute>) = Unit
}
