package com.pocketpass.app.ui.components

fun passingStatsLine(streakDays: Int, weekPasses: Int): String? {
    val parts = buildList {
        if (streakDays >= 2) add("$streakDays-day streak")
        when {
            weekPasses == 1 -> add("1 pass this week")
            weekPasses > 1 -> add("$weekPasses passes this week")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
