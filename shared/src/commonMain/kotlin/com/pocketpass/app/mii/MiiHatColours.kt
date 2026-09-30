package com.pocketpass.app.mii

data class MiiHatColourLayout(
    val colours: Int = 1,
    val colour2: Int = WHITE_FAVORITE_COLOR,
) {
    val hasSecondColour: Boolean
        get() = colours >= 2

    companion object {
        const val WHITE_FAVORITE_COLOR = 10
        private const val FAVORITE_COLOR_COUNT = 12

        fun of(colours: Int, colour2: Int): MiiHatColourLayout = MiiHatColourLayout(
            colours = if (colours >= 2) 2 else 1,
            colour2 = colour2.takeIf { it in 0 until FAVORITE_COLOR_COUNT } ?: WHITE_FAVORITE_COLOR,
        )
    }
}

fun List<MiiHatColourLayout>.forHat(hatType: Int): MiiHatColourLayout? =
    if (hatType < 0) null else getOrNull(hatType)

fun MiiAppearance.resolvedHatColour2(layouts: List<MiiHatColourLayout>): Int =
    extHatSecondaryColor.takeIf { it >= 0 }
        ?: layouts.forHat(extHatType)?.colour2
        ?: MiiHatColourLayout.WHITE_FAVORITE_COLOR
