package com.pocketpass.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignSurfaceOverscanTest {
    @Test
    fun fourByThreePanelsLeaveBarsOnOneAxisOnly() {
        val top = overscanFor(640f, 480f, TOP_DESIGN_WIDTH, TOP_DESIGN_HEIGHT)
        assertEquals(0f, top.x, 0.01f)
        assertEquals(180f, top.y, 0.01f)

        val bottom = overscanFor(640f, 480f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
        assertEquals(100f, bottom.x, 0.01f)
        assertEquals(0f, bottom.y, 0.01f)
    }

    @Test
    fun exactFitsHaveNoOverscan() {
        val top = overscanFor(1920f, 1080f, TOP_DESIGN_WIDTH, TOP_DESIGN_HEIGHT)
        assertEquals(0f, top.x, 0.01f)
        assertEquals(0f, top.y, 0.01f)

        val bottom = overscanFor(1240f, 1080f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
        assertEquals(0f, bottom.x, 0.01f)
        assertEquals(0f, bottom.y, 0.01f)
    }
}
