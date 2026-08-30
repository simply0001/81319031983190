package com.pocketpass.app.ui.components

import com.pocketpass.app.ui.controller.FocusDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PocketKeyboardNeighborsTest {
    private val rows = listOf(
        listOf(KeySlot("q", 100f), KeySlot("w", 200f), KeySlot("e", 300f)),
        listOf(KeySlot("a", 150f), KeySlot("s", 250f)),
        listOf(KeySlot("symbols", 60f), KeySlot("space", 200f), KeySlot("send", 340f)),
    )
    private val neighbors = wrapNeighbors(rows)

    @Test
    fun rowEndsWrapToTheNextRowStartAndThePreviousRowEnd() {
        assertEquals("a", neighbors.getValue("e")[FocusDirection.Right])
        assertEquals("send", neighbors.getValue("q")[FocusDirection.Left])
        assertEquals("symbols", neighbors.getValue("s")[FocusDirection.Right])
        assertEquals("e", neighbors.getValue("a")[FocusDirection.Left])
        assertEquals("q", neighbors.getValue("send")[FocusDirection.Right])
        assertEquals("s", neighbors.getValue("symbols")[FocusDirection.Left])
        assertEquals("s", neighbors.getValue("a")[FocusDirection.Right])
        assertEquals("a", neighbors.getValue("s")[FocusDirection.Left])
        assertEquals("symbols", neighbors.getValue("space")[FocusDirection.Left])
        assertEquals("send", neighbors.getValue("space")[FocusDirection.Right])
        assertNull(neighbors.getValue("a")[FocusDirection.Down])
        assertNull(neighbors.getValue("a")[FocusDirection.Up])
    }

    @Test
    fun bottomRowWrapsDownToTheNearestTopKeyAndTopRowDoesNotWrapUp() {
        assertEquals("q", neighbors.getValue("symbols")[FocusDirection.Down])
        assertEquals("w", neighbors.getValue("space")[FocusDirection.Down])
        assertEquals("e", neighbors.getValue("send")[FocusDirection.Down])
        assertNull(neighbors.getValue("q")[FocusDirection.Up])
        assertNull(neighbors.getValue("w")[FocusDirection.Up])
        assertNull(neighbors.getValue("e")[FocusDirection.Up])
        assertNull(neighbors.getValue("w")[FocusDirection.Down])
    }

    @Test
    fun topRowUpTargetsResolveByKeyCentre() {
        val split = wrapNeighbors(rows) { centerX -> if (centerX < 200f) "field" else "send" }
        assertEquals("field", split.getValue("q")[FocusDirection.Up])
        assertEquals("send", split.getValue("w")[FocusDirection.Up])
        assertEquals("send", split.getValue("e")[FocusDirection.Up])
        assertNull(split.getValue("a")[FocusDirection.Up])
        assertNull(split.getValue("space")[FocusDirection.Up])
    }

    @Test
    fun emptyRowsAreIgnored() {
        assertEquals(emptyMap<String, Map<FocusDirection, String>>(), wrapNeighbors(listOf(emptyList())))
        val single = wrapNeighbors(listOf(emptyList(), listOf(KeySlot("only", 0f))))
        assertEquals("only", single.getValue("only")[FocusDirection.Right])
        assertEquals("only", single.getValue("only")[FocusDirection.Down])
    }
}
