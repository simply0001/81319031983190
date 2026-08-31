package com.pocketpass.app.ui.mii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiiTraitIconCatalogTest {
    @Test
    fun pathBoundsFollowAbsoluteAndRelativeCommands() {
        val bounds = svgPathBounds("M10 20 L30 40 h5 v-10 c1 1 2 2 3 3 Q40 5 50 10 Z")!!

        assertEquals(10f, bounds.minX, 0.001f)
        assertEquals(5f, bounds.minY, 0.001f)
        assertEquals(50f, bounds.maxX, 0.001f)
        assertEquals(40f, bounds.maxY, 0.001f)
    }

    @Test
    fun implicitLineToAfterMoveAndClosePathReturnToStart() {
        val bounds = svgPathBounds("m2 3 4 5 z l1 1")!!

        assertEquals(2f, bounds.minX, 0.001f)
        assertEquals(3f, bounds.minY, 0.001f)
        assertEquals(6f, bounds.maxX, 0.001f)
        assertEquals(8f, bounds.maxY, 0.001f)
    }

    @Test
    fun centeredViewBoxKeepsSizeAndMovesContentToTheMiddle() {
        val svg = """<svg width="52" height="52" viewBox="0 0 52 52"><path d="M10 10 L30 10 L30 20 Z"/><path d="M20 0 L20 4"/></svg>"""

        val centered = svg.withCenteredViewBox()

        assertTrue(centered.contains("""viewBox="-6 -16 52 52""""))
        assertEquals(SvgBounds(10f, 0f, 30f, 20f), svgContentBounds(svg))
    }

    @Test
    fun commonPaletteDisplayOrderIsARainbowWithNeutralsLast() {
        val order = MiiEditorColors.commonDisplayOrder
        val keys = MiiEditorColors.common.map { it.oklab() }

        assertEquals(MiiEditorColors.common.indices.toList(), order.sorted())
        assertEquals(99, order.last())
        assertTrue(order.indexOf(20) < order.indexOf(90))
        assertTrue(order.indexOf(90) < order.indexOf(69))
        assertTrue(order.indexOf(69) < order.indexOf(50))
        assertTrue(order.indexOf(50) < order.indexOf(41))
        assertTrue(order.indexOf(41) < order.indexOf(8))
        assertTrue(order.indexOf(8) < order.indexOf(96))

        val rows = order.chunked(MiiEditorColors.PALETTE_COLUMNS)
        assertEquals(10, rows.size)
        rows.dropLast(1).forEachIndexed { index, row ->
            val darkest = row.minBy { keys[it].lightness }
            val lightest = row.maxBy { keys[it].lightness }
            if (index % 2 == 0) {
                assertEquals(darkest, row.first())
                assertEquals(lightest, row.last())
            } else {
                assertEquals(lightest, row.first())
                assertEquals(darkest, row.last())
            }
        }
        val greyest = keys.indices.sortedBy { keys[it].chroma }.take(MiiEditorColors.PALETTE_COLUMNS)
        assertEquals(greyest.sorted(), rows.last().sorted())
        assertEquals(rows.last().map { keys[it].lightness }.sorted(), rows.last().map { keys[it].lightness })
        rows.dropLast(1).zipWithNext { above, below ->
            val hue = { index: Int -> (keys[index].hue - 20f + 360f) % 360f }
            assertTrue(above.maxOf(hue) <= below.minOf(hue))
        }
    }

    @Test
    fun oklabMatchesReferenceValues() {
        val white = androidx.compose.ui.graphics.Color.White.oklab()
        assertEquals(1f, white.lightness, 0.001f)
        assertEquals(0f, white.chroma, 0.001f)
        val red = androidx.compose.ui.graphics.Color.Red.oklab()
        assertEquals(0.628f, red.lightness, 0.002f)
        assertEquals(29f, red.hue, 1f)
    }

    @Test
    fun svgWithoutPathsIsLeftAlone() {
        val svg = """<svg viewBox="0 0 52 52"><circle cx="1" cy="1" r="1"/></svg>"""

        assertNull(svgContentBounds(svg))
        assertEquals(svg, svg.withCenteredViewBox())
    }
}
