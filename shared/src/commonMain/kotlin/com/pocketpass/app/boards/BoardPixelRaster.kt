package com.pocketpass.app.boards

import kotlin.math.floor
import kotlin.math.roundToInt

object BoardPixelRaster {
    const val width = 320
    const val height = 240
    const val cellSize = 2.5f

    data class Run(val x: Int, val y: Int, val length: Int)

    fun coordinate(value: Float, limit: Int): Int =
        floor(value / cellSize).toInt().coerceIn(0, limit - 1)

    fun brushWidth(size: Float): Int = (size / cellSize).roundToInt().coerceIn(1, 16)

    fun runs(stroke: BoardStroke): List<Run> {
        if (stroke.points.isEmpty()) return emptyList()
        val filled = HashSet<Int>()
        val brush = brushWidth(stroke.size)
        val offset = (brush - 1) / 2
        fun stamp(x: Int, y: Int) {
            for (py in y - offset until y - offset + brush) {
                if (py !in 0 until height) continue
                for (px in x - offset until x - offset + brush) {
                    if (px in 0 until width) filled.add(py * width + px)
                }
            }
        }

        var previousX = coordinate(stroke.points.first()[0], width)
        var previousY = coordinate(stroke.points.first()[1], height)
        stamp(previousX, previousY)
        for (point in stroke.points.drop(1)) {
            val targetX = coordinate(point[0], width)
            val targetY = coordinate(point[1], height)
            var x = previousX
            var y = previousY
            val dx = kotlin.math.abs(targetX - x)
            val dy = kotlin.math.abs(targetY - y)
            val sx = if (x < targetX) 1 else -1
            val sy = if (y < targetY) 1 else -1
            var error = dx - dy
            while (true) {
                stamp(x, y)
                if (x == targetX && y == targetY) break
                val doubled = 2 * error
                if (doubled > -dy) { error -= dy; x += sx }
                if (doubled < dx) { error += dx; y += sy }
            }
            previousX = targetX
            previousY = targetY
        }

        val result = ArrayList<Run>()
        var start = -1
        var end = -1
        for (pixel in filled.sorted()) {
            if (start >= 0 && pixel == end + 1 && pixel / width == end / width) {
                end = pixel
            } else {
                if (start >= 0) result.add(Run(start % width, start / width, end - start + 1))
                start = pixel
                end = pixel
            }
        }
        if (start >= 0) result.add(Run(start % width, start / width, end - start + 1))
        return result
    }
}
