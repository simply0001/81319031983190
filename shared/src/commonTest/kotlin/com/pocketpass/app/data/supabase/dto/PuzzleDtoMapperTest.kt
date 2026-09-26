package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.domain.model.PuzzleArtwork
import com.pocketpass.app.domain.model.PuzzleCollection
import com.pocketpass.app.domain.model.PuzzleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PuzzleDtoMapperTest {
    private val ownPiip = PuzzleDto(
        index = 0,
        kind = "own_piip",
        puzzleKey = "own_piip",
        columns = 4,
        rows = 4,
        totalPieces = 16,
        ownedPieces = listOf(5, 5, 16, -2),
    )

    private val panel = PuzzleDto(
        index = 1,
        kind = "panel",
        puzzleKey = "panel:abc",
        panelId = "abc",
        slug = "pocki_happy",
        title = "Pocki Happy",
        imagePath = "panels/pocki_happy.png",
        columns = 3,
        rows = 5,
        totalPieces = 15,
        ownedPieces = listOf(2, 7),
        startedAt = "2026-09-07T07:36:57.123456+00:00",
    )

    @Test
    fun theOwnPiipUsesThePortraitAndPanelsGetPublicUrls() {
        val collection = PuzzleCollectionDto(
            currentIndex = 1,
            piecePrice = 15,
            piecesOwnedTotal = 3,
            puzzles = listOf(ownPiip, panel),
        ).toDomain { path -> "https://cdn.test/$path" }

        val own = collection.puzzles[0]
        assertEquals(PuzzleKind.OwnPiip, own.kind)
        assertEquals(PuzzleArtwork.OwnPortrait, own.artwork)
        assertEquals(PuzzleCollection.OWN_PIIP_TITLE, own.title)
        assertEquals(setOf(5), own.ownedPieces)

        val pocki = collection.puzzles[1]
        assertEquals(PuzzleKind.Panel, pocki.kind)
        assertEquals(PuzzleArtwork.Remote("https://cdn.test/panels/pocki_happy.png"), pocki.artwork)
        assertEquals("panels/pocki_happy.png", pocki.imagePath)
        assertEquals("Pocki Happy", pocki.title)
        assertEquals(3, pocki.columns)
        assertEquals(5, pocki.rows)
        assertEquals(setOf(2, 7), pocki.ownedPieces)
        assertNotNull(pocki.startedAt)
        assertNull(pocki.completedAt)

        assertEquals(1, collection.currentIndex)
        assertEquals(15, collection.piecePriceTokens)
        assertEquals(3, collection.ownedPieceCount)
    }

    @Test
    fun puzzlesAreOrderedByIndexAndAStrayCurrentIndexIsDropped() {
        val collection = PuzzleCollectionDto(
            currentIndex = 7,
            piecePrice = 15,
            puzzles = listOf(panel, ownPiip),
        ).toDomain { it }

        assertEquals(listOf("own_piip", "panel:abc"), collection.puzzles.map { it.id })
        assertNull(collection.currentIndex)
    }

    @Test
    fun aPanelWithoutArtworkFallsBackToItsSlug() {
        val puzzle = panel.copy(imagePath = null, title = " ").toDomain { it }
        assertEquals(PuzzleArtwork.Bundled("pocki_happy"), puzzle.artwork)
        assertEquals("Puzzle", puzzle.title)
        assertNull(puzzle.imagePath)
    }
}
