package com.pocketpass.app.widget

import java.time.Instant
import java.time.ZoneId

actual fun startOfLocalDayEpochMillis(nowEpochMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(nowEpochMillis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}
