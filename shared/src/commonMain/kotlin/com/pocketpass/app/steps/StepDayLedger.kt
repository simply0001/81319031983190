package com.pocketpass.app.steps

import kotlin.math.abs

data class StepLedgerState(
    val dayStartEpochMillis: Long,
    val bootEpochMillis: Long,
    val lastCounter: Long,
    val lastSampleEpochMillis: Long,
    val stepsToday: Int,
)

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
        if (previous == null) {
            return baseline(if (bootEpochMillis >= dayStartEpochMillis) counterNow.toIntSteps() else 0)
        }

        val sameDay = dayStartEpochMillis == previous.dayStartEpochMillis
        val carried = if (sameDay) previous.stepsToday else 0
        val rebooted = abs(bootEpochMillis - previous.bootEpochMillis) > BOOT_DRIFT_TOLERANCE_MILLIS
        if (rebooted) {
            val sinceBoot = if (bootEpochMillis >= dayStartEpochMillis) counterNow.toIntSteps() else 0
            return baseline(carried + sinceBoot)
        }
        if (counterNow < previous.lastCounter) return baseline(carried)

        val delta = (counterNow - previous.lastCounter).toIntSteps()
        if (sameDay) return baseline(carried + delta)

        val elapsed = (nowEpochMillis - previous.lastSampleEpochMillis).coerceAtLeast(1L)
        val afterMidnight = (nowEpochMillis - dayStartEpochMillis).coerceIn(0L, elapsed)
        return baseline((delta.toLong() * afterMidnight / elapsed).toInt())
    }

    fun bootBaseline(
        previous: StepLedgerState?,
        nowEpochMillis: Long,
        bootEpochMillis: Long,
        dayStartEpochMillis: Long,
    ): StepLedgerState {
        if (
            previous != null &&
            abs(bootEpochMillis - previous.bootEpochMillis) <= BOOT_DRIFT_TOLERANCE_MILLIS &&
            previous.lastCounter > 0L
        ) {
            return previous
        }
        val carried = if (previous != null && previous.dayStartEpochMillis == dayStartEpochMillis) {
            previous.stepsToday
        } else {
            0
        }
        return StepLedgerState(
            dayStartEpochMillis = dayStartEpochMillis,
            bootEpochMillis = bootEpochMillis,
            lastCounter = 0L,
            lastSampleEpochMillis = nowEpochMillis,
            stepsToday = carried,
        )
    }

    private fun Long.toIntSteps(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
