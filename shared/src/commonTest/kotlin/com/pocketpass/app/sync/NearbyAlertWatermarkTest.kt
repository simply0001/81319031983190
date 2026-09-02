package com.pocketpass.app.sync

import com.pocketpass.app.data.local.entity.NotificationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearbyAlertWatermarkTest {
    private fun pass(id: String, createdAt: Long) = NotificationEntity(
        accountId = "me",
        notificationId = id,
        kind = "NearbyEncounter",
        actorUserId = "them",
        actorDisplayName = "Them",
        actorAvatarKind = null,
        actorAvatarValue = null,
        actorUpdatedAtEpochMillis = null,
        friendRequestId = null,
        friendRequestStatus = null,
        conversationId = null,
        title = "Nearby encounter",
        body = "You passed Them",
        eventCount = 1,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
        readAtEpochMillis = null,
        deletedAtEpochMillis = null,
    )

    @Test
    fun firstRunSwallowsTheBacklogAndRemembersIt() {
        val plan = planNearbyAlerts(
            unread = listOf(pass("a", 1_000), pass("b", 2_000)),
            seenThroughEpochMillis = 0L,
            nowEpochMillis = 1_500,
        )

        assertTrue(plan.announce.isEmpty())
        assertEquals(2_000, plan.seenThroughEpochMillis)
    }

    @Test
    fun firstRunWithNothingUnreadStartsFromNow() {
        val plan = planNearbyAlerts(emptyList(), seenThroughEpochMillis = 0L, nowEpochMillis = 5_000)

        assertTrue(plan.announce.isEmpty())
        assertEquals(5_000, plan.seenThroughEpochMillis)
    }

    @Test
    fun onlyPassesAfterTheWatermarkAreAnnounced() {
        val plan = planNearbyAlerts(
            unread = listOf(pass("old", 1_000), pass("new", 3_000), pass("newer", 4_000)),
            seenThroughEpochMillis = 2_000,
            nowEpochMillis = 9_000,
        )

        assertEquals(listOf("new", "newer"), plan.announce.map { it.notificationId })
        assertEquals(4_000, plan.seenThroughEpochMillis)
    }

    @Test
    fun theWatermarkNeverMovesBackwards() {
        val plan = planNearbyAlerts(
            unread = listOf(pass("old", 1_000)),
            seenThroughEpochMillis = 2_000,
            nowEpochMillis = 9_000,
        )

        assertTrue(plan.announce.isEmpty())
        assertEquals(2_000, plan.seenThroughEpochMillis)
    }
}
