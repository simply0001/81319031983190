package com.pocketpass.app.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything a home-screen widget shows, in one flat document. This is a wire
 * contract: the Android widget reads it from the app's files directory and the
 * iOS widget extension (Swift, Codable) reads it from the App Group container,
 * so field names and encodings must stay stable across versions.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = CURRENT_VERSION,
    val signedIn: Boolean,
    val displayName: String,
    val bio: String,
    /** File name of the Mii portrait PNG copied next to the snapshot, if any. */
    val portraitFileName: String?,
    val avatarBundledKey: String?,
    val encountersToday: Int,
    val lastEncounterEpochMillis: Long?,
    /** A [com.pocketpass.app.nearby.NearbyRuntimeStatus] name. */
    val nearbyStatus: String,
    val unreadNotifications: Int,
    val friendsOnline: Int,
    /** A [com.pocketpass.app.model.ThemeMode] name. */
    val themeMode: String,
    val updatedAtEpochMillis: Long,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1
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
