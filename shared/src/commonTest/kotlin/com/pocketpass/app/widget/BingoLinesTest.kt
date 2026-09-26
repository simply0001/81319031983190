package com.pocketpass.app.widget

import com.pocketpass.app.domain.model.BingoCell
import kotlin.test.Test
import kotlin.test.assertEquals

class BingoLinesTest {
    private fun card(vararg completed: Int): List<BingoCell> = (0 until 25).map { position ->
        BingoCell(
            position = position,
            slug = "goal-$position",
            text = "Goal $position",
            shortLabel = "G$position",
            completed = position in completed,
            progressCurrent = 0,
            progressTarget = 1,
        )
    }

    @Test
    fun countsRowsColumnsAndDiagonals() {
        assertEquals(0, bingoLines(card()))
        assertEquals(1, bingoLines(card(0, 1, 2, 3, 4)))
        assertEquals(1, bingoLines(card(2, 7, 12, 17, 22)))
        assertEquals(1, bingoLines(card(0, 6, 12, 18, 24)))
        assertEquals(1, bingoLines(card(4, 8, 12, 16, 20)))
        assertEquals(0, bingoLines(card(0, 1, 2, 3)))
        assertEquals(12, bingoLines(card(*(0 until 25).toList().toIntArray())))
    }

    @Test
    fun aMissingCellNeverCompletesALine() {
        val partialCard = card(0, 1, 2, 3, 4).filter { it.position != 4 }

        assertEquals(0, bingoLines(partialCard))
        assertEquals(0, bingoLines(emptyList()))
    }
}
