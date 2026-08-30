package com.pocketpass.app.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.pocketpass.app.ui.DesignMetrics

private const val OVERLAY_EXIT_MILLIS = 220
private const val OVERLAY_EXIT_DROP = 42f

@Composable
fun <T> ExitingOverlay(
    metrics: DesignMetrics,
    visible: Boolean,
    snapshot: T,
    content: @Composable (T) -> Unit,
) {
    var retained by remember { mutableStateOf(snapshot) }
    var everShown by remember { mutableStateOf(false) }
    val presence = remember { Animatable(if (visible) 1f else 0f) }
    SideEffect {
        if (visible) {
            retained = snapshot
            everShown = true
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            presence.snapTo(1f)
            return@LaunchedEffect
        }
        if (!everShown) return@LaunchedEffect
        if (ValueAnimator.areAnimatorsEnabled()) {
            presence.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = OVERLAY_EXIT_MILLIS, easing = FastOutSlowInEasing),
            )
        } else {
            presence.snapTo(0f)
        }
        everShown = false
    }
    if (!visible && !everShown) return
    val shown = if (visible) snapshot else retained
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = presence.value
                alpha = p
                translationY = metrics.dp(OVERLAY_EXIT_DROP).toPx() * (1f - p)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        content(shown)
    }
}
