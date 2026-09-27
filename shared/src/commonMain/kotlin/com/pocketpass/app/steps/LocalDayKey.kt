package com.pocketpass.app.steps

expect fun localDayKey(nowEpochMillis: Long): String

expect fun localUtcOffsetMinutes(nowEpochMillis: Long): Int
