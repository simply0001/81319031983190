package com.pocketpass.app.steps

import java.time.Instant
import java.time.ZoneId

actual fun localDayKey(nowEpochMillis: Long): String =
    Instant.ofEpochMilli(nowEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

actual fun localUtcOffsetMinutes(nowEpochMillis: Long): Int =
    ZoneId.systemDefault()
        .rules
        .getOffset(Instant.ofEpochMilli(nowEpochMillis))
        .totalSeconds / 60
