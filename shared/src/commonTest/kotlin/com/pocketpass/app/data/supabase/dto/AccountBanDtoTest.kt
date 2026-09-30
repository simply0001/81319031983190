package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.domain.model.AccountBanNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AccountBanDtoTest {
    @Test
    fun jsonNullMeansNoBan() {
        assertNull(decodeAccountBan("null"))
        assertNull(decodeAccountBan(" null\n"))
        assertNull(decodeAccountBan(""))
    }

    @Test
    fun timedBanKeepsTheReasonAndEndTime() {
        val ban = decodeAccountBan(
            """{"reason":"Spam in Boards.","ends_at":"2026-10-12T09:30:00+00:00","created_at":"2026-09-28T09:30:00+00:00"}""",
        )

        assertEquals(
            AccountBanNotice(
                reason = "Spam in Boards.",
                endsAtEpochMillis = Instant.parse("2026-10-12T09:30:00Z").toEpochMilliseconds(),
            ),
            ban,
        )
    }

    @Test
    fun missingEndTimeMeansPermanent() {
        val ban = decodeAccountBan(
            """{"reason":"Harassment.","ends_at":null,"created_at":"2026-09-28 09:30:00.123+00","case_id":7}""",
        )

        assertEquals(AccountBanNotice(reason = "Harassment.", endsAtEpochMillis = null), ban)
    }
}
