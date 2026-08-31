package com.pocketpass.app.ui.screens

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush

// Only reached when supportsAnimatedPatterns() is true, i.e. on Android 13+.
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal actual fun GlobeShaderSurface(
    mapBytes: ByteArray,
    spinFraction: () -> Float,
    modifier: Modifier,
) {
    val mapBitmap = remember(mapBytes) {
        BitmapFactory.decodeByteArray(mapBytes, 0, mapBytes.size)
    }
    val shader = remember { RuntimeShader(GlobeShader) }
    val brush = remember(mapBitmap) {
        shader.setInputShader(
            "map",
            BitmapShader(mapBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP),
        )
        ShaderBrush(shader)
    }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("mapSize", mapBitmap.width.toFloat(), mapBitmap.height.toFloat())
        shader.setFloatUniform("spin", spinFraction())
        drawRect(brush)
    }
}
