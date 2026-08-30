package com.pocketpass.app.domain.model

data class BingoCell(
    val position: Int,
    val slug: String,
    val text: String,
    val shortLabel: String,
    val completed: Boolean,
    val progressCurrent: Int,
    val progressTarget: Int,
) {
    init {
        require(position in 0..24) { "Bingo position must be on the card" }
        require(progressTarget >= 1) { "Bingo target must be positive" }
    }
}
