package com.pocketpass.app.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WidgetSize(
    val label: String,
    val tileCount: Int,
    val cellWidth: Int,
    val cellHeight: Int,
) {
    Mini("Mini", tileCount = 0, cellWidth = 1, cellHeight = 1),
    Small("Small", tileCount = 3, cellWidth = 2, cellHeight = 2),
    Wide("Wide", tileCount = 3, cellWidth = 4, cellHeight = 2),
    Tall("Tall", tileCount = 4, cellWidth = 2, cellHeight = 4),
    ;

    val cellsLabel: String
        get() = "$cellWidth x $cellHeight"
}

enum class WidgetBlockGroup(val title: String) {
    Core("Stats"),
    Profile("Profile"),
    Activities("Activities"),
    People("People"),
}

@Serializable
enum class WidgetBlock(
    val label: String,
    val shortLabel: String,
    val group: WidgetBlockGroup,
    val heroCapable: Boolean = true,
    val tileCapable: Boolean = true,
) {
    Tokens("Tokens", "Tokens", WidgetBlockGroup.Core),
    StepsToday("Steps today", "Steps", WidgetBlockGroup.Core),
    EncountersToday("Encounters today", "Encounters", WidgetBlockGroup.Core),
    LastPass("Last pass", "Last pass", WidgetBlockGroup.Core, heroCapable = false),
    FriendsOnline("Friends online", "Online", WidgetBlockGroup.Core),
    UnreadNotifications("Unread notifications", "Unread", WidgetBlockGroup.Core, heroCapable = false),
    NearbyStatus("Nearby status", "Nearby", WidgetBlockGroup.Core, heroCapable = false),
    Profile("Profile card", "Profile", WidgetBlockGroup.Profile, tileCapable = false),
    DisplayName("Name", "Name", WidgetBlockGroup.Profile, heroCapable = false),
    FriendCode("Friend code", "Code", WidgetBlockGroup.Profile),
    Achievements("Achievements", "Achievements", WidgetBlockGroup.Activities),
    BingoLines("Bingo lines", "Bingo", WidgetBlockGroup.Activities),
    WorldTourCountries("World Tour countries", "Countries", WidgetBlockGroup.Activities),
    LeaderboardRank("Leaderboard rank", "Rank", WidgetBlockGroup.Activities),
    RecentPeople("Recent people", "Recent", WidgetBlockGroup.People, tileCapable = false),
    OnlineFriends("Online friends", "Online", WidgetBlockGroup.People, tileCapable = false),
    ;

    companion object {
        val heroChoices: List<WidgetBlock> = entries.filter { it.heroCapable }
        val tileChoices: List<WidgetBlock> = entries.filter { it.tileCapable }
    }
}

@Serializable
data class WidgetDesign(
    val id: String,
    val name: String,
    val size: WidgetSize,
    val hero: WidgetBlock? = null,
    val tiles: List<WidgetBlock?> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val blocks: List<WidgetBlock>
        get() = listOfNotNull(hero) + tiles.filterNotNull()

    val isEmpty: Boolean
        get() = hero == null && tiles.all { it == null }

    fun normalized(): WidgetDesign {
        val validHero = hero?.takeIf { it.heroCapable }
        val validTiles = List(size.tileCount) { index ->
            tiles.getOrNull(index)?.takeIf { it.tileCapable }
        }
        return if (validHero == hero && validTiles == tiles) this else copy(hero = validHero, tiles = validTiles)
    }

    fun withSize(newSize: WidgetSize, now: Long): WidgetDesign =
        copy(size = newSize, updatedAtEpochMillis = now).normalized()

    fun withHero(block: WidgetBlock?, now: Long): WidgetDesign =
        copy(hero = block?.takeIf { it.heroCapable }, updatedAtEpochMillis = now).normalized()

    fun withTile(index: Int, block: WidgetBlock?, now: Long): WidgetDesign {
        val padded = normalized().tiles.toMutableList()
        if (index !in padded.indices) return this
        padded[index] = block?.takeIf { it.tileCapable }
        return copy(tiles = padded, updatedAtEpochMillis = now).normalized()
    }

    fun withName(newName: String, now: Long): WidgetDesign =
        copy(name = newName.trim().take(MAX_NAME_LENGTH).ifBlank { name }, updatedAtEpochMillis = now)

    fun summary(): String = blocks.joinToString { it.shortLabel }.ifBlank { "Empty" }

    companion object {
        const val MAX_NAME_LENGTH = 24
    }
}

@Serializable
data class WidgetDesignDocument(
    val version: Int = CURRENT_VERSION,
    val designs: List<WidgetDesign> = emptyList(),
) {
    fun encode(): String = WidgetJson.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "designs.json"

        fun decode(text: String): WidgetDesignDocument? =
            runCatching { WidgetJson.decodeFromString(serializer(), text) }
                .getOrNull()
                ?.let { document -> document.copy(designs = document.designs.map { it.normalized() }) }
    }
}

@Serializable
data class WidgetBindings(
    val byAppWidgetId: Map<Int, String> = emptyMap(),
    val pendingDesignId: String? = null,
    val pendingSize: WidgetSize? = null,
    val pendingAtEpochMillis: Long? = null,
) {
    fun designIdFor(appWidgetId: Int): String? = byAppWidgetId[appWidgetId]

    fun bind(appWidgetId: Int, designId: String): WidgetBindings =
        copy(byAppWidgetId = byAppWidgetId + (appWidgetId to designId))

    fun unbind(appWidgetIds: Collection<Int>): WidgetBindings =
        copy(byAppWidgetId = byAppWidgetId - appWidgetIds.toSet())

    fun withPending(designId: String, size: WidgetSize, nowEpochMillis: Long): WidgetBindings =
        copy(pendingDesignId = designId, pendingSize = size, pendingAtEpochMillis = nowEpochMillis)

    fun clearPending(): WidgetBindings =
        copy(pendingDesignId = null, pendingSize = null, pendingAtEpochMillis = null)

    fun pendingFor(size: WidgetSize?, nowEpochMillis: Long): String? {
        val requestedAt = pendingAtEpochMillis ?: return null
        if (pendingSize != size) return null
        if (nowEpochMillis - requestedAt !in 0..PENDING_WINDOW_MILLIS) return null
        return pendingDesignId
    }

    fun encode(): String = WidgetJson.encodeToString(serializer(), this)

    companion object {
        const val FILE_NAME = "bindings.json"
        const val PENDING_WINDOW_MILLIS = 5 * 60 * 1000L

        fun decode(text: String): WidgetBindings? =
            runCatching { WidgetJson.decodeFromString(serializer(), text) }.getOrNull()
    }
}

internal val WidgetJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
