package com.pocketpass.app.ui.components

import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import com.pocketpass.app.ui.DesignMetrics

const val TYPING_CARET_INLINE_ID = "typing_caret"

fun typingCaretInline(
    metrics: DesignMetrics,
    color: Color,
    height: Float,
): Map<String, InlineTextContent> = mapOf(
    TYPING_CARET_INLINE_ID to InlineTextContent(
        Placeholder(
            width = metrics.sp(14f),
            height = metrics.sp(height),
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        TypingCaret(metrics, color, height, Modifier.padding(start = metrics.dp(4f)))
    },
)

@Composable
fun TypingCaret(
    metrics: DesignMetrics,
    color: Color,
    height: Float,
    modifier: Modifier = Modifier,
) {
    val blink = if (platformAnimationsEnabled()) {
        rememberInfiniteTransition(label = "typing caret").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1_060
                    1f at 0
                    1f at 500
                    0f at 560
                    0f at 1_000
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "typing caret alpha",
        )
    } else {
        null
    }
    Box(
        modifier
            .width(metrics.dp(6f))
            .height(metrics.dp(height))
            .graphicsLayer { alpha = blink?.value ?: 1f }
            .background(color, RoundedCornerShape(metrics.dp(3f))),
    )
}
