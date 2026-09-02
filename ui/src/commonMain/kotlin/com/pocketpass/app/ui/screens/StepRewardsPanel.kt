package com.pocketpass.app.ui.screens

import androidx.compose.runtime.Composable
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.steps.MAX_STEP_TOKENS_PER_DAY
import com.pocketpass.app.steps.STEPS_PER_TOKEN
import com.pocketpass.app.steps.StepRewardsStatus
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.theme.pocketPalette

/**
 * The Step Rewards settings row, shared by the phone list and the dual-screen
 * settings stack. Only shown on devices with a step counter.
 */
@Composable
internal fun StepRewardsPanel(
    metrics: DesignMetrics,
    y: Float,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val steps = state.stepRewards
    val subtitle = when {
        !state.stepRewardsEnabled ->
            "1 token per $STEPS_PER_TOKEN steps, up to $MAX_STEP_TOKENS_PER_DAY a day"

        steps.status == StepRewardsStatus.NeedsPermission -> "Allow physical activity access"

        else ->
            "Today: ${formatStepCount(steps.stepsToday)} steps · " +
                "${steps.tokensToday}/$MAX_STEP_TOKENS_PER_DAY tokens"
    }
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "step_rewards_toggle",
        onClick = { dispatch(PocketPassEvent.SetStepRewardsEnabled(!state.stepRewardsEnabled)) },
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsSteps,
            title = "Step Rewards",
            subtitle = subtitle,
        )
        NearbyToggle(
            metrics = metrics,
            enabled = state.stepRewardsEnabled,
        )
    }
}

/** "12345" → "12,345"; the app has no locale-aware number formatter in common code. */
internal fun formatStepCount(steps: Int): String {
    val digits = steps.coerceAtLeast(0).toString()
    return digits.reversed().chunked(3).joinToString(",").reversed()
}
