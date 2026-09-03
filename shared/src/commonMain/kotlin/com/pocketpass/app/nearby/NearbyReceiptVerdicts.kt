package com.pocketpass.app.nearby

import com.pocketpass.app.domain.model.EncounterId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class NearbyReceiptVerdict {
    NewEncounter,
    AlreadyCountedToday,
    Unknown,
    NotQueued,
}

data class NearbyReceiptOutcome(
    val submittedEncounterId: EncounterId,
    val resolvedEncounterId: EncounterId?,
) {
    val verdict: NearbyReceiptVerdict
        get() = when (resolvedEncounterId) {
            null -> NearbyReceiptVerdict.Unknown
            submittedEncounterId -> NearbyReceiptVerdict.NewEncounter
            else -> NearbyReceiptVerdict.AlreadyCountedToday
        }
}

class NearbyReceiptVerdictBus {
    private val outcomes = MutableSharedFlow<NearbyReceiptOutcome>(
        replay = REPLAY,
        extraBufferCapacity = REPLAY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun report(outcome: NearbyReceiptOutcome) {
        outcomes.tryEmit(outcome)
    }

    suspend fun await(
        encounterId: EncounterId,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): NearbyReceiptOutcome? = withTimeoutOrNull(timeout) {
        outcomes.first { it.submittedEncounterId == encounterId }
    }

    companion object {
        // The LED pulse waits for the server's verdict so a repeat pass on the
        // same day stays silent; when the receipt takes longer than this to
        // clear, the pulse fires anyway rather than arriving seconds late.
        val DEFAULT_TIMEOUT: Duration = 2.5.seconds
        private const val REPLAY = 32
    }
}
