package com.pocketpass.app.data.supabase

import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

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
            assertEquals(timestamp, expected, parseSupabaseInstant(timestamp))
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
