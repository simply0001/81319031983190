package com.pocketpass.app.mii

import com.pocketpass.app.ui.mii.MiiEditorColors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiiEditorCatalogContractTest {
    @Test
    fun traitCountsMatchPinnedUpstreamIconCatalog() {
        val counts = MiiEditorCatalog.categories
            .flatMap(MiiCategoryDescriptor::traits)
            .associate { it.field to it.optionCount }

        assertEquals(12, counts[MiiTraitField.FaceType])
        assertEquals(12, counts[MiiTraitField.MakeupType])
        assertEquals(12, counts[MiiTraitField.WrinklesType])
        assertEquals(132, counts[MiiTraitField.HairType])
        assertEquals(24, counts[MiiTraitField.EyebrowType])
        assertEquals(60, counts[MiiTraitField.EyeType])
        assertEquals(18, counts[MiiTraitField.NoseType])
        assertEquals(36, counts[MiiTraitField.MouthType])
        assertEquals(6, counts[MiiTraitField.MustacheType])
        assertEquals(6, counts[MiiTraitField.BeardType])
        assertEquals(20, counts[MiiTraitField.GlassesType])
        assertEquals(10, counts[MiiTraitField.HatType])
    }

    @Test
    fun gatedAdjustmentsBelongToTheirCategoryToggle() {
        MiiEditorCatalog.categories.forEach { category ->
            category.adjustments.mapNotNull { it.gate }.forEach { gate ->
                assertTrue(
                    "${category.category} gates ${gate} without offering the toggle",
                    gate in category.toggles,
                )
            }
        }
    }

    @Test
    fun paletteFieldsOfferTheWholeCommonColorTableAndHaveSwatchesForIt() {
        val descriptors = MiiEditorCatalog.categories.flatMap(MiiCategoryDescriptor::colors)

        descriptors.filter { it.field.isPalette }.forEach { descriptor ->
            assertEquals(
                "${descriptor.field} should index the whole common colour table",
                100,
                descriptor.optionCount,
            )
        }
        assertEquals(100, MiiEditorColors.common.size)

        val skin = descriptors.single { it.field == MiiColorField.Skin }
        assertEquals(10, skin.optionCount)
        assertEquals(skin.optionCount, MiiEditorColors.skin.size)
    }

    @Test
    fun everyCatalogBoundaryRoundTripsThroughNormalizedAppearance() {
        MiiEditorCatalog.categories.forEach { category ->
            category.traits.forEach { descriptor ->
                assertEquals(
                    descriptor.optionCount - 1,
                    MiiAppearance()
                        .withTrait(descriptor.field, descriptor.optionCount - 1)
                        .traitValue(descriptor.field),
                )
            }
            category.colors.forEach { descriptor ->
                assertEquals(
                    descriptor.optionCount - 1,
                    MiiAppearance()
                        .withColor(descriptor.field, descriptor.optionCount - 1)
                        .colorValue(descriptor.field),
                )
            }
            category.adjustments.forEach { descriptor ->
                assertEquals(
                    descriptor.minimum,
                    MiiAppearance()
                        .withAdjustment(descriptor.field, descriptor.minimum)
                        .adjustmentValue(descriptor.field),
                )
                assertEquals(
                    descriptor.maximum,
                    MiiAppearance()
                        .withAdjustment(descriptor.field, descriptor.maximum)
                        .adjustmentValue(descriptor.field),
                )
            }
        }
    }

    @Test
    fun persistedPagesAreClampedToAvailablePinnedTraits() = runTest {
        val persistence = InMemoryMiiEditorPersistence()

        persistence.save(
            "account",
            MiiPersistedEditorSession(
                traitPageByCategory = mapOf(
                    MiiCategory.Face to 100,
                    MiiCategory.Hair to 100,
                    MiiCategory.Eyes to -5,
                ),
            ),
        )

        assertEquals(
            mapOf(
                MiiCategory.Face to 0,
                MiiCategory.Hair to 10,
                MiiCategory.Eyes to 0,
            ),
            persistence.load("account")?.traitPageByCategory,
        )
    }

    @Test
    fun bodyControlsBelongOnlyToTheBodyCategory() {
        val body = MiiEditorCatalog.descriptor(MiiCategory.Body)

        assertEquals(listOf(MiiTraitField.Gender), body.traits.map { it.field })
        assertEquals(listOf(MiiColorField.Favorite), body.colors.map { it.field })
        assertEquals(
            listOf(MiiAdjustmentField.Height, MiiAdjustmentField.Build),
            body.adjustments.map { it.field },
        )

        val otherCategories = MiiEditorCatalog.categories.filter {
            it.category != MiiCategory.Body
        }
        assertEquals(
            emptyList<MiiAdjustmentField>(),
            otherCategories
                .flatMap { it.adjustments }
                .map { it.field }
                .filter {
                    it == MiiAdjustmentField.Height ||
                        it == MiiAdjustmentField.Build
                },
        )
    }

    @Test
    fun hatLikeHairstylesAreHiddenFromThePicker() {
        val hair = MiiEditorCatalog.trait(MiiCategory.Hair, MiiTraitField.HairType)!!

        assertEquals(setOf(34, 57), hair.hiddenOptions)
        assertTrue(hair.hiddenOptions.all { it in 0 until hair.optionCount })
    }
}
