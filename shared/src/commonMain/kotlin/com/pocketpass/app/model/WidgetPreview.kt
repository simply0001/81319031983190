package com.pocketpass.app.model

import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.widget.WidgetSnapshot
import com.pocketpass.app.widget.WidgetSnapshotPublisher
import com.pocketpass.app.widget.bingoLines
import com.pocketpass.app.widget.startOfLocalDayEpochMillis

fun PocketPassUiState.widgetPreviewSnapshot(nowEpochMillis: Long): WidgetSnapshot {
    val encounters = recentInteractions
    val dayStart = startOfLocalDayEpochMillis(nowEpochMillis)
    val me = profile?.userId
    return WidgetSnapshot(
        signedIn = true,
        displayName = profile?.displayName.orEmpty(),
        bio = profile?.bio?.ifBlank { null } ?: WidgetSnapshotPublisher.DEFAULT_BIO,
        portraitFileName = null,
        avatarBundledKey = (profile?.avatar as? AvatarReference.Bundled)?.key,
        encountersToday = encounters.count { it.occurredAt.toEpochMilliseconds() >= dayStart },
        lastEncounterEpochMillis = encounters.maxOfOrNull { it.occurredAt.toEpochMilliseconds() },
        nearbyStatus = nearbyRuntime.status.name,
        unreadNotifications = unreadNotificationCount,
        friendsOnline = onlineFriendCount,
        themeMode = themeMode.name,
        updatedAtEpochMillis = nowEpochMillis,
        tokenBalance = shop.tokenBalance,
        stepsToday = stepRewards.stepsToday,
        friendCode = myFriendCode?.value,
        achievementsUnlocked = achievements.achievements.count { it.unlocked },
        achievementsTotal = AchievementCatalog.definitions.size,
        bingoLines = bingoLines(bingo.cells),
        worldTourCountries = worldTour.regions.distinctBy { it.countryCode }.size,
        leaderboardRank = me?.let { id ->
            leaderboard.entries.indexOfFirst { it.userId == id }.takeIf { it >= 0 }?.plus(1)
        },
        leaderboardScope = leaderboard.scope.name,
        recentPeople = WidgetSnapshotPublisher.recentPeople(encounters),
        onlineFriends = WidgetSnapshotPublisher.onlineFriends(friends),
    )
}
