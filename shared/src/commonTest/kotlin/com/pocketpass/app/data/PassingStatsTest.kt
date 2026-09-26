package com.pocketpass.app.data

import com.pocketpass.app.data.supabase.dto.PassingStatsDto
import com.pocketpass.app.data.supabase.dto.toDomain
import com.pocketpass.app.domain.model.PassingStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PassingStatsTest {
    @Test
    fun serverRowsMapOntoTheModel() {
        val stats = PassingStatsDto(
            currentStreak = 2,
            bestStreak = 3,
            weekPasses = 4,
            weekPeople = 2,
            weekRegions = 2,
            weekStart = "2026-08-24",
        ).toDomain()
        assertEquals(PassingStats(2, 3, 4, 2, 2), stats)
    }

    @Test
    fun impossibleCombinationsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PassingStats(3, 2, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { PassingStats(0, 0, 1, 2, 0) }
        assertFailsWith<IllegalArgumentException> { PassingStats(0, 0, 2, 1, 2) }
    }
}
