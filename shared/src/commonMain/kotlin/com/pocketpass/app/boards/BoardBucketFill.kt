package com.pocketpass.app.boards

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

sealed interface BoardBucketResult {
    data class Filled(val stroke: BoardStroke) : BoardBucketResult
    data object AlreadyFilled : BoardBucketResult
    data object TooDetailed : BoardBucketResult
}

object BoardBucketFill {
    private const val white = 0xFFFFFF
    private const val maxRuns = 10_000

    fun fill(drawing: BoardDrawing, x: Float, y: Float, color: String): BoardBucketResult {
        val width = BoardPixelRaster.width
        val height = BoardPixelRaster.height
        val pixels = IntArray(width * height) { white }
        drawing.strokes.forEach { stroke ->
            val ink = if (stroke.pen == "eraser") white else stroke.color.removePrefix("#").toInt(16)
            when (stroke.pen) {
                "pixel" -> BoardPixelRaster.runs(stroke).forEach { run ->
                    val row = run.y * width
                    for (px in run.x until run.x + run.length) pixels[row + px] = ink
                }
                "bucket" -> stroke.points.chunked(2).forEach { pair ->
                    if (pair.size != 2) return@forEach
                    val row = BoardPixelRaster.coordinate(pair[0][1], height) * width
                    val start = BoardPixelRaster.coordinate(pair[0][0], width)
                    val end = ceil(pair[1][0] / BoardPixelRaster.cellSize).toInt().coerceIn(0, width)
                    for (px in start until end) pixels[row + px] = ink
                }
                "smooth", "eraser" -> paintSmooth(pixels, stroke, ink)
            }
        }

        val replacement = color.removePrefix("#").toInt(16)
        val start = BoardPixelRaster.coordinate(y, height) * width + BoardPixelRaster.coordinate(x, width)
        val target = pixels[start]
        if (target == replacement) return BoardBucketResult.AlreadyFilled

        val queue = IntArray(pixels.size)
        val selected = BooleanArray(pixels.size)
        var head = 0
        var tail = 1
        queue[0] = start
        selected[start] = true
        while (head < tail) {
            val at = queue[head++]
            val px = at % width
            val py = at / width
            fun visit(next: Int) {
                if (!selected[next] && pixels[next] == target) {
                    selected[next] = true
                    queue[tail++] = next
                }
            }
            if (px > 0) visit(at - 1)
            if (px + 1 < width) visit(at + 1)
            if (py > 0) visit(at - width)
            if (py + 1 < height) visit(at + width)
        }

        val points = ArrayList<List<Float>>()
        var runs = 0
        for (py in 0 until height) {
            var px = 0
            while (px < width) {
                if (!selected[py * width + px]) { px++; continue }
                val first = px
                while (px < width && selected[py * width + px]) px++
                if (++runs > maxRuns) return BoardBucketResult.TooDetailed
                points.add(listOf(first * BoardPixelRaster.cellSize, py * BoardPixelRaster.cellSize))
                points.add(listOf(px * BoardPixelRaster.cellSize, py * BoardPixelRaster.cellSize))
            }
        }
        return BoardBucketResult.Filled(BoardStroke("bucket", color, 2f, points))
    }

    private fun paintSmooth(pixels: IntArray, stroke: BoardStroke, ink: Int) {
        val points = stroke.points
        if (points.isEmpty()) return
        val cell = BoardPixelRaster.cellSize
        val radius = max(stroke.size / 2f, cell * .7f)
        for (index in 0 until max(1, points.size - 1)) {
            val from = points[index]
            val to = points[min(index + 1, points.lastIndex)]
            val x1 = from[0]; val y1 = from[1]
            val dx = to[0] - x1; val dy = to[1] - y1
            val lengthSquared = dx * dx + dy * dy
            val left = floor((min(x1, to[0]) - radius) / cell).toInt().coerceIn(0, BoardPixelRaster.width - 1)
            val right = ceil((max(x1, to[0]) + radius) / cell).toInt().coerceIn(0, BoardPixelRaster.width - 1)
            val top = floor((min(y1, to[1]) - radius) / cell).toInt().coerceIn(0, BoardPixelRaster.height - 1)
            val bottom = ceil((max(y1, to[1]) + radius) / cell).toInt().coerceIn(0, BoardPixelRaster.height - 1)
            for (py in top..bottom) for (px in left..right) {
                val cx = (px + .5f) * cell
                val cy = (py + .5f) * cell
                val t = if (lengthSquared == 0f) 0f else (((cx - x1) * dx + (cy - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
                val ex = cx - x1 - t * dx
                val ey = cy - y1 - t * dy
                if (ex * ex + ey * ey <= radius * radius) pixels[py * BoardPixelRaster.width + px] = ink
            }
        }
    }
}
