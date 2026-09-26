package com.pocketpass.app.ui.controller

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerFocusHierarchyTest {
    private fun ControllerFocus.card(id: String, left: Float) {
        register(id, 10, FocusDisplay.Bottom) { enterChildren(id) }
        updateBounds(id, Rect(left, 0f, left + 650f, 240f))
    }

    private fun ControllerFocus.action(id: String, parentId: String, left: Float, onActivate: () -> Unit = {}) {
        register(id, 10, FocusDisplay.Bottom, parentId = parentId, onActivate = onActivate)
        updateBounds(id, Rect(left, 150f, left + 150f, 214f))
    }

    @Test
    fun browsingSelectsWholeCardsAndActivationEntersTheirActions() {
        val focus = ControllerFocus()
        var purchases = 0
        var wears = 0
        focus.card("hat_one", 0f)
        focus.action("buy_one", "hat_one", 232f) { purchases++ }
        focus.action("wear_one", "hat_one", 460f) { wears++ }
        focus.card("hat_two", 690f)
        focus.action("wear_two", "hat_two", 1150f)
        focus.move(FocusDirection.Down)
        assertEquals("hat_one", focus.focusId)
        focus.move(FocusDirection.Right)
        assertEquals("hat_two", focus.focusId)
        focus.move(FocusDirection.Left)
        focus.activate()
        assertEquals("buy_one", focus.focusId)
        assertEquals(0, purchases)
        assertEquals(0, wears)
        focus.move(FocusDirection.Right)
        assertEquals("wear_one", focus.focusId)
        focus.move(FocusDirection.Right)
        assertEquals("wear_one", focus.focusId)
        focus.activate()
        assertEquals(1, wears)
        assertTrue(focus.exitToParent())
        assertEquals("hat_one", focus.focusId)
        assertFalse(focus.exitToParent())
        focus.move(FocusDirection.Right)
        assertEquals("hat_two", focus.focusId)
    }

    @Test
    fun ownedHatEntersWearAndUnavailableHatStaysOnTheCard() {
        val focus = ControllerFocus()
        focus.card("owned", 0f)
        focus.action("wear", "owned", 232f)
        focus.card("unavailable", 690f)
        focus.focus("owned")
        focus.activate()
        assertEquals("wear", focus.focusId)
        focus.exitToParent()
        focus.move(FocusDirection.Right)
        focus.activate()
        assertEquals("unavailable", focus.focusId)
        assertFalse(focus.canExitToParent())
    }

    @Test
    fun confirmationTakesPriorityAndRestoresItsActionOrCard() {
        val focus = ControllerFocus()
        focus.card("hat", 0f)
        focus.action("buy", "hat", 232f)
        focus.focus("hat")
        focus.activate()
        val origin = requireNotNull(focus.focusedTarget(FocusDisplay.Bottom))
        focus.register("cancel", 20, FocusDisplay.Bottom) {}
        focus.updateBounds("cancel", Rect(100f, 100f, 200f, 200f))
        focus.focus("cancel")
        assertFalse(focus.canExitToParent())
        focus.restoreFocus(origin)
        assertEquals("cancel", focus.focusId)
        focus.unregister("cancel")
        focus.restoreFocus(origin)
        assertEquals("buy", focus.focusId)
        focus.unregister("buy")
        assertEquals("hat", focus.focusId)
        focus.restoreFocus(origin)
        assertEquals("hat", focus.focusId)
    }

    @Test
    fun touchRevealAndDisplaySwapDoNotBypassTheCardLevel() {
        val focus = ControllerFocus()
        focus.card("hat", 0f)
        focus.action("wear", "hat", 232f)
        focus.register("top", 10, FocusDisplay.Top) {}
        focus.updateBounds("top", Rect(0f, 0f, 100f, 100f))
        focus.focus("hat")
        focus.hide()
        focus.activate()
        assertEquals("hat", focus.focusId)
        focus.activate()
        assertEquals("wear", focus.focusId)
        assertTrue(focus.swapDisplay())
        assertEquals("top", focus.focusId)
        assertTrue(focus.swapDisplay())
        assertEquals("hat", focus.focusId)
    }
}
