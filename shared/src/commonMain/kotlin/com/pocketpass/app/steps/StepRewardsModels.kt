package com.pocketpass.app.steps

const val STEPS_PER_TOKEN = 400

const val MAX_STEP_TOKENS_PER_DAY = 25

fun tokensForSteps(steps: Int): Int =
    (steps.coerceAtLeast(0) / STEPS_PER_TOKEN).coerceAtMost(MAX_STEP_TOKENS_PER_DAY)

enum class StepRewardsStatus {
    Disabled,

    Unsupported,

    NeedsPermission,

    Tracking,
}

enum class StepPermission {
    NotRequired,
    NotDetermined,
    Denied,
    Granted,
}

data class StepSample(
    val localDay: String,
    val utcOffsetMinutes: Int,
    val stepsToday: Int,
    val sampledAtEpochMillis: Long,
)

data class StepRewardsState(
    val status: StepRewardsStatus = StepRewardsStatus.Disabled,
    val localDay: String? = null,
    val stepsToday: Int = 0,
    val tokensToday: Int = 0,
    val claimError: String? = null,
) {
    val supported: Boolean
        get() = status != StepRewardsStatus.Unsupported

    val visible: Boolean
        get() = status == StepRewardsStatus.Tracking || status == StepRewardsStatus.NeedsPermission
}

data class DailyStepReward(
    val localDay: String,
    val steps: Int,
    val tokensAwarded: Int,
    val tokensCredited: Int,
    val balance: Int,
    val piecesAwarded: Int = 0,
    val piecesCredited: Int = 0,
)
