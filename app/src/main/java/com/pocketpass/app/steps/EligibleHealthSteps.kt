package com.pocketpass.app.steps

internal enum class StepRecordingMethod { Automatic, Active, Manual, Unknown }

internal data class HealthStepInterval(
    val origin: String,
    val startMillis: Long,
    val endMillis: Long,
    val count: Long,
    val method: StepRecordingMethod,
    val hasDevice: Boolean,
)

internal fun eligibleHealthSteps(
    records: List<HealthStepInterval>,
    dayStartMillis: Long,
    nowMillis: Long,
): Int = records.asSequence()
    .filter {
        it.method == StepRecordingMethod.Automatic || it.method == StepRecordingMethod.Active
    }
    .filter { it.hasDevice && it.origin.isNotBlank() }
    .filter {
        it.count > 0 && it.startMillis >= dayStartMillis &&
            it.endMillis <= nowMillis && it.endMillis > it.startMillis
    }
    .filter { it.count <= (it.endMillis - it.startMillis) * MAX_STEPS_PER_MILLISECOND }
    .toList()
    .let(::stepsWithoutOverlaps)
    .toInt()

private fun stepsWithoutOverlaps(records: List<HealthStepInterval>): Long {
    val events = sortedMapOf<Long, MutableList<Pair<Double, Int>>>()
    for (record in records) {
        val rate = record.count.toDouble() / (record.endMillis - record.startMillis)
        events.getOrPut(record.startMillis) { mutableListOf() }.add(rate to 1)
        events.getOrPut(record.endMillis) { mutableListOf() }.add(rate to -1)
    }
    val activeRates = java.util.TreeMap<Double, Int>()
    var total = 0.0
    var previousTime: Long? = null
    for ((time, changes) in events) {
        previousTime?.let { previous ->
            if (activeRates.isNotEmpty()) total += activeRates.lastKey() * (time - previous)
        }
        for ((rate, change) in changes) {
            val next = (activeRates[rate] ?: 0) + change
            if (next == 0) activeRates.remove(rate) else activeRates[rate] = next
        }
        previousTime = time
    }
    return total.toLong().coerceAtMost(Int.MAX_VALUE.toLong())
}

private const val MAX_STEPS_PER_MILLISECOND = 5.0 / 1_000.0
