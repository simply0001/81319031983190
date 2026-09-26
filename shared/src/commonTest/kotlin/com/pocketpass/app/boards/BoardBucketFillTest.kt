package com.pocketpass.app.boards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardBucketFillTest {
    @Test
    fun blankPaperFillsAsOneUndoableAction() {
        val result = assertIs<BoardBucketResult.Filled>(BoardBucketFill.fill(BoardDrawing(), 20f, 20f, "#3379D6"))
        val drawing = BoardDrawingHistory().add(result.stroke)
        drawing.drawing.validate()
        assertEquals("bucket", result.stroke.pen)
        assertEquals(480, result.stroke.points.size)
        assertEquals(BoardDrawing(), drawing.undo().drawing)
        assertEquals(BoardBucketResult.AlreadyFilled, BoardBucketFill.fill(drawing.drawing, 20f, 20f, "#3379D6"))
    }

    @Test
    fun enclosedPixelsFillOnlyTheirRegion() {
        fun edge(x1: Float, y1: Float, x2: Float, y2: Float) =
            BoardStroke("pixel", "#222222", 2f, listOf(listOf(x1, y1), listOf(x2, y2)))
        val outline = BoardDrawing(strokes = listOf(
            edge(25f, 25f, 75f, 25f), edge(75f, 25f, 75f, 75f),
            edge(75f, 75f, 25f, 75f), edge(25f, 75f, 25f, 25f),
        ))
        val filled = assertIs<BoardBucketResult.Filled>(BoardBucketFill.fill(outline, 50f, 50f, "#E84A5F"))
        val runs = filled.stroke.points.chunked(2)
        assertTrue(runs.any { it[0][0] <= 50f && it[1][0] > 50f && it[0][1] == 50f })
        assertFalse(runs.any { it[0][0] == 0f || it[0][1] == 0f })
        outline.copy(strokes = outline.strokes + filled.stroke).validate()
    }

    @Test
    fun thinSmoothOutlineDoesNotLeakToTheOutside() {
        val outline = BoardDrawing(strokes = listOf(BoardStroke("smooth", "#222222", 2f, listOf(
            listOf(25f, 25f), listOf(75f, 25f), listOf(75f, 75f),
            listOf(25f, 75f), listOf(25f, 25f),
        ))))
        val filled = assertIs<BoardBucketResult.Filled>(BoardBucketFill.fill(outline, 50f, 50f, "#F6B93B"))
        assertFalse(filled.stroke.points.chunked(2).any { it[0][0] == 0f || it[0][1] == 0f })
    }

    @Test
    fun rejectsUnpairedOrOverlappingFillGeometry() {
        val stroke = BoardStroke("bucket", "#3379D6", 2f, listOf(listOf(5f, 5f)))
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = listOf(stroke)).validate() }
        val backwards = stroke.copy(points = listOf(listOf(7.5f, 5f), listOf(5f, 5f)))
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = listOf(backwards)).validate() }
    }
}
