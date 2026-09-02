package com.pocketpass.app.steps

/** Steps behind each token; the server applies the same rule. */
const val STEPS_PER_TOKEN = 400

/** Tokens a day of walking can earn at most. */
const val MAX_STEP_TOKENS_PER_DAY = 25

/** Steps that earn the full daily reward. */
const val STEP_GOAL = STEPS_PER_TOKEN * MAX_STEP_TOKENS_PER_DAY

fun tokensForSteps(steps: Int): Int =
    (steps.coerceAtLeast(0) / STEPS_PER_TOKEN).coerceAtMost(MAX_STEP_TOKENS_PER_DAY)

enum class StepRewardsStatus {
    /** The setting is off or nobody is signed in. */
    Disabled,

    /** This device has no step counter; the setting stays hidden. */
    Unsupported,

    /** The setting is on but the app may not read the counter yet. */
    NeedsPermission,

    /** Steps are being counted and reported. */
    Tracking,
}

enum class StepPermission {
    NotRequired,
    NotDetermined,
    Denied,
    Granted,
}

/** One reading of the pedometer: steps counted so far in the named local day. */
data class StepSample(
    /** ISO calendar date (`yyyy-MM-dd`) in the device's time zone. */
    val localDay: String,
    val utcOffsetMinutes: Int,
    val stepsToday: Int,
    val sampledAtEpochMillis: Long,
)

data class StepRewardsState(
    val status: StepRewardsStatus = StepRewardsStatus.Disabled,
    val localDay: String? = null,
    val stepsToday: Int = 0,
    /** Tokens the server has confirmed for [localDay]. */
    val tokensToday: Int = 0,
    val claimError: String? = null,
) {
    val supported: Boolean
        get() = status != StepRewardsStatus.Unsupported

    /** Whether the Activities counter has anything to show. */
    val visible: Boolean
        get() = status == StepRewardsStatus.Tracking || status == StepRewardsStatus.NeedsPermission

    /** Tokens the current step count is worth but the server has not paid yet. */
    val tokensPending: Int
        get() = (tokensForSteps(stepsToday) - tokensToday).coerceAtLeast(0)
}

/** What the server answered to a step report. */
data class DailyStepReward(
    val localDay: String,
    val steps: Int,
    val tokensAwarded: Int,
    val tokensCredited: Int,
    val balance: Int,
)
