package com.pocketpass.app.data.supabase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SupabaseTimestampTest {
    @Test
    fun parsesEquivalentPostgresTimestampFormats() {
        val expected = Instant.parse("2026-07-29T01:02:13.355968Z")

        listOf(
            "2026-07-29T01:02:13.355968Z",
            "2026-07-29T01:02:13.355968+00:00",
            "2026-07-29T01:02:13.355968+0000",
            "2026-07-29T01:02:13.355968+00",
            "2026-07-29 01:02:13.355968+00:00",
        ).forEach { timestamp ->
            assertEquals(expected, parseSupabaseInstant(timestamp), timestamp)
        }
    }

    @Test
    fun preservesNonUtcOffsets() {
        assertEquals(
            Instant.parse("2026-07-28T23:02:13.355968Z"),
            parseSupabaseInstant("2026-07-29 01:02:13.355968+02"),
        )
    }
}
