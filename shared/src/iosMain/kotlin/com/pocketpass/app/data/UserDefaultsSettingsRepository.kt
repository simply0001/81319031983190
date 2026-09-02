package com.pocketpass.app.data

import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSUserDefaults

/**
 * iOS counterpart of the Android DataStore settings: every value lives in
 * NSUserDefaults under a pocketpass-prefixed key.
 */
class UserDefaultsSettingsRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsRepository {
    private val state = MutableStateFlow(load())

    override val settings: Flow<LocalSettings> = state

    private fun key(name: String) = "pocketpass.settings.$name"

    private fun bool(name: String, fallback: Boolean): Boolean =
        if (defaults.objectForKey(key(name)) == null) fallback else defaults.boolForKey(key(name))

    private fun float(name: String, fallback: Float): Float =
        if (defaults.objectForKey(key(name)) == null) fallback else defaults.floatForKey(key(name))

    private fun int(name: String, fallback: Int): Int =
        if (defaults.objectForKey(key(name)) == null) fallback else defaults.integerForKey(key(name)).toInt()

    private fun string(name: String): String? = defaults.stringForKey(key(name))

    private inline fun <reified T : Enum<T>> enum(name: String, fallback: T): T =
        string(name)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: fallback

    private fun load(): LocalSettings {
        val base = LocalSettings()
        return LocalSettings(
            nearbyEnabled = bool("nearbyEnabled", base.nearbyEnabled),
            nearbyOnboardingCompleted = bool("nearbyOnboardingCompleted", base.nearbyOnboardingCompleted),
            soundLevel = float("soundLevel", base.soundLevel),
            sfxLevel = float("sfxLevel", base.sfxLevel),
            themeMode = enum("themeMode", base.themeMode),
            moodEmojisEnabled = bool("moodEmojisEnabled", base.moodEmojisEnabled),
            homeMood = string("homeMood")?.let { stored ->
                HomeMood.entries.firstOrNull { it.name == stored }
            },
            encounterLedEnabled = bool("encounterLedEnabled", base.encounterLedEnabled),
            encounterAlertsEnabled = bool("encounterAlertsEnabled", base.encounterAlertsEnabled),
            nearbyRepairAlertsEnabled = bool("nearbyRepairAlertsEnabled", base.nearbyRepairAlertsEnabled),
            updateAlertsEnabled = bool("updateAlertsEnabled", base.updateAlertsEnabled),
            stepRewardsEnabled = bool("stepRewardsEnabled", base.stepRewardsEnabled),
            lastNotifiedUpdateVersionCode = int("lastNotifiedUpdateVersionCode", base.lastNotifiedUpdateVersionCode),
            lastSeenMinSupportedVersionCode = int("lastSeenMinSupportedVersionCode", base.lastSeenMinSupportedVersionCode),
            leaderboardScope = enum("leaderboardScope", base.leaderboardScope),
            recentInteractionsSort = enum("recentInteractionsSort", base.recentInteractionsSort),
            friendsSort = enum("friendsSort", base.friendsSort),
        )
    }

    private fun mutate(write: (LocalSettings) -> LocalSettings) {
        state.update(write)
        persist(state.value)
    }

    private fun persist(settings: LocalSettings) {
        defaults.setBool(settings.nearbyEnabled, key("nearbyEnabled"))
        defaults.setBool(settings.nearbyOnboardingCompleted, key("nearbyOnboardingCompleted"))
        defaults.setFloat(settings.soundLevel, key("soundLevel"))
        defaults.setFloat(settings.sfxLevel, key("sfxLevel"))
        defaults.setObject(settings.themeMode.name, key("themeMode"))
        defaults.setBool(settings.moodEmojisEnabled, key("moodEmojisEnabled"))
        settings.homeMood
            ?.let { defaults.setObject(it.name, key("homeMood")) }
            ?: defaults.removeObjectForKey(key("homeMood"))
        defaults.setBool(settings.encounterLedEnabled, key("encounterLedEnabled"))
        defaults.setBool(settings.encounterAlertsEnabled, key("encounterAlertsEnabled"))
        defaults.setBool(settings.nearbyRepairAlertsEnabled, key("nearbyRepairAlertsEnabled"))
        defaults.setBool(settings.updateAlertsEnabled, key("updateAlertsEnabled"))
        defaults.setBool(settings.stepRewardsEnabled, key("stepRewardsEnabled"))
        defaults.setInteger(settings.lastNotifiedUpdateVersionCode.toLong(), key("lastNotifiedUpdateVersionCode"))
        defaults.setInteger(settings.lastSeenMinSupportedVersionCode.toLong(), key("lastSeenMinSupportedVersionCode"))
        defaults.setObject(settings.leaderboardScope.name, key("leaderboardScope"))
        defaults.setObject(settings.recentInteractionsSort.name, key("recentInteractionsSort"))
        defaults.setObject(settings.friendsSort.name, key("friendsSort"))
    }

    override suspend fun setNearby(enabled: Boolean) = mutate { it.copy(nearbyEnabled = enabled) }

    override suspend fun setNearbyOnboardingCompleted(completed: Boolean) =
        mutate { it.copy(nearbyOnboardingCompleted = completed) }

    override suspend fun setSoundLevel(level: Float) = mutate { it.copy(soundLevel = level) }

    override suspend fun setSfxLevel(level: Float) = mutate { it.copy(sfxLevel = level) }

    override suspend fun setThemeMode(mode: ThemeMode) = mutate { it.copy(themeMode = mode) }

    override suspend fun setMoodEmojisEnabled(enabled: Boolean) =
        mutate { it.copy(moodEmojisEnabled = enabled) }

    override suspend fun setHomeMood(mood: HomeMood?) = mutate { it.copy(homeMood = mood) }

    override suspend fun setEncounterLedEnabled(enabled: Boolean) =
        mutate { it.copy(encounterLedEnabled = enabled) }

    override suspend fun setEncounterAlertsEnabled(enabled: Boolean) =
        mutate { it.copy(encounterAlertsEnabled = enabled) }

    override suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) =
        mutate { it.copy(nearbyRepairAlertsEnabled = enabled) }

    override suspend fun setUpdateAlertsEnabled(enabled: Boolean) =
        mutate { it.copy(updateAlertsEnabled = enabled) }

    override suspend fun setStepRewardsEnabled(enabled: Boolean) =
        mutate { it.copy(stepRewardsEnabled = enabled) }

    override suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int) =
        mutate { it.copy(lastNotifiedUpdateVersionCode = versionCode) }

    override suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int) =
        mutate { it.copy(lastSeenMinSupportedVersionCode = versionCode) }

    override suspend fun setLeaderboardScope(scope: LeaderboardScope) =
        mutate { it.copy(leaderboardScope = scope) }

    override suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort) =
        mutate { it.copy(recentInteractionsSort = sort) }

    override suspend fun setFriendsSort(sort: RecentInteractionsSort) =
        mutate { it.copy(friendsSort = sort) }

    override suspend fun resetSettings() = mutate { LocalSettings() }
}
