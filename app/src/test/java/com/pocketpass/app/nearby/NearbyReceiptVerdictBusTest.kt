package com.pocketpass.app.nearby

import com.pocketpass.app.domain.model.EncounterId
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyReceiptVerdictBusTest {
    @Test
    fun outcomeReportedBeforeAwaitIsStillDelivered() = runTest {
        val bus = NearbyReceiptVerdictBus()
        bus.report(NearbyReceiptOutcome(SUBMITTED, SUBMITTED))

        val outcome = bus.await(SUBMITTED, 500.milliseconds)

        assertEquals(NearbyReceiptVerdict.NewEncounter, outcome?.verdict)
    }

    @Test
    fun awaitReturnsTheMatchingOutcomeAndIgnoresOthers() = runTest {
        val bus = NearbyReceiptVerdictBus()
        val pending = async { bus.await(SUBMITTED, 500.milliseconds) }
        runCurrent()

        bus.report(NearbyReceiptOutcome(EncounterId("other"), EncounterId("other")))
        bus.report(NearbyReceiptOutcome(SUBMITTED, EncounterId("existing-today")))

        assertEquals(NearbyReceiptVerdict.AlreadyCountedToday, pending.await()?.verdict)
    }

    @Test
    fun aFailedSubmissionIsUnknownRatherThanASkip() = runTest {
        val bus = NearbyReceiptVerdictBus()
        bus.report(NearbyReceiptOutcome(SUBMITTED, null))

        assertEquals(NearbyReceiptVerdict.Unknown, bus.await(SUBMITTED, 500.milliseconds)?.verdict)
    }

    @Test
    fun awaitGivesUpAfterTheTimeout() = runTest {
        val bus = NearbyReceiptVerdictBus()

        assertNull(bus.await(SUBMITTED, 200.milliseconds))
    }

    private companion object {
        val SUBMITTED = EncounterId("submitted")
    }
}
