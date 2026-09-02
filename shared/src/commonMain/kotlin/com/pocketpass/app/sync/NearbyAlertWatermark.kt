package com.pocketpass.app.sync

import com.pocketpass.app.data.local.entity.NotificationEntity

/** Which unread pass notifications to announce now, and the watermark to remember afterwards. */
data class NearbyAlertPlan(
    val announce: List<NotificationEntity>,
    val seenThroughEpochMillis: Long,
)

/**
 * Decides which passes deserve a system notification. Only passes created
 * after the remembered watermark are announced, so a reinstall or a cold
 * start never replays the unread backlog: the first run on an install
 * simply records the backlog as seen. This mirrors the encounter list,
 * which never resurfaces old passes either.
 */
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
