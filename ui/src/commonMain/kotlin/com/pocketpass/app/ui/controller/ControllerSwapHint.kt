package com.pocketpass.app.ui.controller

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.StatusPill
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.platformAnimationsEnabled
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun ControllerSwapHint(metrics: DesignMetrics, belowStatus: Boolean = false) {
    val focus = LocalControllerFocus.current ?: return
    val visible = focus.showSwapHint()
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (platformAnimationsEnabled()) 160 else 0),
        label = "screen swap hint",
    )
    if (!visible && alpha == 0f) return
    val palette = pocketPalette
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        StatusPill(
            metrics = metrics,
            x = 740f,
            width = 440f,
            horizontal = DesignAnchor.Center,
            y = if (belowStatus) 210f else 50f,
        ) {
            Row(
                modifier = Modifier
                    .testTag("controller_swap_hint")
                    .clearAndSetSemantics {
                        if (visible) contentDescription = "Press X to move the highlight to the other screen"
                    },
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(18f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.requiredSize(metrics.dp(70f), metrics.dp(70f))) {
                    Box(
                        Modifier.fillMaxSize()
                            .offset(y = metrics.dp(5f))
                            .pocketShadow(metrics, 35f, alpha = palette.shadowAlpha, blurRadius = 5f),
                    )
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape)
                            .pocketFrame(
                                Brush.verticalGradient(listOf(palette.tealBorder, palette.teal)),
                                metrics.dp(5f),
                                palette.surface,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "X",
                            color = palette.surface,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Bold,
                            fontSize = metrics.sp(40f),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = "to swap",
                    color = palette.teal,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(52f),
                    maxLines = 1,
                )
            }
        }
    }
}
