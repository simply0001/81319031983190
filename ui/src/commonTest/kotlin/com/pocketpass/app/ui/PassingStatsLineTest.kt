package com.pocketpass.app.ui

import com.pocketpass.app.ui.components.passingStatsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PassingStatsLineTest {
    @Test
    fun quietWeeksShowNothing() {
        assertNull(passingStatsLine(streakDays = 0, weekPasses = 0))
        assertNull(passingStatsLine(streakDays = 1, weekPasses = 0))
    }

    @Test
    fun streaksStartAtTwoDaysAndPassesAtOne() {
        assertEquals("2-day streak", passingStatsLine(streakDays = 2, weekPasses = 0))
        assertEquals("1 pass this week", passingStatsLine(streakDays = 1, weekPasses = 1))
    }

    @Test
    fun bothFactsJoinWithADot() {
        assertEquals(
            "5-day streak · 12 passes this week",
            passingStatsLine(streakDays = 5, weekPasses = 12),
        )
    }
}
