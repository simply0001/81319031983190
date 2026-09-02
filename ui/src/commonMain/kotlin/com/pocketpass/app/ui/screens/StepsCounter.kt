package com.pocketpass.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.steps.MAX_STEP_TOKENS_PER_DAY
import com.pocketpass.app.steps.StepRewardsStatus
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.IdleMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.theme.pocketPalette

/**
 * The third Activities counter: steps walked today and the tokens they have
 * earned. Sized like the coin/puzzle counters; shown only while step rewards
 * are on. Without the activity permission it turns into a tap-to-allow.
 */
@Composable
internal fun StepsCounter(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    artSize: Float,
    modifier: Modifier = Modifier,
    numberSize: Float = 128f,
) {
    val steps = state.stepRewards
    val needsPermission = steps.status == StepRewardsStatus.NeedsPermission
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .testTag("steps_counter")
            .clickable(
                enabled = needsPermission,
                interactionSource = interactionSource,
                indication = null,
            ) { dispatch(PocketPassEvent.RequestStepRewardsPermission) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MotionLayer(
            modifier = Modifier.requiredSize(metrics.dp(artSize)),
            entrance = EntranceMotion.ActivityPuzzleSettle,
            idle = IdleMotion.PuzzleBob,
            delayMillis = 140,
            idlePhaseMillis = 450,
        ) {
            FigmaAsset(resource = Assets.ActivitiesSteps, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(metrics.dp(10f)))
        MotionLayer(entrance = EntranceMotion.ActivityCountRise, delayMillis = 220) {
            Text(
                text = if (needsPermission) "–" else formatStepCount(steps.stepsToday),
                color = pocketPalette.ink(Color(0xFF1D596B)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(numberSize),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Text(
            text = if (needsPermission) {
                "Tap to allow"
            } else {
                "${steps.tokensToday}/$MAX_STEP_TOKENS_PER_DAY tokens"
            },
            color = pocketPalette.textMuted,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(numberSize * 0.36f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
