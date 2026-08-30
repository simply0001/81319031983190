package com.pocketpass.app.mii

fun MiiAppearance.withTrait(
    field: MiiTraitField,
    index: Int,
): MiiAppearance = when (field) {
    MiiTraitField.FaceType -> copy(faceType = index)
    MiiTraitField.MakeupType -> copy(makeupType = index)
    MiiTraitField.WrinklesType -> copy(wrinklesType = index)
    MiiTraitField.HairType -> copy(hairType = index)
    MiiTraitField.EyebrowType -> copy(eyebrowType = index)
    MiiTraitField.EyeType -> copy(eyeType = index)
    MiiTraitField.NoseType -> copy(noseType = index)
    MiiTraitField.MouthType -> copy(mouthType = index)
    MiiTraitField.MustacheType -> copy(mustacheType = index)
    MiiTraitField.BeardType -> copy(beardType = index)
    MiiTraitField.GlassesType -> copy(glassesType = index)
    MiiTraitField.Gender -> copy(gender = index)
    MiiTraitField.HatType -> copy(
        extHatType = index,
        extHatColor = if (index >= 0 && extHatColor < 0) favoriteColor else extHatColor,
    )
}.normalized()

fun MiiAppearance.withoutLockedHat(ownedHatTypes: Set<Int>): MiiAppearance =
    if (extHatType >= 0 && extHatType !in ownedHatTypes) {
        withTrait(MiiTraitField.HatType, -1)
    } else {
        this
    }

fun MiiAppearance.withColor(
    field: MiiColorField,
    index: Int,
): MiiAppearance = when (field) {
    MiiColorField.Skin -> copy(skinColor = index)
    MiiColorField.Eyes -> copy(eyeColor = index)
    MiiColorField.Favorite -> copy(favoriteColor = index)
    MiiColorField.Hat -> copy(extHatColor = index)
    MiiColorField.FacePaint -> copy(extFacePaintColor = index)
    MiiColorField.Hair -> foldCommon(
        index,
        LEGACY_HAIR_COMMON_COLORS,
        onLegacy = { copy(hairColor = it, extHairColor = -1) },
        onExt = { copy(extHairColor = it) },
    )

    MiiColorField.Eyebrows -> foldCommon(
        index,
        LEGACY_HAIR_COMMON_COLORS,
        onLegacy = { copy(eyebrowColor = it, extEyebrowColor = -1) },
        onExt = { copy(extEyebrowColor = it) },
    )

    MiiColorField.Mouth -> foldCommon(
        index,
        LEGACY_MOUTH_COMMON_COLORS,
        onLegacy = { copy(mouthColor = it, extMouthColor = -1) },
        onExt = { copy(extMouthColor = it) },
    )

    MiiColorField.FacialHair -> foldCommon(
        index,
        LEGACY_HAIR_COMMON_COLORS,
        onLegacy = { copy(facialHairColor = it, extFacialHairColor = -1) },
        onExt = { copy(extFacialHairColor = it) },
    )

    MiiColorField.Glasses -> foldCommon(
        index,
        LEGACY_GLASSES_COMMON_COLORS,
        onLegacy = { copy(glassesColor = it, extGlassesColor = -1) },
        onExt = { copy(extGlassesColor = it) },
    )
}.normalized()

private inline fun MiiAppearance.foldCommon(
    index: Int,
    legacyTable: IntArray,
    onLegacy: (Int) -> MiiAppearance,
    onExt: (Int) -> MiiAppearance,
): MiiAppearance {
    val legacy = legacyTable.indexOf(index)
    return if (legacy >= 0) onLegacy(legacy) else onExt(index)
}

fun MiiAppearance.withAdjustment(
    field: MiiAdjustmentField,
    value: Int,
): MiiAppearance = when (field) {
    MiiAdjustmentField.EyeScale -> copy(eyeScale = value)
    MiiAdjustmentField.EyeVerticalStretch -> copy(eyeVerticalStretch = value)
    MiiAdjustmentField.EyeRotation -> copy(eyeRotation = value)
    MiiAdjustmentField.EyeSpacing -> copy(eyeSpacing = value)
    MiiAdjustmentField.EyeYPosition -> copy(eyeYPosition = value)
    MiiAdjustmentField.EyebrowScale -> copy(eyebrowScale = value)
    MiiAdjustmentField.EyebrowVerticalStretch -> copy(eyebrowVerticalStretch = value)
    MiiAdjustmentField.EyebrowRotation -> copy(eyebrowRotation = value)
    MiiAdjustmentField.EyebrowSpacing -> copy(eyebrowSpacing = value)
    MiiAdjustmentField.EyebrowYPosition -> copy(eyebrowYPosition = value)
    MiiAdjustmentField.NoseScale -> copy(noseScale = value)
    MiiAdjustmentField.NoseYPosition -> copy(noseYPosition = value)
    MiiAdjustmentField.MouthScale -> copy(mouthScale = value)
    MiiAdjustmentField.MouthHorizontalStretch -> copy(mouthHorizontalStretch = value)
    MiiAdjustmentField.MouthYPosition -> copy(mouthYPosition = value)
    MiiAdjustmentField.MustacheScale -> copy(mustacheScale = value)
    MiiAdjustmentField.MustacheYPosition -> copy(mustacheYPosition = value)
    MiiAdjustmentField.GlassesScale -> copy(glassesScale = value)
    MiiAdjustmentField.GlassesYPosition -> copy(glassesYPosition = value)
    MiiAdjustmentField.MoleScale -> copy(moleScale = value)
    MiiAdjustmentField.MoleXPosition -> copy(moleXPosition = value)
    MiiAdjustmentField.MoleYPosition -> copy(moleYPosition = value)
    MiiAdjustmentField.Height -> copy(height = value)
    MiiAdjustmentField.Build -> copy(build = value)
}.normalized()

fun MiiAppearance.withToggle(
    field: MiiToggleField,
    enabled: Boolean,
): MiiAppearance = when (field) {
    MiiToggleField.FlipHair -> copy(flipHair = enabled)
    MiiToggleField.Mole -> copy(moleEnabled = enabled)
}

fun MiiAppearance.toggleValue(field: MiiToggleField): Boolean = when (field) {
    MiiToggleField.FlipHair -> flipHair
    MiiToggleField.Mole -> moleEnabled
}

fun MiiAppearance.traitValue(field: MiiTraitField): Int = when (field) {
    MiiTraitField.FaceType -> faceType
    MiiTraitField.MakeupType -> makeupType
    MiiTraitField.WrinklesType -> wrinklesType
    MiiTraitField.HairType -> hairType
    MiiTraitField.EyebrowType -> eyebrowType
    MiiTraitField.EyeType -> eyeType
    MiiTraitField.NoseType -> noseType
    MiiTraitField.MouthType -> mouthType
    MiiTraitField.MustacheType -> mustacheType
    MiiTraitField.BeardType -> beardType
    MiiTraitField.GlassesType -> glassesType
    MiiTraitField.Gender -> gender
    MiiTraitField.HatType -> extHatType
}

fun MiiAppearance.colorValue(field: MiiColorField): Int = when (field) {
    MiiColorField.Skin -> skinColor
    MiiColorField.Eyes -> eyeColor
    MiiColorField.Favorite -> favoriteColor
    MiiColorField.Hat -> extHatColor
    MiiColorField.FacePaint -> extFacePaintColor
    MiiColorField.Hair -> hairCommonColor
    MiiColorField.Eyebrows -> eyebrowCommonColor
    MiiColorField.Mouth -> mouthCommonColor
    MiiColorField.FacialHair -> facialHairCommonColor
    MiiColorField.Glasses -> glassesCommonColor
}

fun MiiAppearance.adjustmentValue(field: MiiAdjustmentField): Int = when (field) {
    MiiAdjustmentField.EyeScale -> eyeScale
    MiiAdjustmentField.EyeVerticalStretch -> eyeVerticalStretch
    MiiAdjustmentField.EyeRotation -> eyeRotation
    MiiAdjustmentField.EyeSpacing -> eyeSpacing
    MiiAdjustmentField.EyeYPosition -> eyeYPosition
    MiiAdjustmentField.EyebrowScale -> eyebrowScale
    MiiAdjustmentField.EyebrowVerticalStretch -> eyebrowVerticalStretch
    MiiAdjustmentField.EyebrowRotation -> eyebrowRotation
    MiiAdjustmentField.EyebrowSpacing -> eyebrowSpacing
    MiiAdjustmentField.EyebrowYPosition -> eyebrowYPosition
    MiiAdjustmentField.NoseScale -> noseScale
    MiiAdjustmentField.NoseYPosition -> noseYPosition
    MiiAdjustmentField.MouthScale -> mouthScale
    MiiAdjustmentField.MouthHorizontalStretch -> mouthHorizontalStretch
    MiiAdjustmentField.MouthYPosition -> mouthYPosition
    MiiAdjustmentField.MustacheScale -> mustacheScale
    MiiAdjustmentField.MustacheYPosition -> mustacheYPosition
    MiiAdjustmentField.GlassesScale -> glassesScale
    MiiAdjustmentField.GlassesYPosition -> glassesYPosition
    MiiAdjustmentField.MoleScale -> moleScale
    MiiAdjustmentField.MoleXPosition -> moleXPosition
    MiiAdjustmentField.MoleYPosition -> moleYPosition
    MiiAdjustmentField.Height -> height
    MiiAdjustmentField.Build -> build
}

val LEGACY_GLASSES_COMMON_COLORS = intArrayOf(8, 14, 15, 16, 17, 18)
val LEGACY_HAIR_COMMON_COLORS = intArrayOf(8, 1, 2, 3, 4, 5, 6, 7)
val LEGACY_MOUTH_COMMON_COLORS = intArrayOf(19, 20, 21, 22, 23)

private fun resolveCommon(ext: Int, legacyIndex: Int, legacyTable: IntArray): Int =
    if (ext >= 0) ext else legacyTable[legacyIndex.coerceIn(0, legacyTable.lastIndex)]

val MiiAppearance.glassesCommonColor: Int
    get() = resolveCommon(extGlassesColor, glassesColor, LEGACY_GLASSES_COMMON_COLORS)

val MiiAppearance.hairCommonColor: Int
    get() = resolveCommon(extHairColor, hairColor, LEGACY_HAIR_COMMON_COLORS)

val MiiAppearance.eyebrowCommonColor: Int
    get() = resolveCommon(extEyebrowColor, eyebrowColor, LEGACY_HAIR_COMMON_COLORS)

val MiiAppearance.mouthCommonColor: Int
    get() = resolveCommon(extMouthColor, mouthColor, LEGACY_MOUTH_COMMON_COLORS)

val MiiAppearance.facialHairCommonColor: Int
    get() = resolveCommon(extFacialHairColor, facialHairColor, LEGACY_HAIR_COMMON_COLORS)
