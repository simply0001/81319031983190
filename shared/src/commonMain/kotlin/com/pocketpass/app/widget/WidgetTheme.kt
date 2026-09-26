package com.pocketpass.app.widget

enum class WidgetTheme(
    val background: Long,
    val ink: Long,
    val softInk: Long,
) {
    Green(0xFFA6FCAB, 0xFF1D586B, 0xFF26706A),
    Yellow(0xFFFBF5A5, 0xFF6B401D, 0xFF8A5A2B),
    Orange(0xFFFFE3B0, 0xFF6B401D, 0xFF8A5A2B),
    Pink(0xFFF8CDF6, 0xFF6B1D62, 0xFF8A2B7E),
    Blue(0xFFBFE0FF, 0xFF1D3F6B, 0xFF2B5B8A),
}

val WidgetBlock.theme: WidgetTheme
    get() = when (this) {
        WidgetBlock.Tokens -> WidgetTheme.Orange
        WidgetBlock.StepsToday,
        WidgetBlock.EncountersToday,
        WidgetBlock.LastPass,
        WidgetBlock.NearbyStatus,
        -> WidgetTheme.Green
        WidgetBlock.FriendsOnline,
        WidgetBlock.RecentPeople,
        WidgetBlock.OnlineFriends,
        -> WidgetTheme.Pink
        WidgetBlock.UnreadNotifications,
        WidgetBlock.Profile,
        WidgetBlock.DisplayName,
        WidgetBlock.FriendCode,
        WidgetBlock.WorldTourCountries,
        -> WidgetTheme.Blue
        WidgetBlock.Achievements,
        WidgetBlock.BingoLines,
        WidgetBlock.LeaderboardRank,
        -> WidgetTheme.Yellow
    }

fun WidgetDesign.theme(): WidgetTheme = hero?.theme ?: tiles.firstNotNullOfOrNull { it }?.theme ?: WidgetTheme.Green
