package com.pocketpass.app.steps

import kotlin.test.Test
import kotlin.test.assertEquals

class StepDayLedgerTest {
    private val day = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val boot = day - hour

    private fun first(counter: Long = 5_000, at: Long = day + hour) =
        StepDayLedger.advance(null, counter, at, boot, day)

    @Test
    fun firstReadingAfterAnEarlierBootStartsAtZero() {
        val state = first()

        assertEquals(0, state.stepsToday)
        assertEquals(5_000L, state.lastCounter)
        assertEquals(day, state.dayStartEpochMillis)
    }

    @Test
    fun firstReadingAfterBootingTodayCountsEverythingSinceBoot() {
        val bootedAt = day + hour

        val state = StepDayLedger.advance(null, 1_200, day + 3 * hour, bootedAt, day)

        assertEquals(1_200, state.stepsToday)
        assertEquals(1_200L, state.lastCounter)
    }

    @Test
    fun bootBaselineCarriesTodaysCountAndRestartsTheCounterAtZero() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 2 * hour, boot, day)
        val rebootedAt = day + 3 * hour

        val baseline = StepDayLedger.bootBaseline(previous, rebootedAt + 60_000L, rebootedAt, day)
        val state = StepDayLedger.advance(baseline, 250, day + 4 * hour, rebootedAt, day)

        assertEquals(300, baseline.stepsToday)
        assertEquals(0L, baseline.lastCounter)
        assertEquals(300 + 250, state.stepsToday)
    }

    @Test
    fun bootBaselineOnANewDayStartsAtZero() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 22 * hour, boot, day)
        val nextDay = day + 24 * hour
        val rebootedAt = day + 23 * hour

        val baseline = StepDayLedger.bootBaseline(previous, nextDay + 60_000L, rebootedAt, nextDay)
        val state = StepDayLedger.advance(baseline, 4_000, nextDay + 9 * hour, rebootedAt, nextDay)

        assertEquals(0, baseline.stepsToday)
        assertEquals(4_000, state.stepsToday)
    }

    @Test
    fun bootBaselineKeepsAReadingAlreadyTakenSinceThatBoot() {
        val rebootedAt = day + 3 * hour
        val previous = StepDayLedger.advance(first(), 250, day + 4 * hour, rebootedAt, day)

        val baseline = StepDayLedger.bootBaseline(previous, day + 4 * hour + 60_000L, rebootedAt, day)

        assertEquals(previous, baseline)
    }

    @Test
    fun sameDayIncreaseIsAdded() {
        val state = StepDayLedger.advance(first(), 5_400, day + 2 * hour, boot, day)

        assertEquals(400, state.stepsToday)
        assertEquals(5_400L, state.lastCounter)
    }

    @Test
    fun rebootTodayCountsEverythingSinceBoot() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 2 * hour, boot, day)
        val rebootedAt = day + 3 * hour

        val state = StepDayLedger.advance(previous, 250, day + 4 * hour, rebootedAt, day)

        assertEquals(300 + 250, state.stepsToday)
        assertEquals(rebootedAt, state.bootEpochMillis)
    }

    @Test
    fun rebootBeforeMidnightOnlyKeepsWhatWasAlreadyCounted() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 2 * hour, boot, day)
        val rebootedAt = day - 30 * 60_000L

        val state = StepDayLedger.advance(previous, 9_000, day + 4 * hour, rebootedAt, day)

        assertEquals(300, state.stepsToday)
        assertEquals(9_000L, state.lastCounter)
    }

    @Test
    fun counterResetWithoutRebootStartsANewBaseline() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 2 * hour, boot, day)

        val state = StepDayLedger.advance(previous, 100, day + 3 * hour, boot, day)

        assertEquals(300, state.stepsToday)
        assertEquals(100L, state.lastCounter)
    }

    @Test
    fun dayRolloverKeepsOnlyTheShareAfterMidnight() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 22 * hour, boot, day)
        val nextDay = day + 24 * hour

        val state = StepDayLedger.advance(previous, 5_700, nextDay + 2 * hour, boot, nextDay)

        assertEquals(200, state.stepsToday)
        assertEquals(nextDay, state.dayStartEpochMillis)
    }

    @Test
    fun timeZoneChangeIsTreatedAsARollover() {
        val previous = StepDayLedger.advance(first(), 5_300, day + 5 * hour, boot, day)
        val shiftedDayStart = day + hour

        val state = StepDayLedger.advance(previous, 5_900, day + 6 * hour, boot, shiftedDayStart)

        assertEquals(600, state.stepsToday)
        assertEquals(shiftedDayStart, state.dayStartEpochMillis)
    }
}
