package com.pocketpass.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@Composable
internal actual fun GlobeShaderSurface(
    mapBytes: ByteArray,
    spinFraction: () -> Float,
    modifier: Modifier,
) {
    val map = remember(mapBytes) { Image.makeFromEncoded(mapBytes) }
    val builder = remember(map) {
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(GlobeShader)).apply {
            child("map", map.makeShader(FilterTileMode.REPEAT, FilterTileMode.CLAMP))
        }
    }
    Canvas(modifier) {
        builder.uniform("resolution", size.width, size.height)
        builder.uniform("mapSize", map.width.toFloat(), map.height.toFloat())
        builder.uniform("spin", spinFraction())
        drawRect(ShaderBrush(builder.makeShader().asComposeShader()))
    }
}
