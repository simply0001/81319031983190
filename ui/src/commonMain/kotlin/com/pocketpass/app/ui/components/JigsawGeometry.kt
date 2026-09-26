package com.pocketpass.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.min

enum class JigsawEdge {
    Flat,
    Tab,
    Blank,
}

data class JigsawPieceShape(
    val top: JigsawEdge,
    val right: JigsawEdge,
    val bottom: JigsawEdge,
    val left: JigsawEdge,
)

sealed interface JigsawSegment {
    data class Line(val to: Offset) : JigsawSegment

    data class Cubic(val control1: Offset, val control2: Offset, val to: Offset) : JigsawSegment
}

data class JigsawOutline(
    val start: Offset,
    val segments: List<JigsawSegment>,
)

data class PuzzleBoardLayout(
    val cell: Float,
    val width: Float,
    val height: Float,
    val tabDepth: Float,
)

fun jigsawPieceShape(index: Int, columns: Int, rows: Int, seed: Int): JigsawPieceShape {
    val row = index / columns
    val column = index % columns
    fun tabBelow(r: Int, c: Int): Boolean = jigsawMix(seed, r * columns + c, 0) and 1 == 0
    fun tabRightOf(r: Int, c: Int): Boolean = jigsawMix(seed, r * columns + c, 1) and 1 == 0
    return JigsawPieceShape(
        top = when {
            row == 0 -> JigsawEdge.Flat
            tabBelow(row - 1, column) -> JigsawEdge.Blank
            else -> JigsawEdge.Tab
        },
        right = when {
            column == columns - 1 -> JigsawEdge.Flat
            tabRightOf(row, column) -> JigsawEdge.Tab
            else -> JigsawEdge.Blank
        },
        bottom = when {
            row == rows - 1 -> JigsawEdge.Flat
            tabBelow(row, column) -> JigsawEdge.Tab
            else -> JigsawEdge.Blank
        },
        left = when {
            column == 0 -> JigsawEdge.Flat
            tabRightOf(row, column - 1) -> JigsawEdge.Blank
            else -> JigsawEdge.Tab
        },
    )
}

internal fun jigsawMix(seed: Int, cell: Int, axis: Int): Int {
    var hash = (seed * -1640531535) xor (cell * -2048144777) xor (axis * -1028477379)
    hash = hash xor (hash ushr 15)
    hash *= 739903597
    hash = hash xor (hash ushr 12)
    hash *= 695536057
    hash = hash xor (hash ushr 15)
    return hash
}

fun jigsawPieceOutline(shape: JigsawPieceShape, cell: Rect, tabDepth: Float): JigsawOutline {
    val segments = mutableListOf<JigsawSegment>()
    appendEdge(segments, cell.topLeft, cell.topRight, Offset(0f, -1f), shape.top, tabDepth)
    appendEdge(segments, cell.topRight, cell.bottomRight, Offset(1f, 0f), shape.right, tabDepth)
    appendEdge(segments, cell.bottomRight, cell.bottomLeft, Offset(0f, 1f), shape.bottom, tabDepth)
    appendEdge(segments, cell.bottomLeft, cell.topLeft, Offset(-1f, 0f), shape.left, tabDepth)
    return JigsawOutline(start = cell.topLeft, segments = segments)
}

private fun appendEdge(
    out: MutableList<JigsawSegment>,
    from: Offset,
    to: Offset,
    normal: Offset,
    edge: JigsawEdge,
    tabDepth: Float,
) {
    if (edge == JigsawEdge.Flat) {
        out += JigsawSegment.Line(to)
        return
    }
    val sign = if (edge == JigsawEdge.Tab) 1f else -1f
    val along = to - from
    fun point(t: Float, s: Float): Offset = from + along * t + normal * (s * sign * tabDepth)
    out += JigsawSegment.Line(point(0.35f, 0f))
    out += JigsawSegment.Cubic(point(0.42f, 0f), point(0.45f, 0.36f), point(0.42f, 0.56f))
    out += JigsawSegment.Cubic(point(0.39f, 0.78f), point(0.41f, 1f), point(0.5f, 1f))
    out += JigsawSegment.Cubic(point(0.59f, 1f), point(0.61f, 0.78f), point(0.58f, 0.56f))
    out += JigsawSegment.Cubic(point(0.55f, 0.36f), point(0.58f, 0f), point(0.65f, 0f))
    out += JigsawSegment.Line(to)
}

fun JigsawOutline.toPath(): Path = Path().apply {
    moveTo(start.x, start.y)
    segments.forEach { segment ->
        when (segment) {
            is JigsawSegment.Line -> lineTo(segment.to.x, segment.to.y)
            is JigsawSegment.Cubic -> cubicTo(
                segment.control1.x,
                segment.control1.y,
                segment.control2.x,
                segment.control2.y,
                segment.to.x,
                segment.to.y,
            )
        }
    }
    close()
}

fun puzzleBoardLayout(columns: Int, rows: Int, maxWidth: Float, maxHeight: Float): PuzzleBoardLayout {
    val cell = min(maxWidth / columns, maxHeight / rows)
    return PuzzleBoardLayout(
        cell = cell,
        width = cell * columns,
        height = cell * rows,
        tabDepth = cell * JIGSAW_TAB_FRACTION,
    )
}

const val JIGSAW_TAB_FRACTION = 0.2f
