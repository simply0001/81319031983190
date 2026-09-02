package com.pocketpass.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.pocketpass.app.AppContainer
import com.pocketpass.app.PocketPassApplication
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.audio.SoundEffectSink
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.status.AndroidStatusProvider
import com.pocketpass.app.status.StatusProvider
import com.pocketpass.app.ui.controller.ControllerFocus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

private const val SAVED_ROUTES_KEY = "routes"
private val RouteJson = Json { ignoreUnknownKeys = true }

private class SavedStateRouteStore(
    private val savedStateHandle: SavedStateHandle,
) : RouteStateStore {
    override fun restore(): List<PocketPassRoute>? =
        savedStateHandle.get<String>(SAVED_ROUTES_KEY)
            ?.let { json ->
                runCatching { RouteJson.decodeFromString<List<PocketPassRoute>>(json) }.getOrNull()
            }

    override fun persist(routes: List<PocketPassRoute>) {
        savedStateHandle[SAVED_ROUTES_KEY] = RouteJson.encodeToString(routes)
    }
}

private fun AppContainer.asStoreContainer(): PocketPassStoreContainer {
    val container = this
    return object : PocketPassStoreContainer {
        override val soundEffects get() = container.soundEffects
        override val miiEditor: MiiEditorController get() = container.miiEditor
        override val integrityCompromised get() = container.integrityCompromised
        override val miiEditorEnabled get() = container.miiEditorEnabled
        override val pretendoImportEnabled get() = container.pretendoImportEnabled
        override val encounterLedSupported get() = container.encounterLedSupported
        override val activeAccountId get() = container.activeAccountId
        override val repositories get() = container.repositories
        override val auth get() = container.auth
        override val accountSetup get() = container.accountSetup
        override val homeProfile get() = container.homeProfile
        override val profileViewer get() = container.profileViewer
        override val friends get() = container.friends
        override val connectedApps get() = container.connectedApps
        override val messages get() = container.messages
        override val notifications get() = container.notifications
        override val activities get() = container.activities
        override val shop get() = container.shop
        override val games get() = container.games
        override val leaderboard get() = container.leaderboard
        override val achievements get() = container.achievements
        override val worldTour get() = container.worldTour
        override val bingo get() = container.bingo
        override val settings get() = container.settings
        override val nearby = object : NearbyActions {
            override val state get() = container.nearby.state
            override fun onNearbyPreferenceChanged(enabled: Boolean) =
                container.nearby.onNearbyPreferenceChanged(enabled)
            override fun requestPermissions() = container.nearby.requestPermissions()
            override fun skipOnboarding() = container.nearby.skipOnboarding()
            override fun onAppOpened(openRepair: Boolean) = container.nearby.onAppOpened(openRepair)
            override fun onPermissionResult() = container.nearby.onPermissionResult()
        }
        override val stepRewards get() = container.stepRewards
        override val appUpdate = object : AppUpdateActions {
            override val state get() = container.appUpdate.state
            override fun check() = container.appUpdate.check()
            override fun download() = container.appUpdate.download()
            override fun install() = container.appUpdate.install()
        }
        override val requestedAppUpdate get() = container.requestedAppUpdate
        override val requestedConversation get() = container.requestedConversation

        override fun consumeRequestedAppUpdate() = container.consumeRequestedAppUpdate()
        override fun consumeRequestedConversation() = container.consumeRequestedConversation()

        override suspend fun deleteMiiSlot(slot: Int) = container.deleteMiiSlot(slot)
        override suspend fun deleteAccount() = container.deleteAccount()
        override suspend fun signOut() = container.signOut()
        override suspend fun handleAuthCallback(callbackUri: String) =
            container.handleAuthCallback(callbackUri)
        override suspend fun resetSettings() = container.resetSettings()
        override suspend fun setUpdateAlertsEnabled(enabled: Boolean) =
            container.setUpdateAlertsEnabled(enabled)
    }
}

class PocketPassViewModel(
    application: Application,
    private val container: AppContainer,
    statusProvider: StatusProvider = AndroidStatusProvider(),
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(application) {
    private val store = PocketPassStore(
        container = container.asStoreContainer(),
        statusFeed = { statusProvider.status(application) },
        routeStore = SavedStateRouteStore(savedStateHandle),
        scope = viewModelScope,
    )

    val state: StateFlow<PocketPassUiState> get() = store.state
    val soundEffects: SoundEffectSink
        get() = container.soundEffects
    val controllerFocus: ControllerFocus =
        ControllerFocus(
            onMoved = { container.soundEffects.play(SoundEffect.Navigation) },
        )
    val miiEditorController: MiiEditorController
        get() = container.miiEditor

    fun dispatch(event: PocketPassEvent) = store.dispatch(event)

    fun handleAuthCallback(callbackUri: String) = store.handleAuthCallback(callbackUri)

    fun handleConsentLink(authorizationId: String) = store.handleConsentLink(authorizationId)

    fun onAppOpened(openNearbyRepair: Boolean) = store.onAppOpened(openNearbyRepair)

    fun onNearbyPermissionResult() = store.onNearbyPermissionResult()

    fun onStepRewardsPermissionResult() = store.onStepRewardsPermissionResult()

    class Factory(
        private val application: PocketPassApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras,
        ): T {
            require(modelClass.isAssignableFrom(PocketPassViewModel::class.java))
            return PocketPassViewModel(
                application = application,
                container = application.container,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }
}
