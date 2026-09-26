package com.pocketpass.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.theme.pocketPalette

internal enum class CredentialGlyph { User, Lock }

@Composable
internal fun CredentialPanel(
    metrics: DesignMetrics,
    y: Float,
    value: String,
    placeholder: String,
    tag: String,
    active: Boolean,
    onClick: () -> Unit,
    x: Float = 102f,
    width: Float = 1036f,
    height: Float = 108f,
    masked: Boolean = false,
    fontSize: Float = 44f,
    glyph: CredentialGlyph? = null,
    trailingSpace: Float = 0f,
) {
    PocketPanel(
        metrics = metrics,
        x = x,
        y = y,
        width = width,
        height = height,
        borderColor = if (active) PocketGreenBorder else PocketBorder,
        borderWidth = 14f,
        radius = 90f,
        fillBrush = PocketWhitePanel,
        tag = tag,
        onClick = onClick,
    ) {
        val tint = if (value.isEmpty()) PocketGreenText.copy(alpha = 0.56f) else PocketGreenText
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = metrics.dp(if (glyph == null) 44f else 38f),
                    end = metrics.dp(44f + trailingSpace),
                    top = metrics.dp(18f),
                    bottom = metrics.dp(18f),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                val glyphColor = pocketPalette.ink(tint)
                Canvas(Modifier.size(metrics.dp(46f))) { drawCredentialGlyph(glyph, glyphColor) }
                Spacer(Modifier.width(metrics.dp(22f)))
            }
            val shown = when {
                value.isEmpty() -> placeholder
                masked -> "•".repeat(value.length)
                else -> value
            }
            Text(
                text = shown,
                style = pocketAuthText(metrics, fontSize, tint, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun DrawScope.drawCredentialGlyph(glyph: CredentialGlyph, color: Color) {
    val w = size.width
    when (glyph) {
        CredentialGlyph.User -> {
            drawCircle(color, radius = w * 0.19f, center = Offset(w * 0.5f, w * 0.29f))
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.11f, w * 0.56f),
                size = Size(w * 0.78f, w * 0.78f),
            )
        }

        CredentialGlyph.Lock -> {
            val stroke = w * 0.12f
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.26f, w * 0.08f),
                size = Size(w * 0.48f, w * 0.48f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(color, Offset(w * 0.26f, w * 0.32f), Offset(w * 0.26f, w * 0.5f), stroke)
            drawLine(color, Offset(w * 0.74f, w * 0.32f), Offset(w * 0.74f, w * 0.5f), stroke)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, w * 0.46f),
                size = Size(w * 0.76f, w * 0.5f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            )
        }
    }
}

internal fun applyCredentialKey(
    current: String,
    key: PocketKey,
    allowSpace: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    when (key) {
        is PocketKey.Character -> onChange(current + key.value)
        PocketKey.Space -> if (allowSpace) onChange("$current ")
        PocketKey.Backspace -> onChange(current.dropLast(1))
        PocketKey.Submit -> onSubmit()
        PocketKey.Alphabet, PocketKey.Emoji -> Unit
    }
}
