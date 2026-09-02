package com.pocketpass.app.ui

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
import com.pocketpass.ui.resources.Res
import com.pocketpass.ui.resources.gochi_hand
import com.pocketpass.ui.resources.instrument_sans_400
import com.pocketpass.ui.resources.instrument_sans_500
import com.pocketpass.ui.resources.instrument_sans_600
import com.pocketpass.ui.resources.instrument_sans_700
import com.pocketpass.ui.resources.rubik_400
import com.pocketpass.ui.resources.rubik_500
import com.pocketpass.ui.resources.rubik_600
import com.pocketpass.ui.resources.rubik_700
import com.pocketpass.ui.resources.rubik_800
import com.pocketpass.ui.resources.rubik_900
import com.pocketpass.ui.resources.staatliches
import kotlin.jvm.JvmInline
import kotlin.math.min
import org.jetbrains.compose.resources.Font

const val TOP_DESIGN_WIDTH = 1920f
const val TOP_DESIGN_HEIGHT = 1080f
const val BOTTOM_DESIGN_WIDTH = 1240f
const val BOTTOM_DESIGN_HEIGHT = 1080f

val Rubik: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.rubik_400, FontWeight.Normal),
        Font(Res.font.rubik_500, FontWeight.Medium),
        Font(Res.font.rubik_600, FontWeight.SemiBold),
        Font(Res.font.rubik_700, FontWeight.Bold),
        Font(Res.font.rubik_800, FontWeight.ExtraBold),
        Font(Res.font.rubik_900, FontWeight.Black),
    )

val GochiHand: FontFamily
    @Composable get() = FontFamily(Font(Res.font.gochi_hand, FontWeight.Normal))

val Staatliches: FontFamily
    @Composable get() = FontFamily(Font(Res.font.staatliches, FontWeight.Normal))

val InstrumentSans: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.instrument_sans_400, FontWeight.Normal),
        Font(Res.font.instrument_sans_500, FontWeight.Medium),
        Font(Res.font.instrument_sans_600, FontWeight.SemiBold),
        Font(Res.font.instrument_sans_700, FontWeight.Bold),
    )

// A file under composeResources that FigmaAsset streams through Coil: the Figma exports are
// plain SVGs, which Compose resources cannot rasterise on Android, so one loader serves every
// platform instead.
@JvmInline
value class PocketAsset(val path: String)

private fun figma(name: String) = PocketAsset("files/figma/$name")

@Immutable
class DesignMetrics(
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
    val AuthLeaf = figma("auth_leaf.png")
    val StatusWifi = figma("status_wifi.svg")
    val StatusBattery = figma("status_battery.svg")
    val NavMessages = figma("nav_messages.svg")
    val NavFriends = figma("nav_friends.svg")
    val NavHome = figma("nav_home.svg")
    val NavActivities = figma("nav_activities.svg")
    val NavSettings = figma("nav_settings.svg")
    val HomeAvatarMatt = figma("home_avatar_matt.svg")
    val HomeCardOnline = figma("home_card_online.png")
    val OnlineDot = figma("online_dot.svg")
    val PatternHomeBottom = figma("pattern_home_bottom.svg")
    val PatternHomeTop = figma("pattern_home_top.svg")
    val HomeAvatarPetah = figma("home_avatar_petah.svg")
    val Filter = figma("filter_icon.svg")
    val HomeMore = figma("home_more.svg")
    val HomeMoodHappy = figma("home_mood_happy.svg")
    val HomeMoodSad = figma("home_mood_sad.svg")
    val HomeMoodNeutral = figma("home_mood_neutral.svg")
    val HomeMoodParty = figma("home_mood_party.svg")
    val HomeMoodPlayful = figma("home_mood_playful.svg")
    val HomeMoodCool = figma("home_mood_cool.svg")
    val HomeMoodTrigger = figma("home_mood_trigger.svg")
    val HomeMoodEmojiHappy = figma("home_mood_emoji_happy.svg")
    val HomeMoodEmojiSad = figma("home_mood_emoji_sad.svg")
    val HomeMoodEmojiNeutral = figma("home_mood_emoji_neutral.svg")
    val HomeMoodEmojiParty = figma("home_mood_emoji_party.svg")
    val HomeMoodEmojiPlayful = figma("home_mood_emoji_playful.svg")
    val HomeMoodEmojiCool = figma("home_mood_emoji_cool.svg")
    val PatternActivitiesBottom = figma("pattern_activities_bottom.svg")
    val PatternActivitiesTop = figma("pattern_activities_top.svg")
    val ActivitiesGames = figma("activities_games.svg")
    val ActivitiesArrowGreen = figma("activities_arrow_green.svg")
    val ActivitiesShop = figma("activities_shop.svg")
    val ActivitiesArrowOrange = figma("activities_arrow_orange.svg")
    val ActivitiesTrophy = figma("activities_trophy.svg")
    val ActivitiesPanelGames = figma("activities_panel_games.png")
    val ActivitiesPanelShop = figma("activities_panel_shop.png")
    val ActivitiesPanelLeaderboard = figma("activities_panel_leaderboard.png")
    val ActivitiesArrowYellow = figma("activities_arrow_yellow.svg")
    val ActivitiesCoinDefault = figma("activities_coin_default.png")
    val ActivitiesPuzzleDefault = figma("activities_puzzle_default.png")
    val ActivitiesSteps = figma("activities_steps.svg")
    val ActivitiesShuffle = figma("activities_shuffle.png")
    val ActivitiesShuffleYOverlay = figma("activities_shuffle_y_overlay.svg")
    val ActivitiesCoinAlt = figma("activities_coin_alt.png")
    val ActivitiesPuzzleAlt = figma("activities_puzzle_alt.png")
    val GameWoodTop = figma("game_wood_top.jpg")
    val GameWoodBottom = figma("game_wood_bottom.jpg")
    val PuzzleSwapTitle = figma("puzzle_swap_title.svg")
    val PuzzleSwapBottom = figma("puzzle_bottom.jpg")
    val BingoPaper = figma("bingo_paper.png")
    val BingoTitle = figma("bingo_title.svg")
    val BingoNotePaper = figma("bingo_note_paper.png")
    val WorldTourSpace = figma("worldtour_space.jpg")
    val WorldTourGlobe = figma("worldtour_globe.png")
    val WorldTourGlobeMap = figma("worldtour_globe_map.png")
    val WorldTourMap = figma("worldtour_map.png")
    val GamesHero = figma("games_hero.svg")
    val GamesIconPuzzleSwap = figma("games_icon_puzzle_swap.svg")
    val GamesIconBingo = figma("games_icon_bingo.svg")
    val GamesIconWorldTour = figma("games_icon_world_tour.svg")
    val ShopCategoryHats = figma("shop_category_hats.svg")
    val ShopItemBaseballCap = figma("shop_item_baseball_cap.png")
    val ShopItemHalo = figma("shop_item_halo.png")
    val PatternMessagesTop = figma("pattern_messages_top.svg")
    val PatternMessagesBottom = figma("pattern_messages_bottom.svg")
    val MessagesAvatarSpob = figma("messages_avatar_spob.svg")
    val MessagesAvatarSans = figma("messages_avatar_sans.svg")
    val MessagesBadge = figma("messages_badge.svg")
    val MessagesDetailPattern = figma("messages_detail_pattern.svg")
    val MessageTailIncoming = figma("message_tail_incoming.svg")
    val MessageTailOutgoing = figma("message_tail_outgoing.svg")
    val MessagesSendButton = figma("messages_send_button.svg")
    val MessageActionAdd = figma("message_action_add.svg")
    val MessageActionEmoji = figma("message_action_emoji.svg")
    val MessageActionImage = figma("message_action_image.svg")
    val MessageActionFile = figma("message_action_file.svg")
    val MessagesBadgeComposite = figma("messages_badge_composite.png")
    val MessagesListPanel = figma("messages_list_panel.png")
    val PatternFriendsBottom = figma("pattern_friends_bottom.svg")
    val PatternFriendsTop = figma("pattern_friends_top.svg")
    val FriendsAvatarMatt = figma("friends_avatar_matt.svg")
    val FriendsFilter = figma("friends_filter.svg")
    val FriendsBadge = figma("friends_badge.png")
    val FriendsCardOnline = figma("friends_card_online.png")
    val FriendsCardOffline = figma("friends_card_offline.png")
    val PatternSettingsBottom = figma("pattern_settings_bottom.svg")
    val PatternSettingsTop = figma("pattern_settings_top.svg")
    val SettingsNearby = figma("settings_nearby.svg")
    val SettingsNearbyPanel = figma("settings_nearby_panel.png")
    val SettingsSwitchOn = figma("settings_switch_on.svg")
    val SettingsSound = figma("settings_sound.svg")
    val SettingsSoundPanel = figma("settings_sound_panel.png")
    val SettingsGear = figma("settings_gear_face.svg")
    val SettingsGearShadow = figma("settings_gear_shadow.svg")
    val SettingsNotifications = figma("settings_notifications.svg")
    val SettingsArrow = figma("settings_arrow.svg")
    val SettingsTheme = figma("settings_theme.svg")
    val SettingsEditMii = figma("settings_edit_mii.svg")
    val SettingsEditName = figma("settings_edit_name.svg")
    val ThemeLight = figma("theme_light.svg")
    val ThemeSystem = figma("theme_system.svg")
    val ThemeDark = figma("theme_dark.svg")
    val SettingsAccessibility = figma("settings_accessibility.svg")
    val SettingsMoodEmojis = figma("settings_mood_emojis.svg")
    val SettingsEncounterLed = figma("settings_encounter_led.svg")
    val SettingsSteps = figma("settings_steps.svg")
    val SettingsEncounterAlerts = figma("settings_encounter_alerts.svg")
    val SettingsRepairAlerts = figma("settings_repair_alerts.svg")
    val SettingsVersion = figma("settings_version.svg")
    val SettingsLogout = figma("settings_logout.svg")
    val SettingsConnectedApps = figma("settings_connected_apps.svg")
    val SettingsSocial = figma("settings_social.svg")
    val SettingsCreditsAvatar = figma("settings_credits_avatar.svg")
    val SettingsCreditsAvatarSimply = figma("settings_credits_avatar_simply.png")
    val SettingsCreditsAvatarBrocoDev = figma("settings_credits_avatar_brocodev.png")
    val SettingsCreditsAvatarK0o1 = figma("settings_credits_avatar_k0o1.jpg")
    val SettingsCreditsAvatarAriankordi = figma("settings_credits_avatar_ariankordi.jpg")
    val SettingsDelete = figma("settings_delete.svg")
    val NotificationAccept = figma("notification_accept.svg")
    val NotificationDecline = figma("notification_decline.svg")
    val FriendTrophy = figma("friend_trophy.svg")
    val FriendWave = figma("friend_wave.svg")
    val LeaderboardTrophyHero = figma("leaderboard_trophy_hero.svg")
    val LeaderboardTrophyGloss = figma("leaderboard_trophy_gloss.svg")
    val LeaderboardTrophy = figma("leaderboard_trophy.svg")
    val LeaderboardWave = figma("leaderboard_wave.svg")
    val AchievementGauge = figma("achievement_gauge.svg")
    val AchievementDayOne = figma("achievement_day_one.svg")
    val AchievementSavingUp = figma("achievement_saving_up.svg")
    val AchievementIcebreaker = figma("achievement_icebreaker.svg")
    val AchievementStreak = figma("achievement_streak.svg")
    val AchievementPlusOne = figma("achievement_plus_one.svg")
    val AchievementFirstEncounter = figma("achievement_first_encounter.svg")
    val AchievementSmallWorld = figma("achievement_small_world.svg")
    val AchievementPassportStamped = figma("achievement_passport_stamped.svg")
    val AchievementContinental = figma("achievement_continental.svg")
    val AchievementFullSet = figma("achievement_full_set.svg")
    val AchievementMissingPiece = figma("achievement_missing_piece.svg")
}
