package com.pocketpass.app.ui

import androidx.annotation.RawRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.pocketpass.app.R
import kotlin.math.min

const val TOP_DESIGN_WIDTH = 1920f
const val TOP_DESIGN_HEIGHT = 1080f
const val BOTTOM_DESIGN_WIDTH = 1240f
const val BOTTOM_DESIGN_HEIGHT = 1080f

val Rubik = FontFamily(
    Font(R.font.rubik_400, FontWeight.Normal),
    Font(R.font.rubik_500, FontWeight.Medium),
    Font(R.font.rubik_600, FontWeight.SemiBold),
    Font(R.font.rubik_700, FontWeight.Bold),
    Font(R.font.rubik_800, FontWeight.ExtraBold),
    Font(R.font.rubik_900, FontWeight.Black),
)

val GochiHand = FontFamily(Font(R.font.gochi_hand, FontWeight.Normal))

val Staatliches = FontFamily(Font(R.font.staatliches, FontWeight.Normal))

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_400, FontWeight.Normal),
    Font(R.font.instrument_sans_500, FontWeight.Medium),
    Font(R.font.instrument_sans_600, FontWeight.SemiBold),
    Font(R.font.instrument_sans_700, FontWeight.Bold),
)

@Immutable
class DesignMetrics internal constructor(
    private val density: Density,
    val designWidth: Float = 0f,
    val designHeight: Float = 0f,
    val overscanX: Float = 0f,
    val overscanY: Float = 0f,
    val scale: Float = 1f,
) {
    fun dp(px: Number): Dp = with(density) { px.toFloat().toDp() }
    fun sp(px: Number): TextUnit = with(density) { px.toFloat().toSp() }

    val hasOverscan: Boolean
        get() = overscanX > 0.5f || overscanY > 0.5f
}

fun overscanFor(
    viewportWidth: Float,
    viewportHeight: Float,
    designWidth: Float,
    designHeight: Float,
): Offset {
    val scale = min(viewportWidth / designWidth, viewportHeight / designHeight)
    return Offset(
        ((viewportWidth / scale - designWidth) / 2f).coerceAtLeast(0f),
        ((viewportHeight / scale - designHeight) / 2f).coerceAtLeast(0f),
    )
}

@Stable
class DesignBackdropHost internal constructor() {
    private val entries = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()

    val layers: List<Pair<Any, @Composable () -> Unit>>
        get() = entries

    internal fun set(owner: Any, content: @Composable () -> Unit) {
        val index = entries.indexOfFirst { it.first === owner }
        if (index >= 0) entries[index] = owner to content else entries += owner to content
    }

    internal fun clear(owner: Any) {
        entries.removeAll { it.first === owner }
    }
}

val LocalDesignBackdrop = staticCompositionLocalOf<DesignBackdropHost?> { null }

@Composable
fun DesignSurface(
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.(DesignMetrics) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val scale = min(
            viewportWidthPx / designWidth,
            viewportHeightPx / designHeight,
        )
        val overscan = overscanFor(viewportWidthPx, viewportHeightPx, designWidth, designHeight)
        val metrics = remember(density, designWidth, designHeight, overscan, scale) {
            DesignMetrics(density, designWidth, designHeight, overscan.x, overscan.y, scale)
        }
        val backdrop = remember { DesignBackdropHost() }
        val panelWidth = designWidth + 2f * overscan.x
        val panelHeight = designHeight + 2f * overscan.y
        val layer = Modifier
            .requiredSize(metrics.dp(panelWidth), metrics.dp(panelHeight))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }

        if (metrics.hasOverscan) {
            Box(layer) {
                backdrop.layers.forEach { (owner, content) -> key(owner) { content() } }
            }
        }
        Box(layer.clipToBounds()) {
            Box(
                Modifier
                    .designBounds(metrics, overscan.x, overscan.y, designWidth, designHeight)
                    .background(background),
            ) {
                CompositionLocalProvider(
                    LocalDesignBackdrop provides backdrop,
                    LocalDesignMetrics provides metrics,
                ) {
                    content(metrics)
                }
            }
        }
    }
}

@Composable
fun DesignBackdrop(
    metrics: DesignMetrics,
    alpha: () -> Float = { 1f },
    key: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalDesignBackdrop.current
    if (host == null || !metrics.hasOverscan) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val latest = rememberUpdatedState(content)
    val latestAlpha = rememberUpdatedState(alpha)
    val latestKey = rememberUpdatedState(key)
    val owner = remember { Any() }
    DisposableEffect(host, owner) {
        host.set(owner) {
            if (latestAlpha.value() > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = latestAlpha.value() }) {
                    key(latestKey.value) { latest.value(this) }
                }
            }
        }
        onDispose { host.clear(owner) }
    }
}

val LocalDesignMetrics = staticCompositionLocalOf<DesignMetrics?> { null }

val LocalDesignOrigin = compositionLocalOf { Offset.Zero }

enum class DesignAnchor { Start, Center, End, Stretch }

private fun anchorShift(anchor: DesignAnchor, overscan: Float): Float = when (anchor) {
    DesignAnchor.Start, DesignAnchor.Stretch -> -overscan
    DesignAnchor.Center -> 0f
    DesignAnchor.End -> overscan
}

fun DesignMetrics.anchorOrigin(horizontal: DesignAnchor, vertical: DesignAnchor): Offset =
    Offset(anchorShift(horizontal, overscanX), anchorShift(vertical, overscanY))

@Composable
fun DesignMetrics.anchoredX(x: Number, anchor: DesignAnchor): Float =
    x.toFloat() + anchorShift(anchor, overscanX) - LocalDesignOrigin.current.x

@Composable
fun DesignMetrics.anchoredY(y: Number, anchor: DesignAnchor): Float =
    y.toFloat() + anchorShift(anchor, overscanY) - LocalDesignOrigin.current.y

@Composable
fun Modifier.anchoredBounds(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
    horizontal: DesignAnchor = DesignAnchor.Center,
    vertical: DesignAnchor = DesignAnchor.Center,
): Modifier {
    val origin = LocalDesignOrigin.current
    val left = x.toFloat() + anchorShift(horizontal, metrics.overscanX) - origin.x
    val top = y.toFloat() + anchorShift(vertical, metrics.overscanY) - origin.y
    val spanX = if (horizontal == DesignAnchor.Stretch) 2f * metrics.overscanX else 0f
    val spanY = if (vertical == DesignAnchor.Stretch) 2f * metrics.overscanY else 0f
    return graphicsLayer {
        translationX = left
        translationY = top
    }.fixedDesignSize(metrics.dp(width.toFloat() + spanX), metrics.dp(height.toFloat() + spanY))
}

private fun Modifier.fixedDesignSize(width: Dp, height: Dp): Modifier = layout { measurable, constraints ->
    val widthPx = width.roundToPx()
    val heightPx = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
    layout(constraints.constrainWidth(widthPx), constraints.constrainHeight(heightPx)) {
        placeable.place(0, 0)
    }
}

@Composable
fun DesignBox(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
    horizontal: DesignAnchor = DesignAnchor.Center,
    vertical: DesignAnchor = DesignAnchor.Center,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, x, y, width, height, horizontal, vertical)
            .then(modifier),
        contentAlignment = contentAlignment,
    ) {
        val scope = this
        CompositionLocalProvider(
            LocalDesignOrigin provides metrics.anchorOrigin(horizontal, vertical),
        ) {
            scope.content()
        }
    }
}

fun Modifier.designBounds(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
): Modifier = this
    .graphicsLayer {
        translationX = x.toFloat()
        translationY = y.toFloat()
    }
    .requiredSize(metrics.dp(width), metrics.dp(height))

fun Modifier.designBounds(
    metrics: DesignMetrics,
    width: Number,
    height: Number,
    position: () -> Offset,
): Modifier = this
    .graphicsLayer {
        val offset = position()
        translationX = offset.x
        translationY = offset.y
    }
    .requiredSize(metrics.dp(width), metrics.dp(height))

object Assets {
    @DrawableRes val AuthLeaf = R.drawable.auth_leaf
    @RawRes val StatusWifi = R.raw.status_wifi
    @RawRes val StatusBattery = R.raw.status_battery
    @RawRes val NavMessages = R.raw.nav_messages
    @RawRes val NavFriends = R.raw.nav_friends
    @RawRes val NavHome = R.raw.nav_home
    @RawRes val NavActivities = R.raw.nav_activities
    @RawRes val NavSettings = R.raw.nav_settings
    @RawRes val HomeAvatarMatt = R.raw.home_avatar_matt
    @DrawableRes val HomeCardOnline = R.drawable.home_card_online
    @RawRes val OnlineDot = R.raw.online_dot
    @RawRes val PatternHomeBottom = R.raw.pattern_home_bottom
    @RawRes val PatternHomeTop = R.raw.pattern_home_top
    @RawRes val HomeAvatarPetah = R.raw.home_avatar_petah
    @RawRes val Filter = R.raw.filter_icon
    @RawRes val HomeMore = R.raw.home_more
    @RawRes val HomeMoodHappy = R.raw.home_mood_happy
    @RawRes val HomeMoodSad = R.raw.home_mood_sad
    @RawRes val HomeMoodNeutral = R.raw.home_mood_neutral
    @RawRes val HomeMoodParty = R.raw.home_mood_party
    @RawRes val HomeMoodPlayful = R.raw.home_mood_playful
    @RawRes val HomeMoodCool = R.raw.home_mood_cool
    @RawRes val HomeMoodTrigger = R.raw.home_mood_trigger
    @RawRes val HomeMoodEmojiHappy = R.raw.home_mood_emoji_happy
    @RawRes val HomeMoodEmojiSad = R.raw.home_mood_emoji_sad
    @RawRes val HomeMoodEmojiNeutral = R.raw.home_mood_emoji_neutral
    @RawRes val HomeMoodEmojiParty = R.raw.home_mood_emoji_party
    @RawRes val HomeMoodEmojiPlayful = R.raw.home_mood_emoji_playful
    @RawRes val HomeMoodEmojiCool = R.raw.home_mood_emoji_cool
    @RawRes val PatternActivitiesBottom = R.raw.pattern_activities_bottom
    @RawRes val PatternActivitiesTop = R.raw.pattern_activities_top
    @RawRes val ActivitiesGames = R.raw.activities_games
    @RawRes val ActivitiesArrowGreen = R.raw.activities_arrow_green
    @RawRes val ActivitiesShop = R.raw.activities_shop
    @RawRes val ActivitiesArrowOrange = R.raw.activities_arrow_orange
    @RawRes val ActivitiesTrophy = R.raw.activities_trophy
    @DrawableRes val ActivitiesPanelGames = R.drawable.activities_panel_games
    @DrawableRes val ActivitiesPanelShop = R.drawable.activities_panel_shop
    @DrawableRes val ActivitiesPanelLeaderboard = R.drawable.activities_panel_leaderboard
    @RawRes val ActivitiesArrowYellow = R.raw.activities_arrow_yellow
    @DrawableRes val ActivitiesCoinDefault = R.drawable.activities_coin_default
    @DrawableRes val ActivitiesPuzzleDefault = R.drawable.activities_puzzle_default
    @DrawableRes val ActivitiesShuffle = R.drawable.activities_shuffle
    @RawRes val ActivitiesShuffleYOverlay = R.raw.activities_shuffle_y_overlay
    @DrawableRes val ActivitiesCoinAlt = R.drawable.activities_coin_alt
    @DrawableRes val ActivitiesPuzzleAlt = R.drawable.activities_puzzle_alt

    @DrawableRes val GameWoodTop = R.drawable.game_wood_top
    @DrawableRes val GameWoodBottom = R.drawable.game_wood_bottom
    @RawRes val PuzzleSwapTitle = R.raw.puzzle_swap_title
    @DrawableRes val PuzzleSwapBottom = R.drawable.puzzle_bottom
    @DrawableRes val BingoPaper = R.drawable.bingo_paper
    @RawRes val BingoTitle = R.raw.bingo_title
    @DrawableRes val BingoNotePaper = R.drawable.bingo_note_paper
    @DrawableRes val WorldTourSpace = R.drawable.worldtour_space
    @DrawableRes val WorldTourGlobe = R.drawable.worldtour_globe
    @DrawableRes val WorldTourGlobeMap = R.drawable.worldtour_globe_map
    @DrawableRes val WorldTourMap = R.drawable.worldtour_map

    @RawRes val GamesHero = R.raw.games_hero
    @RawRes val GamesIconPuzzleSwap = R.raw.games_icon_puzzle_swap
    @RawRes val GamesIconBingo = R.raw.games_icon_bingo
    @RawRes val GamesIconWorldTour = R.raw.games_icon_world_tour

    @RawRes val ShopCategoryHats = R.raw.shop_category_hats
    @DrawableRes val ShopItemBaseballCap = R.drawable.shop_item_baseball_cap
    @DrawableRes val ShopItemHalo = R.drawable.shop_item_halo
    @RawRes val PatternMessagesTop = R.raw.pattern_messages_top
    @RawRes val PatternMessagesBottom = R.raw.pattern_messages_bottom
    @RawRes val MessagesAvatarSpob = R.raw.messages_avatar_spob
    @RawRes val MessagesAvatarSans = R.raw.messages_avatar_sans
    @RawRes val MessagesBadge = R.raw.messages_badge
    @RawRes val MessagesDetailPattern = R.raw.messages_detail_pattern
    @RawRes val MessageTailIncoming = R.raw.message_tail_incoming
    @RawRes val MessageTailOutgoing = R.raw.message_tail_outgoing
    @RawRes val MessagesSendButton = R.raw.messages_send_button
    @RawRes val MessageActionAdd = R.raw.message_action_add
    @RawRes val MessageActionEmoji = R.raw.message_action_emoji
    @RawRes val MessageActionImage = R.raw.message_action_image
    @RawRes val MessageActionFile = R.raw.message_action_file
    @DrawableRes val MessagesBadgeComposite = R.drawable.messages_badge_composite
    @DrawableRes val MessagesListPanel = R.drawable.messages_list_panel
    @RawRes val PatternFriendsBottom = R.raw.pattern_friends_bottom
    @RawRes val PatternFriendsTop = R.raw.pattern_friends_top
    @RawRes val FriendsAvatarMatt = R.raw.friends_avatar_matt
    @RawRes val FriendsFilter = R.raw.friends_filter
    @DrawableRes val FriendsBadge = R.drawable.friends_badge
    @DrawableRes val FriendsCardOnline = R.drawable.friends_card_online
    @DrawableRes val FriendsCardOffline = R.drawable.friends_card_offline
    @RawRes val PatternSettingsBottom = R.raw.pattern_settings_bottom
    @RawRes val PatternSettingsTop = R.raw.pattern_settings_top
    @RawRes val SettingsNearby = R.raw.settings_nearby
    @DrawableRes val SettingsNearbyPanel = R.drawable.settings_nearby_panel
    @RawRes val SettingsSwitchOn = R.raw.settings_switch_on
    @RawRes val SettingsSound = R.raw.settings_sound
    @DrawableRes val SettingsSoundPanel = R.drawable.settings_sound_panel
    @RawRes val SettingsGear = R.raw.settings_gear_face
    @RawRes val SettingsGearShadow = R.raw.settings_gear_shadow
    @RawRes val SettingsNotifications = R.raw.settings_notifications
    @RawRes val SettingsArrow = R.raw.settings_arrow
    @RawRes val SettingsTheme = R.raw.settings_theme
    @RawRes val SettingsEditMii = R.raw.settings_edit_mii
    @RawRes val SettingsEditName = R.raw.settings_edit_name
    @RawRes val ThemeLight = R.raw.theme_light
    @RawRes val ThemeSystem = R.raw.theme_system
    @RawRes val ThemeDark = R.raw.theme_dark
    @RawRes val SettingsAccessibility = R.raw.settings_accessibility
    @RawRes val SettingsMoodEmojis = R.raw.settings_mood_emojis
    @RawRes val SettingsEncounterLed = R.raw.settings_encounter_led
    @RawRes val SettingsEncounterAlerts = R.raw.settings_encounter_alerts
    @RawRes val SettingsRepairAlerts = R.raw.settings_repair_alerts
    @RawRes val SettingsVersion = R.raw.settings_version
    @RawRes val SettingsLogout = R.raw.settings_logout
    @RawRes val SettingsConnectedApps = R.raw.settings_connected_apps
    @RawRes val SettingsSocial = R.raw.settings_social
    @RawRes val SettingsCreditsAvatar = R.raw.settings_credits_avatar
    @DrawableRes val SettingsCreditsAvatarSimply = R.drawable.settings_credits_avatar_simply
    @DrawableRes val SettingsCreditsAvatarBrocoDev = R.drawable.settings_credits_avatar_brocodev
    @DrawableRes val SettingsCreditsAvatarK0o1 = R.drawable.settings_credits_avatar_k0o1
    @DrawableRes val SettingsCreditsAvatarAriankordi = R.drawable.settings_credits_avatar_ariankordi
    @RawRes val SettingsDelete = R.raw.settings_delete
    @RawRes val NotificationAccept = R.raw.notification_accept
    @RawRes val NotificationDecline = R.raw.notification_decline
    @RawRes val FriendTrophy = R.raw.friend_trophy
    @RawRes val FriendWave = R.raw.friend_wave
    @RawRes val LeaderboardTrophyHero = R.raw.leaderboard_trophy_hero
    @RawRes val LeaderboardTrophyGloss = R.raw.leaderboard_trophy_gloss
    @RawRes val LeaderboardTrophy = R.raw.leaderboard_trophy
    @RawRes val LeaderboardWave = R.raw.leaderboard_wave
    @RawRes val AchievementGauge = R.raw.achievement_gauge
    @RawRes val AchievementDayOne = R.raw.achievement_day_one
    @RawRes val AchievementSavingUp = R.raw.achievement_saving_up
    @RawRes val AchievementIcebreaker = R.raw.achievement_icebreaker
    @RawRes val AchievementStreak = R.raw.achievement_streak
    @RawRes val AchievementPlusOne = R.raw.achievement_plus_one
    @RawRes val AchievementFirstEncounter = R.raw.achievement_first_encounter
    @RawRes val AchievementSmallWorld = R.raw.achievement_small_world
    @RawRes val AchievementPassportStamped = R.raw.achievement_passport_stamped
    @RawRes val AchievementContinental = R.raw.achievement_continental
    @RawRes val AchievementFullSet = R.raw.achievement_full_set
    @RawRes val AchievementMissingPiece = R.raw.achievement_missing_piece
}
