package com.pocketpass.app.mii

// The renderer's field vocabulary; shared so the Android and iOS render adapters
// cannot drift apart.
fun MiiAppearance.toNativeRendererFields(): Map<String, Int> {
    val appearance = normalized()
    return linkedMapOf(
        "gender" to appearance.gender,
        "favoriteColor" to appearance.favoriteColor,
        "build" to appearance.build,
        "height" to appearance.height,
        "facelineType" to appearance.faceType,
        "facelineColor" to appearance.skinColor,
        "facelineWrinkle" to appearance.wrinklesType,
        "facelineMake" to appearance.makeupType,
        "hairType" to appearance.hairType,
        "hairColor" to appearance.hairCommonColor,
        "hairFlip" to appearance.flipHair.toNativeFlag(),
        "eyeType" to appearance.eyeType,
        "eyeColor" to VER3_EYE_COLORS[appearance.eyeColor],
        "eyeScale" to appearance.eyeScale,
        "eyeAspect" to appearance.eyeVerticalStretch,
        "eyeRotate" to appearance.eyeRotation,
        "eyeX" to appearance.eyeSpacing,
        "eyeY" to appearance.eyeYPosition,
        "eyebrowType" to appearance.eyebrowType,
        "eyebrowColor" to appearance.eyebrowCommonColor,
        "eyebrowScale" to appearance.eyebrowScale,
        "eyebrowAspect" to appearance.eyebrowVerticalStretch,
        "eyebrowRotate" to appearance.eyebrowRotation,
        "eyebrowX" to appearance.eyebrowSpacing,
        "eyebrowY" to appearance.eyebrowYPosition,
        "noseType" to appearance.noseType,
        "noseScale" to appearance.noseScale,
        "noseY" to appearance.noseYPosition,
        "mouthType" to appearance.mouthType,
        "mouthColor" to appearance.mouthCommonColor,
        "mouthScale" to appearance.mouthScale,
        "mouthAspect" to appearance.mouthHorizontalStretch,
        "mouthY" to appearance.mouthYPosition,
        "mustacheType" to appearance.mustacheType,
        "mustacheScale" to appearance.mustacheScale,
        "mustacheY" to appearance.mustacheYPosition,
        "beardType" to appearance.beardType,
        "beardColor" to appearance.facialHairCommonColor,
        "glassType" to appearance.glassesType,
        "glassColor" to appearance.glassesCommonColor,
        "glassScale" to appearance.glassesScale,
        "glassY" to appearance.glassesYPosition,
        "moleType" to appearance.moleEnabled.toNativeFlag(),
        "moleScale" to appearance.moleScale,
        "moleX" to appearance.moleXPosition,
        "moleY" to appearance.moleYPosition,
        "hatType" to appearance.extHatType,
        "hatFavoriteColor" to appearance.extHatColor,
        "hatCommonColor" to -1,
        "facePaintColor" to appearance.extFacePaintColor,
    )
}

private fun Boolean.toNativeFlag(): Int = if (this) 1 else 0

private val VER3_EYE_COLORS = intArrayOf(8, 9, 10, 11, 12, 13)
