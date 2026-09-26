package com.pocketpass.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class PuzzleModelsTest {
    private fun progress(
        id: String = "panel:test",
        columns: Int = 4,
        rows: Int = 4,
        ownedPieces: Set<Int> = setOf(5),
        startedAt: Instant? = Instant.fromEpochSeconds(1_000),
        completedAt: Instant? = null,
    ) = PuzzleProgress(
        id = id,
        kind = PuzzleKind.Panel,
        title = "Test",
        slug = "test",
        artwork = PuzzleArtwork.Bundled("test"),
        columns = columns,
        rows = rows,
        ownedPieces = ownedPieces,
        startedAt = startedAt,
        completedAt = completedAt,
    )

    @Test
    fun piecesOutsideTheGridAreRejected() {
        assertFailsWith<IllegalArgumentException> { progress(ownedPieces = setOf(16)) }
        assertFailsWith<IllegalArgumentException> { progress(ownedPieces = setOf(-1)) }
        assertFailsWith<IllegalArgumentException> { progress(columns = 0) }
    }

    @Test
    fun theCurrentIndexMustPointIntoTheCollection() {
        assertFailsWith<IllegalArgumentException> {
            PuzzleCollection(listOf(progress()), currentIndex = 1, piecePriceTokens = 15)
        }
        assertFailsWith<IllegalArgumentException> {
            PuzzleCollection(listOf(progress()), currentIndex = 0, piecePriceTokens = -1)
        }
    }

    @Test
    fun indexHelpersRoundTripOnAFiveByFourGrid() {
        val puzzle = progress(columns = 5, rows = 4)
        for (index in 0 until 20) {
            assertEquals(index, puzzle.indexOf(puzzle.rowOf(index), puzzle.columnOf(index)))
        }
        assertEquals(1, puzzle.rowOf(7))
        assertEquals(2, puzzle.columnOf(7))
        assertEquals(20, puzzle.totalPieces)
    }

    @Test
    fun completionAndLockingFollowThePieces() {
        val complete = progress(ownedPieces = (0 until 16).toSet())
        assertTrue(complete.isComplete)
        assertFalse(complete.isLocked)

        val locked = progress(ownedPieces = emptySet(), startedAt = null)
        assertTrue(locked.isLocked)
        assertFalse(locked.isComplete)

        val started = progress(ownedPieces = setOf(3), startedAt = null)
        assertFalse(started.isLocked)
        assertTrue(started.owns(3))
        assertFalse(started.owns(4))
    }

    @Test
    fun theSeedIsTheOwnPiipWithPieceFive() {
        val seed = PuzzleCollection.seed()
        assertEquals(1, seed.puzzles.size)
        assertEquals(PuzzleKind.OwnPiip, seed.puzzles.single().kind)
        assertEquals(PuzzleArtwork.OwnPortrait, seed.puzzles.single().artwork)
        assertEquals(setOf(PuzzleCollection.OWN_PIIP_START_PIECE), seed.puzzles.single().ownedPieces)
        assertEquals(0, seed.currentIndex)
        assertEquals(1, seed.ownedPieceCount)
        assertEquals(PuzzleCollection.DEFAULT_PIECE_PRICE_TOKENS, seed.piecePriceTokens)
    }

    @Test
    fun browsingStopsAtTheCurrentPuzzleOrTheLastStartedOne() {
        val complete = progress(id = "own_piip", ownedPieces = (0 until 16).toSet(), completedAt = Instant.fromEpochSeconds(2_000))
        val current = progress(id = "panel:one", ownedPieces = setOf(1, 2))
        val locked = progress(id = "panel:two", ownedPieces = emptySet(), startedAt = null)

        val inProgress = PuzzleCollection(listOf(complete, current, locked), currentIndex = 1, piecePriceTokens = 15)
        assertEquals(1, inProgress.lastBrowsableIndex)
        assertEquals(current, inProgress.current)
        assertEquals(18, inProgress.ownedPieceCount)

        val finished = PuzzleCollection(listOf(complete, complete.copy(id = "panel:one"), locked), currentIndex = null, piecePriceTokens = 15)
        assertEquals(1, finished.lastBrowsableIndex)
        assertEquals(null, finished.current)

        assertEquals(null, PuzzleCollection.Empty.lastBrowsableIndex)
    }
}
