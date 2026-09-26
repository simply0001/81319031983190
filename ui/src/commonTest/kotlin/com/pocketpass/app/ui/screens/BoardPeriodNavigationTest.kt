package com.pocketpass.app.ui.screens

import androidx.compose.ui.geometry.Rect
import com.pocketpass.app.boards.BoardPeriod
import com.pocketpass.app.boards.BoardSort
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.FocusDisplay
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardPeriodNavigationTest {
    @Test
    fun horizontalNavigationStaysInPeriodRowAsItExpands() {
        val focus = ControllerFocus()
        BoardSort.entries.forEachIndexed { index, sort ->
            val id = "board_sort_${sort.wire}"
            focus.register(id, 0, FocusDisplay.Bottom) {}
            focus.updateBounds(id, Rect(index * 100f, 0f, index * 100f + 96f, 50f))
        }
        BoardPeriod.entries.forEach { period ->
            focus.register("board_period_${period.wire}", 0, FocusDisplay.Bottom,
                neighbors = boardPeriodNeighbors(period)) {}
        }

        // During the reveal, the sort row is closer than the next period. Include
        // the settled layout and key repeat, which uses a different geometry path.
        for(top in listOf(1f, 20f, 100f)) {
            BoardPeriod.entries.forEachIndexed { index, period ->
                val left = 8f + index * 94f
                focus.updateBounds("board_period_${period.wire}", Rect(left, top, left + 90f, top + 50f))
            }
            for(held in listOf(false, true)) {
                focus.focus("board_period_today")
                focus.move(FocusDirection.Right, held)
                assertEquals("board_period_week", focus.focusId)
                focus.move(FocusDirection.Right, held)
                assertEquals("board_period_all", focus.focusId)
                focus.move(FocusDirection.Right, held)
                assertEquals("board_period_all", focus.focusId)
                focus.move(FocusDirection.Left, held)
                assertEquals("board_period_week", focus.focusId)
                focus.move(FocusDirection.Left, held)
                assertEquals("board_period_today", focus.focusId)
                focus.move(FocusDirection.Left, held)
                assertEquals("board_period_today", focus.focusId)
                focus.move(FocusDirection.Up, held)
                assertEquals("board_sort_newest", focus.focusId)
            }
        }
    }
}
