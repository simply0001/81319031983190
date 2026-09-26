package com.pocketpass.app.widget

import com.pocketpass.app.domain.model.BingoCell

private const val BINGO_SIDE = 5

fun bingoLines(cells: List<BingoCell>): Int {
    val done = BooleanArray(BINGO_SIDE * BINGO_SIDE)
    cells.forEach { cell -> if (cell.completed) done[cell.position] = true }
    fun line(positions: List<Int>): Int = if (positions.all { done[it] }) 1 else 0
    var lines = 0
    for (index in 0 until BINGO_SIDE) {
        lines += line(List(BINGO_SIDE) { index * BINGO_SIDE + it })
        lines += line(List(BINGO_SIDE) { it * BINGO_SIDE + index })
    }
    lines += line(List(BINGO_SIDE) { it * BINGO_SIDE + it })
    lines += line(List(BINGO_SIDE) { it * BINGO_SIDE + (BINGO_SIDE - 1 - it) })
    return lines
}
