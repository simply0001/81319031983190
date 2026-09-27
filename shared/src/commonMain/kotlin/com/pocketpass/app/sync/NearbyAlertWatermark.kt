package com.pocketpass.app.sync

import com.pocketpass.app.data.local.entity.NotificationEntity

data class NearbyAlertPlan(
    val announce: List<NotificationEntity>,
    val seenThroughEpochMillis: Long,
)

fun planNearbyAlerts(
    unread: List<NotificationEntity>,
    seenThroughEpochMillis: Long,
    nowEpochMillis: Long,
): NearbyAlertPlan {
    val newest = unread.maxOfOrNull { it.createdAtEpochMillis }
    if (seenThroughEpochMillis <= 0L) {
        return NearbyAlertPlan(
            announce = emptyList(),
            seenThroughEpochMillis = maxOf(newest ?: 0L, nowEpochMillis),
        )
    }
    return NearbyAlertPlan(
        announce = unread.filter { it.createdAtEpochMillis > seenThroughEpochMillis },
        seenThroughEpochMillis = maxOf(seenThroughEpochMillis, newest ?: seenThroughEpochMillis),
    )
}
