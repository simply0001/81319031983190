package com.pocketpass.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.pocketPassDataStore by preferencesDataStore(name = "pocketpass_settings")

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

    suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int)

    suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int)

    suspend fun setLeaderboardScope(scope: LeaderboardScope)

    suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort)

    suspend fun setFriendsSort(sort: RecentInteractionsSort)

    suspend fun resetSettings()
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    private object Keys {
        val nearbyEnabled = booleanPreferencesKey("nearby_enabled")
        val nearbyOnboardingCompleted =
            booleanPreferencesKey("nearby_onboarding_completed")
        val soundLevel = floatPreferencesKey("sound_level")
        val sfxLevel = floatPreferencesKey("sfx_level")
        val themeMode = stringPreferencesKey("theme_mode")
        val moodEmojisEnabled = booleanPreferencesKey("mood_emojis_enabled")
        val homeMood = stringPreferencesKey("home_mood")
        val encounterLedEnabled = booleanPreferencesKey("encounter_led_enabled")
        val encounterAlertsEnabled = booleanPreferencesKey("encounter_alerts_enabled")
        val nearbyRepairAlertsEnabled =
            booleanPreferencesKey("nearby_repair_alerts_enabled")
        val updateAlertsEnabled = booleanPreferencesKey("update_alerts_enabled")
        val lastNotifiedUpdateVersionCode =
            intPreferencesKey("last_notified_update_version_code")
        val lastSeenMinSupportedVersionCode =
            intPreferencesKey("last_seen_min_supported_version_code")
        val leaderboardScope = stringPreferencesKey("leaderboard_scope")
        val recentInteractionsSort =
            stringPreferencesKey("recent_interactions_sort")
        val friendsSort = stringPreferencesKey("friends_sort")
    }

    override val settings: Flow<LocalSettings> = context.pocketPassDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            LocalSettings(
                nearbyEnabled = preferences[Keys.nearbyEnabled] ?: true,
                nearbyOnboardingCompleted =
                    preferences[Keys.nearbyOnboardingCompleted] ?: false,
                soundLevel = preferences[Keys.soundLevel] ?: 0.45f,
                sfxLevel = preferences[Keys.sfxLevel] ?: 0.6f,
                themeMode = preferences[Keys.themeMode]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.System,
                moodEmojisEnabled = preferences[Keys.moodEmojisEnabled] ?: true,
                homeMood = preferences[Keys.homeMood]
                    ?.let { stored -> HomeMood.entries.firstOrNull { it.name == stored } },
                encounterLedEnabled = preferences[Keys.encounterLedEnabled] ?: true,
                encounterAlertsEnabled =
                    preferences[Keys.encounterAlertsEnabled] ?: true,
                nearbyRepairAlertsEnabled =
                    preferences[Keys.nearbyRepairAlertsEnabled] ?: true,
                updateAlertsEnabled = preferences[Keys.updateAlertsEnabled] ?: true,
                lastNotifiedUpdateVersionCode =
                    preferences[Keys.lastNotifiedUpdateVersionCode] ?: 0,
                lastSeenMinSupportedVersionCode =
                    preferences[Keys.lastSeenMinSupportedVersionCode] ?: 0,
                leaderboardScope = preferences[Keys.leaderboardScope]
                    ?.let { stored ->
                        LeaderboardScope.entries.firstOrNull { it.key == stored }
                    }
                    ?: LeaderboardScope.Friends,
                recentInteractionsSort = preferences[Keys.recentInteractionsSort]
                    ?.let { stored ->
                        RecentInteractionsSort.entries.firstOrNull { it.key == stored }
                    }
                    ?: RecentInteractionsSort.LatestEncounter,
                friendsSort = preferences[Keys.friendsSort]
                    ?.let { stored ->
                        RecentInteractionsSort.entries.firstOrNull { it.key == stored }
                    }
                    ?: RecentInteractionsSort.LatestEncounter,
            )
        }

    override suspend fun setNearby(enabled: Boolean) {
        context.pocketPassDataStore.edit { it[Keys.nearbyEnabled] = enabled }
    }

    override suspend fun setNearbyOnboardingCompleted(completed: Boolean) {
        context.pocketPassDataStore.edit {
            it[Keys.nearbyOnboardingCompleted] = completed
        }
    }

    override suspend fun setSoundLevel(level: Float) {
        context.pocketPassDataStore.edit {
            it[Keys.soundLevel] = level.coerceIn(0f, 1f)
        }
    }

    override suspend fun setSfxLevel(level: Float) {
        context.pocketPassDataStore.edit {
            it[Keys.sfxLevel] = level.coerceIn(0f, 1f)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.pocketPassDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun setMoodEmojisEnabled(enabled: Boolean) {
        context.pocketPassDataStore.edit { it[Keys.moodEmojisEnabled] = enabled }
    }

    override suspend fun setHomeMood(mood: HomeMood?) {
        context.pocketPassDataStore.edit {
            if (mood == null) it.remove(Keys.homeMood) else it[Keys.homeMood] = mood.name
        }
    }

    override suspend fun setEncounterLedEnabled(enabled: Boolean) {
        context.pocketPassDataStore.edit { it[Keys.encounterLedEnabled] = enabled }
    }

    override suspend fun setEncounterAlertsEnabled(enabled: Boolean) {
        context.pocketPassDataStore.edit { it[Keys.encounterAlertsEnabled] = enabled }
    }

    override suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) {
        context.pocketPassDataStore.edit {
            it[Keys.nearbyRepairAlertsEnabled] = enabled
        }
    }

    override suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
        context.pocketPassDataStore.edit {
            it[Keys.updateAlertsEnabled] = enabled
        }
    }

    override suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int) {
        context.pocketPassDataStore.edit {
            it[Keys.lastNotifiedUpdateVersionCode] = versionCode
        }
    }

    override suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int) {
        context.pocketPassDataStore.edit {
            it[Keys.lastSeenMinSupportedVersionCode] = versionCode
        }
    }

    override suspend fun setLeaderboardScope(scope: LeaderboardScope) {
        context.pocketPassDataStore.edit { it[Keys.leaderboardScope] = scope.key }
    }

    override suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort) {
        context.pocketPassDataStore.edit {
            it[Keys.recentInteractionsSort] = sort.key
        }
    }

    override suspend fun setFriendsSort(sort: RecentInteractionsSort) {
        context.pocketPassDataStore.edit { it[Keys.friendsSort] = sort.key }
    }

    override suspend fun resetSettings() {
        context.pocketPassDataStore.edit { preferences ->
            preferences[Keys.nearbyEnabled] = true
            preferences[Keys.nearbyOnboardingCompleted] = false
            preferences[Keys.soundLevel] = 0.45f
            preferences[Keys.sfxLevel] = 0.6f
            preferences[Keys.themeMode] = ThemeMode.System.name
            preferences[Keys.moodEmojisEnabled] = true
            preferences.remove(Keys.homeMood)
            preferences[Keys.encounterLedEnabled] = true
            preferences[Keys.encounterAlertsEnabled] = true
            preferences[Keys.nearbyRepairAlertsEnabled] = true
            preferences[Keys.updateAlertsEnabled] = true
            preferences[Keys.leaderboardScope] = LeaderboardScope.Friends.key
            preferences[Keys.recentInteractionsSort] =
                RecentInteractionsSort.LatestEncounter.key
            preferences[Keys.friendsSort] =
                RecentInteractionsSort.LatestEncounter.key
        }
    }
}
