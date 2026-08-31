package com.pocketpass.app.mii

import kotlin.test.Test
import kotlin.test.assertEquals

class MiiRendererFieldsTest {
    @Test
    fun defaultAppearanceMapsEveryPinnedRendererField() {
        val fields = MiiAppearance().toNativeRendererFields()

        assertEquals(PINNED_APPEARANCE_FIELDS, fields.keys)
        assertEquals(
            mapOf(
                "gender" to 0,
                "favoriteColor" to 0,
                "build" to 64,
                "height" to 64,
                "facelineType" to 0,
                "facelineColor" to 0,
                "facelineWrinkle" to 0,
                "facelineMake" to 0,
                "hairType" to 33,
                "hairColor" to 1,
                "hairFlip" to 0,
                "eyeType" to 2,
                "eyeColor" to 8,
                "eyeScale" to 4,
                "eyeAspect" to 3,
                "eyeRotate" to 4,
                "eyeX" to 2,
                "eyeY" to 12,
                "eyebrowType" to 6,
                "eyebrowColor" to 1,
                "eyebrowScale" to 4,
                "eyebrowAspect" to 3,
                "eyebrowRotate" to 6,
                "eyebrowX" to 2,
                "eyebrowY" to 10,
                "noseType" to 1,
                "noseScale" to 4,
                "noseY" to 9,
                "mouthType" to 23,
                "mouthColor" to 19,
                "mouthScale" to 4,
                "mouthAspect" to 3,
                "mouthY" to 13,
                "mustacheType" to 0,
                "mustacheScale" to 4,
                "mustacheY" to 10,
                "beardType" to 0,
                "beardColor" to 8,
                "glassType" to 0,
                "glassColor" to 8,
                "glassScale" to 4,
                "glassY" to 10,
                "moleType" to 0,
                "moleScale" to 4,
                "moleX" to 2,
                "moleY" to 20,
                "hatType" to -1,
                "hatFavoriteColor" to -1,
                "hatCommonColor" to -1,
                "facePaintColor" to -1,
            ),
            fields,
        )
    }

    @Test
    fun logicalPaletteIndexesUsePinnedVer3ColorTables() {
        val fields = MiiAppearance(
            hairColor = 7,
            eyebrowColor = 6,
            facialHairColor = 5,
            eyeColor = 5,
            mouthColor = 4,
            glassesColor = 5,
            flipHair = true,
            moleEnabled = true,
            extHatType = 8,
            extHatColor = 11,
            extFacePaintColor = 10,
        ).toNativeRendererFields()

        assertEquals(7, fields["hairColor"])
        assertEquals(6, fields["eyebrowColor"])
        assertEquals(5, fields["beardColor"])
        assertEquals(13, fields["eyeColor"])
        assertEquals(23, fields["mouthColor"])
        assertEquals(18, fields["glassColor"])
        assertEquals(1, fields["hairFlip"])
        assertEquals(1, fields["moleType"])
        assertEquals(8, fields["hatType"])
        assertEquals(11, fields["hatFavoriteColor"])
        assertEquals(-1, fields["hatCommonColor"])
        assertEquals(10, fields["facePaintColor"])
    }

    @Test
    fun commonGlassesColorOverridesTheLegacyGlassesPalette() {
        val common = MiiAppearance(glassesColor = 2, extGlassesColor = 57).toNativeRendererFields()
        val legacy = MiiAppearance(glassesColor = 2, extGlassesColor = -1).toNativeRendererFields()
        val clamped = MiiAppearance(glassesColor = 2, extGlassesColor = 500).toNativeRendererFields()

        assertEquals(57, common["glassColor"])
        assertEquals(15, legacy["glassColor"])
        assertEquals(99, clamped["glassColor"])
    }

    @Test
    fun mappingNormalizesUntrustedAppearanceBeforeIndexingTables() {
        val fields = MiiAppearance(
            hairColor = Int.MAX_VALUE,
            eyeColor = Int.MIN_VALUE,
            glassesType = 500,
            eyebrowYPosition = -100,
            extHatType = 100,
            extHatColor = -100,
        ).toNativeRendererFields()

        assertEquals(7, fields["hairColor"])
        assertEquals(8, fields["eyeColor"])
        assertEquals(19, fields["glassType"])
        assertEquals(3, fields["eyebrowY"])
        assertEquals(9, fields["hatType"])
        assertEquals(-1, fields["hatFavoriteColor"])
    }

    private companion object {
        val PINNED_APPEARANCE_FIELDS = setOf(
            "beardColor",
            "beardType",
            "build",
            "eyeAspect",
            "eyeColor",
            "eyeRotate",
            "eyeScale",
            "eyeType",
            "eyeX",
            "eyeY",
            "eyebrowAspect",
            "eyebrowColor",
            "eyebrowRotate",
            "eyebrowScale",
            "eyebrowType",
            "eyebrowX",
            "eyebrowY",
            "facelineColor",
            "facelineMake",
            "facelineType",
            "facelineWrinkle",
            "facePaintColor",
            "favoriteColor",
            "gender",
            "glassColor",
            "glassScale",
            "glassType",
            "glassY",
            "hairColor",
            "hairFlip",
            "hairType",
            "hatCommonColor",
            "hatFavoriteColor",
            "hatType",
            "height",
            "moleScale",
            "moleType",
            "moleX",
            "moleY",
            "mouthAspect",
            "mouthColor",
            "mouthScale",
            "mouthType",
            "mouthY",
            "mustacheScale",
            "mustacheType",
            "mustacheY",
            "noseScale",
            "noseType",
            "noseY",
        )
    }
}
