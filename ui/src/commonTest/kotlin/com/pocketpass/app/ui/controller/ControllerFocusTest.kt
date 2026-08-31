package com.pocketpass.app.ui.controller

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class ControllerFocusTest {
    private fun entry(id: String, left: Float, top: Float) = FocusEntry(
        id = id,
        layer = 0,
        display = FocusDisplay.Bottom,
        bounds = Rect(left, top, left + 100f, top + 60f),
        onActivate = {},
    )

    private val vertical = listOf(
        entry("a", 0f, 0f),
        entry("b", 0f, 100f),
        entry("c", 0f, 200f),
    )

    @Test
    fun nullCurrentSelectsTopLeftMost() {
        assertEquals("a", chooseNextFocus(vertical, null, FocusDirection.Down))
    }

    @Test
    fun downAndUpStepThroughVerticalList() {
        assertEquals("b", chooseNextFocus(vertical, "a", FocusDirection.Down))
        assertEquals("c", chooseNextFocus(vertical, "b", FocusDirection.Down))
        assertEquals("a", chooseNextFocus(vertical, "b", FocusDirection.Up))
    }

    @Test
    fun movingPastTheEndStaysPut() {
        assertEquals("c", chooseNextFocus(vertical, "c", FocusDirection.Down))
        assertEquals("a", chooseNextFocus(vertical, "a", FocusDirection.Up))
    }

    @Test
    fun horizontalMovementPicksNearestInDirection() {
        val row = listOf(
            entry("left", 0f, 0f),
            entry("mid", 200f, 0f),
            entry("right", 400f, 0f),
        )
        assertEquals("mid", chooseNextFocus(row, "left", FocusDirection.Right))
        assertEquals("mid", chooseNextFocus(row, "right", FocusDirection.Left))
        assertEquals("right", chooseNextFocus(row, "left", FocusDirection.Right).let {
            chooseNextFocus(row, it, FocusDirection.Right)
        })
    }

    @Test
    fun explicitNeighborsWinOverGeometryAndWrapTheGrid() {
        val grid = listOf(
            entry("a", 0f, 0f).copy(neighbors = mapOf(FocusDirection.Left to "d")),
            entry("b", 200f, 0f).copy(neighbors = mapOf(FocusDirection.Right to "c")),
            entry("c", 0f, 100f).copy(neighbors = mapOf(FocusDirection.Left to "b")),
            entry("d", 200f, 100f).copy(neighbors = mapOf(FocusDirection.Right to "a")),
        )
        assertEquals("c", chooseNextFocus(grid, "b", FocusDirection.Right))
        assertEquals("b", chooseNextFocus(grid, "c", FocusDirection.Left))
        assertEquals("a", chooseNextFocus(grid, "d", FocusDirection.Right))
        assertEquals("d", chooseNextFocus(grid, "a", FocusDirection.Left))
        assertEquals("c", chooseNextFocus(grid, "b", FocusDirection.Right, held = true))
        assertEquals("d", chooseNextFocus(grid, "b", FocusDirection.Down))
    }

    @Test
    fun scrolledOutRowsAreOnlyReachableFromInsideTheirViewport() {
        val list = ControllerFocusViewport().apply { bounds = Rect(0f, 300f, 400f, 700f) }
        val header = entry("header", 0f, 100f)
        val scrolledPastHeader = entry("past", 0f, -100f).copy(viewport = list)
        val hiddenAbove = entry("hidden", 0f, 220f).copy(viewport = list)
        val visible = entry("visible", 0f, 350f).copy(viewport = list)
        val hiddenBelow = entry("below", 0f, 800f).copy(viewport = list)
        val screen = listOf(header, scrolledPastHeader, hiddenAbove, visible, hiddenBelow)

        assertEquals("header", chooseNextFocus(screen, "header", FocusDirection.Up))
        assertEquals("visible", chooseNextFocus(screen, "header", FocusDirection.Down))
        assertEquals("hidden", chooseNextFocus(screen, "visible", FocusDirection.Up))
        assertEquals("below", chooseNextFocus(screen, "visible", FocusDirection.Down))
        assertEquals("header", chooseNextFocus(screen, "hidden", FocusDirection.Up))
    }

    @Test
    fun neighborsPointingAtInactiveTargetsFallBackToGeometry() {
        val row = listOf(
            entry("left", 0f, 0f).copy(neighbors = mapOf(FocusDirection.Right to "gone")),
            entry("mid", 200f, 0f),
        )
        assertEquals("mid", chooseNextFocus(row, "left", FocusDirection.Right))
    }

    private fun ControllerFocus.add(
        id: String,
        top: Float,
        layer: Int = 0,
        focusable: Boolean = true,
        display: FocusDisplay = FocusDisplay.Bottom,
        onActivate: () -> Unit = {},
    ) {
        register(id, layer, display, focusable = focusable, onActivate = onActivate)
        updateBounds(id, Rect(0f, top, 100f, top + 60f))
    }

    @Test
    fun swapDisplayMovesBetweenScreensAndRemembersEachSide() {
        val focus = ControllerFocus()
        focus.add("bottom_a", 0f)
        focus.add("bottom_b", 100f)
        focus.add("top_a", 0f, display = FocusDisplay.Top)
        focus.add("top_b", 100f, display = FocusDisplay.Top)
        focus.move(FocusDirection.Down)
        focus.move(FocusDirection.Down)
        assertEquals("bottom_b", focus.focusedTarget(FocusDisplay.Bottom)?.id)
        assertNull(focus.focusedTarget(FocusDisplay.Top))

        assertTrue(focus.canSwapDisplay())
        assertTrue(focus.swapDisplay())
        assertEquals("top_a", focus.focusedTarget(FocusDisplay.Top)?.id)
        assertNull(focus.focusedTarget(FocusDisplay.Bottom))

        focus.move(FocusDirection.Down)
        assertEquals("top_b", focus.focusedTarget(FocusDisplay.Top)?.id)
        focus.move(FocusDirection.Down)
        assertEquals("top_b", focus.focusedTarget(FocusDisplay.Top)?.id)

        assertTrue(focus.swapDisplay())
        assertEquals("bottom_b", focus.focusedTarget(FocusDisplay.Bottom)?.id)
        assertTrue(focus.swapDisplay())
        assertEquals("top_b", focus.focusedTarget(FocusDisplay.Top)?.id)
    }

    @Test
    fun swapDisplayNeedsTargetsOnTheOtherScreenAtTheActiveLayer() {
        val focus = ControllerFocus()
        focus.add("bottom_a", 0f)
        assertFalse(focus.canSwapDisplay())
        assertFalse(focus.swapDisplay())

        focus.add("overlay", 0f, layer = 10, display = FocusDisplay.Top)
        focus.move(FocusDirection.Down)
        assertEquals("overlay", focus.focusedTarget(FocusDisplay.Top)?.id)
        assertFalse(focus.canSwapDisplay())

        focus.unregister("overlay")
        focus.focus("bottom_a")
        focus.add("top_a", 0f, display = FocusDisplay.Top)
        assertTrue(focus.canSwapDisplay())
    }

    @Test
    fun barrierAboveTargetsBlocksThemWithoutBecomingFocusable() {
        val focus = ControllerFocus()
        focus.add("a", 0f)
        focus.add("b", 100f)
        assertTrue(focus.move(FocusDirection.Down))
        assertEquals("a", focus.focusedTarget(FocusDisplay.Bottom)?.id)

        focus.add("barrier", 0f, layer = 10, focusable = false)
        assertFalse(focus.hasTargets())
        assertFalse(focus.move(FocusDirection.Down))
        assertNull(focus.focusedTarget(FocusDisplay.Bottom))

        focus.unregister("barrier")
        assertEquals("a", focus.focusedTarget(FocusDisplay.Bottom)?.id)
    }

    @Test
    fun touchHidesHighlightAndNextInputRestoresItInPlace() {
        var activated = 0
        val focus = ControllerFocus()
        focus.add("a", 0f) { activated++ }
        focus.add("b", 100f)
        focus.move(FocusDirection.Down)
        focus.move(FocusDirection.Down)
        assertEquals("b", focus.focusedTarget(FocusDisplay.Bottom)?.id)

        focus.hide()
        assertNull(focus.focusedTarget(FocusDisplay.Bottom))
        assertTrue(focus.move(FocusDirection.Up))
        assertEquals("b", focus.focusedTarget(FocusDisplay.Bottom)?.id)

        focus.move(FocusDirection.Up)
        focus.hide()
        assertTrue(focus.activate())
        assertEquals(0, activated)
        assertEquals("a", focus.focusedTarget(FocusDisplay.Bottom)?.id)
        assertTrue(focus.activate())
        assertEquals(1, activated)
    }

    @Test
    fun revealRectClearsTheRingAndTheChromeAboveTheViewport() {
        val viewport = ControllerFocusViewport(topInset = 234f)
        val rect = revealRect(Size(100f, 60f), viewport)
        assertEquals(Rect(-20f, -254f, 120f, 80f), rect)
        assertEquals(Rect(-20f, -20f, 120f, 80f), revealRect(Size(100f, 60f), null))
    }

    private fun grouped(id: String, left: Float, top: Float, group: String) =
        entry(id, left, top).copy(group = group)

    @Test
    fun heldMoveOnlyContinuesInAStraightLine() {
        val layout = listOf(
            entry("top", 200f, 0f),
            entry("bottom", 200f, 100f),
            entry("aside", 0f, 300f),
        )
        assertEquals("bottom", chooseNextFocus(layout, "top", FocusDirection.Down, held = true))
        assertEquals("aside", chooseNextFocus(layout, "bottom", FocusDirection.Down))
        assertEquals("bottom", chooseNextFocus(layout, "bottom", FocusDirection.Down, held = true))
    }

    @Test
    fun heldMoveStaysInsideItsGroup() {
        val layout = listOf(
            grouped("rail", 0f, 0f, "rail"),
            grouped("grid", 200f, 0f, "grid"),
        )
        assertEquals("rail", chooseNextFocus(layout, "grid", FocusDirection.Left))
        assertEquals("grid", chooseNextFocus(layout, "grid", FocusDirection.Left, held = true))
    }

    @Test
    fun ringMotionKeepsWhenFocusIsUnchangedOrCleared() {
        val a = entry("a", 0f, 0f)
        val scrolled = a.copy(bounds = a.bounds.translate(0f, 40f))
        assertEquals(RingMotion.Keep, ringMotion(a, scrolled, Rect.Zero, 0f, animate = true))
        assertEquals(RingMotion.Keep, ringMotion(a, null, Rect.Zero, 0f, animate = true))
    }

    @Test
    fun ringMotionSnapsWhenRingWasHiddenOrAbsent() {
        assertEquals(RingMotion.Snap, ringMotion(null, entry("a", 0f, 0f), Rect.Zero, 0f, animate = true))
    }

    @Test
    fun ringMotionSlidesAcrossLayersButSnapsWithoutAnimators() {
        val a = entry("a", 0f, 0f)
        val overlay = entry("b", 0f, 100f).copy(layer = 10)
        assertTrue(ringMotion(a, overlay, Rect.Zero, 0f, animate = true) is RingMotion.Slide)
        assertEquals(RingMotion.Snap, ringMotion(a, entry("b", 0f, 100f), Rect.Zero, 0f, animate = false))
    }

    @Test
    fun ringMotionSlidesFromTheDrawnRingNotTheOldTarget() {
        val motion = ringMotion(
            entry("a", 0f, 0f),
            entry("b", 0f, 100f),
            shift = Rect(10f, 0f, 10f, 0f),
            radiusShift = 0f,
            animate = true,
        )
        assertEquals(RingMotion.Slide(Rect(10f, -100f, 10f, -100f), 0f), motion)
    }

    @Test
    fun ringMotionCarriesTheRadiusDifference() {
        val rounded = entry("a", 0f, 0f).copy(cornerRadius = 8f, scale = 2f)
        val motion = ringMotion(rounded, entry("b", 0f, 100f), Rect.Zero, 0f, animate = true)
        assertEquals(-14f, (motion as RingMotion.Slide).radiusShift, 0f)
    }

    @Test
    fun ringRadiusFallsBackToAPillAndScales() {
        assertEquals(30f, entry("a", 0f, 0f).ringRadius(), 0f)
        assertEquals(16f, entry("a", 0f, 0f).copy(cornerRadius = 8f, scale = 2f).ringRadius(), 0f)
    }

    @Test
    fun revealAfterTouchSnapsInsteadOfSliding() {
        val focus = ControllerFocus()
        focus.add("a", 0f)
        focus.add("b", 100f)
        val tracker = RingTracker()
        var now = 0L
        fun step(): RingMotion {
            val next = focus.focusedTarget(FocusDisplay.Bottom)
            now += 16_000_000L
            val previous = tracker.previousFor(next, focus.hidden, now)
            return ringMotion(previous, next, Rect.Zero, 0f, animate = true)
        }
        focus.move(FocusDirection.Down)
        assertEquals(RingMotion.Snap, step())
        focus.move(FocusDirection.Down)
        assertTrue(step() is RingMotion.Slide)
        focus.hide()
        assertEquals(RingMotion.Keep, step())
        focus.focus("a", reveal = false)
        assertEquals(RingMotion.Keep, step())
        focus.move(FocusDirection.Up)
        assertEquals(RingMotion.Snap, step())
        assertEquals("a", focus.focusedTarget(FocusDisplay.Bottom)?.id)
    }

    @Test
    fun leadingEdgeOfASlideGetsTheStifferSpring() {
        val (leftSpring, rightSpring) = edgeSprings(-1f)
        assertTrue(rightSpring.stiffness > leftSpring.stiffness)
        val (topSpring, bottomSpring) = edgeSprings(1f)
        assertTrue(topSpring.stiffness > bottomSpring.stiffness)
    }

    @Test
    fun briefGapKeepsTheRingSlidingFromWhereItWas() {
        val tracker = RingTracker(gapLimitNanos = 100L)
        val panel = entry("panel", 0f, 0f)
        val button = entry("button", 0f, 100f).copy(layer = 5)
        assertNull(tracker.previousFor(panel, hidden = false, nowNanos = 0L))
        assertEquals(panel, tracker.previousFor(null, hidden = false, nowNanos = 10L))
        assertEquals(panel, tracker.previousFor(button, hidden = false, nowNanos = 50L))
        assertTrue(ringMotion(panel, button, Rect.Zero, 0f, animate = true) is RingMotion.Slide)
    }

    @Test
    fun longOrHiddenGapsForgetTheRing() {
        val a = entry("a", 0f, 0f)
        val b = entry("b", 0f, 100f)
        val slow = RingTracker(gapLimitNanos = 100L)
        slow.previousFor(a, hidden = false, nowNanos = 0L)
        slow.previousFor(null, hidden = false, nowNanos = 10L)
        assertNull(slow.previousFor(b, hidden = false, nowNanos = 500L))
        val touched = RingTracker(gapLimitNanos = 100L)
        touched.previousFor(a, hidden = false, nowNanos = 0L)
        touched.previousFor(null, hidden = true, nowNanos = 10L)
        assertNull(touched.previousFor(b, hidden = false, nowNanos = 20L))
    }
}
