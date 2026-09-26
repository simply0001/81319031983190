package com.pocketpass.app.boards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardPixelRasterTest {
    @Test
    fun quickDiagonalIsConnectedOnTheDrawingGrid() {
        val stroke = BoardStroke(size = 2f, points = listOf(listOf(2.5f, 2.5f), listOf(50f, 25f)))
        val pixels = BoardPixelRaster.runs(stroke).flatMap { run ->
            (run.x until run.x + run.length).map { x -> x to run.y }
        }
        assertEquals(20, pixels.maxOf { it.first } - pixels.minOf { it.first } + 1)
        for (x in pixels.minOf { it.first }..pixels.maxOf { it.first }) {
            assertTrue(pixels.any { it.first == x }, "fast pointer movement must not leave gaps")
        }
    }

    @Test
    fun tappingAndZoomedStrokesUseTheSamePixelGrid() {
        val tap = BoardStroke(size = 2f, points = listOf(listOf(6.25f, 6.25f)))
        assertEquals(listOf(BoardPixelRaster.Run(2, 2, 1)), BoardPixelRaster.runs(tap))
        val cornerTap = tap.copy(points = listOf(listOf(0f, 0f)))
        assertEquals(listOf(BoardPixelRaster.Run(0, 0, 1)), BoardPixelRaster.runs(cornerTap))
        assertEquals(1, BoardPixelRaster.brushWidth(2f))
        assertEquals(2, BoardPixelRaster.brushWidth(4f))
    }

    @Test
    fun clippedWideBrushNeverLeavesThePaper() {
        val stroke = BoardStroke(size = 40f, points = listOf(listOf(800f, 600f)))
        val runs = BoardPixelRaster.runs(stroke)
        assertTrue(runs.isNotEmpty())
        assertTrue(runs.all { it.x >= 0 && it.y >= 0 && it.x + it.length <= 320 && it.y < 240 })
    }
}
