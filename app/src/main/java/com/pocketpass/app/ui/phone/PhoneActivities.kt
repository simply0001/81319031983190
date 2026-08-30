package com.pocketpass.app.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AchievementSection
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.model.ActivityVariant
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.IdleMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.rememberActivitiesSwapProgress
import com.pocketpass.app.ui.screens.AchievementSectionPanel
import com.pocketpass.app.ui.screens.ActivityPanel
import com.pocketpass.app.ui.screens.GameEntries
import com.pocketpass.app.ui.screens.GameRow
import com.pocketpass.app.ui.screens.LeaderboardPanel
import com.pocketpass.app.ui.screens.LeaderboardScopeOption
import com.pocketpass.app.ui.screens.OVERLAY_POP_BASE_DELAY_MILLIS
import com.pocketpass.app.ui.screens.OVERLAY_POP_STAGGER_MILLIS
import com.pocketpass.app.ui.screens.ShopCategoryPanel
import com.pocketpass.app.ui.screens.selfLeaderboardEntry
import com.pocketpass.app.ui.theme.pocketPalette

private const val PHONE_ACTIVITY_PANEL_HEIGHT = 270f
private const val PHONE_ACTIVITY_PANEL_GAP = 80f

private enum class ActivitiesSection { Rows, Shop, Games, Leaderboard, Achievements }

private fun PocketPassUiState.activitiesSection(): ActivitiesSection = when {
    achievements.visible -> ActivitiesSection.Achievements
    leaderboard.visible -> ActivitiesSection.Leaderboard
    games.visible -> ActivitiesSection.Games
    shop.visible -> ActivitiesSection.Shop
    else -> ActivitiesSection.Rows
}

@Composable
internal fun PhoneActivitiesTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    when (state.activitiesSection()) {
        ActivitiesSection.Rows -> ActivitiesScaffold(
            metrics = metrics,
            panes = panes,
            title = null,
            subtitle = null,
            backTag = null,
            onBack = null,
            art = { big -> PhoneActivitiesHero(metrics, state, dispatch, big) },
        ) {
            listOf(
                Triple("Games", Color(0xFF1D6B25) to Color(0xFF5EAC5E), Color(0xFFBDF8CB)),
                Triple("Shop", Color(0xFF6B331D) to Color(0xFFFF8D41), Color(0xFFF8DFBD)),
                Triple("Leaderboard", Color(0xFF6B5C1D) to Color(0xFFFFEF41), Color(0xFFF1F8BD)),
            ).forEachIndexed { index, (title, colors, fill) ->
                DeckSlot(metrics, PHONE_ACTIVITY_PANEL_HEIGHT) {
                    ActivityPanel(
                        metrics = metrics,
                        y = 0f,
                        title = title,
                        textColor = colors.first,
                        borderColor = colors.second,
                        fillBottom = fill,
                        icon = when (index) {
                            0 -> Assets.ActivitiesGames
                            1 -> Assets.ActivitiesShop
                            else -> Assets.ActivitiesTrophy
                        },
                        arrow = when (index) {
                            0 -> Assets.ActivitiesArrowGreen
                            1 -> Assets.ActivitiesArrowOrange
                            else -> Assets.ActivitiesArrowYellow
                        },
                        entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + index * OVERLAY_POP_STAGGER_MILLIS,
                        height = PHONE_ACTIVITY_PANEL_HEIGHT,
                        onClick = {
                            dispatch(
                                when (index) {
                                    0 -> PocketPassEvent.OpenGames
                                    1 -> PocketPassEvent.OpenShop
                                    else -> PocketPassEvent.OpenLeaderboard
                                },
                            )
                        },
                    )
                }
                if (index < 2) Spacer(Modifier.height(metrics.dp(PHONE_ACTIVITY_PANEL_GAP)))
            }
        }

        ActivitiesSection.Shop -> ActivitiesScaffold(
            metrics = metrics,
            panes = panes,
            title = "Shop",
            subtitle = if (state.shop.purchasingItemIds.isNotEmpty()) {
                "${state.shop.tokenBalance} Tokens · Purchase pending…"
            } else {
                "${state.shop.tokenBalance} Tokens · Earn by playing games & interacting!"
            },
            backTag = "shop_back",
            onBack = { dispatch(PocketPassEvent.CloseShop) },
            art = { big ->
                SectionArt(metrics, Assets.ActivitiesCoinDefault, if (big) 496.082f else 300f, if (big) 496.082f else 300f, IdleMotion.CoinRock)
            },
        ) {
            (state.shop.purchaseError ?: state.shop.refreshError)?.let { message ->
                SectionNotice(metrics, message, "shop_notice")
                Spacer(Modifier.height(metrics.dp(24f)))
            }
            state.shop.categories.forEachIndexed { index, category ->
                MotionLayer(
                    entrance = EntranceMotion.OverlayPop,
                    delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + index * OVERLAY_POP_STAGGER_MILLIS,
                ) {
                    ShopCategoryPanel(metrics = metrics, category = category, state = state, dispatch = dispatch)
                }
                Spacer(Modifier.height(metrics.dp(40f)))
            }
        }

        ActivitiesSection.Games -> ActivitiesScaffold(
            metrics = metrics,
            panes = panes,
            title = "Games",
            subtitle = "Puzzle Swap, Bingo and World Tour",
            backTag = "games_back",
            onBack = { dispatch(PocketPassEvent.CloseGames) },
            art = { big -> SectionArt(metrics, Assets.GamesHero, if (big) 502.66f else 280f, if (big) 620.59f else 345.7f, IdleMotion.None) },
        ) {
            GameEntries.forEachIndexed { index, entry ->
                DeckSlot(metrics, 220f) {
                    GameRow(
                        metrics = metrics,
                        y = 0f,
                        entry = entry,
                        entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + index * OVERLAY_POP_STAGGER_MILLIS,
                        onClick = { dispatch(PocketPassEvent.OpenGame(entry.target)) },
                    )
                }
                Spacer(Modifier.height(metrics.dp(50f)))
            }
        }

        ActivitiesSection.Leaderboard -> {
            val settings = state.leaderboard.settingsVisible
            ActivitiesScaffold(
                metrics = metrics,
                panes = panes,
                title = if (settings) "Leaderboard Settings" else "Leaderboard",
                subtitle = if (settings) {
                    "Who you're ranked against"
                } else {
                    when (state.leaderboard.scope) {
                        LeaderboardScope.Friends -> "Just you and your friends"
                        LeaderboardScope.Global -> "Everyone on PocketPass"
                    }
                },
                backTag = if (settings) "leaderboard_settings_back" else "leaderboard_back",
                onBack = {
                    dispatch(if (settings) PocketPassEvent.CloseLeaderboardSettings else PocketPassEvent.CloseLeaderboard)
                },
                art = { big -> SectionArt(metrics, Assets.LeaderboardTrophyHero, if (big) 572.848f else 300f, if (big) 544.19f else 285f, IdleMotion.None) },
            ) {
                if (settings) {
                    listOf(
                        Triple(LeaderboardScope.Friends, "Friends", "Just you and your friends"),
                        Triple(LeaderboardScope.Global, "Global", "Everyone on PocketPass"),
                    ).forEach { (scope, title, subtitle) ->
                        DeckSlot(metrics, 220f) {
                            LeaderboardScopeOption(
                                metrics = metrics,
                                y = 0f,
                                title = title,
                                subtitle = subtitle,
                                selected = state.leaderboard.scope == scope,
                                tag = "leaderboard_scope_${scope.key}",
                                onClick = { dispatch(PocketPassEvent.SetLeaderboardScope(scope)) },
                            )
                        }
                        Spacer(Modifier.height(metrics.dp(50f)))
                    }
                } else {
                    state.leaderboard.refreshError?.let { message ->
                        SectionNotice(metrics, message, "leaderboard_notice")
                        Spacer(Modifier.height(metrics.dp(24f)))
                    }
                    val self = selfLeaderboardEntry(state)
                    MotionLayer(
                        entrance = EntranceMotion.OverlayPop,
                        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
                    ) {
                        LeaderboardPanel(
                            metrics = metrics,
                            scope = state.leaderboard.scope,
                            self = self?.let { (entry, rank) -> entry.copy(displayName = "You (#$rank)") },
                            entries = state.leaderboard.entries.filterNot { it.userId == self?.first?.userId },
                            onSelfClick = { dispatch(PocketPassEvent.OpenAchievements) },
                            onSettingsClick = { dispatch(PocketPassEvent.OpenLeaderboardSettings) },
                            maxHeight = 4_000f,
                        )
                    }
                    Spacer(Modifier.height(metrics.dp(40f)))
                }
            }
        }

        ActivitiesSection.Achievements -> {
            val rank = selfLeaderboardEntry(state)?.second
            ActivitiesScaffold(
                metrics = metrics,
                panes = panes,
                title = "Achievements",
                subtitle = if (rank != null) "You (#$rank)" else "You",
                backTag = "achievements_back",
                onBack = { dispatch(PocketPassEvent.CloseAchievements) },
                art = { big -> SectionArt(metrics, Assets.LeaderboardTrophyHero, if (big) 572.848f else 260f, if (big) 544.19f else 247f, IdleMotion.None) },
            ) {
                state.achievements.refreshError?.let { message ->
                    SectionNotice(metrics, message, "achievements_notice")
                    Spacer(Modifier.height(metrics.dp(24f)))
                }
                val byKey = state.achievements.achievements.associateBy(AchievementState::key)
                var expanded by remember { mutableStateOf(emptySet<AchievementSection>()) }
                AchievementSection.entries.forEachIndexed { index, section ->
                    val rows = AchievementCatalog.definitions.filter { it.section == section }
                    if (rows.isEmpty()) return@forEachIndexed
                    MotionLayer(
                        entrance = EntranceMotion.OverlayPop,
                        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + (index + 1) * OVERLAY_POP_STAGGER_MILLIS,
                    ) {
                        AchievementSectionPanel(
                            metrics = metrics,
                            section = section,
                            rows = rows.map { definition ->
                                definition to (
                                    byKey[definition.key]
                                        ?: AchievementState(key = definition.key, unlocked = false, unlockedAt = null, progressPercent = 0)
                                    )
                            },
                            expanded = section in expanded,
                            onToggle = { expanded = if (section in expanded) expanded - section else expanded + section },
                        )
                    }
                    Spacer(Modifier.height(metrics.dp(40f)))
                }
            }
        }
    }
}

@Composable
private fun DeckSlot(metrics: DesignMetrics, height: Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(height)),
    ) { content() }
}

@Composable
private fun SectionNotice(metrics: DesignMetrics, message: String, tag: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))
            .testTag(tag),
        color = pocketPalette.ink(Color(0xFFB31E3A)),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(26f),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SectionArt(
    metrics: DesignMetrics,
    resource: Int,
    width: Float,
    height: Float,
    idle: IdleMotion,
) {
    MotionLayer(entrance = EntranceMotion.ArcadeDrop, idle = idle) {
        FigmaAsset(
            resource = resource,
            modifier = Modifier.requiredSize(metrics.dp(width), metrics.dp(height)),
        )
    }
}

@Composable
private fun ActivitiesScaffold(
    metrics: DesignMetrics,
    panes: WidePanes?,
    title: String?,
    subtitle: String?,
    backTag: String?,
    onBack: (() -> Unit)?,
    art: @Composable (big: Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val header: @Composable () -> Unit = {
        if (title != null && backTag != null && onBack != null) {
            PhonePageHeader(metrics, title, subtitle, backTag, onBack)
        }
    }
    if (panes == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = metrics.dp(insets.top + 24f), bottom = metrics.dp(60f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()
            Spacer(Modifier.height(metrics.dp(if (title == null) 16f else 28f)))
            art(false)
            Spacer(Modifier.height(metrics.dp(48f)))
            content()
        }
    } else {
        PhonePanes(
            metrics = metrics,
            panes = panes,
            stage = {
                if (title == null) {
                    PhoneStageScroll(metrics) { art(true) }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = metrics.dp(insets.top + 24f), bottom = metrics.dp(insets.bottom + 40f)),
                    ) {
                        header()
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = metrics.dp(40f)),
                            contentAlignment = Alignment.Center,
                        ) { art(true) }
                    }
                }
            },
            deck = {
                Box(Modifier.fillMaxSize(), contentAlignment = if (title == null) Alignment.Center else Alignment.TopCenter) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = metrics.dp(insets.top + 40f), bottom = metrics.dp(60f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content()
                    }
                }
            },
        )
    }
}

@Composable
private fun PhoneActivitiesHero(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    big: Boolean,
) {
    val shuffled = state.activityVariant == ActivityVariant.Shuffled
    val swapProgress = rememberActivitiesSwapProgress(shuffled)
    val defaultIdle by remember(swapProgress) {
        derivedStateOf { !swapProgress.isRunning && swapProgress.value <= 0.0001f }
    }
    val alternateIdle by remember(swapProgress) {
        derivedStateOf { !swapProgress.isRunning && swapProgress.value >= 0.9999f }
    }
    val artSize = if (big) 400f else 320f
    val layerHeight = artSize + 150f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(metrics.dp(layerHeight))
                .clipToBounds(),
        ) {
            CounterLayer(
                metrics = metrics,
                alternate = false,
                leftCount = state.activitySnapshot?.coinCount ?: 22,
                rightCount = state.activitySnapshot?.puzzleCount ?: 3,
                idleEnabled = defaultIdle,
                artSize = artSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -size.height * swapProgress.value },
            )
            CounterLayer(
                metrics = metrics,
                alternate = true,
                leftCount = state.activitySnapshot?.nearbyCount ?: 12,
                rightCount = state.activitySnapshot?.locationCount ?: 3,
                idleEnabled = alternateIdle,
                artSize = artSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = size.height * (1f - swapProgress.value) },
            )
        }
        Spacer(Modifier.height(metrics.dp(24f)))
        ShuffleButton(metrics) { dispatch(PocketPassEvent.ShuffleActivities) }
    }
}

@Composable
private fun CounterLayer(
    metrics: DesignMetrics,
    alternate: Boolean,
    leftCount: Int,
    rightCount: Int,
    idleEnabled: Boolean,
    artSize: Float,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        Counter(
            metrics = metrics,
            resource = if (alternate) Assets.ActivitiesCoinAlt else Assets.ActivitiesCoinDefault,
            count = leftCount,
            color = pocketPalette.ink(if (alternate) Color(0xFF33398D) else Color(0xFF803427)),
            entrance = EntranceMotion.ActivityCoinSettle,
            idle = if (idleEnabled) IdleMotion.CoinRock else IdleMotion.None,
            artSize = artSize,
            delay = 0,
        )
        Counter(
            metrics = metrics,
            resource = if (alternate) Assets.ActivitiesPuzzleAlt else Assets.ActivitiesPuzzleDefault,
            count = rightCount,
            color = pocketPalette.ink(if (alternate) Color(0xFF851111) else Color(0xFF11851E)),
            entrance = EntranceMotion.ActivityPuzzleSettle,
            idle = if (idleEnabled) IdleMotion.PuzzleBob else IdleMotion.None,
            artSize = artSize * 484.11f / 496.082f,
            delay = 70,
        )
    }
}

@Composable
private fun Counter(
    metrics: DesignMetrics,
    resource: Int,
    count: Int,
    color: Color,
    entrance: EntranceMotion,
    idle: IdleMotion,
    artSize: Float,
    delay: Int,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MotionLayer(
            modifier = Modifier.requiredSize(metrics.dp(artSize)),
            entrance = entrance,
            idle = idle,
            delayMillis = delay,
            idlePhaseMillis = if (delay > 0) 900 else 0,
        ) {
            FigmaAsset(resource = resource, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(metrics.dp(10f)))
        MotionLayer(entrance = EntranceMotion.ActivityCountRise, delayMillis = 80 + delay) {
            Text(
                text = count.toString(),
                color = color,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(if (artSize > 300f) 128f else 96f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShuffleButton(metrics: DesignMetrics, onClick: () -> Unit) {
    val shape = RoundedCornerShape(metrics.dp(96f))
    MotionLayer(entrance = EntranceMotion.ActivityButtonRise, delayMillis = 220) {
        Row(
            modifier = Modifier
                .width(metrics.dp(480f))
                .height(metrics.dp(160f))
                .phoneShadow(metrics, 96f, 10f, 0.14f)
                .clip(shape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to pocketPalette.surface,
                            0.44712f to pocketPalette.surface,
                            1f to pocketPalette.tint(Color(0xFFA8FFC7)),
                        ),
                    ),
                    metrics.dp(16f),
                    Color(0xFF73E881),
                    shape,
                )
                .testTag("shuffle_activities")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Shuffle",
                modifier = Modifier.offset(y = metrics.dp(-4f)),
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFF94FF9B), Color(0xFF20AF42))),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(82.814f),
                ),
                maxLines = 1,
            )
        }
    }
}
