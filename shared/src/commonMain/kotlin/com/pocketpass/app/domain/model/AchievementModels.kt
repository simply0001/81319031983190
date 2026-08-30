package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class AchievementState(
    val key: String,
    val unlocked: Boolean,
    val unlockedAt: Instant?,
    val progressPercent: Int,
) {
    init {
        require(progressPercent in 0..100) { "Progress must be a percentage" }
    }
}

enum class AchievementSection(val title: String) {
    Main("Main Achievements"),
    Encounters("Encounters"),
    WorldTour("World Tour"),
    PuzzleSwap("Puzzle Swap"),
}

data class AchievementDefinition(
    val key: String,
    val name: String,
    val description: String,
    val section: AchievementSection,
)

object AchievementCatalog {
    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition(
            key = "day_one",
            name = "Day One",
            description = "Have an account with the old PocketPass app.",
            section = AchievementSection.Main,
        ),
        AchievementDefinition(
            key = "saving_up",
            name = "Saving Up",
            description = "Have 500 tokens in your balance.",
            section = AchievementSection.Main,
        ),
        AchievementDefinition(
            key = "icebreaker",
            name = "Icebreaker",
            description = "Send your first message!",
            section = AchievementSection.Main,
        ),
        AchievementDefinition(
            key = "streak",
            name = "Streak",
            description = "Held a conversation every day over ten straight days.",
            section = AchievementSection.Main,
        ),
        AchievementDefinition(
            key = "plus_one",
            name = "Plus One",
            description = "Add your first friend.",
            section = AchievementSection.Main,
        ),
        AchievementDefinition(
            key = "first_encounter",
            name = "First Encounter",
            description = "Have your first encounter.",
            section = AchievementSection.Encounters,
        ),
        AchievementDefinition(
            key = "small_world",
            name = "Small World",
            description = "Have ten encounters.",
            section = AchievementSection.Encounters,
        ),
        AchievementDefinition(
            key = "passport_stamped",
            name = "Passport Stamped",
            description = "Met your first person from another country.",
            section = AchievementSection.WorldTour,
        ),
        AchievementDefinition(
            key = "continental",
            name = "Continental",
            description = "Have at least one person from every continent.",
            section = AchievementSection.WorldTour,
        ),
        AchievementDefinition(
            key = "full_set",
            name = "Full Set",
            description = "Gotten every piece of every puzzle.",
            section = AchievementSection.PuzzleSwap,
        ),
        AchievementDefinition(
            key = "missing_piece",
            name = "Missing Piece",
            description = "Completed a puzzle using a piece someone else handed over.",
            section = AchievementSection.PuzzleSwap,
        ),
    )

    val orderedKeys: List<String> = definitions.map(AchievementDefinition::key)
}
