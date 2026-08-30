package com.pocketpass.app.mii

enum class MiiCategory {
    Face,
    Hair,
    Eyebrows,
    Eyes,
    Nose,
    Mouth,
    Glasses,
    Body,
}

enum class MiiTraitField {
    FaceType,
    MakeupType,
    WrinklesType,
    HairType,
    EyebrowType,
    EyeType,
    NoseType,
    MouthType,
    MustacheType,
    BeardType,
    GlassesType,
    Gender,
    HatType,
}

enum class MiiColorField {
    Skin,
    Hair,
    Eyebrows,
    Eyes,
    Mouth,
    FacialHair,
    Glasses,
    Favorite,
    Hat,
    FacePaint,
}

val MiiColorField.isPalette: Boolean
    get() = when (this) {
        MiiColorField.Hair,
        MiiColorField.Eyebrows,
        MiiColorField.Mouth,
        MiiColorField.FacialHair,
        MiiColorField.Glasses,
        -> true

        else -> false
    }

enum class MiiAdjustmentField {
    EyeScale,
    EyeVerticalStretch,
    EyeRotation,
    EyeSpacing,
    EyeYPosition,
    EyebrowScale,
    EyebrowVerticalStretch,
    EyebrowRotation,
    EyebrowSpacing,
    EyebrowYPosition,
    NoseScale,
    NoseYPosition,
    MouthScale,
    MouthHorizontalStretch,
    MouthYPosition,
    MustacheScale,
    MustacheYPosition,
    GlassesScale,
    GlassesYPosition,
    MoleScale,
    MoleXPosition,
    MoleYPosition,
    Height,
    Build,
}

enum class MiiToggleField {
    FlipHair,
    Mole,
}

data class MiiTraitDescriptor(
    val field: MiiTraitField,
    val optionCount: Int,
    val figmaPrimary: Boolean = false,
    val colorField: MiiColorField? = null,
    val optional: Boolean = false,
    val hiddenOptions: Set<Int> = emptySet(),
)

data class MiiColorDescriptor(
    val field: MiiColorField,
    val optionCount: Int,
    val figmaPrimary: Boolean = false,
)

data class MiiAdjustmentDescriptor(
    val field: MiiAdjustmentField,
    val minimum: Int,
    val maximum: Int,
    val defaultValue: Int,
    val gate: MiiToggleField? = null,
)

data class MiiCategoryDescriptor(
    val category: MiiCategory,
    val traits: List<MiiTraitDescriptor> = emptyList(),
    val colors: List<MiiColorDescriptor> = emptyList(),
    val adjustments: List<MiiAdjustmentDescriptor> = emptyList(),
    val toggles: List<MiiToggleField> = emptyList(),
)

private val HAT_HAIRSTYLES = setOf(34, 57)

object MiiEditorCatalog {
    val categories: List<MiiCategoryDescriptor> = listOf(
        MiiCategoryDescriptor(
            category = MiiCategory.Face,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.FaceType, 12, figmaPrimary = true),
                MiiTraitDescriptor(MiiTraitField.MakeupType, 12),
                MiiTraitDescriptor(MiiTraitField.WrinklesType, 12),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Skin, 10, figmaPrimary = true),
                MiiColorDescriptor(MiiColorField.FacePaint, 12),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(
                    MiiAdjustmentField.MoleScale,
                    0,
                    8,
                    4,
                    gate = MiiToggleField.Mole,
                ),
                MiiAdjustmentDescriptor(
                    MiiAdjustmentField.MoleXPosition,
                    0,
                    16,
                    2,
                    gate = MiiToggleField.Mole,
                ),
                MiiAdjustmentDescriptor(
                    MiiAdjustmentField.MoleYPosition,
                    0,
                    30,
                    20,
                    gate = MiiToggleField.Mole,
                ),
            ),
            toggles = listOf(MiiToggleField.Mole),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Hair,
            traits = listOf(
                MiiTraitDescriptor(
                    MiiTraitField.HairType,
                    132,
                    figmaPrimary = true,
                    colorField = MiiColorField.Hair,
                    hiddenOptions = HAT_HAIRSTYLES,
                ),
                MiiTraitDescriptor(
                    MiiTraitField.HatType,
                    10,
                    colorField = MiiColorField.Hat,
                    optional = true,
                ),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Hair, 100, figmaPrimary = true),
                MiiColorDescriptor(MiiColorField.Hat, 12),
            ),
            toggles = listOf(MiiToggleField.FlipHair),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Eyebrows,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.EyebrowType, 24, figmaPrimary = true),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Eyebrows, 100, figmaPrimary = true),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyebrowYPosition, 3, 18, 10),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyebrowScale, 0, 8, 4),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyebrowVerticalStretch, 0, 6, 3),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyebrowRotation, 0, 11, 6),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyebrowSpacing, 0, 12, 2),
            ),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Eyes,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.EyeType, 60, figmaPrimary = true),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Eyes, 6, figmaPrimary = true),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyeYPosition, 0, 18, 12),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyeScale, 0, 7, 4),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyeVerticalStretch, 0, 6, 3),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyeRotation, 0, 7, 4),
                MiiAdjustmentDescriptor(MiiAdjustmentField.EyeSpacing, 0, 12, 2),
            ),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Nose,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.NoseType, 18, figmaPrimary = true),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.NoseYPosition, 0, 18, 9),
                MiiAdjustmentDescriptor(MiiAdjustmentField.NoseScale, 0, 8, 4),
            ),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Mouth,
            traits = listOf(
                MiiTraitDescriptor(
                    MiiTraitField.MouthType,
                    36,
                    figmaPrimary = true,
                    colorField = MiiColorField.Mouth,
                ),
                MiiTraitDescriptor(
                    MiiTraitField.MustacheType,
                    6,
                    colorField = MiiColorField.FacialHair,
                ),
                MiiTraitDescriptor(
                    MiiTraitField.BeardType,
                    6,
                    colorField = MiiColorField.FacialHair,
                ),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Mouth, 100, figmaPrimary = true),
                MiiColorDescriptor(MiiColorField.FacialHair, 100),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.MouthYPosition, 0, 18, 13),
                MiiAdjustmentDescriptor(MiiAdjustmentField.MouthScale, 0, 8, 4),
                MiiAdjustmentDescriptor(MiiAdjustmentField.MouthHorizontalStretch, 0, 6, 3),
                MiiAdjustmentDescriptor(MiiAdjustmentField.MustacheYPosition, 0, 16, 10),
                MiiAdjustmentDescriptor(MiiAdjustmentField.MustacheScale, 0, 8, 4),
            ),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Glasses,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.GlassesType, 20, figmaPrimary = true),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Glasses, 100, figmaPrimary = true),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.GlassesYPosition, 0, 20, 10),
                MiiAdjustmentDescriptor(MiiAdjustmentField.GlassesScale, 0, 7, 4),
            ),
        ),
        MiiCategoryDescriptor(
            category = MiiCategory.Body,
            traits = listOf(
                MiiTraitDescriptor(MiiTraitField.Gender, 2, figmaPrimary = true),
            ),
            colors = listOf(
                MiiColorDescriptor(MiiColorField.Favorite, 12, figmaPrimary = true),
            ),
            adjustments = listOf(
                MiiAdjustmentDescriptor(MiiAdjustmentField.Height, 0, 127, 64),
                MiiAdjustmentDescriptor(MiiAdjustmentField.Build, 0, 127, 64),
            ),
        ),
    )

    private val byCategory = categories.associateBy(MiiCategoryDescriptor::category)

    fun descriptor(category: MiiCategory): MiiCategoryDescriptor =
        requireNotNull(byCategory[category])

    fun trait(category: MiiCategory, field: MiiTraitField): MiiTraitDescriptor? =
        descriptor(category).traits.firstOrNull { it.field == field }

    fun color(category: MiiCategory, field: MiiColorField): MiiColorDescriptor? =
        descriptor(category).colors.firstOrNull { it.field == field }

    fun adjustment(
        category: MiiCategory,
        field: MiiAdjustmentField,
    ): MiiAdjustmentDescriptor? =
        descriptor(category).adjustments.firstOrNull { it.field == field }
}

val MiiAdjustmentField.verticalUpDelta: Int?
    get() = when {
        this == MiiAdjustmentField.Height -> 1
        name.endsWith("YPosition") -> -1
        else -> null
    }
