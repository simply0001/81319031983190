package com.pocketpass.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetDesignTest {
    private val design = WidgetDesign(
        id = "d1",
        name = "Morning",
        size = WidgetSize.Wide,
        hero = WidgetBlock.Tokens,
        tiles = listOf(WidgetBlock.StepsToday, null, WidgetBlock.FriendsOnline),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
    )

    @Test
    fun documentSurvivesAJsonRoundTrip() {
        val document = WidgetDesignDocument(designs = listOf(design))

        val decoded = WidgetDesignDocument.decode(document.encode())

        assertEquals(document, decoded)
        assertNull(WidgetDesignDocument.decode("nope"))
    }

    @Test
    fun normalizingFitsTheTilesToTheSizeAndDropsMisplacedBlocks() {
        val messy = design.copy(
            size = WidgetSize.Small,
            hero = WidgetBlock.LastPass,
            tiles = listOf(WidgetBlock.Profile, WidgetBlock.BingoLines, WidgetBlock.Tokens, WidgetBlock.Tokens),
        )

        val clean = messy.normalized()

        assertNull(clean.hero)
        assertEquals(listOf(null, WidgetBlock.BingoLines, WidgetBlock.Tokens), clean.tiles)
        assertEquals(WidgetSize.Small.tileCount, clean.tiles.size)
    }

    @Test
    fun changingSizeKeepsTheLeadingTiles() {
        val tall = design.withSize(WidgetSize.Tall, now = 5L)
        val small = tall.withSize(WidgetSize.Small, now = 6L)

        assertEquals(listOf(WidgetBlock.StepsToday, null, WidgetBlock.FriendsOnline, null), tall.tiles)
        assertEquals(listOf(WidgetBlock.StepsToday, null, WidgetBlock.FriendsOnline), small.tiles)
        assertEquals(emptyList(), small.withSize(WidgetSize.Mini, 7L).tiles)
        assertEquals(6L, small.updatedAtEpochMillis)
    }

    @Test
    fun slotEditsRespectWhatEachSlotCanHold() {
        assertEquals(WidgetBlock.RecentPeople, design.withHero(WidgetBlock.RecentPeople, 3L).hero)
        assertNull(design.withHero(WidgetBlock.LastPass, 3L).hero)
        assertEquals(WidgetBlock.LastPass, design.withTile(1, WidgetBlock.LastPass, 3L).tiles[1])
        assertNull(design.withTile(1, WidgetBlock.Profile, 3L).tiles[1])
        assertEquals(design, design.withTile(7, WidgetBlock.Tokens, 3L))
    }

    @Test
    fun namesAreTrimmedCappedAndNeverBlank() {
        assertEquals("Morning", design.withName("   ", 3L).name)
        assertEquals("Evening", design.withName("  Evening ", 3L).name)
        assertEquals(WidgetDesign.MAX_NAME_LENGTH, design.withName("x".repeat(80), 3L).name.length)
        assertEquals("Tokens, Steps, Online", design.summary())
        assertEquals("Empty", design.copy(hero = null, tiles = emptyList()).summary())
    }

    @Test
    fun bindingsClaimThePendingDesignOnlyForTheRightSizeAndWindow() {
        val pending = WidgetBindings().withPending("d1", WidgetSize.Wide, nowEpochMillis = 1_000L)

        assertEquals("d1", pending.pendingFor(WidgetSize.Wide, 1_000L + 60_000L))
        assertNull(pending.pendingFor(WidgetSize.Small, 1_000L + 60_000L))
        assertNull(pending.pendingFor(WidgetSize.Wide, 1_000L + WidgetBindings.PENDING_WINDOW_MILLIS + 1L))
        assertNull(pending.pendingFor(null, 1_000L))

        val bound = pending.bind(7, "d1").clearPending()
        assertEquals("d1", bound.designIdFor(7))
        assertNull(bound.pendingDesignId)
        assertTrue(bound.unbind(listOf(7)).byAppWidgetId.isEmpty())
        assertEquals(bound, WidgetBindings.decode(bound.encode()))
    }

    @Test
    fun newDesignsTakeTheNextFreeNumber() {
        val existing = listOf(design.copy(name = "Widget 1"), design.copy(id = "d2", name = "Widget 3"))

        assertEquals("Widget 4", WidgetDesignsStateHolder.nextName(existing))
        assertEquals("Widget 1", WidgetDesignsStateHolder.nextName(emptyList()))
    }
}
