package com.pocketpass.app.mii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiiHatColoursTest {
    @Test
    fun rendererLayoutsAreClampedToOneOrTwoColoursAndFavouriteColours() {
        assertEquals(MiiHatColourLayout(colours = 2, colour2 = 10), MiiHatColourLayout.of(colours = 3, colour2 = 42))
        assertEquals(MiiHatColourLayout(colours = 1, colour2 = 10), MiiHatColourLayout.of(colours = 0, colour2 = -1))
        assertEquals(MiiHatColourLayout(colours = 2, colour2 = 4), MiiHatColourLayout.of(colours = 2, colour2 = 4))
        assertTrue(MiiHatColourLayout.of(2, 0).hasSecondColour)
        assertFalse(MiiHatColourLayout.of(1, 0).hasSecondColour)
    }

    @Test
    fun secondColourFallsBackToTheModelDefaultThenWhite() {
        val layouts = listOf(MiiHatColourLayout(), MiiHatColourLayout(colours = 2, colour2 = 3))

        assertEquals(3, MiiAppearance(extHatType = 1).resolvedHatColour2(layouts))
        assertEquals(8, MiiAppearance(extHatType = 1, extHatSecondaryColor = 8).resolvedHatColour2(layouts))
        assertEquals(10, MiiAppearance(extHatType = 5).resolvedHatColour2(layouts))
        assertEquals(10, MiiAppearance(extHatType = -1).resolvedHatColour2(emptyList()))
        assertEquals(null, layouts.forHat(-1))
    }

    @Test
    fun secondColourIsNormalizedAndSentToTheRenderer() {
        assertEquals(11, MiiAppearance(extHatSecondaryColor = 40).normalized().extHatSecondaryColor)
        assertEquals(-1, MiiAppearance(extHatSecondaryColor = -7).normalized().extHatSecondaryColor)
        assertEquals(-1, MiiAppearance().toNativeRendererFields()["hatSecondaryColor"])
    }
}
