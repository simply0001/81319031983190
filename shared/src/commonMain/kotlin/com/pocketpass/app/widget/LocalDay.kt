package com.pocketpass.app.widget

/** Epoch millis of local midnight for the day containing [nowEpochMillis]. */
expect fun startOfLocalDayEpochMillis(nowEpochMillis: Long): Long
