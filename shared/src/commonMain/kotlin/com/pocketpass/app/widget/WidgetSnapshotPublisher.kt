package com.pocketpass.app.widget

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.feature.FriendsFeatureState
import com.pocketpass.app.feature.HomeProfileFeatureState
import com.pocketpass.app.feature.NotificationFeatureState
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.nearby.NearbyFeatureState
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Folds the feature-holder states into a [WidgetSnapshot] and hands changes to
 * the platform [WidgetSnapshotSink]. Rapid bursts (a sync landing) are
 * debounced, and identical content is not republished; the timestamp is
 * stamped at publish time so it never defeats the equality check.
 */
class WidgetSnapshotPublisher(
    private val scope: CoroutineScope,
    private val activeAccountId: StateFlow<UserId?>,
    private val homeProfile: StateFlow<HomeProfileFeatureState>,
    private val notifications: StateFlow<NotificationFeatureState>,
    private val friends: StateFlow<FriendsFeatureState>,
    private val nearby: StateFlow<NearbyFeatureState>,
    private val miiEditor: StateFlow<MiiEditorUiState>,
    private val settings: StateFlow<LocalSettings>,
    private val sink: WidgetSnapshotSink,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val startOfLocalDay: (Long) -> Long = ::startOfLocalDayEpochMillis,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    /** Snapshot content plus where the live portrait file is, before timestamping. */
    data class Pending(
        val content: WidgetSnapshot,
        val portraitSourcePath: String?,
    )

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            pendingUpdates().collect { publish(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Publishes the current state immediately (app launch, background refresh). */
    suspend fun publishNow() {
        publish(current())
    }

    fun current(): Pending = build(
        accountId = activeAccountId.value,
        homeProfile = homeProfile.value,
        notifications = notifications.value,
        friends = friends.value,
        nearby = nearby.value,
        miiEditor = miiEditor.value,
        settings = settings.value,
    )

    @OptIn(FlowPreview::class)
    private fun pendingUpdates(): Flow<Pending> {
        val core = combine(
            activeAccountId,
            homeProfile,
            notifications,
            friends,
            nearby,
        ) { accountId, home, notificationState, friendState, nearbyState ->
            Core(accountId, home, notificationState, friendState, nearbyState)
        }
        return combine(core, miiEditor, settings) { coreState, mii, localSettings ->
            build(
                accountId = coreState.accountId,
                homeProfile = coreState.homeProfile,
                notifications = coreState.notifications,
                friends = coreState.friends,
                nearby = coreState.nearby,
                miiEditor = mii,
                settings = localSettings,
            )
        }
            .debounce(debounceMillis)
            .distinctUntilChanged()
    }

    private suspend fun publish(pending: Pending) {
        sink.publish(
            snapshot = pending.content.copy(updatedAtEpochMillis = nowEpochMillis()),
            portraitSourcePath = pending.portraitSourcePath,
        )
    }

    internal fun build(
        accountId: UserId?,
        homeProfile: HomeProfileFeatureState,
        notifications: NotificationFeatureState,
        friends: FriendsFeatureState,
        nearby: NearbyFeatureState,
        miiEditor: MiiEditorUiState,
        settings: LocalSettings,
    ): Pending {
        val profile: UserProfile? = homeProfile.profile.valueOrNull()
        val encounters = homeProfile.recentInteractions.valueOrNull().orEmpty()
        val dayStart = startOfLocalDay(nowEpochMillis())
        val lastEncounter = listOfNotNull(
            encounters.maxOfOrNull { it.occurredAt.toEpochMilliseconds() },
            nearby.runtime.lastEncounterAt?.toEpochMilliseconds(),
        ).maxOrNull()
        val portraitPath = miiEditor.activePortraitFilePath?.takeIf { it.isNotBlank() }
        val content = WidgetSnapshot(
            signedIn = accountId != null,
            displayName = profile?.displayName.orEmpty(),
            bio = profile?.bio?.ifBlank { null } ?: DEFAULT_BIO,
            portraitFileName = portraitPath?.let { WidgetSnapshot.PORTRAIT_FILE_NAME },
            avatarBundledKey = (profile?.avatar as? AvatarReference.Bundled)?.key,
            encountersToday = encounters.count { it.occurredAt.toEpochMilliseconds() >= dayStart },
            lastEncounterEpochMillis = lastEncounter,
            nearbyStatus = nearby.runtime.status.name,
            unreadNotifications = notifications.unreadCount,
            friendsOnline = friends.onlineCount,
            themeMode = settings.themeMode.name,
            updatedAtEpochMillis = 0L,
        )
        return Pending(content = content, portraitSourcePath = portraitPath)
    }

    private class Core(
        val accountId: UserId?,
        val homeProfile: HomeProfileFeatureState,
        val notifications: NotificationFeatureState,
        val friends: FriendsFeatureState,
        val nearby: NearbyFeatureState,
    )

    private fun <T> LoadState<T>.valueOrNull(): T? = when (this) {
        is LoadState.Data -> value
        is LoadState.Error -> cachedValue
        LoadState.Loading -> null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
        // Mirrors the phone home hero's placeholder greeting.
        const val DEFAULT_BIO = "Hello! Nice to meet you!"
    }
}
