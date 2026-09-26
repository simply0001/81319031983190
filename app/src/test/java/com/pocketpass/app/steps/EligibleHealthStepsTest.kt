package com.pocketpass.app.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class EligibleHealthStepsTest {
    private fun record(
        origin: String = "watch",
        start: Long = 0,
        end: Long = 60_000,
        count: Long = 100,
        method: StepRecordingMethod = StepRecordingMethod.Automatic,
        device: Boolean = true,
    ) = HealthStepInterval(origin, start, end, count, method, device)

    @Test fun manualUnknownAndDeviceLessEntriesNeverCount() {
        val records = listOf(
            record(method = StepRecordingMethod.Manual, count = 200),
            record(method = StepRecordingMethod.Unknown, count = 200),
            record(device = false, count = 200),
            record(count = 100),
        )
        assertEquals(100, eligibleHealthSteps(records, 0, 60_000))
    }

    @Test fun activeDeviceRecordsCountButDifferentOriginsDoNotAdd() {
        val records = listOf(
            record(count = 100),
            record(origin = "phone", count = 120, method = StepRecordingMethod.Active),
        )
        assertEquals(120, eligibleHealthSteps(records, 0, 60_000))
    }

    @Test fun overlappingCopiesWithinOriginDoNotDoubleCount() {
        val records = listOf(record(count = 100), record(count = 120))
        assertEquals(120, eligibleHealthSteps(records, 0, 60_000))
    }

    @Test fun distinctIntervalsFromOneOriginAdd() {
        val records = listOf(record(count = 100), record(start = 60_000, end = 120_000, count = 80))
        assertEquals(180, eligibleHealthSteps(records, 0, 120_000))
    }

    @Test fun distinctIntervalsFromDifferentDevicesAdd() {
        val records = listOf(
            record(origin = "watch", count = 100),
            record(origin = "phone", start = 60_000, end = 120_000, count = 80),
        )
        assertEquals(180, eligibleHealthSteps(records, 0, 120_000))
    }

    @Test fun crossingMidnightFutureAndImplausibleImportsAreRejected() {
        val records = listOf(
            record(start = -1, end = 60_000, count = 100),
            record(start = 60_000, end = 120_001, count = 100),
            record(start = 0, end = 1_000, count = 100),
        )
        assertEquals(0, eligibleHealthSteps(records, 0, 120_000))
    }
}
