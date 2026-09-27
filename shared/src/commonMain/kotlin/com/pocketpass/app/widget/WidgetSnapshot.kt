package com.pocketpass.app.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetSnapshot(
    val version: Int = CURRENT_VERSION,
    val signedIn: Boolean,
    val displayName: String,
    val bio: String,
    val portraitFileName: String?,
    val avatarBundledKey: String?,
    val encountersToday: Int,
    val lastEncounterEpochMillis: Long?,
    val nearbyStatus: String,
    val unreadNotifications: Int,
    val friendsOnline: Int,
    val themeMode: String,
    val updatedAtEpochMillis: Long,
    val tokenBalance: Int = 0,
    val stepsToday: Int = 0,
    val friendCode: String? = null,
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = 0,
    val bingoLines: Int = 0,
    val worldTourCountries: Int = 0,
    val leaderboardRank: Int? = null,
    val leaderboardScope: String? = null,
    val recentPeople: List<WidgetPerson> = emptyList(),
    val onlineFriends: List<WidgetPerson> = emptyList(),
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 2
        const val MAX_PEOPLE = 4
        const val PORTRAIT_FILE_NAME = "portrait.png"
        const val SNAPSHOT_FILE_NAME = "snapshot.json"

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun decode(text: String): WidgetSnapshot? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

@Serializable
data class WidgetPerson(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarBundledKey: String? = null,
    val occurredAtEpochMillis: Long? = null,
)
