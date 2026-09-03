package com.pocketpass.app.domain.model

import com.pocketpass.app.data.repository.FixtureData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class RecentInteractionsTest {
    @Test
    fun repeatedEncountersCollapseToTheLatestOnePerPerson() {
        val encounters = FixtureData.encounters
        val first = encounters.first()
        val again = first.copy(
            id = EncounterId(first.id.value + "-again"),
            occurredAt = first.occurredAt + 2.hours,
        )
        val earlier = first.copy(
            id = EncounterId(first.id.value + "-earlier"),
            occurredAt = first.occurredAt - 5.hours,
        )

        val collapsed = (listOf(earlier) + encounters + listOf(again)).latestPerPerson()

        assertEquals(encounters.size, collapsed.size)
        assertEquals(
            encounters.map { it.profile.userId }.toSet(),
            collapsed.map { it.profile.userId }.toSet(),
        )
        val kept = collapsed.single { it.profile.userId == first.profile.userId }
        assertEquals(again, kept)
        assertTrue(collapsed.zipWithNext().all { (a, b) -> a.occurredAt >= b.occurredAt })
    }

    @Test
    fun collapsingIsIdempotent() {
        val once = FixtureData.encounters.latestPerPerson()
        assertEquals(once, once.latestPerPerson())
    }
}
