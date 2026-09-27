package com.pocketpass.app.widget

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.feature.AchievementsFeatureState
import com.pocketpass.app.feature.BingoFeatureState
import com.pocketpass.app.feature.FriendsFeatureState
import com.pocketpass.app.feature.HomeProfileFeatureState
import com.pocketpass.app.feature.LeaderboardFeatureState
import com.pocketpass.app.feature.NotificationFeatureState
import com.pocketpass.app.feature.ShopFeatureState
import com.pocketpass.app.feature.WorldTourFeatureState
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.nearby.NearbyFeatureState
import com.pocketpass.app.steps.StepRewardsState
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

class WidgetSnapshotPublisher(
    private val scope: CoroutineScope,
    private val activeAccountId: StateFlow<UserId?>,
    private val homeProfile: StateFlow<HomeProfileFeatureState>,
    private val notifications: StateFlow<NotificationFeatureState>,
    private val friends: StateFlow<FriendsFeatureState>,
    private val nearby: StateFlow<NearbyFeatureState>,
    private val miiEditor: StateFlow<MiiEditorUiState>,
    private val settings: StateFlow<LocalSettings>,
    private val shop: StateFlow<ShopFeatureState>,
    private val stepRewards: StateFlow<StepRewardsState>,
    private val achievements: StateFlow<AchievementsFeatureState>,
    private val bingo: StateFlow<BingoFeatureState>,
    private val worldTour: StateFlow<WorldTourFeatureState>,
    private val leaderboard: StateFlow<LeaderboardFeatureState>,
    private val sink: WidgetSnapshotSink,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val startOfLocalDay: (Long) -> Long = ::startOfLocalDayEpochMillis,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    data class Pending(
        val content: WidgetSnapshot,
        val portraitSourcePath: String?,
    )

    internal data class Inputs(
        val accountId: UserId?,
        val homeProfile: HomeProfileFeatureState,
        val notifications: NotificationFeatureState,
        val friends: FriendsFeatureState,
        val nearby: NearbyFeatureState,
        val miiEditor: MiiEditorUiState,
        val settings: LocalSettings,
        val shop: ShopFeatureState = ShopFeatureState(),
        val stepRewards: StepRewardsState = StepRewardsState(),
        val achievements: AchievementsFeatureState = AchievementsFeatureState(),
        val bingo: BingoFeatureState = BingoFeatureState(),
        val worldTour: WorldTourFeatureState = WorldTourFeatureState(),
        val leaderboard: LeaderboardFeatureState = LeaderboardFeatureState(),
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

    suspend fun publishNow() {
        publish(current())
    }

    fun current(): Pending = build(
        Inputs(
            accountId = activeAccountId.value,
            homeProfile = homeProfile.value,
            notifications = notifications.value,
            friends = friends.value,
            nearby = nearby.value,
            miiEditor = miiEditor.value,
            settings = settings.value,
            shop = shop.value,
            stepRewards = stepRewards.value,
            achievements = achievements.value,
            bingo = bingo.value,
            worldTour = worldTour.value,
            leaderboard = leaderboard.value,
        ),
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
        val activity = combine(
            shop,
            stepRewards,
            achievements,
            bingo,
            worldTour,
        ) { shopState, steps, achievementState, bingoState, worldTourState ->
            Activity(shopState, steps, achievementState, bingoState, worldTourState)
        }
        return combine(core, activity, leaderboard, miiEditor, settings) { coreState, activityState, board, mii, localSettings ->
            build(
                Inputs(
                    accountId = coreState.accountId,
                    homeProfile = coreState.homeProfile,
                    notifications = coreState.notifications,
                    friends = coreState.friends,
                    nearby = coreState.nearby,
                    miiEditor = mii,
                    settings = localSettings,
                    shop = activityState.shop,
                    stepRewards = activityState.stepRewards,
                    achievements = activityState.achievements,
                    bingo = activityState.bingo,
                    worldTour = activityState.worldTour,
                    leaderboard = board,
                ),
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

    internal fun build(inputs: Inputs): Pending {
        val profile: UserProfile? = inputs.homeProfile.profile.valueOrNull()
        val encounters = inputs.homeProfile.recentInteractions.valueOrNull().orEmpty()
        val friendList = inputs.friends.friends.valueOrNull().orEmpty()
        val dayStart = startOfLocalDay(nowEpochMillis())
        val lastEncounter = listOfNotNull(
            encounters.maxOfOrNull { it.occurredAt.toEpochMilliseconds() },
            inputs.nearby.runtime.lastEncounterAt?.toEpochMilliseconds(),
        ).maxOrNull()
        val portraitPath = inputs.miiEditor.activePortraitFilePath?.takeIf { it.isNotBlank() }
        val rank = inputs.accountId?.let { me ->
            inputs.leaderboard.entries.indexOfFirst { it.userId == me }.takeIf { it >= 0 }?.plus(1)
        }
        val content = WidgetSnapshot(
            signedIn = inputs.accountId != null,
            displayName = profile?.displayName.orEmpty(),
            bio = profile?.bio?.ifBlank { null } ?: DEFAULT_BIO,
            portraitFileName = portraitPath?.let { WidgetSnapshot.PORTRAIT_FILE_NAME },
            avatarBundledKey = (profile?.avatar as? AvatarReference.Bundled)?.key,
            encountersToday = encounters.count { it.occurredAt.toEpochMilliseconds() >= dayStart },
            lastEncounterEpochMillis = lastEncounter,
            nearbyStatus = inputs.nearby.runtime.status.name,
            unreadNotifications = inputs.notifications.unreadCount,
            friendsOnline = inputs.friends.onlineCount,
            themeMode = inputs.settings.themeMode.name,
            updatedAtEpochMillis = 0L,
            tokenBalance = inputs.shop.tokenBalance,
            stepsToday = inputs.stepRewards.stepsToday,
            friendCode = inputs.friends.myFriendCode.valueOrNull()?.value,
            achievementsUnlocked = inputs.achievements.achievements.count { it.unlocked },
            achievementsTotal = AchievementCatalog.definitions.size,
            bingoLines = bingoLines(inputs.bingo.cells),
            worldTourCountries = inputs.worldTour.regions.distinctBy { it.countryCode }.size,
            leaderboardRank = rank,
            leaderboardScope = inputs.leaderboard.scope.name,
            recentPeople = recentPeople(encounters),
            onlineFriends = onlineFriends(friendList),
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

    private class Activity(
        val shop: ShopFeatureState,
        val stepRewards: StepRewardsState,
        val achievements: AchievementsFeatureState,
        val bingo: BingoFeatureState,
        val worldTour: WorldTourFeatureState,
    )

    private fun <T> LoadState<T>.valueOrNull(): T? = when (this) {
        is LoadState.Data -> value
        is LoadState.Error -> cachedValue
        LoadState.Loading -> null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
        const val DEFAULT_BIO = "Hello! Nice to meet you!"

        fun recentPeople(encounters: List<NearbyEncounter>): List<WidgetPerson> =
            encounters
                .sortedByDescending { it.occurredAt }
                .distinctBy { it.profile.userId }
                .take(WidgetSnapshot.MAX_PEOPLE)
                .map { encounter -> encounter.profile.toWidgetPerson(encounter.occurredAt.toEpochMilliseconds()) }

        fun onlineFriends(friends: List<Friend>): List<WidgetPerson> =
            friends
                .filter(Friend::isOnline)
                .sortedBy { it.profile.displayName.lowercase() }
                .take(WidgetSnapshot.MAX_PEOPLE)
                .map { friend -> friend.profile.toWidgetPerson(null) }

        private fun UserProfile.toWidgetPerson(occurredAtEpochMillis: Long?): WidgetPerson = WidgetPerson(
            userId = userId.value,
            displayName = displayName,
            avatarUrl = (avatar as? AvatarReference.Remote)?.url,
            avatarBundledKey = (avatar as? AvatarReference.Bundled)?.key,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
    }
}
