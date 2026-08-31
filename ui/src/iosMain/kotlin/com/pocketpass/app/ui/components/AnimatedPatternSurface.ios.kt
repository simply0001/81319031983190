package com.pocketpass.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@Composable
actual fun AnimatedPatternSurface(
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    geometryWidth: Float,
) {
    val geometry = patternGeometryFor(geometryWidth)
    val effect = remember { RuntimeEffect.makeForShader(PATTERN_SHADER) }
    val builder = remember(effect) { RuntimeShaderBuilder(effect) }
    val transition = rememberInfiniteTransition(label = "pattern")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(geometry.rowCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val bandY = remember(geometry) {
        FloatArray(MAX_BANDS) { index ->
            (geometry.bands.getOrNull(index) ?: geometry.bands.last()).sourceCenterY / 2f
        }
    }
    val bandH = remember(geometry) {
        FloatArray(MAX_BANDS) { index ->
            (geometry.bands.getOrNull(index) ?: geometry.bands.last()).logicalNativeHeight / 2f
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        builder.uniform("viewSize", size.width, size.height)
        builder.uniform("designSize", designWidth, designHeight)
        builder.uniform("phase", phase)
        builder.uniform("holdFraction", holdFraction)
        builder.uniform("lift", if (isDarkPatternBackdrop(topColor, bottomColor)) 1f else 0f)
        builder.uniform("topColor", topColor.red, topColor.green, topColor.blue)
        builder.uniform("bottomColor", bottomColor.red, bottomColor.green, bottomColor.blue)
        builder.uniform("bandY", bandY)
        builder.uniform("bandH", bandH)
        builder.uniform("bandCount", geometry.bands.size)
        builder.uniform("rowPitch", geometry.rowPitch)
        builder.uniform("firstRowCenter", geometry.firstRowCenter)
        builder.uniform("cellPeriod", geometry.nativeCellWidth / 2f)
        builder.uniform("firstCenter", geometry.nativePhaseOffset / 2f)
        builder.uniform(
            "depthRange",
            geometry.bands.first().sourceCenterY / 2f,
            geometry.bands.last().sourceCenterY / 2f,
        )
        val triangle = patternChannels(geometry.triangleColor)
        builder.uniform("triangleColor", triangle[0], triangle[1], triangle[2])
        builder.uniform(
            "sourceAlpha",
            patternAlpha(geometry.triangleColor) * TRIANGLE_COLOR_BURN_STRENGTH,
        )
        drawRect(ShaderBrush(builder.makeShader().asComposeShader()))
    }
}
