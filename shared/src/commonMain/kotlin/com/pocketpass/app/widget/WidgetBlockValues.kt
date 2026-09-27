package com.pocketpass.app.widget

data class WidgetTileModel(
    val block: WidgetBlock,
    val label: String,
    val value: String,
)

object WidgetBlockValues {
    fun tile(block: WidgetBlock, snapshot: WidgetSnapshot, nowEpochMillis: Long): WidgetTileModel = when (block) {
        WidgetBlock.Tokens -> WidgetTileModel(block, "Tokens", snapshot.tokenBalance.grouped())
        WidgetBlock.StepsToday -> WidgetTileModel(block, "Steps", snapshot.stepsToday.grouped())
        WidgetBlock.EncountersToday -> WidgetTileModel(block, "Encounters", snapshot.encountersToday.toString())
        WidgetBlock.LastPass -> WidgetTileModel(
            block,
            "Last pass",
            lastPassValue(snapshot.lastEncounterEpochMillis, nowEpochMillis),
        )
        WidgetBlock.FriendsOnline -> WidgetTileModel(
            block,
            "Online",
            snapshot.friendsOnline.toString(),
        )
        WidgetBlock.UnreadNotifications -> WidgetTileModel(
            block,
            "Unread",
            snapshot.unreadNotifications.toString(),
        )
        WidgetBlock.NearbyStatus -> WidgetTileModel(block, "Nearby", nearbyLabel(snapshot.nearbyStatus))
        WidgetBlock.Profile -> WidgetTileModel(block, "Profile", snapshot.displayName.ifBlank { "PocketPass" })
        WidgetBlock.DisplayName -> WidgetTileModel(block, "Name", snapshot.displayName.ifBlank { "PocketPass" })
        WidgetBlock.FriendCode -> WidgetTileModel(block, "Friend code", friendCode(snapshot))
        WidgetBlock.Achievements -> WidgetTileModel(
            block,
            "Achievements",
            "${snapshot.achievementsUnlocked}/${snapshot.achievementsTotal}",
        )
        WidgetBlock.BingoLines -> WidgetTileModel(block, "Bingo lines", snapshot.bingoLines.toString())
        WidgetBlock.WorldTourCountries -> WidgetTileModel(block, "Countries", snapshot.worldTourCountries.toString())
        WidgetBlock.LeaderboardRank -> WidgetTileModel(block, "Rank", rank(snapshot))
        WidgetBlock.RecentPeople -> WidgetTileModel(
            block,
            "Recent",
            snapshot.recentPeople.firstOrNull()?.displayName ?: "Nobody yet",
        )
        WidgetBlock.OnlineFriends -> WidgetTileModel(
            block,
            "Online now",
            snapshot.onlineFriends.firstOrNull()?.displayName ?: "Nobody",
        )
    }

    fun lastPassValue(lastEpochMillis: Long?, nowEpochMillis: Long): String {
        if (lastEpochMillis == null) return "None yet"
        val elapsed = (nowEpochMillis - lastEpochMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = elapsed / 3_600_000L
        val days = elapsed / 86_400_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            else -> "$days days ago"
        }
    }

    fun nearbyLabel(status: String): String = when (status) {
        "Running" -> "On"
        "Starting" -> "Starting"
        "BluetoothOff" -> "Bluetooth off"
        "NeedsPermissions", "NeedsOnboarding" -> "Needs setup"
        "Unsupported" -> "Unsupported"
        "Error" -> "Error"
        else -> "Off"
    }

    fun heroNumber(block: WidgetBlock, snapshot: WidgetSnapshot, nowEpochMillis: Long): String = when (block) {
        WidgetBlock.Tokens -> compact(snapshot.tokenBalance)
        WidgetBlock.StepsToday -> compact(snapshot.stepsToday)
        WidgetBlock.EncountersToday -> compact(snapshot.encountersToday)
        WidgetBlock.FriendsOnline -> compact(snapshot.friendsOnline)
        WidgetBlock.UnreadNotifications -> compact(snapshot.unreadNotifications)
        WidgetBlock.Achievements -> "${snapshot.achievementsUnlocked}/${snapshot.achievementsTotal}"
        WidgetBlock.BingoLines -> compact(snapshot.bingoLines)
        WidgetBlock.WorldTourCountries -> compact(snapshot.worldTourCountries)
        WidgetBlock.LeaderboardRank -> rank(snapshot)
        WidgetBlock.FriendCode -> friendCode(snapshot)
        WidgetBlock.LastPass -> shortLastPass(snapshot.lastEncounterEpochMillis, nowEpochMillis)
        WidgetBlock.NearbyStatus -> nearbyLabel(snapshot.nearbyStatus)
        WidgetBlock.Profile, WidgetBlock.DisplayName -> snapshot.displayName.ifBlank { "PocketPass" }
        WidgetBlock.RecentPeople -> compact(snapshot.recentPeople.size)
        WidgetBlock.OnlineFriends -> compact(snapshot.onlineFriends.size)
    }

    fun heroLabel(block: WidgetBlock, snapshot: WidgetSnapshot): String = when (block) {
        WidgetBlock.Profile -> snapshot.bio.ifBlank { block.label }
        else -> block.label
    }

    fun tileNumber(block: WidgetBlock, snapshot: WidgetSnapshot, nowEpochMillis: Long): String = when (block) {
        WidgetBlock.Achievements -> compact(snapshot.achievementsUnlocked)
        WidgetBlock.FriendCode -> snapshot.friendCode?.take(4) ?: "--"
        else -> heroNumber(block, snapshot, nowEpochMillis)
    }

    fun compact(value: Int): String {
        val n = value.coerceAtLeast(0)
        return when {
            n < 1_000 -> n.toString()
            n < 10_000 -> trimDecimal(n / 1_000f) + "k"
            n < 1_000_000 -> "${n / 1_000}k"
            else -> trimDecimal(n / 1_000_000f) + "M"
        }
    }

    private fun trimDecimal(value: Float): String {
        val tenths = (value * 10f + 0.5f).toInt()
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (fraction == 0) whole.toString() else "$whole.$fraction"
    }

    private fun shortLastPass(lastEpochMillis: Long?, nowEpochMillis: Long): String {
        if (lastEpochMillis == null) return "--"
        val elapsed = (nowEpochMillis - lastEpochMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = elapsed / 3_600_000L
        val days = elapsed / 86_400_000L
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            else -> "${days}d"
        }
    }

    private fun friendCode(snapshot: WidgetSnapshot): String {
        val code = snapshot.friendCode ?: return "--"
        return if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code
    }

    private fun rank(snapshot: WidgetSnapshot): String = snapshot.leaderboardRank?.let { "#$it" } ?: "--"

    private fun Int.grouped(): String {
        val digits = toString()
        if (digits.length <= 3) return digits
        val out = StringBuilder()
        digits.reversed().forEachIndexed { index, char ->
            if (index > 0 && index % 3 == 0) out.append(',')
            out.append(char)
        }
        return out.reverse().toString()
    }
}
