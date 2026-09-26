package com.pocketpass.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

@Composable
internal fun ColouredBubbleTail(palette: BubblePalette, modifier: Modifier) {
    val path = remember {
        PathParser().parsePathString("M13.1875 18.4449C23.4653 4.59962 44.1909 4.59962 54.4687 18.4449C67.0599 35.4071 54.9528 59.4732 33.8281 59.4732C12.7034 59.4732 0.596339 35.4071 13.1875 18.4449Z").toPath()
    }
    Canvas(modifier) {
        scale(size.width / 67.6562f, size.height / 77.1344f, Offset.Zero) {
            drawPath(path, Brush.verticalGradient(colorStops = palette.fill, endY = 60f))
            drawPath(path, palette.border, style = Stroke(16.1217f))
        }
    }
}
