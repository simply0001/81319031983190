package com.pocketpass.app.steps

import kotlin.math.abs

data class StepLedgerState(
    val dayStartEpochMillis: Long,
    val bootEpochMillis: Long,
    val lastCounter: Long,
    val lastSampleEpochMillis: Long,
    val stepsToday: Int,
)

/**
 * Turns a since-boot step counter into steps per local day. The counter only
 * grows while the device stays up, so the ledger remembers the last reading
 * and attributes each increase to the day it was observed in. Readings that
 * cannot be attributed (the first one, a counter reset, a reboot before
 * midnight) start a fresh baseline instead of guessing.
 */
object StepDayLedger {
    private const val BOOT_DRIFT_TOLERANCE_MILLIS = 120_000L

    fun advance(
        previous: StepLedgerState?,
        counter: Long,
        nowEpochMillis: Long,
        bootEpochMillis: Long,
        dayStartEpochMillis: Long,
    ): StepLedgerState {
        val counterNow = counter.coerceAtLeast(0L)
        fun baseline(stepsToday: Int) = StepLedgerState(
            dayStartEpochMillis = dayStartEpochMillis,
            bootEpochMillis = bootEpochMillis,
            lastCounter = counterNow,
            lastSampleEpochMillis = nowEpochMillis,
            stepsToday = stepsToday.coerceAtLeast(0),
        )
        if (previous == null) return baseline(0)

        val sameDay = dayStartEpochMillis == previous.dayStartEpochMillis
        val carried = if (sameDay) previous.stepsToday else 0
        val rebooted = abs(bootEpochMillis - previous.bootEpochMillis) > BOOT_DRIFT_TOLERANCE_MILLIS
        if (rebooted) {
            // Everything the counter holds was walked since boot, which is
            // today's walking only if the device came up after midnight.
            val sinceBoot = if (bootEpochMillis >= dayStartEpochMillis) counterNow.toIntSteps() else 0
            return baseline(carried + sinceBoot)
        }
        if (counterNow < previous.lastCounter) return baseline(carried)

        val delta = (counterNow - previous.lastCounter).toIntSteps()
        if (sameDay) return baseline(carried + delta)

        // The day rolled over between samples: spread the increase across the
        // gap and keep only the part that falls after midnight.
        val elapsed = (nowEpochMillis - previous.lastSampleEpochMillis).coerceAtLeast(1L)
        val afterMidnight = (nowEpochMillis - dayStartEpochMillis).coerceIn(0L, elapsed)
        return baseline((delta.toLong() * afterMidnight / elapsed).toInt())
    }

    private fun Long.toIntSteps(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
