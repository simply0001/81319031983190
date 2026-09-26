package com.pocketpass.app.ui.widget

import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.widget.WidgetBlock

fun WidgetBlock.glyph(): PocketAsset = when (this) {
    WidgetBlock.Tokens -> Assets.ActivitiesCoinDefault
    WidgetBlock.StepsToday -> Assets.SettingsSteps
    WidgetBlock.EncountersToday -> Assets.SettingsNearby
    WidgetBlock.LastPass -> Assets.FriendWave
    WidgetBlock.FriendsOnline -> Assets.NavFriends
    WidgetBlock.UnreadNotifications -> Assets.SettingsNotifications
    WidgetBlock.NearbyStatus -> Assets.SettingsNearby
    WidgetBlock.Profile -> Assets.NavHome
    WidgetBlock.DisplayName -> Assets.NavHome
    WidgetBlock.FriendCode -> Assets.NavFriends
    WidgetBlock.Achievements -> Assets.ActivitiesTrophy
    WidgetBlock.BingoLines -> Assets.GamesIconBingo
    WidgetBlock.WorldTourCountries -> Assets.GamesIconWorldTour
    WidgetBlock.LeaderboardRank -> Assets.ActivitiesTrophy
    WidgetBlock.RecentPeople -> Assets.FriendWave
    WidgetBlock.OnlineFriends -> Assets.NavFriends
}

fun WidgetBlock.settingsGlyph(): PocketAsset = when (this) {
    WidgetBlock.Tokens -> Assets.SettingsTokens
    WidgetBlock.StepsToday -> Assets.SettingsSteps
    WidgetBlock.EncountersToday -> Assets.SettingsNearby
    WidgetBlock.LastPass -> Assets.SettingsClock
    WidgetBlock.FriendsOnline -> Assets.SettingsSocial
    WidgetBlock.UnreadNotifications -> Assets.SettingsNotifications
    WidgetBlock.NearbyStatus -> Assets.SettingsNearby
    WidgetBlock.Profile -> Assets.SettingsEditName
    WidgetBlock.DisplayName -> Assets.SettingsEditName
    WidgetBlock.FriendCode -> Assets.SettingsFriendCode
    WidgetBlock.Achievements -> Assets.SettingsTrophy
    WidgetBlock.BingoLines -> Assets.SettingsBingo
    WidgetBlock.WorldTourCountries -> Assets.SettingsGlobe
    WidgetBlock.LeaderboardRank -> Assets.SettingsPodium
    WidgetBlock.RecentPeople -> Assets.SettingsContributors
    WidgetBlock.OnlineFriends -> Assets.SettingsSocial
}

fun WidgetBlock.widgetGlyph(): PocketAsset = when (this) {
    WidgetBlock.Tokens -> Assets.WidgetGlyphTokens
    WidgetBlock.StepsToday -> Assets.WidgetGlyphSteps
    WidgetBlock.EncountersToday -> Assets.WidgetGlyphEncounters
    WidgetBlock.LastPass -> Assets.WidgetGlyphClock
    WidgetBlock.FriendsOnline -> Assets.WidgetGlyphSocial
    WidgetBlock.UnreadNotifications -> Assets.WidgetGlyphNotifications
    WidgetBlock.NearbyStatus -> Assets.WidgetGlyphNearby
    WidgetBlock.Profile -> Assets.WidgetGlyphPerson
    WidgetBlock.DisplayName -> Assets.WidgetGlyphPerson
    WidgetBlock.FriendCode -> Assets.WidgetGlyphFriendCode
    WidgetBlock.Achievements -> Assets.WidgetGlyphAchievements
    WidgetBlock.BingoLines -> Assets.WidgetGlyphBingo
    WidgetBlock.WorldTourCountries -> Assets.WidgetGlyphGlobe
    WidgetBlock.LeaderboardRank -> Assets.WidgetGlyphPodium
    WidgetBlock.RecentPeople -> Assets.WidgetGlyphGroups
    WidgetBlock.OnlineFriends -> Assets.WidgetGlyphSocial
}
