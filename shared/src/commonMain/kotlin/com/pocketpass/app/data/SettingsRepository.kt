package com.pocketpass.app.data

import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow

data class LocalSettings(
    val nearbyEnabled: Boolean = true,
    val nearbyOnboardingCompleted: Boolean = false,
    val soundLevel: Float = 0.45f,
    val sfxLevel: Float = 0.6f,
    val themeMode: ThemeMode = ThemeMode.System,
    val moodEmojisEnabled: Boolean = true,
    val homeMood: HomeMood? = null,
    val encounterLedEnabled: Boolean = true,
    val encounterAlertsEnabled: Boolean = true,
    val nearbyRepairAlertsEnabled: Boolean = true,
    val updateAlertsEnabled: Boolean = true,
    val stepRewardsEnabled: Boolean = false,
    val lastNotifiedUpdateVersionCode: Int = 0,
    val lastSeenMinSupportedVersionCode: Int = 0,
    val leaderboardScope: LeaderboardScope = LeaderboardScope.Friends,
    val recentInteractionsSort: RecentInteractionsSort =
        RecentInteractionsSort.LatestEncounter,
    val friendsSort: RecentInteractionsSort =
        RecentInteractionsSort.LatestEncounter,
)

interface SettingsRepository {
    val settings: Flow<LocalSettings>

    suspend fun setNearby(enabled: Boolean)

    suspend fun setNearbyOnboardingCompleted(completed: Boolean)

    suspend fun setSoundLevel(level: Float)

    suspend fun setSfxLevel(level: Float)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMoodEmojisEnabled(enabled: Boolean)

    suspend fun setHomeMood(mood: HomeMood?)

    suspend fun setEncounterLedEnabled(enabled: Boolean)

    suspend fun setEncounterAlertsEnabled(enabled: Boolean)

    suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean)

    suspend fun setUpdateAlertsEnabled(enabled: Boolean)

    suspend fun setStepRewardsEnabled(enabled: Boolean)

    suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int)

    suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int)

    suspend fun setLeaderboardScope(scope: LeaderboardScope)

    suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort)

    suspend fun setFriendsSort(sort: RecentInteractionsSort)

    suspend fun resetSettings()
}
