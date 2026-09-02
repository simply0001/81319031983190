package com.pocketpass.app.steps

/** ISO calendar date (`yyyy-MM-dd`) of [nowEpochMillis] in the device's time zone. */
expect fun localDayKey(nowEpochMillis: Long): String

/** The device's UTC offset at [nowEpochMillis], in minutes. */
expect fun localUtcOffsetMinutes(nowEpochMillis: Long): Int
