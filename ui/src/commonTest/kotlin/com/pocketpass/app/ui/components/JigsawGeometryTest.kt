package com.pocketpass.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JigsawGeometryTest {
    private val grids = listOf(5 to 4, 4 to 4, 3 to 5, 2 to 2)

    private fun JigsawEdge.complement(): JigsawEdge = when (this) {
        JigsawEdge.Tab -> JigsawEdge.Blank
        JigsawEdge.Blank -> JigsawEdge.Tab
        JigsawEdge.Flat -> JigsawEdge.Flat
    }

    @Test
    fun outerEdgesAreFlat() {
        grids.forEach { (columns, rows) ->
            for (index in 0 until columns * rows) {
                val shape = jigsawPieceShape(index, columns, rows, seed = 7)
                val row = index / columns
                val column = index % columns
                assertEquals(row == 0, shape.top == JigsawEdge.Flat, "top of $index on ${columns}x$rows")
                assertEquals(row == rows - 1, shape.bottom == JigsawEdge.Flat, "bottom of $index on ${columns}x$rows")
                assertEquals(column == 0, shape.left == JigsawEdge.Flat, "left of $index on ${columns}x$rows")
                assertEquals(column == columns - 1, shape.right == JigsawEdge.Flat, "right of $index on ${columns}x$rows")
            }
        }
    }

    @Test
    fun neighboursInterlock() {
        grids.forEach { (columns, rows) ->
            for (index in 0 until columns * rows) {
                val shape = jigsawPieceShape(index, columns, rows, seed = 99)
                val row = index / columns
                val column = index % columns
                if (column < columns - 1) {
                    val right = jigsawPieceShape(index + 1, columns, rows, seed = 99)
                    assertEquals(shape.right.complement(), right.left, "vertical edge after $index on ${columns}x$rows")
                }
                if (row < rows - 1) {
                    val below = jigsawPieceShape(index + columns, columns, rows, seed = 99)
                    assertEquals(shape.bottom.complement(), below.top, "horizontal edge below $index on ${columns}x$rows")
                }
            }
        }
    }

    @Test
    fun shapesAreDeterministicPerSeed() {
        val first = (0 until 16).map { jigsawPieceShape(it, 4, 4, seed = 42) }
        val again = (0 until 16).map { jigsawPieceShape(it, 4, 4, seed = 42) }
        val other = (0 until 16).map { jigsawPieceShape(it, 4, 4, seed = 43) }
        assertEquals(first, again)
        assertNotEquals(first, other)
        assertTrue(first.any { it.right == JigsawEdge.Tab } && first.any { it.right == JigsawEdge.Blank })
    }

    @Test
    fun outlinesStartAtTheCornerAndUseSixSegmentsPerKnob() {
        val cell = Rect(10f, 20f, 110f, 120f)
        val flat = jigsawPieceOutline(
            JigsawPieceShape(JigsawEdge.Flat, JigsawEdge.Flat, JigsawEdge.Flat, JigsawEdge.Flat),
            cell,
            tabDepth = 20f,
        )
        assertEquals(cell.topLeft, flat.start)
        assertEquals(4, flat.segments.size)
        assertEquals(JigsawSegment.Line(cell.topLeft), flat.segments.last())

        val knobby = jigsawPieceOutline(
            JigsawPieceShape(JigsawEdge.Tab, JigsawEdge.Blank, JigsawEdge.Flat, JigsawEdge.Tab),
            cell,
            tabDepth = 20f,
        )
        assertEquals(6 + 6 + 1 + 6, knobby.segments.size)
        assertEquals(JigsawSegment.Line(cell.topLeft), knobby.segments.last())
        val topKnobTip = (knobby.segments[2] as JigsawSegment.Cubic).to
        assertEquals(Offset(60f, 0f), topKnobTip)
        val rightKnobTip = (knobby.segments[8] as JigsawSegment.Cubic).to
        assertEquals(Offset(90f, 70f), rightKnobTip)
    }

    @Test
    fun boardLayoutsKeepCellsSquare() {
        val wide = puzzleBoardLayout(5, 4, 800f, 640f)
        assertEquals(160f, wide.cell)
        assertEquals(800f, wide.width)
        assertEquals(640f, wide.height)
        assertEquals(32f, wide.tabDepth)

        val square = puzzleBoardLayout(4, 4, 800f, 640f)
        assertEquals(160f, square.cell)
        assertEquals(640f, square.width)

        val tall = puzzleBoardLayout(3, 5, 800f, 660f)
        assertEquals(132f, tall.cell)
        assertEquals(396f, tall.width)
        assertEquals(660f, tall.height)
    }
}
