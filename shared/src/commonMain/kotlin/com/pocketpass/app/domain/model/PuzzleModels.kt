package com.pocketpass.app.domain.model

import kotlin.time.Instant

enum class PuzzleKind {
    OwnPiip,
    Panel,
}

sealed interface PuzzleArtwork {
    data object OwnPortrait : PuzzleArtwork

    data class File(val path: String) : PuzzleArtwork

    data class Bundled(val key: String) : PuzzleArtwork

    data class Remote(val url: String) : PuzzleArtwork
}

data class PuzzleProgress(
    val id: String,
    val kind: PuzzleKind,
    val title: String,
    val slug: String?,
    val artwork: PuzzleArtwork,
    val columns: Int,
    val rows: Int,
    val ownedPieces: Set<Int>,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val completedByHandover: Boolean = false,
    val imagePath: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Puzzle id cannot be blank" }
        require(columns in 1..MAX_GRID_SIDE && rows in 1..MAX_GRID_SIDE) {
            "Puzzle grid must be between 1x1 and ${MAX_GRID_SIDE}x$MAX_GRID_SIDE"
        }
        require(ownedPieces.all { it in 0 until columns * rows }) { "Puzzle pieces must be inside the grid" }
    }

    val totalPieces: Int
        get() = columns * rows

    val ownedCount: Int
        get() = ownedPieces.size

    val isComplete: Boolean
        get() = ownedCount >= totalPieces

    val isLocked: Boolean
        get() = startedAt == null && ownedPieces.isEmpty()

    fun owns(index: Int): Boolean = index in ownedPieces

    fun rowOf(index: Int): Int = index / columns

    fun columnOf(index: Int): Int = index % columns

    fun indexOf(row: Int, column: Int): Int = row * columns + column

    companion object {
        const val MAX_GRID_SIDE = 12
    }
}

data class PuzzleCollection(
    val puzzles: List<PuzzleProgress>,
    val currentIndex: Int?,
    val piecePriceTokens: Int,
) {
    init {
        require(currentIndex == null || currentIndex in puzzles.indices) {
            "The current puzzle must be part of the collection"
        }
        require(piecePriceTokens >= 0) { "A piece cannot cost negative tokens" }
    }

    val current: PuzzleProgress?
        get() = currentIndex?.let(puzzles::get)

    val ownedPieceCount: Int
        get() = puzzles.sumOf(PuzzleProgress::ownedCount)

    val lastBrowsableIndex: Int?
        get() = currentIndex ?: puzzles.indexOfLast { !it.isLocked }.takeIf { it >= 0 }

    companion object {
        const val OWN_PIIP_ID = "own_piip"
        const val OWN_PIIP_TITLE = "Your Piip"
        const val OWN_PIIP_START_PIECE = 5
        const val DEFAULT_PIECE_PRICE_TOKENS = 15

        val Empty = PuzzleCollection(emptyList(), null, DEFAULT_PIECE_PRICE_TOKENS)

        fun seed(piecePriceTokens: Int = DEFAULT_PIECE_PRICE_TOKENS): PuzzleCollection = PuzzleCollection(
            puzzles = listOf(
                PuzzleProgress(
                    id = OWN_PIIP_ID,
                    kind = PuzzleKind.OwnPiip,
                    title = OWN_PIIP_TITLE,
                    slug = null,
                    artwork = PuzzleArtwork.OwnPortrait,
                    columns = 4,
                    rows = 4,
                    ownedPieces = setOf(OWN_PIIP_START_PIECE),
                    startedAt = null,
                    completedAt = null,
                ),
            ),
            currentIndex = 0,
            piecePriceTokens = piecePriceTokens,
        )
    }
}

data class BuyPuzzlePieceCommand(
    val accountId: UserId,
    val priceTokens: Int,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val requestedAt: Instant,
) {
    init {
        require(priceTokens >= 0) { "A piece cannot cost negative tokens" }
    }
}

sealed interface PuzzlePiecePurchaseOutcome {
    data class Completed(
        val puzzleId: String,
        val pieceIndex: Int,
        val balance: Int,
        val puzzleCompleted: Boolean,
        val nextPuzzleId: String?,
    ) : PuzzlePiecePurchaseOutcome

    data class Rejected(val reason: PuzzlePurchaseRejection) : PuzzlePiecePurchaseOutcome
}

enum class PuzzlePurchaseRejection(val code: String) {
    InsufficientTokens("INSUFFICIENT_TOKENS"),
    CollectionComplete("COLLECTION_COMPLETE"),
}
