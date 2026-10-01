package com.pocketpass.app.ui.screens

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pocketpass.app.ui.components.dropLastCodePoint
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import okio.Path.Companion.toPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeCap
import coil3.compose.AsyncImage
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.domain.model.PROFILE_NAME_MAX_LENGTH
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.ui.components.AvatarCollage
import com.pocketpass.app.ui.components.ExitingOverlay
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.IMAGE_MESSAGE_PLACEHOLDER_BODY
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AchievementDefinition
import com.pocketpass.app.domain.model.AchievementSection
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiTraitField
import com.pocketpass.app.model.ShopItemStatus
import com.pocketpass.app.ui.mii.rememberMiiTraitIcon
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.state.PendingState
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.model.BioEditorUiState
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.feature.ACCOUNT_SETUP_AGE_MAX
import com.pocketpass.app.feature.ACCOUNT_SETUP_AGE_MIN
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ThemeMode
import com.pocketpass.app.update.AppUpdateFailureStage
import com.pocketpass.app.update.AppUpdatePhase
import com.pocketpass.app.update.AppUpdateUiState
import com.pocketpass.app.update.releaseNoteLines
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.LocalDesignOrigin
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.LocalDesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.anchoredX
import com.pocketpass.app.ui.components.BelowTabBarShape
import com.pocketpass.app.ui.components.rememberBelowTabBarFocusViewport
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.LocalRouteRevealGeneration
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.ControllerRestFrame
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerRestFrame
import com.pocketpass.app.ui.controller.controllerRestFrameInside
import com.pocketpass.app.ui.controller.controllerRestFrameOutside
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.controller.FOCUS_SLIDE_DAMPING_RATIO
import com.pocketpass.app.ui.controller.FOCUS_SLIDE_STIFFNESS
import com.pocketpass.app.ui.components.POCKET_KEYBOARD_HEIGHT
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketKeyboardPalette
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.TYPING_CARET_INLINE_ID
import com.pocketpass.app.ui.components.TypingCaret
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.typingCaretInline
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import androidx.compose.ui.draw.alpha
import com.pocketpass.app.mii.MII_FIRST_SLOT
import com.pocketpass.app.mii.MII_SLOT_COUNT
import com.pocketpass.app.mii.MiiSlotSummary
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun BottomScreen(
    route: PocketPassRoute,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    when (route) {
        is PocketPassRoute.MessageDetail -> MessageDetailBottom(
            conversationId = route.conversationId,
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.NewGroup -> NewGroupBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.Accessibility -> AccessibilityBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.AppSettings -> AppSettingsBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.ChatColours -> ChatColoursBottom(state, dispatch)
        PocketPassRoute.Social -> SocialBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.EditInfo -> EditInfoBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.AccountSecurity -> AccountSecurityBottom(
            state = state.accountSecurity,
            dispatch = dispatch,
        )
        PocketPassRoute.Contributors -> ContributorsBottom(dispatch = dispatch)
        PocketPassRoute.NotificationSettings -> NotificationSettingsBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.BoardNotificationSettings -> BoardNotificationSettingsBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.AppUpdate -> AppUpdateBottom(
            state = state,
            dispatch = dispatch,
        )
        PocketPassRoute.WidgetMaker -> WidgetsBottom(state = state, dispatch = dispatch)
        is PocketPassRoute.WidgetEditor -> WidgetEditorBottom(
            state = state,
            dispatch = dispatch,
            designId = route.designId,
        )
        is PocketPassRoute.Root -> when (route.destination) {
            PocketPassDestination.Home -> HomeBottom(state, dispatch)
            PocketPassDestination.Activities -> ActivitiesBottom(state, dispatch)
            PocketPassDestination.Messages -> if (state.boardsVisible) BoardsBottom(state, dispatch) else MessagesBottom(state, dispatch)
            PocketPassDestination.Friends -> FriendsBottom(state, dispatch)
            PocketPassDestination.Settings -> SettingsBottom(state, dispatch)
        }
    }
}

@Composable
private fun HomeBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.PanelRise) { metrics ->
        val palette = pocketPalette
        val sortMenuMounted = rememberSortMenuMounted(state.sortMenuOpen)
        SectionTitle(
            metrics = metrics,
            title = "Recent Interactions",
            color = palette.teal,
            filterAsset = Assets.Filter,
            buttonBorder = palette.tealBorder,
            buttonTint = palette.tint(Color(0xFFBDF8CB)),
            onFilter = { dispatch(PocketPassEvent.ToggleSortMenu) },
        )
        NotificationHeaderAction(
            metrics = metrics,
            color = palette.teal,
            unreadCount = state.unreadNotificationCount,
            onClick = { dispatch(PocketPassEvent.ToggleNotifications) },
        )
        if (state.recentInteractions.isEmpty()) {
            RecentInteractionsEmptyPanel(metrics)
        } else {
            val sort = state.recentInteractionsSort
            val sorted = remember(state.recentInteractions, sort) {
                when (sort) {
                    RecentInteractionsSort.LatestEncounter ->
                        state.recentInteractions.sortedByDescending { it.occurredAt }
                    RecentInteractionsSort.OldestEncounter ->
                        state.recentInteractions.sortedBy { it.occurredAt }
                    RecentInteractionsSort.NameAZ ->
                        state.recentInteractions.sortedBy { encounter ->
                            encounter.profile.displayName.lowercase()
                        }
                }
            }
            HorizontalCards(
                metrics = metrics,
                people = sorted.toEncounterCardUi(),
                cardBorder = palette.tealBorder,
                cardBottom = palette.tint(Color(0xFFBDF8CB)),
                nameColor = palette.teal,
                detailColor = palette.ink(Color(0xFF2F948C)),
                onCard = {
                    dispatch(
                        PocketPassEvent.OpenUserProfile(
                            userId = it,
                            source = ProfileViewerSource.RecentInteraction,
                        ),
                    )
                },
            )
        }
        if (sortMenuMounted.value) {
            SortMenu(
                metrics = metrics,
                open = state.sortMenuOpen,
                selected = state.recentInteractionsSort,
                borderColor = palette.tealBorder,
                textColor = palette.teal,
                tagPrefix = "recent_sort",
                onSelect = { sort ->
                    dispatch(PocketPassEvent.SetRecentInteractionsSort(sort))
                    dispatch(PocketPassEvent.CloseSortMenu)
                },
                onDismiss = { dispatch(PocketPassEvent.CloseSortMenu) },
                onExited = { sortMenuMounted.value = false },
            )
        }
    }
}

@Composable
private fun rememberSortMenuMounted(open: Boolean): MutableState<Boolean> {
    val mounted = remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) mounted.value = true
    }
    return mounted
}

private const val SORT_MENU_FOCUS_LAYER = 10

private fun sortMenuRowTag(prefix: String, sort: RecentInteractionsSort) = "${prefix}_${sort.key}"

@Composable
private fun SortMenu(
    metrics: DesignMetrics,
    open: Boolean,
    selected: RecentInteractionsSort,
    borderColor: Color,
    textColor: Color,
    tagPrefix: String,
    onSelect: (RecentInteractionsSort) -> Unit,
    onDismiss: () -> Unit,
    onExited: () -> Unit,
) {
    if (open) {
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch)
                .testTag("${tagPrefix}_scrim")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
    val focus = LocalControllerFocus.current
    LaunchedEffect(open) {
        if (open) {
            focus?.focus(sortMenuRowTag(tagPrefix, selected), reveal = false)
        } else {
            focus?.focus("section_filter", reveal = false)
        }
    }
    val expand = remember { Animatable(0f) }
    LaunchedEffect(open) {
        val target = if (open) 1f else 0f
        if (!platformAnimationsEnabled()) {
            expand.snapTo(target)
        } else if (open) {
            expand.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 430f),
            )
        } else {
            expand.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
            )
        }
        if (!open) onExited()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.93f, 0.31f)
                scaleX = expand.value.coerceAtLeast(0f)
                scaleY = expand.value.coerceAtLeast(0f)
                alpha = (expand.value * 2.2f).coerceIn(0f, 1f)
            },
    ) {
        val panelShape = RoundedCornerShape(metrics.dp(48f))
        Box(
            Modifier
                .anchoredBounds(metrics, 632f, 411f, 558f, 356f, DesignAnchor.End)
                .pocketShadow(metrics, 48f),
        )
        Column(
            modifier = Modifier
                .anchoredBounds(metrics, 632f, 399f, 558f, 356f, DesignAnchor.End)
                .clip(panelShape)
                .pocketFrame(pocketPalette.surface, metrics.dp(8f), borderColor, panelShape)
                .padding(vertical = metrics.dp(22f)),
        ) {
            SortMenuRow(
                metrics = metrics,
                label = "Latest Encounter",
                selected = selected == RecentInteractionsSort.LatestEncounter,
                textColor = textColor,
                tag = sortMenuRowTag(tagPrefix, RecentInteractionsSort.LatestEncounter),
                focusable = open,
            ) { onSelect(RecentInteractionsSort.LatestEncounter) }
            SortMenuRow(
                metrics = metrics,
                label = "Oldest Encounter",
                selected = selected == RecentInteractionsSort.OldestEncounter,
                textColor = textColor,
                tag = sortMenuRowTag(tagPrefix, RecentInteractionsSort.OldestEncounter),
                focusable = open,
            ) { onSelect(RecentInteractionsSort.OldestEncounter) }
            SortMenuRow(
                metrics = metrics,
                label = "Name A-Z",
                selected = selected == RecentInteractionsSort.NameAZ,
                textColor = textColor,
                tag = sortMenuRowTag(tagPrefix, RecentInteractionsSort.NameAZ),
                focusable = open,
            ) { onSelect(RecentInteractionsSort.NameAZ) }
        }
    }
}

@Composable
internal fun SortMenuRow(
    metrics: DesignMetrics,
    label: String,
    selected: Boolean,
    textColor: Color,
    tag: String,
    focusable: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(metrics.dp(104f))
            .testTag(tag)
            .then(
                if (focusable) {
                    Modifier.controllerTarget(
                        tag,
                        layer = SORT_MENU_FOCUS_LAYER,
                        cornerRadius = 40f,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = metrics.dp(44f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.align(Alignment.CenterStart),
            color = if (selected) textColor else textColor.copy(alpha = 0.62f),
            fontFamily = Rubik,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = metrics.sp(42f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .requiredSize(metrics.dp(52f))
                .clip(CircleShape)
                .pocketFrame(
                    if (selected) Color(0xFF3CBC29) else Color.Transparent,
                    metrics.dp(7f),
                    if (selected) Color(0xFF2F9A20) else pocketPalette.line(Color(0xFF9FB6C1)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(30f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FriendsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.PanelRise) { metrics ->
        val palette = pocketPalette
        val sortMenuMounted = rememberSortMenuMounted(state.sortMenuOpen)
        SectionTitle(
            metrics = metrics,
            title = "All Friends (${state.friends.size})",
            color = palette.ink(Color(0xFF820A79)),
            filterAsset = Assets.FriendsFilter,
            buttonBorder = Color(0xFFCB4AC0),
            buttonTint = palette.tint(Color(0xFFFED3FF)),
            onFilter = { dispatch(PocketPassEvent.ToggleSortMenu) },
        )
        FriendsHeaderActions(
            metrics = metrics,
            onAddFriend = { dispatch(PocketPassEvent.OpenAddFriend) },
        )
        val friendsSort = state.friendsSort
        val sortedFriends = remember(state.friends, friendsSort) {
            when (friendsSort) {
                RecentInteractionsSort.LatestEncounter ->
                    state.friends.sortedByDescending { friend ->
                        friend.lastInteractionAt ?: Instant.fromEpochSeconds(0)
                    }
                RecentInteractionsSort.OldestEncounter ->
                    state.friends.sortedWith(
                        compareBy(nullsLast()) { friend -> friend.lastInteractionAt },
                    )
                RecentInteractionsSort.NameAZ ->
                    state.friends.sortedBy { friend ->
                        friend.profile.displayName.lowercase()
                    }
            }
        }
        if (state.friends.isNotEmpty()) {
            HorizontalCards(
                metrics = metrics,
                people = sortedFriends.toFriendCardUi(),
                cardBorder = Color(0xFFCB4AC0),
                cardBottom = palette.tint(Color(0xFFFED3FF)),
                nameColor = palette.ink(Color(0xFF511D6B)),
                detailColor = palette.ink(Color(0xFF820A79)),
                onCard = {
                    dispatch(
                        PocketPassEvent.OpenUserProfile(
                            userId = it,
                            source = ProfileViewerSource.Friend,
                        ),
                    )
                },
            )
            state.friendsRefreshError?.let { refreshError ->
                FriendsRefreshNotice(
                    metrics = metrics,
                    message = refreshError,
                    refreshing = state.friendsRefreshing,
                    onRetry = { dispatch(PocketPassEvent.RefreshFriends) },
                )
            }
        } else {
            FriendsStatusPanel(
                metrics = metrics,
                loading = state.friendsLoading || state.friendsRefreshing,
                error = state.friendsRefreshError,
                onAddFriend = { dispatch(PocketPassEvent.OpenAddFriend) },
                onRetry = { dispatch(PocketPassEvent.RefreshFriends) },
            )
        }
        if (sortMenuMounted.value) {
            SortMenu(
                metrics = metrics,
                open = state.sortMenuOpen,
                selected = state.friendsSort,
                borderColor = Color(0xFFCB4AC0),
                textColor = palette.ink(Color(0xFF511D6B)),
                tagPrefix = "friends_sort",
                onSelect = { sort ->
                    dispatch(PocketPassEvent.SetFriendsSort(sort))
                    dispatch(PocketPassEvent.CloseSortMenu)
                },
                onDismiss = { dispatch(PocketPassEvent.CloseSortMenu) },
                onExited = { sortMenuMounted.value = false },
            )
        }
    }
}

@Composable
private fun RecentInteractionsEmptyPanel(metrics: DesignMetrics) {
    EmptyStateRow(
        metrics = metrics,
        icon = Assets.NavHome,
        title = "No recent interactions yet",
        subtitle = "Nearby PocketPass users you meet appear here",
        tag = "recent_interactions_empty",
    )
}

@Composable
internal fun EmptyStateRow(
    metrics: DesignMetrics,
    icon: PocketAsset,
    title: String,
    subtitle: String,
    tag: String,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = 435f,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = tag,
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 43f, 38.675f, 142.65f, 142.65f)
                .clip(CircleShape)
                .pocketFrame(pocketPalette.surface, metrics.dp(9f), Color(0xFF517F92), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FigmaAsset(
                resource = icon,
                modifier = Modifier.requiredSize(metrics.dp(74f)),
                colorFilter = ColorFilter.tint(Color(0xFF3F7D90)),
            )
        }
        Text(
            text = title,
            modifier = Modifier.anchoredBounds(metrics, 210f, 42f, 900f, 76f, DesignAnchor.Stretch),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            modifier = Modifier.anchoredBounds(metrics, 210f, 117f, 900f, 55f, DesignAnchor.Stretch),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun FriendsStatusPanel(
    metrics: DesignMetrics,
    loading: Boolean,
    error: String?,
    onAddFriend: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = when {
        loading -> "Loading friends…"
        error != null -> "Friends unavailable"
        else -> "No friends yet"
    }
    val subtitle = when {
        loading -> "Checking PocketPass for your friends"
        error != null -> "Tap to try again"
        else -> "Tap to add someone with a Friend Code"
    }
    val onClick: (() -> Unit)? = when {
        loading -> null
        error != null -> onRetry
        else -> onAddFriend
    }
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = 435f,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = if (error == null) "friends_empty_add" else "friends_retry",
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 43f, 38.675f, 142.65f, 142.65f)
                .clip(CircleShape)
                .pocketFrame(pocketPalette.surface, metrics.dp(9f), Color(0xFF517F92), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FigmaAsset(
                resource = Assets.NavFriends,
                modifier = Modifier.requiredSize(metrics.dp(74f)),
                colorFilter = ColorFilter.tint(Color(0xFF3F7D90)),
            )
        }
        Text(
            text = title,
            modifier = Modifier.designBounds(metrics, 210f, 42f, 767f, 76f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            modifier = Modifier.anchoredBounds(metrics, 210f, 117f, 820f, 55f, DesignAnchor.Stretch),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            maxLines = 1,
        )
        if (onClick != null) {
            FigmaAsset(
                resource = Assets.SettingsArrow,
                colorFilter = chevronTint(),
                modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
            )
        }
    }
}

@Composable
private fun FriendsRefreshNotice(
    metrics: DesignMetrics,
    message: String,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    val palette = pocketPalette
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = if (refreshing) "Refreshing friends…" else "$message  Tap to retry.",
        modifier = Modifier
            .designBounds(metrics, 50f, 389f, 1060f, 38f)
            .testTag("friends_refresh_notice")
            .clickable(
                enabled = !refreshing,
                interactionSource = interaction,
                indication = null,
                onClick = onRetry,
            ),
        color = palette.ink(Color(0xFF820A79)),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(26f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FriendsHeaderActions(
    metrics: DesignMetrics,
    onAddFriend: () -> Unit,
) {
    val palette = pocketPalette
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(metrics.dp(40f))
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 303f, 80f, 80f, DesignAnchor.End)
            .pocketShadow(metrics, 40f),
    )
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 297f, 80f, 80f, DesignAnchor.End)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFFED3FF))),
                ),
                metrics.dp(6f),
                Color(0xFFCB4AC0),
                shape,
            )
            .controllerTarget("add_friend") { onAddFriend() }
            .testTag("add_friend")
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onAddFriend,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = palette.ink(Color(0xFF820A79)),
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.7f),
                strokeWidth = 11f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = palette.ink(Color(0xFF820A79)),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height / 2f),
                strokeWidth = 11f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MessagesHeaderActions(
    metrics: DesignMetrics,
    onNewGroup: () -> Unit,
) {
    val palette = pocketPalette
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(metrics.dp(40f))
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 303f, 80f, 80f, DesignAnchor.End)
            .pocketShadow(metrics, 40f),
    )
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 297f, 80f, 80f, DesignAnchor.End)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFD1EDFB))),
                ),
                metrics.dp(6f),
                pocketPalette.tealBorder,
                shape,
            )
            .controllerTarget("messages_new_group") { onNewGroup() }
            .testTag("messages_new_group")
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onNewGroup,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = palette.teal,
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.7f),
                strokeWidth = 11f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = palette.teal,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height / 2f),
                strokeWidth = 11f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun NotificationHeaderAction(
    metrics: DesignMetrics,
    color: Color,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(metrics.dp(40f))
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 303f, 80f, 80f, DesignAnchor.End)
            .pocketShadow(metrics, 40f),
    )
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1008f, 297f, 80f, 80f, DesignAnchor.End)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFBDF8CB))),
                ),
                metrics.dp(6f),
                pocketPalette.tealBorder,
                shape,
            )
            .controllerTarget(
                "notifications",
                badgeBounds = if (unreadCount > 0) androidx.compose.ui.geometry.Rect(50f, -25f, 105f, 30f) else null,
            ) { onClick() }
            .testTag("notifications")
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(metrics.dp(12f)),
        ) {
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.62f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 9f,
                cap = StrokeCap.Round,
            ),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.51f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.73f),
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.51f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.73f),
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.73f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.73f),
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = color,
            radius = 6f,
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.87f),
        )
        }
    }
    if (unreadCount > 0) {
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 1058f, 272f, 55f, 55f, DesignAnchor.End)
                .clip(RoundedCornerShape(metrics.dp(27.5f)))
                .pocketFrame(
                    Color(0xFFF44F4F),
                    metrics.dp(5f),
                    Color.White,
                    RoundedCornerShape(metrics.dp(27.5f)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = unreadCount.coerceAtMost(99).toString(),
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(26f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun FriendsAddFriendOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    AddFriendOverlay(
        metrics = metrics,
        state = state,
        onClose = { dispatch(PocketPassEvent.CloseFriendsOverlay) },
        onValueChange = { dispatch(PocketPassEvent.UpdateFriendCode(it)) },
        onSubmit = { dispatch(PocketPassEvent.SubmitFriendCode) },
    )
}

@Composable
private fun AddFriendOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    onClose: () -> Unit,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val palette = pocketPalette
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    var keypadVisible by remember { mutableStateOf(false) }
    val keypadProgress by animateFloatAsState(
        targetValue = if (keypadVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "friendCodeKeypad",
    )
    val focus = LocalControllerFocus.current
    LaunchedEffect(Unit) { focus?.focus("friend_code_field", reveal = false) }
    val submitCode = {
        keypadVisible = false
        onSubmit()
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("add_friend_overlay")
            .controllerFocusBarrier("add_friend_overlay", layer = ADD_FRIEND_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 94f, 1080f, 920f)
            .graphicsLayer {
                translationY = entrance.value - ADD_FRIEND_KEYPAD_LIFT * keypadProgress
            }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 80f, 1080f, 920f)
            .graphicsLayer {
                translationY = entrance.value - ADD_FRIEND_KEYPAD_LIFT * keypadProgress
            }
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        0.58f to pocketPalette.surface,
                        1f to pocketPalette.tint(Color(0xFFFED3FF)),
                    ),
                ),
                metrics.dp(15f),
                Color(0xFFCB4AC0),
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("add_friend_panel"),
    ) {
        Text(
            text = "Add Friend",
            modifier = Modifier.designBounds(metrics, 58f, 50f, 760f, 94f),
            color = palette.ink(Color(0xFF511D6B)),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        val closeInteraction = remember { MutableInteractionSource() }
        Canvas(
            Modifier
                .designBounds(metrics, 945f, 55f, 72f, 72f)
                .testTag("close_add_friend")
                .controllerTarget("close_add_friend", layer = ADD_FRIEND_FOCUS_LAYER) { onClose() }
                .clickable(
                    interactionSource = closeInteraction,
                    indication = null,
                    onClick = onClose,
                ),
        ) {
            drawLine(
                palette.ink(Color(0xFF820A79)),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawLine(
                palette.ink(Color(0xFF820A79)),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = "Your Friend Code",
            modifier = Modifier.designBounds(metrics, 62f, 177f, 620f, 62f),
            color = palette.ink(Color(0xFF820A79)),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(43f),
            maxLines = 1,
        )
        Text(
            text = state.myFriendCode?.formatted ?: "•••• ••••",
            modifier = Modifier.designBounds(metrics, 62f, 241f, 650f, 85f),
            color = palette.ink(Color(0xFF511D6B)),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            letterSpacing = metrics.sp(4f),
            maxLines = 1,
        )
        val copyInteraction = remember { MutableInteractionSource() }
        val copyShape = RoundedCornerShape(metrics.dp(45f))
        val copyFriendCode = {
            clipboardManager.setText(AnnotatedString(state.myFriendCode?.value.orEmpty()))
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 773f, 214f, 238f, 96f)
                .clip(copyShape)
                .pocketFrame(palette.surfaceSunken, metrics.dp(9f), Color(0xFFCB4AC0), copyShape)
                .testTag("copy_friend_code")
                .controllerTarget("copy_friend_code", layer = ADD_FRIEND_FOCUS_LAYER, cornerRadius = 45f) {
                    if (state.myFriendCode != null) copyFriendCode()
                }
                .clickable(
                    enabled = state.myFriendCode != null,
                    interactionSource = copyInteraction,
                    indication = null,
                    onClick = copyFriendCode,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Copy",
                color = palette.ink(Color(0xFF820A79)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(39f),
            )
        }
        Text(
            text = "Enter Friend Code",
            modifier = Modifier.designBounds(metrics, 62f, 374f, 650f, 62f),
            color = palette.ink(Color(0xFF820A79)),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(43f),
            maxLines = 1,
        )
        FriendCodeField(
            metrics = metrics,
            value = state.friendCodeEntry,
            onFocusField = { keypadVisible = !keypadVisible },
        )
        val enabled = state.friendCodeEntry.length == 8 && !state.friendCodeSubmitting
        val submitInteraction = remember { MutableInteractionSource() }
        val submitShape = RoundedCornerShape(metrics.dp(58f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 263f, 659f, 554f, 116f)
                .clip(submitShape)
                .pocketFrame(
                    Brush.verticalGradient(
                        listOf(
                            palette.surface,
                            if (enabled) palette.tint(Color(0xFFA8FFC7)) else palette.surfaceLow,
                        ),
                    ),
                    metrics.dp(12f),
                    if (enabled) Color(0xFF73E881) else palette.borderGrey,
                    submitShape,
                )
                .testTag("submit_friend_code")
                .controllerTarget("submit_friend_code", layer = ADD_FRIEND_FOCUS_LAYER, cornerRadius = 58f) {
                    if (enabled) submitCode()
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = submitInteraction,
                    indication = null,
                    onClick = submitCode,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.friendCodeSubmitting) "Finding…" else "Add Friend",
                color = if (enabled) palette.ink(Color(0xFF27853A)) else palette.textMuted,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(50f),
                maxLines = 1,
            )
        }
        Text(
            text = state.friendCodeError ?: state.friendCodeMessage.orEmpty(),
            modifier = Modifier.designBounds(metrics, 62f, 820f, 956f, 42f),
            color = if (state.friendCodeError != null) {
                palette.ink(Color(0xFFB31E3A))
            } else {
                palette.ink(Color(0xFF27853A))
            },
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    val keypadShown = remember { derivedStateOf { keypadProgress > 0.001f } }
    if (keypadShown.value) {
        PocketKeyboard(
            metrics = metrics,
            layout = PocketKeyboardLayout.Numeric,
            submitLabel = "Add",
            submitEnabled = state.friendCodeEntry.length == 8 && !state.friendCodeSubmitting,
            onKey = { key ->
                when (key) {
                    is PocketKey.Character ->
                        if (state.friendCodeEntry.length < 8) {
                            onValueChange(state.friendCodeEntry + key.value)
                        }
                    PocketKey.Backspace -> onValueChange(state.friendCodeEntry.dropLast(1))
                    PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
                    PocketKey.Submit -> submitCode()
                }
            },
            modifier = Modifier.graphicsLayer {
                translationY = (1f - keypadProgress) * POCKET_KEYBOARD_HEIGHT
            },
            palette = PocketKeyboardPalette.FriendCode,
            focusLayer = ADD_FRIEND_FOCUS_LAYER,
            focusReturnTag = "friend_code_field",
        )
    }
}

@Composable
private fun FriendCodeField(
    metrics: DesignMetrics,
    value: String,
    onFocusField: () -> Unit,
) {
    val palette = pocketPalette
    Row(
        modifier = Modifier
            .designBounds(metrics, 62f, 466f, 956f, 124f)
            .testTag("friend_code_field")
            .controllerTarget("friend_code_field", layer = ADD_FRIEND_FOCUS_LAYER, cornerRadius = 24f) {
                onFocusField()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocusField,
            ),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(8) { index ->
            val filled = index < value.length
            val active = index == value.length.coerceAtMost(7)
            val slotShape = RoundedCornerShape(metrics.dp(24f))
            Box(
                modifier = Modifier
                    .requiredSize(metrics.dp(105.5f), metrics.dp(118f))
                    .clip(slotShape)
                    .pocketFrame(
                        palette.surfaceSunken,
                        metrics.dp(if (active) 8f else 6f),
                        if (active) {
                            palette.tealBorder
                        } else if (filled) {
                            Color(0xFF73E881)
                        } else {
                            Color(0xFFCB4AC0)
                        },
                        slotShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.getOrNull(index)?.toString().orEmpty(),
                    color = palette.ink(Color(0xFF511D6B)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(64f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun BioEditorBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val active = if (state.bioEditor.visible) state.bioEditor else null
    var retained by remember { mutableStateOf<BioEditorUiState?>(null) }
    val progress = remember { Animatable(0f) }
    SideEffect {
        if (active != null) retained = active
    }
    LaunchedEffect(active != null) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(if (active != null) 1f else 0f)
            if (active == null) retained = null
            return@LaunchedEffect
        }
        if (active != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
        } else if (retained != null) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
            retained = null
        }
    }
    val editor = active ?: retained ?: return
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .graphicsLayer { alpha = progress.value }
            .background(pocketPalette.scrim)
            .testTag("bio_editor_overlay")
            .controllerFocusBarrier("bio_editor_overlay", layer = BIO_EDITOR_FOCUS_LAYER)
            .clickable(
                enabled = active != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseBioEditor) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 500f)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 500f)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        0.62f to pocketPalette.surface,
                        1f to pocketPalette.tint(Color(0xFFBDF8CB)),
                    ),
                ),
                metrics.dp(15f),
                pocketPalette.tealBorder,
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("bio_editor_panel"),
    ) {
        Text(
            text = "Edit Bio",
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        val closeInteraction = remember { MutableInteractionSource() }
        Canvas(
            Modifier
                .designBounds(metrics, 945f, 55f, 72f, 72f)
                .testTag("close_bio_editor")
                .controllerTarget("close_bio_editor", layer = BIO_EDITOR_FOCUS_LAYER) {
                    dispatch(PocketPassEvent.CloseBioEditor)
                }
                .clickable(
                    interactionSource = closeInteraction,
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseBioEditor) },
        ) {
            drawLine(
                palette.ink(Color(0xFF2F948C)),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawLine(
                palette.ink(Color(0xFF2F948C)),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
        }
        val fieldShape = RoundedCornerShape(metrics.dp(45f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 58f, 166f, 964f, 230f)
                .clip(fieldShape)
                .pocketFrame(palette.surfaceSunken, metrics.dp(8f), palette.tealBorder, fieldShape)
                .testTag("bio_field"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = metrics.dp(36f),
                        vertical = metrics.dp(26f),
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                if (editor.draft.isEmpty()) {
                    Text(
                        text = buildAnnotatedString {
                            appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                            append("Say hello to everyone you meet!")
                        },
                        inlineContent = typingCaretInline(metrics, pocketPalette.teal, 40f),
                        color = palette.ink(Color(0xFF8FB9C6)),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(38f),
                        lineHeight = metrics.sp(48f),
                        maxLines = 4,
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            append(editor.draft)
                            appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                        },
                        inlineContent = typingCaretInline(metrics, pocketPalette.teal, 40f),
                        color = pocketPalette.teal,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(38f),
                        lineHeight = metrics.sp(48f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val editorError = editor.error
        if (editorError != null) {
            Text(
                text = editorError,
                modifier = Modifier.designBounds(metrics, 58f, 414f, 700f, 40f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                maxLines = 1,
            )
        }
        Text(
            text = "${editor.draft.length}/$BIO_MAX_LENGTH",
            modifier = Modifier.designBounds(metrics, 58f, 414f, 964f, 40f),
            color = if (editor.draft.length >= BIO_MAX_LENGTH) {
                palette.ink(Color(0xFFB31E3A))
            } else {
                palette.tealBorder
            },
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
    PocketKeyboard(
        metrics = metrics,
        layout = PocketKeyboardLayout.Text,
        submitLabel = "Save",
        submitEnabled = !editor.saving,
        canBackspace = editor.draft.isNotEmpty(),
        onKey = { key ->
            when (key) {
                is PocketKey.Character ->
                    dispatch(PocketPassEvent.UpdateBioDraft(editor.draft + key.value))

                PocketKey.Space ->
                    dispatch(PocketPassEvent.UpdateBioDraft(editor.draft + " "))

                PocketKey.Backspace ->
                    dispatch(PocketPassEvent.UpdateBioDraft(editor.draft.dropLast(1)))

                PocketKey.Submit -> dispatch(PocketPassEvent.SaveBio)
                PocketKey.Alphabet, PocketKey.Emoji -> Unit
            }
        },
        modifier = Modifier.graphicsLayer {
            translationY = (1f - progress.value) * POCKET_KEYBOARD_HEIGHT
        },
        palette = PocketKeyboardPalette.Home,
        focusLayer = BIO_EDITOR_FOCUS_LAYER,
    )
}

private const val BIO_EDITOR_FOCUS_LAYER = 10

private data class ProfileFieldEditorContent(
    val draft: String,
    val error: String?,
    val errorShakeNonce: Int,
    val submitEnabled: Boolean,
)

@Composable
fun NameEditorBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val editor = state.nameEditor
    ProfileFieldEditorOverlay(
        metrics = metrics,
        active = if (editor.visible) {
            ProfileFieldEditorContent(editor.draft, editor.error, editor.errorShakeNonce, editor.valid && !editor.saving)
        } else {
            null
        },
        tagPrefix = "name_editor",
        closeTag = "close_name_editor",
        title = "Edit Name",
        hint = "The name everyone will see",
        placeholder = "yourname",
        layout = PocketKeyboardLayout.Text,
        onClose = { dispatch(PocketPassEvent.CloseNameEditor) },
        onDraft = { dispatch(PocketPassEvent.UpdateNameDraft(it)) },
        onSubmit = { dispatch(PocketPassEvent.SaveName) },
    ) { content ->
        Text(
            text = "${content.draft.length}/$PROFILE_NAME_MAX_LENGTH",
            modifier = Modifier.designBounds(metrics, 58f, 414f, 964f, 40f),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
fun AgeEditorBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val editor = state.ageEditor
    ProfileFieldEditorOverlay(
        metrics = metrics,
        active = if (editor.visible) {
            ProfileFieldEditorContent(editor.draft, editor.error, editor.errorShakeNonce, editor.valid && !editor.saving)
        } else {
            null
        },
        tagPrefix = "age_editor",
        closeTag = "close_age_editor",
        title = "Edit Age",
        hint = "Shown on your profile",
        placeholder = "--",
        layout = PocketKeyboardLayout.Numeric,
        onClose = { dispatch(PocketPassEvent.CloseAgeEditor) },
        onDraft = { dispatch(PocketPassEvent.UpdateAgeDraft(it)) },
        onSubmit = { dispatch(PocketPassEvent.SaveAge) },
    ) {
        Text(
            text = "$ACCOUNT_SETUP_AGE_MIN to $ACCOUNT_SETUP_AGE_MAX",
            modifier = Modifier.designBounds(metrics, 58f, 414f, 600f, 40f),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            maxLines = 1,
        )
        val hideShape = RoundedCornerShape(metrics.dp(38f))
        val hideEnabled = !editor.saving
        Box(
            modifier = Modifier
                .designBounds(metrics, 802f, 398f, 220f, 76f)
                .clip(hideShape)
                .pocketFrame(cancelButtonBrush(), metrics.dp(8f), Color(0xFF8A8A8A), hideShape)
                .testTag("age_editor_hide")
                .controllerTarget("age_editor_hide", cornerRadius = 38f, layer = NAME_EDITOR_FOCUS_LAYER) {
                    if (hideEnabled) dispatch(PocketPassEvent.HideAge)
                }
                .clickable(
                    enabled = hideEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.HideAge) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Hide",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(36f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProfileFieldEditorOverlay(
    metrics: DesignMetrics,
    active: ProfileFieldEditorContent?,
    tagPrefix: String,
    closeTag: String,
    title: String,
    hint: String,
    placeholder: String,
    layout: PocketKeyboardLayout,
    onClose: () -> Unit,
    onDraft: (String) -> Unit,
    onSubmit: () -> Unit,
    footer: @Composable BoxScope.(ProfileFieldEditorContent) -> Unit,
) {
    val palette = pocketPalette
    var retained by remember { mutableStateOf<ProfileFieldEditorContent?>(null) }
    val progress = remember { Animatable(0f) }
    SideEffect {
        if (active != null) retained = active
    }
    LaunchedEffect(active != null) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(if (active != null) 1f else 0f)
            if (active == null) retained = null
            return@LaunchedEffect
        }
        if (active != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
        } else if (retained != null) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
            retained = null
        }
    }
    val editor = active ?: retained ?: return
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .graphicsLayer { alpha = progress.value }
            .background(pocketPalette.scrim)
            .testTag("${tagPrefix}_overlay")
            .controllerFocusBarrier("${tagPrefix}_overlay", layer = NAME_EDITOR_FOCUS_LAYER)
            .clickable(
                enabled = active != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClose() },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 500f)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 500f)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        0.62f to pocketPalette.surface,
                        1f to pocketPalette.tint(Color(0xFFBDF8CB)),
                    ),
                ),
                metrics.dp(15f),
                pocketPalette.tealBorder,
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("${tagPrefix}_panel"),
    ) {
        Text(
            text = title,
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        val closeInteraction = remember { MutableInteractionSource() }
        Canvas(
            Modifier
                .designBounds(metrics, 945f, 55f, 72f, 72f)
                .testTag(closeTag)
                .controllerTarget(closeTag, layer = NAME_EDITOR_FOCUS_LAYER) {
                    onClose()
                }
                .clickable(
                    interactionSource = closeInteraction,
                    indication = null,
                ) { onClose() },
        ) {
            drawLine(
                palette.ink(Color(0xFF2F948C)),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawLine(
                palette.ink(Color(0xFF2F948C)),
                androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = editor.error ?: hint,
            modifier = Modifier.designBounds(metrics, 58f, 150f, 964f, 50f),
            color = if (editor.error != null) {
                palette.ink(Color(0xFFB31E3A))
            } else {
                palette.tealBorder
            },
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val shake = remember { Animatable(0f) }
        LaunchedEffect(editor.errorShakeNonce) {
            if (editor.errorShakeNonce > 0 && platformAnimationsEnabled()) {
                shake.snapTo(0f)
                listOf(-11f, 9f, -6f, 4f, 0f).forEach { offset ->
                    shake.animateTo(offset, tween(48))
                }
            }
        }
        val fieldShape = RoundedCornerShape(metrics.dp(83f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 58f, 222f, 964f, 166f)
                .graphicsLayer { translationX = shake.value }
                .clip(fieldShape)
                .pocketFrame(palette.surfaceSunken, metrics.dp(8f), palette.tealBorder, fieldShape)
                .testTag("${tagPrefix}_field"),
            contentAlignment = Alignment.Center,
        ) {
            if (editor.draft.isEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                        append(placeholder)
                    },
                    inlineContent = typingCaretInline(metrics, pocketPalette.teal, 56f),
                    color = palette.ink(Color(0xFF8FB9C6)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(55f),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        append(editor.draft)
                        appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                    },
                    inlineContent = typingCaretInline(metrics, pocketPalette.teal, 56f),
                    color = pocketPalette.teal,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(55f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        footer(editor)
    }
    PocketKeyboard(
        metrics = metrics,
        layout = layout,
        submitLabel = "Save",
        submitEnabled = editor.submitEnabled,
        canBackspace = editor.draft.isNotEmpty(),
        onKey = { key ->
            when (key) {
                is PocketKey.Character -> onDraft(editor.draft + key.value)

                PocketKey.Backspace -> onDraft(editor.draft.dropLast(1))

                PocketKey.Submit -> onSubmit()
                PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
            }
        },
        modifier = Modifier.graphicsLayer {
            translationY = (1f - progress.value) * POCKET_KEYBOARD_HEIGHT
        },
        palette = PocketKeyboardPalette.Messages,
        focusLayer = NAME_EDITOR_FOCUS_LAYER,
    )
}

internal const val NAME_EDITOR_FOCUS_LAYER = 10

@Composable
private fun SectionTitle(
    metrics: DesignMetrics,
    title: String,
    color: Color,
    filterAsset: PocketAsset,
    buttonBorder: Color,
    buttonTint: Color,
    onFilter: (() -> Unit)? = null,
) {
    Text(
        text = title,
        modifier = Modifier.anchoredBounds(metrics, 50f, 294f, 834.68f, 96f, DesignAnchor.Start),
        color = color,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(73.92f),
        maxLines = 1,
    )
    val shape = RoundedCornerShape(metrics.dp(40f))
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1110f, 303f, 80f, 80f, DesignAnchor.End)
            .pocketShadow(metrics, 40f),
    )
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1110f, 297f, 80f, 80f, DesignAnchor.End)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(listOf(pocketPalette.surface, buttonTint)),
                metrics.dp(6f),
                buttonBorder,
                shape,
            )
            .let { base ->
                if (onFilter != null) {
                    base
                        .testTag("section_filter")
                        .controllerTarget("section_filter", cornerRadius = 40f) { onFilter() }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onFilter,
                        )
                } else {
                    base
                }
            },
    ) {
        FigmaAsset(
            resource = filterAsset,
            modifier = Modifier.designBounds(metrics, 19f, 19f, 42f, 42f),
        )
    }
}

@Composable
private fun HorizontalCards(
    metrics: DesignMetrics,
    people: List<PersonCardUi>,
    cardBorder: Color,
    cardBottom: Color,
    nameColor: Color,
    detailColor: Color,
    onCard: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val focus = LocalControllerFocus.current
    val density = LocalDensity.current
    val cardIds = remember(people) { people.map { "card_${it.focusKey}" } }
    if (focus != null) {
        LaunchedEffect(focus, scroll, cardIds, metrics, density) {
            snapshotFlow { focus.focusId.takeUnless { focus.hidden } }
                .collect { focusId ->
                    val index = cardIds.indexOf(focusId)
                    if (index < 0) return@collect
                    val cardLeft = with(density) { metrics.dp(50f + index * 502.303f).toPx() }
                    val cardRight = with(density) { metrics.dp(50f + index * 502.303f + 452.303f).toPx() }
                    val viewport = scroll.viewportSize
                        .takeIf { it > 0 }
                        ?.toFloat()
                        ?: with(density) { metrics.dp(1240f + 2f * metrics.overscanX).toPx() }
                    val margin = with(density) { metrics.dp(50f).toPx() }
                    val target = when {
                        cardLeft - margin < scroll.value -> cardLeft - margin
                        cardRight + margin > scroll.value + viewport -> cardRight + margin - viewport
                        else -> return@collect
                    }.roundToInt().coerceIn(0, scroll.maxValue)
                    if (target == scroll.value) return@collect
                    if (platformAnimationsEnabled()) {
                        scroll.animateScrollTo(target)
                    } else {
                        scroll.scrollTo(target)
                    }
                }
        }
    }
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 0f, 419f, 1240f, 661f, DesignAnchor.Stretch)
            .clipToBounds()
            .horizontalScroll(scroll),
    ) {
        Box(
            Modifier.requiredSize(
                metrics.dp(
                    (50f + people.size * 502.303f)
                        .coerceAtLeast(1240f + 2f * metrics.overscanX),
                ),
                metrics.dp(661f),
            ),
        ) {
            people.forEachIndexed { index, person ->
                key(person.focusKey) {
                    PersonCard(
                        metrics = metrics,
                        x = 50f + index * 502.303f,
                        y = 16f,
                        borderColor = cardBorder,
                        bottomColor = cardBottom,
                        nameColor = nameColor,
                        detailColor = detailColor,
                        avatar = person.avatar,
                        name = person.name,
                        initial = person.initial,
                        showOnline = person.isOnline,
                        detail = person.detail,
                        focusId = "card_${person.focusKey}",
                        onClick = { onCard(person.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    borderColor: Color,
    bottomColor: Color,
    nameColor: Color,
    detailColor: Color,
    avatar: AvatarReference?,
    initial: String,
    name: String,
    showOnline: Boolean,
    detail: String,
    focusId: String? = null,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(metrics.dp(104.119f))
    val cardBorderBrush = if (borderColor == Color(0xFFCB4AC0)) {
        Brush.verticalGradient(
            listOf(
                Color(0xFFD75CD0),
                Color(0xFFCB4AC0),
                Color(0xFF6E217D),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF76B3C1),
                Color(0xFF5E9AAC),
                Color(0xFF22677C),
            ),
        )
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y + 15.674f, 452.303f, 605.4f)
            .pocketShadow(metrics, 104.119f),
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, 452.303f, 605.4f)
            .clip(cardShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        0.626f to pocketPalette.surface,
                        1f to bottomColor,
                    ),
                ),
                metrics.dp(20.152f),
                cardBorderBrush,
                cardShape,
            )
            .then(
                if (focusId == null) {
                    Modifier
                } else {
                    Modifier.controllerTarget(focusId, cornerRadius = 104.119f) { onClick() }
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val avatarShape = RoundedCornerShape(metrics.dp(55.978f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 55.978f, 55.978f, 340.347f, 326.9f)
                .clip(avatarShape)
                .background(
                    Brush.verticalGradient(
                        listOf(pocketPalette.surface, bottomColor.copy(alpha = 0.72f)),
                    ),
                )
                .pocketBorder(
                    metrics.dp(16.793f),
                    borderColor.copy(alpha = 0.20f),
                    avatarShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = nameColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = metrics.sp(150f),
                maxLines = 1,
            )
            if (avatar != null) {
                DynamicAvatar(
                    avatar = avatar,
                    fallbackResource = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .graphicsLayer {
                            scaleX = CARD_PORTRAIT_ZOOM
                            scaleY = CARD_PORTRAIT_ZOOM
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = name,
            modifier = Modifier.designBounds(metrics, 40f, 392f, 372f, 103f),
            color = nameColor,
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(82.752f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = detail,
            modifier = Modifier.designBounds(metrics, 40f, 492f, 372f, 68f),
            color = detailColor,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(53.739f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (showOnline) {
            FigmaAsset(
                resource = Assets.OnlineDot,
                modifier = Modifier.designBounds(
                    metrics,
                    275.5f,
                    18.5f,
                    158.521f,
                    158.521f,
                ),
            )
        }
    }
}

internal data class PersonCardUi(
    val id: String,
    val focusKey: String,
    val name: String,
    val avatar: AvatarReference?,
    val isOnline: Boolean,
    val detail: String,
    val initial: String,
)

private fun List<Friend>.toFriendCardUi(): List<PersonCardUi> =
    map { friend ->
        val displayName = friend.profile.displayName.trim()
            .ifBlank { "PocketPass User" }
        PersonCardUi(
            id = friend.profile.userId.value,
            focusKey = friend.profile.userId.value,
            name = displayName,
            avatar = friend.profile.avatar,
            isOnline = friend.isOnline,
            detail = if (friend.isOnline) {
                "Now"
            } else {
                friend.profile.lastSeenAt?.let(::relativeTime) ?: "Offline"
            },
            initial = displayName.firstOrNull()?.uppercase() ?: "?",
        )
    }

private fun List<NearbyEncounter>.toEncounterCardUi(): List<PersonCardUi> =
    map { encounter ->
        val displayName = encounter.profile.displayName.trim()
            .ifBlank { "PocketPass User" }
        PersonCardUi(
            id = encounter.profile.userId.value,
            focusKey = encounter.id.value,
            name = displayName,
            avatar = encounter.profile.avatar,
            isOnline = false,
            detail = relativeTime(encounter.occurredAt),
            initial = displayName.firstOrNull()?.uppercase() ?: "?",
        )
    }

internal fun relativeTime(instant: Instant): String {
    val elapsed = (Clock.System.now() - instant).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed.inWholeMinutes < 1L -> "Now"
        elapsed.inWholeMinutes < 60L -> "${elapsed.inWholeMinutes}m Ago"
        elapsed.inWholeHours < 24L -> "${elapsed.inWholeHours}h Ago"
        elapsed.inWholeDays < 30L -> "${elapsed.inWholeDays}d Ago"
        else -> "${(elapsed.inWholeDays / 30f).roundToInt()}mo Ago"
    }
}

internal const val CARD_PORTRAIT_ZOOM = 0.90f


@Composable
private fun ActivitiesBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        val overlayOpen = state.shop.visible || state.games.visible ||
            state.leaderboard.visible || state.achievements.visible
        val returnGeneration = remember { mutableIntStateOf(0) }
        var overlayWasOpen by remember { mutableStateOf(overlayOpen) }
        val focus = LocalControllerFocus.current
        val openActivityFocusId = when {
            state.shop.visible -> "activity_Shop"
            state.games.visible -> "activity_Games"
            state.leaderboard.visible -> "activity_Leaderboard"
            else -> null
        }
        var returnActivityFocusId by remember { mutableStateOf<String?>(openActivityFocusId) }
        LaunchedEffect(openActivityFocusId, focus) {
            if (openActivityFocusId != null) {
                returnActivityFocusId = openActivityFocusId
            } else {
                returnActivityFocusId?.let { focus?.focus(it) }
                returnActivityFocusId = null
            }
        }
        LaunchedEffect(overlayOpen) {
            if (overlayWasOpen && !overlayOpen) returnGeneration.intValue++
            overlayWasOpen = overlayOpen
        }
        ActivityPanel(
            metrics = metrics,
            y = 287f,
            title = "Games",
            textColor = Color(0xFF1D6B25),
            borderColor = Color(0xFF5EAC5E),
            fillBottom = Color(0xFFBDF8CB),
            icon = Assets.ActivitiesGames,
            arrow = Assets.ActivitiesArrowGreen,
            entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
            replayKey = returnGeneration.intValue,
            onClick = { dispatch(PocketPassEvent.OpenGames) },
        )
        ActivityPanel(
            metrics = metrics,
            y = 547f,
            title = "Shop",
            textColor = Color(0xFF6B331D),
            borderColor = Color(0xFFFF8D41),
            fillBottom = Color(0xFFF8DFBD),
            icon = Assets.ActivitiesShop,
            arrow = Assets.ActivitiesArrowOrange,
            entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS +
                OVERLAY_POP_STAGGER_MILLIS,
            replayKey = returnGeneration.intValue,
            onClick = { dispatch(PocketPassEvent.OpenShop) },
        )
        ActivityPanel(
            metrics = metrics,
            y = 807f,
            title = "Leaderboard",
            textColor = Color(0xFF6B5C1D),
            borderColor = Color(0xFFFFEF41),
            fillBottom = Color(0xFFF1F8BD),
            icon = Assets.ActivitiesTrophy,
            arrow = Assets.ActivitiesArrowYellow,
            entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS +
                2 * OVERLAY_POP_STAGGER_MILLIS,
            replayKey = returnGeneration.intValue,
            onClick = { dispatch(PocketPassEvent.OpenLeaderboard) },
        )
    }
}

internal fun shopAssetForKey(key: String): PocketAsset? = when (key) {
    "shop_category_hats" -> Assets.ShopCategoryHats
    "shop_item_baseball_cap" -> Assets.ShopItemBaseballCap
    "shop_item_halo" -> Assets.ShopItemHalo
    else -> null
}

internal val ShopBorder = Color(0xFFFFA812)
internal val ShopTitleColor = Color(0xFF935600)
internal val ShopPriceColor = Color(0xFFBB8270)

@Composable
private fun shopPanelBrush(): Brush = Brush.verticalGradient(
    0.62606f to pocketPalette.surface,
    0.99791f to pocketPalette.tint(Color(0xFFFFE7CE)),
)

@Composable
fun ShopBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val blockInteraction = remember { MutableInteractionSource() }
    val selectedCategory = state.shop.selectedCategory
    val focus = LocalControllerFocus.current
    var returnCategorySlug by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.shop.visible, selectedCategory?.id) {
        if (!state.shop.visible) return@LaunchedEffect
        if (selectedCategory != null) {
            returnCategorySlug = selectedCategory.slug
            focus?.focus(
                selectedCategory.items.firstOrNull()?.let { "shop_item_${it.slug}" }
                    ?: "shop_category_back",
                reveal = false,
            )
        } else {
            val slug = returnCategorySlug ?: state.shop.categories.firstOrNull()?.slug
            if (slug != null) focus?.focus("shop_category_$slug", reveal = false)
            returnCategorySlug = null
        }
    }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternActivitiesBottom,
        topColor = pocketPalette.tint(Color(0xFFF6EEE9)),
        bottomColor = pocketPalette.tint(Color(0xFFFCDCBC)),
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("shop_overlay")
            .controllerFocusBarrier("shop_overlay", layer = 10)
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    )
    (state.miiEditor.wearHatError ?: state.shop.purchaseError ?: state.shop.refreshError)?.let { message ->
        ShopNotice(metrics = metrics, message = message)
    }
    if (selectedCategory == null) {
        Text(
            text = "Categories",
            modifier = Modifier.designBounds(metrics, 56f, 282f, 1128f, 84f),
            color = pocketPalette.ink(ShopTitleColor),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(68f),
        )
        Column(
            modifier = Modifier
                .designBounds(metrics, 40f, 382f, 1160f, 658f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(28f)),
        ) {
            state.shop.categories.forEachIndexed { index, category ->
                MotionLayer(
                    entrance = EntranceMotion.OverlayPop,
                    delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS +
                        index * OVERLAY_POP_STAGGER_MILLIS,
                ) {
                    ShopCategoryCard(metrics, category) {
                        dispatch(PocketPassEvent.OpenShopCategory(category.id))
                    }
                }
            }
        }
    } else {
        val backShape = RoundedCornerShape(metrics.dp(38f))
        Box(
            Modifier
                .designBounds(metrics, 40f, 290f, 282f, 80f)
                .clip(backShape)
                .pocketFrame(shopPanelBrush(), metrics.dp(8f), ShopBorder, backShape)
                .testTag("shop_category_back")
                .controllerTarget("shop_category_back", layer = 10, cornerRadius = 38f) {
                    dispatch(PocketPassEvent.CloseShopCategory)
                }
                .clickable { dispatch(PocketPassEvent.CloseShopCategory) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹ Categories",
                color = pocketPalette.ink(ShopTitleColor),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(34f),
            )
        }
        Text(
            text = selectedCategory.title,
            modifier = Modifier.designBounds(metrics, 354f, 285f, 610f, 86f),
            color = pocketPalette.ink(ShopTitleColor),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${selectedCategory.items.size} items",
            modifier = Modifier.designBounds(metrics, 972f, 309f, 218f, 50f),
            color = pocketPalette.ink(ShopTitleColor).copy(alpha = 0.7f),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
        )
        Column(
            modifier = Modifier
                .designBounds(metrics, 40f, 390f, 1160f, 650f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(24f)),
        ) {
            selectedCategory.items.forEach { item ->
                ShopItemCard(
                    metrics = metrics,
                    item = item,
                    status = state.shop.statusOf(item),
                    canBuy = state.shop.canBuy(item),
                    wearEnabled = state.miiEditorEnabled,
                    previewAppearance = state.miiEditor.draft,
                    wearHatInProgress = state.miiEditor.wearHatInProgress,
                    dispatch = dispatch,
                )
            }
        }
    }
    state.shop.buyPromptItem?.let { item ->
        BuyShopItemConfirmDialog(
            metrics = metrics,
            item = item,
            body = state.shop.purchasePromptBody(item),
            dispatch = dispatch,
        )
    }
}

@Composable
private fun ShopNotice(
    metrics: DesignMetrics,
    message: String,
) {
    Text(
        text = message,
        modifier = Modifier
            .designBounds(metrics, 50f, 238f, 1140f, 40f)
            .testTag("shop_notice"),
        color = pocketPalette.ink(Color(0xFFB31E3A)),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(26f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun ShopCategoryCard(
    metrics: DesignMetrics,
    category: ShopCategory,
    onClick: () -> Unit,
) {
    val panelShape = RoundedCornerShape(metrics.dp(88f))
    Box(Modifier.requiredSize(metrics.dp(1160f), metrics.dp(260f))) {
        Box(
            Modifier
                .designBounds(metrics, 0f, 12f, 1160f, 248f)
                .pocketShadow(metrics, 88f),
        )
        Box(
            Modifier
                .designBounds(metrics, 0f, 0f, 1160f, 248f)
                .clip(panelShape)
                .pocketFrame(shopPanelBrush(), metrics.dp(16f), ShopBorder, panelShape)
                .testTag("shop_category_${category.slug}")
                .controllerTarget("shop_category_${category.slug}", layer = 10, cornerRadius = 88f, onActivate = onClick)
                .clickable(onClick = onClick),
        ) {
            shopAssetForKey(category.iconKey)?.let { icon ->
                FigmaAsset(
                    resource = icon,
                    modifier = Modifier.designBounds(metrics, 50f, 42f, 158f, 158f),
                )
            }
            Column(
                modifier = Modifier.designBounds(metrics, 248f, 24f, 720f, 198f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = category.title,
                    color = pocketPalette.ink(ShopTitleColor),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(68f),
                )
                Spacer(Modifier.requiredHeight(metrics.dp(6f)))
                Text(
                    text = category.subtitle,
                    color = pocketPalette.ink(Color(0xFF861F00)).copy(alpha = 0.56f),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(36f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${category.items.size} items  ›",
                modifier = Modifier.designBounds(metrics, 944f, 91f, 190f, 62f),
                color = pocketPalette.ink(ShopTitleColor),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(32f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ShopItemCard(
    metrics: DesignMetrics,
    item: ShopItem,
    status: ShopItemStatus,
    canBuy: Boolean,
    wearEnabled: Boolean,
    previewAppearance: MiiAppearance,
    wearHatInProgress: Boolean = false,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val cardShape = RoundedCornerShape(metrics.dp(64f))
    val imageShape = RoundedCornerShape(metrics.dp(27f))
    val subscriptionItem = status == ShopItemStatus.Unlocked && item.priceTokens > 0
    val applyingHat = wearHatInProgress && item.miiHatType == previewAppearance.extHatType
    val wearingHat = !wearHatInProgress && item.miiHatType == previewAppearance.extHatType
    val canWearHat = wearEnabled && !wearHatInProgress && !wearingHat
    val focus = LocalControllerFocus.current
    val cardId = "shop_item_${item.slug}"
    val action: (() -> Unit)? = when (status) {
        ShopItemStatus.Available -> {
            { dispatch(PocketPassEvent.OpenBuyShopItem(item.id)) }
        }

        ShopItemStatus.Owned,
        ShopItemStatus.Unlocked,
        -> if (canWearHat) {
            { dispatch(PocketPassEvent.WearShopItem(item.id)) }
        } else {
            null
        }

        ShopItemStatus.Purchasing,
        ShopItemStatus.Unaffordable,
        -> null
    }
    Box(
        Modifier
            .requiredSize(metrics.dp(1160f), metrics.dp(218f))
            .clip(cardShape)
            .pocketFrame(shopPanelBrush(), metrics.dp(20.152f), ShopBorder, cardShape)
            .testTag(cardId)
            .controllerTarget(cardId, layer = 10, cornerRadius = 64f) {
                focus?.enterChildren(cardId)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = action != null && !subscriptionItem,
            ) { action?.invoke() },
    ) {
        val imageModifier = Modifier
            .designBounds(metrics, 30f, 24f, 172f, 170f)
            .clip(imageShape)
            .pocketBorder(
                metrics.dp(16.793f),
                Color(0xFFAC845E).copy(alpha = 0.2f),
                imageShape,
            )
        val hatType = item.miiHatType
        if (hatType != null) {
            val bytes = rememberMiiTraitIcon(
                field = MiiTraitField.HatType,
                index = hatType,
                appearance = previewAppearance,
                centerContent = true,
            )
            Box(
                modifier = imageModifier.background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (bytes != null) {
                    AsyncImage(
                        model = bytes,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(metrics.dp(14f)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        } else {
            shopAssetForKey(item.imageKey)?.let { image ->
                FigmaAsset(
                    resource = image,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            modifier = Modifier.designBounds(metrics, 238f, 20f, 872f, 178f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.name,
                color = pocketPalette.ink(ShopTitleColor),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(if (subscriptionItem) 50f else 54f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val statusModifier = Modifier.testTag("shop_item_${item.slug}_status")
            if (subscriptionItem) {
                Text(
                    text = "Included with Ko-fi",
                    modifier = statusModifier,
                    color = pocketPalette.ink(Color(0xFF1D6B25)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(28f),
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f)),
                ) {
                    ShopItemActionButton(
                        metrics = metrics,
                        label = "Buy · ${item.priceTokens}",
                        tag = "shop_item_${item.slug}_buy",
                        width = 212f,
                        enabled = canBuy,
                        controllerEnabled = canBuy,
                        parentId = cardId,
                    ) { dispatch(PocketPassEvent.OpenBuyShopItem(item.id)) }
                    if (wearEnabled) {
                        ShopItemActionButton(
                            metrics = metrics,
                            label = when {
                                applyingHat -> "Saving"
                                wearingHat -> "Wearing"
                                else -> "Wear"
                            },
                            tag = "shop_item_${item.slug}_wear",
                            width = 170f,
                            enabled = canWearHat,
                            controllerEnabled = canWearHat,
                            selected = wearingHat,
                            parentId = cardId,
                        ) { dispatch(PocketPassEvent.WearShopItem(item.id)) }
                    }
                }
            } else when (status) {
                ShopItemStatus.Available -> ShopItemActionButton(
                    metrics = metrics,
                    label = "Buy · ${item.priceTokens}",
                    tag = "${cardId}_buy",
                    width = 280f,
                    enabled = canBuy,
                    controllerEnabled = canBuy,
                    parentId = cardId,
                ) { dispatch(PocketPassEvent.OpenBuyShopItem(item.id)) }

                ShopItemStatus.Unaffordable -> {
                    Text(
                        text = "${item.priceTokens} Tokens",
                        modifier = statusModifier.alpha(0.45f),
                        color = pocketPalette.ink(ShopPriceColor),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(38.924f),
                        maxLines = 1,
                    )
                    Text(
                        text = "Not enough tokens",
                        color = pocketPalette.ink(ShopPriceColor),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(28f),
                        maxLines = 1,
                    )
                }

                ShopItemStatus.Purchasing -> Text(
                    text = "Buying…",
                    modifier = statusModifier.alpha(0.7f),
                    color = pocketPalette.ink(ShopTitleColor),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(38.924f),
                    maxLines = 1,
                )

                ShopItemStatus.Owned,
                ShopItemStatus.Unlocked,
                -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(28f)),
                ) {
                    Text(
                        text = when {
                            status != ShopItemStatus.Unlocked -> "Owned"
                            item.priceTokens == 0 -> "Free"
                            else -> "Unlocked"
                        },
                        modifier = statusModifier,
                        color = pocketPalette.ink(Color(0xFF1D6B25)),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(38.924f),
                        maxLines = 1,
                    )
                    if (wearEnabled) {
                        ShopItemActionButton(
                            metrics = metrics,
                            label = when {
                                applyingHat -> "Saving"
                                wearingHat -> "Wearing"
                                else -> "Wear"
                            },
                            tag = "${cardId}_wear",
                            width = 170f,
                            enabled = canWearHat,
                            controllerEnabled = canWearHat,
                            selected = wearingHat,
                            parentId = cardId,
                        ) { dispatch(PocketPassEvent.WearShopItem(item.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemActionButton(
    metrics: DesignMetrics,
    label: String,
    tag: String,
    width: Float,
    enabled: Boolean = true,
    controllerEnabled: Boolean,
    selected: Boolean = false,
    parentId: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(32f))
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(width), metrics.dp(64f))
            .alpha(if (enabled || selected) 1f else 0.45f)
            .clip(shape)
            .pocketFrame(greenButtonBrush(), metrics.dp(10f), Color(0xFF3CBC29), shape)
            .testTag(tag)
            .then(
                if (controllerEnabled) Modifier.controllerTarget(tag, layer = 10, cornerRadius = 32f, parentId = parentId) {
                    onClick()
                } else Modifier,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun DialogFocusHandoff(initialFocusId: String?) {
    val focus = LocalControllerFocus.current
    val returnTarget = remember { focus?.focusedTarget(null) }
    DisposableEffect(focus) {
        onDispose { returnTarget?.let { focus?.restoreFocus(it) } }
    }
    LaunchedEffect(initialFocusId) { initialFocusId?.let { focus?.focus(it, reveal = false) } }
}

@Composable
internal fun BuyShopItemConfirmDialog(
    metrics: DesignMetrics,
    item: ShopItem,
    body: String,
    dispatch: (PocketPassEvent) -> Unit,
) {
    DialogFocusHandoff("shop_buy_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("shop_buy_overlay")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseBuyShopItem) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("shop_buy_panel"),
    ) {
        Text(
            text = "Buy ${item.name}?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 130f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(
                    cancelButtonBrush(),
                    metrics.dp(20.152f),
                    Color(0xFF8A8A8A),
                    buttonShape,
                )
                .testTag("shop_buy_cancel")
                .controllerTarget("shop_buy_cancel", layer = 20) {
                    dispatch(PocketPassEvent.CloseBuyShopItem)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseBuyShopItem) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 550f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(greenButtonBrush(), metrics.dp(20.152f), Color(0xFF3CBC29), buttonShape)
                .testTag("shop_buy_confirm")
                .controllerTarget("shop_buy_confirm", layer = 20) {
                    dispatch(PocketPassEvent.ConfirmBuyShopItem)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.ConfirmBuyShopItem) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Buy",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

private val LeaderboardBorder = Color(0xFFEBA637)
private val LeaderboardTrophyColor = Color(0xFFFFA621)
private val LeaderboardWaveColor = Color(0xFF00D600)

@Composable
fun GamesBottomOverlay(
    metrics: DesignMetrics,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(focus) {
        focus?.focus("game_row_${GameEntries.first().target.name}")
    }
    val blockInteraction = remember { MutableInteractionSource() }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternActivitiesBottom,
        topColor = pocketPalette.tint(Color(0xFFE9F6EA)),
        bottomColor = pocketPalette.tint(Color(0xFFBCFCC2)),
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("games_overlay")
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    )
    GameEntries.forEachIndexed { index, entry ->
        GameRow(
            metrics = metrics,
            y = GAME_ROW_TOP + index * (GAME_ROW_HEIGHT + GAME_ROW_GAP),
            entry = entry,
            entranceDelayMillis = OVERLAY_POP_BASE_DELAY_MILLIS +
                index * OVERLAY_POP_STAGGER_MILLIS,
            onClick = { dispatch(PocketPassEvent.OpenGame(entry.target)) },
        )
    }
}

internal class GameEntry(
    val title: String,
    val icon: PocketAsset,
    val target: GameTarget,
)

internal val GameEntries = listOf(
    GameEntry("Puzzle Swap", Assets.GamesIconPuzzleSwap, GameTarget.PuzzleSwap),
    GameEntry("Bingo", Assets.GamesIconBingo, GameTarget.Bingo),
    GameEntry("World Tour", Assets.GamesIconWorldTour, GameTarget.WorldTour),
)

private const val GAME_ROW_TOP = 287f
private const val GAME_ROW_HEIGHT = 220f
private const val GAME_ROW_GAP = 50f
internal const val OVERLAY_POP_BASE_DELAY_MILLIS = 40
internal const val OVERLAY_POP_STAGGER_MILLIS = 60

@Composable
internal fun GameRow(
    metrics: DesignMetrics,
    y: Float,
    entry: GameEntry,
    entranceDelayMillis: Int,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(metrics.dp(200f))
    val interaction = remember(entry.title) { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, 40f, y, 1160f, GAME_ROW_HEIGHT)
            .testTag("game_row_${entry.target.name}")
            .controllerTarget("game_row_${entry.target.name}", layer = 10) { onClick() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = EntranceMotion.OverlayPop,
            delayMillis = entranceDelayMillis,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = metrics.dp(15.674f))
                    .pocketShadow(metrics, 200f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(rowShape)
                    .pocketFrame(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to pocketPalette.surface,
                                0.62606f to pocketPalette.surface,
                                1f to pocketPalette.tint(Color(0xFFBDF8CB)),
                            ),
                        ),
                        metrics.dp(20.152f),
                        Color(0xFF5EAC5E),
                        rowShape,
                    ),
            ) {
                FigmaAsset(
                    resource = entry.icon,
                    modifier = Modifier.designBounds(
                        metrics,
                        43.0f,
                        38.68f,
                        142.65f,
                        142.65f,
                    ),
                )
                Box(
                    modifier = Modifier.designBounds(metrics, 207.65f, 62.5f, 780f, 95f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = entry.title,
                        color = pocketPalette.ink(Color(0xFF1D6B25)),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(80f),
                        maxLines = 1,
                    )
                }
                FigmaAsset(
                    resource = Assets.ActivitiesArrowGreen,
                    modifier = Modifier.designBounds(
                        metrics,
                        1048.628f,
                        75.638f,
                        40.372f,
                        68.725f,
                    ),
                )
            }
        }
    }
}

@Composable
fun LeaderboardBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val focus = LocalControllerFocus.current
    val self = selfLeaderboardEntry(state)
    val firstRow = state.leaderboard.entries.firstOrNull { it.userId != self?.first?.userId }
    val initialFocusId = when {
        state.leaderboard.settingsVisible -> "leaderboard_scope_friends"
        self != null -> "leaderboard_you_card"
        firstRow != null -> leaderboardRowTag(firstRow)
        else -> "leaderboard_empty"
    }
    LaunchedEffect(focus, initialFocusId) {
        focus?.focus(initialFocusId)
    }
    val blockInteraction = remember { MutableInteractionSource() }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternActivitiesBottom,
        topColor = pocketPalette.tint(Color(0xFFF6F4E9)),
        bottomColor = pocketPalette.tint(Color(0xFFFCF0BC)),
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("leaderboard_overlay")
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    )
    if (state.leaderboard.settingsVisible) {
        LeaderboardSettingsBottom(metrics, state, dispatch)
        return
    }
    Box(
        modifier = Modifier.designBounds(metrics, 40f, 287f, 1160f, 753f),
        contentAlignment = Alignment.TopCenter,
    ) {
        MotionLayer(
            entrance = EntranceMotion.OverlayPop,
            delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
        ) {
            LeaderboardPanel(
                metrics = metrics,
                scope = state.leaderboard.scope,
                self = self?.let { (entry, rank) -> entry.copy(displayName = "You (#$rank)") },
                entries = state.leaderboard.entries.filterNot { entry ->
                    entry.userId == self?.first?.userId
                },
                onSelfClick = { dispatch(PocketPassEvent.OpenAchievements) },
                onSettingsClick = { dispatch(PocketPassEvent.OpenLeaderboardSettings) },
            )
        }
    }
}

@Composable
private fun LeaderboardSettingsBottom(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    MotionLayer(
        modifier = Modifier.fillMaxSize(),
        entrance = EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
    ) {
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier
                .designBounds(metrics, 62f, 328f, 40.372f, 68.725f)
                .graphicsLayer { scaleX = -1f },
        )
        Text(
            text = "Leaderboard",
            modifier = Modifier.designBounds(metrics, 142f, 312f, 800f, 100f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(88f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .designBounds(metrics, 38f, 290f, 760f, 150f)
                .testTag("leaderboard_settings_back")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseLeaderboardSettings) },
        )
        Text(
            text = "Choose who appears.",
            modifier = Modifier.designBounds(metrics, 145f, 430f, 900f, 52f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            maxLines = 1,
        )
    }
    DesignBox(metrics, 0f, 510f, 1240f, 570f, DesignAnchor.Stretch, DesignAnchor.Stretch,
        modifier = Modifier.clipToBounds().verticalScroll(rememberScrollState()).testTag("leaderboard_settings_scroll")) {
        Box(Modifier.requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX)).requiredHeight(metrics.dp(860f))) {
            LeaderboardScopeOption(metrics, 27f, "Friends", "Just you and your friends",
                state.leaderboard.scope == LeaderboardScope.Friends, "leaderboard_scope_friends") {
                dispatch(PocketPassEvent.SetLeaderboardScope(LeaderboardScope.Friends))
            }
            LeaderboardScopeOption(metrics, 297f, "Global", "Everyone on PocketPass",
                state.leaderboard.scope == LeaderboardScope.Global, "leaderboard_scope_global") {
                dispatch(PocketPassEvent.SetLeaderboardScope(LeaderboardScope.Global))
            }
            LeaderboardLimitPanel(metrics, 567f, state, dispatch)
        }
    }
}

@Composable
internal fun LeaderboardScopeOption(
    metrics: DesignMetrics,
    y: Float,
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = 220f,
        borderColor = if (selected) LeaderboardBorder else pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = tag,
        focusLayer = 20,
        onClick = onClick,
    ) {
        Text(
            text = title,
            modifier = Modifier.designBounds(metrics, 70f, 42f, 767f, 76f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            modifier = Modifier.designBounds(metrics, 70f, 117f, 820f, 55f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 992f, 74f, 72f, 72f, DesignAnchor.End)
                .clip(CircleShape)
                .pocketFrame(
                    if (selected) Color(0xFF3CBC29) else Color.Transparent,
                    metrics.dp(9f),
                    if (selected) Color(0xFF2F9A20) else pocketPalette.borderGrey,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(44f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LeaderboardPanel(
    metrics: DesignMetrics,
    scope: LeaderboardScope,
    self: LeaderboardEntry?,
    entries: List<LeaderboardEntry>,
    onSelfClick: () -> Unit,
    onSettingsClick: () -> Unit,
    maxHeight: Float = 737.3f,
) {
    val panelShape = RoundedCornerShape(metrics.dp(129f))
    val listShape = RoundedCornerShape(
        bottomStart = metrics.dp(129f),
        bottomEnd = metrics.dp(129f),
    )
    val focusViewport = remember(listShape) { ControllerFocusViewport(shape = listShape) }
    val divider = pocketPalette.ink(Color(0xFFAB8118))
    val listNeighbors = entries.firstOrNull()
        ?.let { mapOf(FocusDirection.Down to leaderboardRowTag(it)) }
        .orEmpty()
    Box(Modifier.requiredWidth(metrics.dp(1140f))) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = metrics.dp(15.674f))
                .pocketShadow(metrics, 129f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = metrics.dp(maxHeight))
                .clip(panelShape)
                .pocketFrame(
                    pocketPalette.surface,
                    metrics.dp(20.152f),
                    LeaderboardBorder,
                    panelShape,
                ),
        ) {
            if (self != null) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(divider.copy(alpha = 0.07f))
                        .padding(
                            start = metrics.dp(52f),
                            end = metrics.dp(71f - LEADERBOARD_RING_INSET_END),
                            top = metrics.dp(55.978f - LEADERBOARD_RING_INSET_Y),
                            bottom = metrics.dp(36f - LEADERBOARD_RING_INSET_Y),
                        ),
                ) {
                    LeaderboardRow(
                        metrics = metrics,
                        entry = self,
                        modifier = Modifier
                            .testTag("leaderboard_you_card")
                            .controllerTarget(
                                "leaderboard_you_card",
                                layer = 10,
                                cornerRadius = LEADERBOARD_RING_RADIUS,
                                neighbors = listNeighbors,
                            ) {
                                onSelfClick()
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onSelfClick,
                            )
                            .padding(
                                end = metrics.dp(LEADERBOARD_RING_INSET_END),
                                top = metrics.dp(LEADERBOARD_RING_INSET_Y),
                                bottom = metrics.dp(LEADERBOARD_RING_INSET_Y),
                            ),
                        trailing = {
                            Box(
                                Modifier
                                    .padding(start = metrics.dp(40f))
                                    .requiredSize(metrics.dp(9f), metrics.dp(92f))
                                    .clip(RoundedCornerShape(metrics.dp(100f)))
                                    .background(divider.copy(alpha = 0.13f)),
                            )
                            val gearInteraction = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .padding(start = metrics.dp(24f))
                                    .requiredSize(metrics.dp(88f), metrics.dp(124.65f))
                                    .testTag("leaderboard_settings_button")
                                    .controllerTarget(
                                        "leaderboard_settings_button",
                                        layer = 10,
                                        neighbors = listNeighbors,
                                    ) { onSettingsClick() }
                                    .clickable(
                                        interactionSource = gearInteraction,
                                        indication = null,
                                        onClick = onSettingsClick,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                FigmaAsset(
                                    resource = Assets.NavSettings,
                                    modifier = Modifier.requiredSize(metrics.dp(64f)),
                                    colorFilter = ColorFilter.tint(
                                        pocketPalette.textPrimary.copy(alpha = 0.6f),
                                    ),
                                )
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .requiredHeight(metrics.dp(12f))
                        .background(divider.copy(alpha = 0.22f)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .controllerFocusViewport(focusViewport)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = metrics.dp(52f),
                        end = metrics.dp(71f - LEADERBOARD_RING_INSET_END),
                        top = metrics.dp((if (self != null) 36f else 55.978f) - LEADERBOARD_RING_INSET_Y),
                        bottom = metrics.dp(55.978f - LEADERBOARD_RING_INSET_Y),
                    ),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(31f - LEADERBOARD_RING_INSET_Y)),
            ) {
                if (entries.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .requiredHeight(metrics.dp(124.65f))
                            .controllerTarget("leaderboard_empty", layer = 10, cornerRadius = LEADERBOARD_RING_RADIUS) {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (scope) {
                                LeaderboardScope.Friends ->
                                    "Add friends to fill the leaderboard!"
                                LeaderboardScope.Global ->
                                    "Nobody else is on the leaderboard yet!"
                            },
                            color = pocketPalette.textPrimary.copy(alpha = 0.4f),
                            fontFamily = Rubik,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = metrics.sp(45f),
                        )
                    }
                }
                CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                    entries.forEachIndexed { index, entry ->
                        key(entry.userId.value) {
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .requiredHeight(metrics.dp(9f))
                                        .clip(RoundedCornerShape(metrics.dp(100f)))
                                        .background(divider.copy(alpha = 0.13f)),
                                )
                            }
                            val rowTag = leaderboardRowTag(entry)
                            LeaderboardRow(
                                metrics = metrics,
                                entry = entry,
                                modifier = Modifier
                                    .controllerTarget(
                                        rowTag,
                                        layer = 10,
                                        cornerRadius = LEADERBOARD_RING_RADIUS,
                                        neighbors = mapOf(
                                            FocusDirection.Left to rowTag,
                                            FocusDirection.Right to rowTag,
                                            FocusDirection.Up to when {
                                                index > 0 -> leaderboardRowTag(entries[index - 1])
                                                self != null -> "leaderboard_you_card"
                                                else -> rowTag
                                            },
                                            FocusDirection.Down to leaderboardRowTag(entries.getOrElse(index + 1) { entry }),
                                        ),
                                    ) {}
                                    .padding(
                                        end = metrics.dp(LEADERBOARD_RING_INSET_END),
                                        top = metrics.dp(LEADERBOARD_RING_INSET_Y),
                                        bottom = metrics.dp(LEADERBOARD_RING_INSET_Y),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun leaderboardRowTag(entry: LeaderboardEntry): String = "leaderboard_row_${entry.userId.value}"

private const val LEADERBOARD_RING_INSET_END = 24f
private const val LEADERBOARD_RING_INSET_Y = 8f
private const val LEADERBOARD_RING_RADIUS = 70.325f

internal fun selfLeaderboardEntry(
    state: PocketPassUiState,
): Pair<LeaderboardEntry, Int>? {
    val selfId = state.profile?.userId ?: return null
    val index = state.leaderboard.entries.indexOfFirst { it.userId == selfId }
    if (index < 0) return null
    return state.leaderboard.entries[index] to (index + 1)
}

@Composable
internal fun LeaderboardRow(
    metrics: DesignMetrics,
    entry: LeaderboardEntry,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val avatarShape = RoundedCornerShape(metrics.dp(62.325f))
    Box(
        modifier
            .fillMaxWidth()
            .requiredHeight(metrics.dp(124.65f))
            .testTag("leaderboard_row_${entry.userId.value}"),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .requiredSize(metrics.dp(124.65f))
                .clip(avatarShape)
                .pocketBorder(
                    metrics.dp(9f),
                    Color(0xFF5F5F5F).copy(alpha = 0.2f),
                    avatarShape,
                ),
        ) {
            DynamicAvatar(
                avatar = entry.avatar,
                fallbackResource = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .requiredWidth(metrics.dp(859f))
                .requiredHeight(metrics.dp(124.65f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.displayName,
                modifier = Modifier.weight(1f),
                color = pocketPalette.textPrimary,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(50f)),
            ) {
                LeaderboardStat(
                    metrics = metrics,
                    icon = Assets.LeaderboardTrophy,
                    iconWidth = 62.647f,
                    iconHeight = 56.272f,
                    value = entry.trophyCount,
                    color = LeaderboardTrophyColor,
                )
                LeaderboardStat(
                    metrics = metrics,
                    icon = Assets.LeaderboardWave,
                    iconWidth = 56.27f,
                    iconHeight = 56.27f,
                    value = entry.encounterCount,
                    color = LeaderboardWaveColor,
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
internal fun LeaderboardStat(
    metrics: DesignMetrics,
    icon: PocketAsset,
    iconWidth: Float,
    iconHeight: Float,
    value: Int,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(20.9f)),
    ) {
        FigmaAsset(
            resource = icon,
            modifier = Modifier.requiredSize(
                metrics.dp(iconWidth),
                metrics.dp(iconHeight),
            ),
        )
        Text(
            text = value.toString(),
            color = color,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(69.696f),
            maxLines = 1,
        )
    }
}

private val AchievementSectionTitleColor = Color(0xFF909090)
private val AchievementProgressColor = Color(0xFFFF6321)
private val AchievementUnlockedBadgeColor = Color(0xFF3ECF00)
private val AchievementLockedBadgeColor = Color(0xFF9F9F9F)

internal fun achievementAssetForKey(key: String): PocketAsset? = when (key) {
    "day_one" -> Assets.AchievementDayOne
    "saving_up" -> Assets.AchievementSavingUp
    "icebreaker" -> Assets.AchievementIcebreaker
    "streak" -> Assets.AchievementStreak
    "plus_one" -> Assets.AchievementPlusOne
    "first_encounter" -> Assets.AchievementFirstEncounter
    "small_world" -> Assets.AchievementSmallWorld
    "passport_stamped" -> Assets.AchievementPassportStamped
    "continental" -> Assets.AchievementContinental
    "full_set" -> Assets.AchievementFullSet
    "missing_piece" -> Assets.AchievementMissingPiece
    else -> null
}

@Composable
fun AchievementsBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val firstSection = AchievementSection.entries.firstOrNull { section ->
        AchievementCatalog.definitions.any { definition -> definition.section == section }
    }
    DialogFocusHandoff(firstSection?.let { "achievement_section_${it.name}" })
    val blockInteraction = remember { MutableInteractionSource() }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternActivitiesBottom,
        topColor = pocketPalette.tint(Color(0xFFF6F4E9)),
        bottomColor = pocketPalette.tint(Color(0xFFFCF0BC)),
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("achievements_overlay")
            .controllerFocusBarrier("achievements_overlay", layer = ACHIEVEMENTS_FOCUS_LAYER)
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    )
    val rank = selfLeaderboardEntry(state)?.second
    val byKey = state.achievements.achievements.associateBy(AchievementState::key)
    val belowTabBar = remember(metrics) { BelowTabBarShape(metrics) }
    val focusViewport = rememberBelowTabBarFocusViewport(metrics)
    var expandedSections by remember { mutableStateOf(emptySet<AchievementSection>()) }
    Column(
        modifier = Modifier
            .anchoredBounds(
                metrics,
                0f,
                0f,
                1240f,
                1080f,
                DesignAnchor.Stretch,
                DesignAnchor.Stretch,
            )
            .clip(belowTabBar)
            .controllerFocusViewport(focusViewport)
            .verticalScroll(rememberScrollState())
            .padding(top = metrics.dp(287f), bottom = metrics.dp(40f)),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(40f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
            MotionLayer(
                entrance = EntranceMotion.OverlayPop,
                delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
            ) {
                Box(
                    Modifier
                        .requiredWidth(metrics.dp(1140f))
                        .requiredHeight(metrics.dp(141f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (rank != null) "You (#$rank)" else "You",
                        color = pocketPalette.textPrimary,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = metrics.sp(90f),
                        maxLines = 1,
                    )
                }
            }
            AchievementSection.entries.forEachIndexed { index, section ->
                val rows = AchievementCatalog.definitions
                    .filter { definition -> definition.section == section }
                if (rows.isEmpty()) return@forEachIndexed
                MotionLayer(
                    entrance = EntranceMotion.OverlayPop,
                    delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS +
                        (index + 1) * OVERLAY_POP_STAGGER_MILLIS,
                ) {
                    AchievementSectionPanel(
                        metrics = metrics,
                        section = section,
                        rows = rows.map { definition ->
                            definition to (
                                byKey[definition.key]
                                    ?: AchievementState(
                                        key = definition.key,
                                        unlocked = false,
                                        unlockedAt = null,
                                        progressPercent = 0,
                                    )
                                )
                        },
                        expanded = section in expandedSections,
                        onToggle = {
                            expandedSections = if (section in expandedSections) {
                                expandedSections - section
                            } else {
                                expandedSections + section
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AchievementSectionPanel(
    metrics: DesignMetrics,
    section: AchievementSection,
    rows: List<Pair<AchievementDefinition, AchievementState>>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val panelShape = RoundedCornerShape(metrics.dp(129f))
    val interaction = remember(section) { MutableInteractionSource() }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "achievement_section_chevron",
    )
    Box(
        Modifier
            .requiredWidth(metrics.dp(1140f))
            .controllerTarget(
                "achievement_section_${section.name}",
                layer = ACHIEVEMENTS_FOCUS_LAYER,
                cornerRadius = 129f,
            ) { onToggle() },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = metrics.dp(15.674f))
                .pocketShadow(metrics, 129f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .pocketFrame(
                    pocketPalette.surface,
                    metrics.dp(20.152f),
                    LeaderboardBorder,
                    panelShape,
                )
                .padding(
                    horizontal = metrics.dp(52f),
                    vertical = metrics.dp(55f),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(metrics.dp(ACHIEVEMENT_HEADER_HEIGHT))
                    .testTag("achievement_section_${section.name}")
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.title,
                    color = pocketPalette.ink(AchievementSectionTitleColor),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(64f),
                    maxLines = 1,
                )
                FigmaAsset(
                    resource = Assets.SettingsArrow,
                    colorFilter = chevronTint(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = metrics.dp(24f))
                        .requiredSize(metrics.dp(40.372f), metrics.dp(68.725f))
                        .graphicsLayer { rotationZ = chevronTurn },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 160)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 120)),
            ) {
                Column(
                    modifier = Modifier.padding(top = metrics.dp(31f)),
                    verticalArrangement = Arrangement.spacedBy(metrics.dp(31f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    rows.forEach { (definition, achievement) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .requiredHeight(metrics.dp(9f))
                                .clip(RoundedCornerShape(metrics.dp(100f)))
                                .background(pocketPalette.ink(Color(0xFFAB8118)).copy(alpha = 0.13f)),
                        )
                        AchievementRow(
                            metrics = metrics,
                            definition = definition,
                            achievement = achievement,
                        )
                    }
                }
            }
        }
    }
}

private const val ACHIEVEMENTS_FOCUS_LAYER = 15
private const val ACHIEVEMENT_HEADER_HEIGHT = 84f

@Composable
internal fun AchievementRow(
    metrics: DesignMetrics,
    definition: AchievementDefinition,
    achievement: AchievementState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("achievement_row_${definition.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(40f)),
    ) {
        Box(
            Modifier
                .requiredSize(metrics.dp(112.185f))
                .clip(CircleShape)
                .background(
                    if (achievement.unlocked) {
                        AchievementUnlockedBadgeColor
                    } else {
                        AchievementLockedBadgeColor
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            achievementAssetForKey(definition.key)?.let { icon ->
                FigmaAsset(
                    resource = icon,
                    modifier = Modifier.requiredSize(metrics.dp(64f)),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = definition.name,
                color = pocketPalette.textPrimary,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = definition.description,
                color = pocketPalette.textPrimary.copy(alpha = 0.68f),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(45f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(18f)),
        ) {
            FigmaAsset(
                resource = Assets.AchievementGauge,
                modifier = Modifier.requiredSize(
                    metrics.dp(69f),
                    metrics.dp(49.433f),
                ),
            )
            Text(
                text = "${achievement.progressPercent}%",
                color = AchievementProgressColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(69.696f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ActivityPanel(
    metrics: DesignMetrics,
    y: Float,
    title: String,
    textColor: Color,
    borderColor: Color,
    fillBottom: Color,
    icon: PocketAsset,
    arrow: PocketAsset,
    entranceDelayMillis: Int,
    onClick: () -> Unit,
    height: Float = 220f,
    replayKey: Any? = null,
) {
    val palette = pocketPalette
    val scale = height / 220f
    val panelShape = RoundedCornerShape(metrics.dp(height / 2f))
    val borderBrush = Brush.verticalGradient(
        listOf(
            lerp(borderColor, Color.White, 0.22f),
            borderColor,
            lerp(borderColor, Color.Black, 0.3f),
        ),
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, 40f, y, 1160f, height)
            .controllerTarget("activity_$title") { onClick() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = EntranceMotion.OverlayPop,
            delayMillis = entranceDelayMillis,
            replayKey = replayKey,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = metrics.dp(15.674f))
                    .pocketShadow(metrics, height / 2f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(panelShape)
                    .pocketFrame(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to palette.surface,
                                0.626f to palette.surface,
                                1f to palette.tint(fillBottom),
                            ),
                        ),
                        metrics.dp(20.152f),
                        borderBrush,
                        panelShape,
                    ),
            ) {
                FigmaAsset(
                    resource = icon,
                    modifier = Modifier.designBounds(metrics, 43f, (height - 142.65f * scale) / 2f, 142.65f * scale, 142.65f * scale),
                )
                Box(
                    modifier = Modifier.designBounds(metrics, 43f + 169f * scale, 0f, 800f, height),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        color = palette.ink(textColor),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(64f * scale),
                        maxLines = 1,
                    )
                }
                FigmaAsset(
                    resource = arrow,
                    modifier = Modifier.designBounds(metrics, 1160f - 70.628f - 40.372f * scale, (height - 68.725f * scale) / 2f, 40.372f * scale, 68.725f * scale),
                )
            }
        }
    }
}

@Composable
private fun MessagesBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.MessagePop) { metrics ->
        SectionTitle(
            metrics = metrics,
            title = "All Messages (${state.messageTotalCount})",
            color = pocketPalette.teal,
            filterAsset = Assets.Filter,
            buttonBorder = pocketPalette.tealBorder,
            buttonTint = pocketPalette.tint(Color(0xFFD1EDFB)),
        )
        MessagesHeaderActions(metrics) { dispatch(PocketPassEvent.OpenNewGroup) }
        state.conversationNotice?.let { notice ->
            LaunchedEffect(notice) {
                delay(CONVERSATION_NOTICE_MILLIS)
                dispatch(PocketPassEvent.DismissConversationNotice)
            }
            ConversationNoticeBanner(metrics, notice) {
                dispatch(PocketPassEvent.DismissConversationNotice)
            }
        }

        val conversations = state.conversations
        val selfId = state.profile?.userId
        if (conversations.isEmpty()) {
            MessagesEmptyPanel(metrics)
            return@BottomPage
        }
        val panelShape = RoundedCornerShape(metrics.dp(104f))
        val messageScroll = rememberScrollState()
        val rowsHeight = MESSAGE_ROW_INSET * 2f + MESSAGE_ROW_HEIGHT * conversations.size
        val panelHeight =
            BOTTOM_DESIGN_HEIGHT - MESSAGE_PANEL_TOP - MESSAGE_PANEL_BOTTOM_MARGIN +
                metrics.overscanY.coerceIn(0f, MESSAGE_PANEL_OVERSCAN_MAX)
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 50f, MESSAGE_PANEL_TOP, 1140f, panelHeight + 16f, DesignAnchor.Stretch),
        ) {
            Box(
                modifier = Modifier
                    .designBounds(metrics, 0f, 15.674f, 1140f + 2f * metrics.overscanX, panelHeight)
                    .pocketShadow(metrics, 104f),
            )
            Box(
                modifier = Modifier
                    .designBounds(metrics, 0f, 0f, 1140f + 2f * metrics.overscanX, panelHeight)
                    .clip(panelShape)
                    .pocketFrame(
                        pocketPalette.surface,
                        metrics.dp(20.152f),
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF76B3C1),
                                Color(0xFF5E9AAC),
                                Color(0xFF22677C),
                            ),
                        ),
                        panelShape,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(messageScroll),
                ) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(metrics.dp(1140f + 2f * metrics.overscanX))
                            .requiredHeight(metrics.dp(rowsHeight.coerceAtLeast(panelHeight))),
                    ) {
                        conversations.forEachIndexed { index, conversation ->
                            key(conversation.id.value) {
                                val palette = messageListRowPalette()
                                MessageRow(
                                    metrics = metrics,
                                    y = MESSAGE_ROW_INSET + index * MESSAGE_ROW_HEIGHT,
                                    conversation = conversation,
                                    palette = palette,
                                    selfId = selfId,
                                    onClick = {
                                        dispatch(PocketPassEvent.OpenMessage(conversation.id.value))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageRow(
    metrics: DesignMetrics,
    y: Float,
    conversation: ConversationSummary,
    palette: MessageRowPalette,
    onClick: () -> Unit,
    selfId: UserId? = null,
    focusLayer: Int = 0,
    neighbors: Map<FocusDirection, String> = emptyMap(),
) {
    val id = conversation.id.value
    val interaction = remember(id) { MutableInteractionSource() }
    Box(
        Modifier
            .designBounds(
                metrics,
                0f,
                y - MESSAGE_ROW_INSET,
                1140f + 2f * metrics.overscanX,
                MESSAGE_ROW_HEIGHT + MESSAGE_ROW_INSET * 2f,
            )
            .controllerTarget("message_$id", layer = focusLayer, cornerRadius = 104f, neighbors = neighbors) { onClick() },
    )
    Box(
        modifier = Modifier
            .designBounds(metrics, 4f, y, 1132f + 2f * metrics.overscanX, 187f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.41346f to Color.Transparent,
                        1f to palette.tintBottom,
                    ),
                ),
            )
            .testTag("message_$id")
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val avatarShape = RoundedCornerShape(metrics.dp(62.325f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 41f, 29.4f, 124.65f, 124.65f)
                .clip(avatarShape)
                .pocketFrame(
                    palette.tintBottom,
                    metrics.dp(8.1f),
                    palette.avatarBorder,
                    avatarShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (conversation.isGroup) {
                AvatarCollage(
                    metrics = metrics,
                    members = conversation.othersThan(selfId),
                    size = 124.65f,
                    initialColor = palette.name,
                    tileFill = palette.tintBottom,
                    divider = palette.avatarBorder,
                    tag = "avatar_collage_$id",
                )
            } else {
                Text(
                    text = conversation.title.trim().firstOrNull()?.uppercase() ?: "?",
                    color = palette.name,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    fontSize = metrics.sp(58f),
                    maxLines = 1,
                )
                DynamicAvatar(
                    avatar = conversation.avatar,
                    fallbackResource = null,
                    modifier = Modifier
                        .requiredSize(metrics.dp(124.65f))
                        .clip(avatarShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = conversation.title,
            modifier = Modifier.designBounds(metrics, 193f, 24f, 700f + 2f * metrics.overscanX, 104f),
            color = palette.name,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(80f),
            maxLines = 1,
        )
        Text(
            text = conversation.latestMessagePreview.let { preview ->
                if (preview.startsWith(">") || preview.isBlank()) preview else "> $preview"
            },
            modifier = Modifier.designBounds(metrics, 193f, 105f, 620f + 2f * metrics.overscanX, 60f),
            color = palette.preview,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(42.024f),
            maxLines = 1,
        )
        Text(
            text = conversation.unreadCount.toString(),
            modifier = Modifier.designBounds(metrics, 934f + 2f * metrics.overscanX, 47f, 133f, 105f),
            color = palette.count,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(80f),
            textAlign = TextAlign.Right,
            maxLines = 1,
        )
    }
}

@Composable
internal fun messageListRowPalette(): MessageRowPalette {
    val theme = pocketPalette
    return MessageRowPalette(
        name = theme.ink(Color(0xFF2365D3)),
        preview = theme.ink(Color(0xFF5B83E5)),
        count = Color(0xFF1371F5),
        tintBottom = theme.tint(Color(0xFFDDE7FC)),
        avatarBorder = theme.tint(Color(0xFFE2E4F0)),
    )
}

internal data class MessageRowPalette(
    val name: Color,
    val preview: Color,
    val count: Color,
    val tintBottom: Color,
    val avatarBorder: Color,
)

@Composable
private fun ConversationNoticeBanner(
    metrics: DesignMetrics,
    notice: String,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(45f))
    Box(
        Modifier
            .designBounds(metrics, 170f, 952f, 900f, 90f)
            .pocketShadow(metrics, 45f),
    )
    Box(
        modifier = Modifier
            .designBounds(metrics, 170f, 940f, 900f, 90f)
            .clip(shape)
            .pocketFrame(greyPanelBrush(), metrics.dp(12f), pocketPalette.borderGrey, shape)
            .testTag("conversation_notice")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = notice,
            modifier = Modifier.padding(horizontal = metrics.dp(40f)),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val CONVERSATION_NOTICE_MILLIS = 4_000L

@Composable
private fun MessagesEmptyPanel(metrics: DesignMetrics) {
    EmptyStateRow(
        metrics = metrics,
        icon = Assets.NavMessages,
        title = "No messages yet",
        subtitle = "Open a profile and tap Message to start chatting",
        tag = "messages_empty",
    )
}

@Composable
private fun MessageDetailBottom(
    conversationId: String,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val conversation = state.conversations.firstOrNull { it.id.value == conversationId }
    val selfId = state.profile?.userId
    val isGroup = conversation?.isGroup == true
    val partnerMessages = if (selfId == null) {
        emptyList()
    } else {
        state.selectedMessages.filter { it.senderId != selfId }
    }
    val partner = conversation
        ?.takeUnless { it.isGroup }
        ?.othersThan(selfId)
        ?.firstOrNull()
        ?.let { member -> state.friends.firstOrNull { it.profile.userId == member.userId } }
        ?: partnerMessages.firstOrNull()?.senderId
            ?.let { partnerId -> state.friends.firstOrNull { it.profile.userId == partnerId } }
    val partnerLastActive = listOfNotNull(
        partner?.profile?.lastSeenAt,
        partnerMessages.maxByOrNull { it.createdAt }?.createdAt,
    ).maxOrNull()
    val partnerStatus = when {
        isGroup && conversation != null -> groupSubtitle(conversation, state.typingUserIds)
        conversationId in state.typingConversationIds -> "typing…"
        partner?.isOnline == true -> "now"
        partnerLastActive != null -> relativeTime(partnerLastActive).lowercase()
        else -> ""
    }
    val density = LocalDensity.current
    val surfaceMetrics = LocalDesignMetrics.current
    val metrics = surfaceMetrics ?: remember(density) { DesignMetrics(density) }
    val canSend = state.messageDraft.trim().isNotEmpty() &&
        state.messageDraft.length <= 4_000 &&
        !state.messageSendInProgress

    LaunchedEffect(conversationId, conversation) {
        if (conversation == null) dispatch(PocketPassEvent.Back)
    }

    Box(Modifier.fillMaxSize()) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.MessagesDetailPattern,
            topColor = pocketPalette.tint(Color(0xFFE9F1F6)),
            bottomColor = pocketPalette.tint(Color(0xFFD1EDFB)),
            holdFraction = 0.4375f,
            designWidth = BOTTOM_DESIGN_WIDTH,
            designHeight = BOTTOM_DESIGN_HEIGHT,
        )

        val headerInteraction = remember(conversationId) { MutableInteractionSource() }
        if (isGroup && conversation != null) {
            GroupThreadHeader(
                metrics = metrics,
                conversation = conversation,
                subtitle = partnerStatus,
                selfId = selfId,
                onBack = { dispatch(PocketPassEvent.Back) },
                onInfo = { dispatch(PocketPassEvent.OpenGroupInfo) },
            )
        } else Box(
            modifier = Modifier
                .designBounds(metrics, 62f, 50f, 1116f, 200f)
                .testTag("message_back")
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                ) { dispatch(PocketPassEvent.Back) },
        ) {
            val avatarShape = RoundedCornerShape(metrics.dp(85f))
            Box(
                modifier = Modifier
                    .designBounds(metrics, 0f, 15f, 170f, 170f)
                    .clip(avatarShape)
                    .pocketFrame(
                        pocketPalette.tint(Color(0xFFD1EDFB)),
                        metrics.dp(10f),
                        Color(0xFF76B3C1),
                        avatarShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = conversation?.title?.trim()?.firstOrNull()?.uppercase() ?: "?",
                    color = pocketPalette.ink(Color(0xFF386F7F)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    fontSize = metrics.sp(78f),
                    maxLines = 1,
                )
                DynamicAvatar(
                    avatar = conversation?.avatar,
                    fallbackResource = null,
                    modifier = Modifier
                        .requiredSize(metrics.dp(170f))
                        .clip(avatarShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier.designBounds(metrics, 208f, 8f, 908f, 114f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = conversation?.title.orEmpty(),
                    color = pocketPalette.ink(Color(0xFF386F7F)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(96f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.designBounds(metrics, 212f, 128f, 500f, 72f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = partnerStatus,
                    color = pocketPalette.ink(Color(0xFF5591A4)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(64f),
                    maxLines = 1,
                )
            }
        }

        var keyboardLayout by remember(conversationId) {
            mutableStateOf(PocketKeyboardLayout.Text)
        }

        MessageComposer(
            metrics = metrics,
            value = state.messageDraft,
            canSend = canSend,
            editing = state.editingMessageId != null,
            error = state.messageOperationError,
            onSend = {
                if (canSend) dispatch(PocketPassEvent.SendMessage)
            },
            onPickPhoto = { dispatch(PocketPassEvent.PickMessageImage) },
        )

        PocketKeyboard(
            metrics = metrics,
            layout = keyboardLayout,
            submitLabel = if (state.editingMessageId != null) "Save" else "Send",
            submitEnabled = canSend,
            height = MESSAGE_KEYBOARD_HEIGHT,
            canBackspace = state.messageDraft.isNotEmpty(),
            emojiKey = true,
            submitSound = null,
            topRowUpTarget = { centerX ->
                if (centerX < BOTTOM_DESIGN_WIDTH / 2f) "message_field" else "message_send"
            },
            onKey = { key ->
                when (key) {
                    is PocketKey.Character -> dispatch(
                        PocketPassEvent.UpdateMessageDraft(
                            state.messageDraft + key.value,
                        ),
                    )

                    PocketKey.Space -> dispatch(
                        PocketPassEvent.UpdateMessageDraft("${state.messageDraft} "),
                    )

                    PocketKey.Backspace -> dispatch(
                        PocketPassEvent.UpdateMessageDraft(
                            state.messageDraft.dropLastCodePoint(),
                        ),
                    )

                    PocketKey.Alphabet -> keyboardLayout = PocketKeyboardLayout.Text

                    PocketKey.Emoji -> keyboardLayout = PocketKeyboardLayout.Emoji

                    PocketKey.Submit -> if (canSend) {
                        dispatch(PocketPassEvent.SendMessage)
                    }
                }
            },
        )

        val actionMessage = state.selectedMessages
            .firstOrNull { it.id.value == state.messageActionMessageId }
        val retainedAction = remember { mutableStateOf(actionMessage) }
        if (actionMessage != null) retainedAction.value = actionMessage
        retainedAction.value?.let { retained ->
            MessageActionsSheet(
                colour = state.messageColour(retained.senderId),
                metrics = metrics,
                message = retained,
                visible = actionMessage != null,
                dispatch = dispatch,
                onHidden = { retainedAction.value = null },
            )
        }
        ExitingOverlay(
            metrics = metrics,
            visible = state.groupInfoOpen && isGroup,
            snapshot = state,
        ) { shown -> GroupInfoBottomOverlay(metrics, shown, dispatch) }
    }
}

@Composable
internal fun MessageBubble(
    metrics: DesignMetrics,
    message: Message,
    outgoing: Boolean,
    onRetry: () -> Unit,
    arrivalPop: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    selected: Boolean = false,
    senderLabel: SenderLabel? = null,
    colour: com.pocketpass.app.domain.model.ChatBubbleColour = com.pocketpass.app.domain.model.ChatBubbleColour.Default,
) {
    val failed = message.pendingState as? PendingState.Failed
    val attachment = message.attachment
    val palette = remember(colour, outgoing) { chatBubblePalette(colour, outgoing) }
    val pressInteraction = remember(message.id.value) { MutableInteractionSource() }
    val sendEntrance = remember(message.id.value) {
        Animatable(
            if (arrivalPop ||
                (outgoing &&
                    (message.pendingState is PendingState.Queued ||
                        message.pendingState is PendingState.Sending))
            ) {
                0f
            } else {
                1f
            },
        )
    }
    LaunchedEffect(message.id.value) {
        if (sendEntrance.value < 1f) {
            if (!platformAnimationsEnabled()) {
                sendEntrance.snapTo(1f)
            } else {
                sendEntrance.animateTo(
                    1f,
                    spring(dampingRatio = 0.75f, stiffness = 430f),
                )
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        if (senderLabel != null && !outgoing) {
            Row(
                modifier = Modifier
                    .padding(start = metrics.dp(BUBBLE_INSET / 2f), bottom = metrics.dp(10f))
                    .testTag("message_sender_${message.id.value}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(metrics.dp(44f))
                        .clip(CircleShape)
                        .pocketFrame(Color(0xFFEDD85E), metrics.dp(4f), Color(0xFFC2B04B), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = senderLabel.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Black,
                        fontSize = metrics.sp(22f),
                        maxLines = 1,
                    )
                    DynamicAvatar(
                        avatar = senderLabel.avatar,
                        fallbackResource = null,
                        modifier = Modifier
                            .requiredSize(metrics.dp(44f))
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    text = senderLabel.name,
                    modifier = Modifier.padding(start = metrics.dp(14f)),
                    color = pocketPalette.ink(Color(0xFF8C6D0D)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(30f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val progress = sendEntrance.value
                    val lift = if (selected) 1.04f else 1f
                    transformOrigin = TransformOrigin(if (outgoing) 0.92f else 0.08f, 1f)
                    scaleX = (0.55f + 0.45f * progress) * lift
                    scaleY = (0.55f + 0.45f * progress) * lift
                    translationY = (1f - progress) * 46f
                    alpha = (progress * 1.8f).coerceIn(0f, 1f)
                }
                .then(
                    if (onLongPress == null) {
                        Modifier
                    } else {
                        Modifier
                            .testTag("message_${message.id.value}")
                            .combinedClickable(
                                interactionSource = pressInteraction,
                                indication = null,
                                onLongClick = onLongPress,
                                onClick = {},
                            )
                    },
                ),
        ) {
            val bubbleRadius = if (attachment != null) ATTACHMENT_RADIUS else BUBBLE_RADIUS
            val shape = RoundedCornerShape(metrics.dp(bubbleRadius))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = metrics.dp(BUBBLE_SHADOW_DROP))
                    .pocketShadow(metrics, bubbleRadius, 0.11f, 6f),
            )
            if (attachment != null) {
                MessageAttachmentCard(
                    metrics = metrics,
                    attachment = attachment,
                    palette = palette,
                    shape = shape,
                    caption = message.body.takeUnless {
                        it == IMAGE_MESSAGE_PLACEHOLDER_BODY
                    },
                    onLongPress = onLongPress,
                )
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(
                            min = metrics.dp(BUBBLE_MIN_WIDTH),
                            max = metrics.dp(BUBBLE_MAX_WIDTH),
                        )
                        .clip(shape)
                        .pocketFrame(
                            Brush.verticalGradient(colorStops = palette.fill),
                            metrics.dp(BUBBLE_BORDER),
                            palette.border,
                            shape,
                        )
                        .padding(metrics.dp(BUBBLE_INSET)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column {
                        Text(
                            text = message.body,
                            color = palette.text,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = metrics.sp(BUBBLE_TEXT_SIZE),
                            lineHeight = metrics.sp(BUBBLE_LINE_HEIGHT),
                            textAlign = TextAlign.Left,
                        )
                        if (message.editedAt != null) {
                            Text(
                                text = "Edited",
                                modifier = Modifier.padding(top = metrics.dp(6f)),
                                color = palette.text.copy(alpha = 0.8f),
                                fontFamily = Rubik,
                                fontWeight = FontWeight.Medium,
                                fontSize = metrics.sp(24f),
                            )
                        }
                    }
                }
            }
            MessageBubbleTail(metrics, palette, outgoing)
            if (failed != null) {
                val retryInteraction = remember(message.id) { MutableInteractionSource() }
                Text(
                    text = "Retry",
                    modifier = Modifier
                        .align(if (outgoing) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(
                            start = metrics.dp(34f),
                            end = metrics.dp(34f),
                            bottom = metrics.dp(13f),
                        )
                        .testTag("retry_${message.id.value}")
                        .clickable(
                            interactionSource = retryInteraction,
                            indication = null,
                            onClick = onRetry,
                        ),
                    color = palette.text,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(21f),
                )
            }
        }
        }
    }
}

internal fun Message.isEditable(): Boolean =
    pendingState == PendingState.Synced && deletedAt == null

private const val BUBBLE_RADIUS = 100f
private const val ATTACHMENT_RADIUS = 60f
private const val BUBBLE_BORDER = 16.122f
private const val BUBBLE_INSET = 46.122f
private const val BUBBLE_MIN_WIDTH = 240f
private const val BUBBLE_MAX_WIDTH = 1060.244f
private const val BUBBLE_TEXT_SIZE = 67.413f
private const val BUBBLE_LINE_HEIGHT = 80f
private const val BUBBLE_SHADOW_DROP = 9.6f
private const val BUBBLE_TAIL_WIDTH = 102.608f
private const val BUBBLE_TAIL_HEIGHT = 99.77f
private const val BUBBLE_TAIL_SHIFT = 79.73f
private const val BUBBLE_TAIL_DROP = 80.27f
private const val BUBBLE_TAIL_ROTATION = 141.4f

@Composable
private fun BoxScope.MessageBubbleTail(
    metrics: DesignMetrics,
    palette: BubblePalette,
    outgoing: Boolean,
) {
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .align(if (outgoing) Alignment.BottomEnd else Alignment.BottomStart)
                .offset(
                    x = metrics.dp(if (outgoing) BUBBLE_TAIL_SHIFT else -BUBBLE_TAIL_SHIFT),
                    y = metrics.dp(BUBBLE_TAIL_DROP),
                )
                .requiredSize(
                    metrics.dp(BUBBLE_TAIL_WIDTH),
                    metrics.dp(BUBBLE_TAIL_HEIGHT),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(metrics.dp(81f), metrics.dp(63f))
                    .graphicsLayer {
                        rotationZ = if (outgoing) {
                            BUBBLE_TAIL_ROTATION
                        } else {
                            -BUBBLE_TAIL_ROTATION
                        }
                    },
            ) {
                val tailModifier = Modifier.designBounds(metrics, 6.674f, -4.164f, 67.656f, 77.134f)
                if (palette.tail != null) FigmaAsset(resource = palette.tail, modifier = tailModifier)
                else ColouredBubbleTail(palette, tailModifier)
            }
        }
    }
}

@Composable
private fun MessageAttachmentCard(
    metrics: DesignMetrics,
    attachment: MessageAttachment,
    palette: BubblePalette,
    shape: RoundedCornerShape,
    caption: String?,
    onLongPress: (() -> Unit)? = null,
) {
    var expanded by remember(attachment) { mutableStateOf(false) }
    if (expanded) MessageImageViewer(attachment) { expanded = false }
    Column(
        modifier = Modifier
            .requiredWidth(metrics.dp(560f))
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(colorStops = palette.fill),
                metrics.dp(BUBBLE_BORDER),
                palette.border,
                shape,
            )
            .padding(metrics.dp(34f)),
    ) {
        AsyncImage(
            model = attachment.localPath?.toPath() ?: attachment.remotePath,
            contentDescription = if (attachment.mimeType == "image/gif") "GIF attachment" else "Image attachment",
            modifier = Modifier
                .requiredSize(metrics.dp(492f), metrics.dp(340f))
                .clip(RoundedCornerShape(metrics.dp(44f)))
                .background(Color.White.copy(alpha = 0.22f))
                .testTag("message_attachment")
                .combinedClickable(onClickLabel = "Open full screen", onClick = { expanded = true }, onLongClick = onLongPress),
            contentScale = ContentScale.Fit,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                modifier = Modifier.padding(
                    start = metrics.dp(14f),
                    end = metrics.dp(14f),
                    top = metrics.dp(20f),
                    bottom = metrics.dp(6f),
                ),
                color = palette.text,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(52f),
                lineHeight = metrics.sp(62f),
            )
        }
    }
}

@Composable
private fun MessageComposer(
    metrics: DesignMetrics,
    value: String,
    canSend: Boolean,
    editing: Boolean,
    error: String?,
    onSend: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val fieldShape = RoundedCornerShape(metrics.dp(80.75f))
    val sendInteraction = remember { MutableInteractionSource() }
    val photoInteraction = remember { MutableInteractionSource() }
    val focus = LocalControllerFocus.current

    Box(
        modifier = Modifier.designBounds(metrics, 62f, COMPOSER_RESTING_Y, 761.063f, 177.174f),
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 0f, 0f, 761.063f, 161.5f)
                .testTag("message_field")
                .controllerTarget("message_field", cornerRadius = 80.75f) { focus?.move(FocusDirection.Down) }
                .clip(fieldShape)
                .pocketFrame(
                    Brush.verticalGradient(
                        listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFBDF8CB))),
                    ),
                    metrics.dp(18f),
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF76B3C1),
                            Color(0xFF5A96A9),
                            Color(0xFF22677C),
                        ),
                    ),
                    fieldShape,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.dp(51f))
                    .testTag("message_composer"),
                contentAlignment = Alignment.CenterStart,
            ) {
                val composerScroll = rememberScrollState()
                LaunchedEffect(composerScroll.maxValue) {
                    composerScroll.scrollTo(composerScroll.maxValue)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(composerScroll, enabled = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (value.isEmpty()) {
                        TypingCaret(metrics, pocketPalette.teal, 62f)
                        Spacer(Modifier.width(metrics.dp(8f)))
                        Text(
                            text = if (editing) "Edit message" else "Message",
                            color = pocketPalette.ink(Color(0xFF2F948C)),
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Bold,
                            fontSize = metrics.sp(57.747f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    } else {
                        Text(
                            text = value,
                            color = pocketPalette.teal,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Bold,
                            fontSize = metrics.sp(57.747f),
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.width(metrics.dp(8f)))
                        TypingCaret(metrics, pocketPalette.teal, 62f)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .designBounds(metrics, 842.079f, COMPOSER_RESTING_Y, 158.452f, 177.174f)
            .testTag("message_send")
            .clickable(
                interactionSource = sendInteraction,
                indication = null,
                enabled = canSend,
                onClick = onSend,
            ),
    ) {
        FigmaAsset(
            resource = Assets.MessagesSendButton,
            modifier = Modifier.fillMaxSize(),
            alpha = if (canSend) 1f else 0.72f,
        )
        Box(
            Modifier
                .designBounds(metrics, 0f, 0f, 158.452f, 161.5f)
                .controllerTarget("message_send", cornerRadius = 80.75f) { if (canSend) onSend() },
        )
    }

    val photoShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .designBounds(metrics, 1019.548f, COMPOSER_RESTING_Y, 158.452f, PHOTO_BUTTON_HEIGHT)
            .testTag("message_photo")
            .controllerTarget("message_photo", cornerRadius = 79f) { onPickPhoto() }
            .clip(photoShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        PHOTO_BUTTON_FILL_HOLD to pocketPalette.surface,
                        1f to pocketPalette.tint(Color(0xFFBDF8CB)),
                    ),
                ),
                metrics.dp(18f),
                Brush.verticalGradient(
                    listOf(Color(0xFF5A96A9), Color(0xFF286C81)),
                ),
                photoShape,
            ),
    ) {
        FigmaAsset(
            resource = Assets.MessageActionImage,
            modifier = Modifier.designBounds(
                metrics,
                (158.452f - PHOTO_GLYPH_SIZE) / 2f,
                (PHOTO_BUTTON_HEIGHT - PHOTO_GLYPH_SIZE) / 2f,
                PHOTO_GLYPH_SIZE,
                PHOTO_GLYPH_SIZE,
            ),
        )
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, 990f, COMPOSER_RESTING_Y - 38.5f, 220f, 230f)
            .clickable(
                interactionSource = photoInteraction,
                indication = null,
                onClick = onPickPhoto,
            ),
    )

    if (editing) {
        MessageEditingChip(
            metrics = metrics,
            modifier = Modifier.designBounds(metrics, 62f, COMPOSER_RESTING_Y - 66f, 560f, 52f),
        )
    }

    if (error != null) {
        Text(
            text = error,
            modifier = Modifier.designBounds(
                metrics,
                112f,
                COMPOSER_RESTING_Y + 170.5f,
                720f,
                35f,
            ),
            color = pocketPalette.ink(Color(0xFF9D3131)),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(22f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MESSAGE_ROW_HEIGHT = 187f
private const val MESSAGE_ROW_INSET = 13f
private const val MESSAGE_PANEL_TOP = 435f
private const val MESSAGE_PANEL_BOTTOM_MARGIN = 50f
private const val MESSAGE_PANEL_OVERSCAN_MAX = 34f

private const val COMPOSER_RESTING_Y = 380f

private const val MESSAGE_KEYBOARD_HEIGHT = 500f

private const val PHOTO_BUTTON_HEIGHT = 161.5f
private const val PHOTO_BUTTON_FILL_HOLD = 0.6265f
private const val PHOTO_GLYPH_SIZE = 81.86f

private const val ADD_FRIEND_KEYPAD_LIFT = 80f
private const val ADD_FRIEND_FOCUS_LAYER = 10

@Composable
private fun SettingsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        val scroll = rememberScrollState()
        val belowTabBar = remember(metrics) { BelowTabBarShape(metrics) }
        val focusViewport = rememberBelowTabBarFocusViewport(metrics)
        val stack = SettingsStack()
        val nearbyY = stack.place(SETTINGS_ROW_HEIGHT)
        val stepsY = if (state.stepRewards.supported) stack.place(SETTINGS_ROW_HEIGHT) else null
        val soundY = stack.place(SOUND_PANEL_HEIGHT)
        val appSettingsY = stack.place(SETTINGS_ROW_HEIGHT)
        val socialY = stack.place(SETTINGS_ROW_HEIGHT)
        val versionY = stack.place(SETTINGS_ROW_HEIGHT)
        val accountY = stack.place(SETTINGS_ROW_HEIGHT)
        val logoutY = stack.place(SETTINGS_ROW_HEIGHT)
        val deleteY = stack.place(SETTINGS_TALL_HEIGHT)
        val contributorsY = stack.place(SETTINGS_ROW_HEIGHT)
        val reveal = LocalRouteRevealGeneration.current
        val rowBounds = listOfNotNull(
            nearbyY to SETTINGS_ROW_HEIGHT,
            stepsY?.let { it to SETTINGS_ROW_HEIGHT },
            soundY to SOUND_PANEL_HEIGHT,
            appSettingsY to SETTINGS_ROW_HEIGHT,
            socialY to SETTINGS_ROW_HEIGHT,
            versionY to SETTINGS_ROW_HEIGHT,
            accountY to SETTINGS_ROW_HEIGHT,
            logoutY to SETTINGS_ROW_HEIGHT,
            deleteY to SETTINGS_TALL_HEIGHT,
            contributorsY to SETTINGS_ROW_HEIGHT,
        )
        val revealScroll = remember(reveal) {
            Snapshot.withoutReadObservation { scroll.value.toFloat() }
        }
        val firstVisibleRow = rowBounds
            .indexOfFirst { (y, height) -> y + height > revealScroll }
            .coerceAtLeast(0)
        val row: @Composable (Float, Float, @Composable BoxScope.() -> Unit) -> Unit =
            { y, height, content ->
                val index = rowBounds.indexOfFirst { it.first == y }
                SettingsRowReveal(
                    reveal = reveal,
                    order = (index - firstVisibleRow).coerceAtLeast(0),
                    y = y,
                    height = height,
                    totalHeight = stack.totalHeight,
                    content = content,
                )
            }
        DesignBox(
            metrics,
            0f,
            0f,
            1240f,
            1080f,
            DesignAnchor.Stretch,
            DesignAnchor.Stretch,
            modifier = Modifier
                .clip(belowTabBar)
                .controllerFocusViewport(focusViewport)
                .verticalScroll(scroll)
                .testTag("settings_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(
                    modifier = Modifier
                        .padding(top = metrics.dp(247f))
                        .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                        .requiredHeight(metrics.dp(stack.totalHeight)),
                ) {
                    row(nearbyY, SETTINGS_ROW_HEIGHT) {
                        NearbyPanel(metrics, nearbyY, state.nearbyEnabled) {
                            dispatch(PocketPassEvent.SetNearby(!state.nearbyEnabled))
                        }
                    }
                    stepsY?.let { y ->
                        row(y, SETTINGS_ROW_HEIGHT) {
                            StepRewardsPanel(metrics, y, state, dispatch)
                        }
                    }
                    row(soundY, SOUND_PANEL_HEIGHT) {
                        SoundPanel(
                            metrics = metrics,
                            y = soundY,
                            musicLevel = state.soundLevel,
                            sfxLevel = state.sfxLevel,
                            onMusicLevelChange = { dispatch(PocketPassEvent.SetSoundLevel(it)) },
                            onSfxLevelChange = { dispatch(PocketPassEvent.SetSfxLevel(it)) },
                        )
                    }
                    row(appSettingsY, SETTINGS_ROW_HEIGHT) {
                        AppSettingsPanel(metrics, appSettingsY) {
                            dispatch(PocketPassEvent.OpenAppSettings)
                        }
                    }
                    row(socialY, SETTINGS_ROW_HEIGHT) {
                        SocialPanel(metrics, socialY) {
                            dispatch(PocketPassEvent.OpenSocial)
                        }
                    }
                    row(versionY, SETTINGS_ROW_HEIGHT) {
                        VersionPanel(metrics, versionY, state.appUpdate) {
                            dispatch(PocketPassEvent.OpenAppUpdate)
                        }
                    }
                    row(accountY, SETTINGS_ROW_HEIGHT) {
                        AccountPanel(metrics, accountY) {
                            dispatch(PocketPassEvent.OpenAccountSecurity)
                        }
                    }
                    row(logoutY, SETTINGS_ROW_HEIGHT) {
                        LogoutPanel(metrics, logoutY) {
                            dispatch(PocketPassEvent.SignOut)
                        }
                    }
                    row(deleteY, SETTINGS_TALL_HEIGHT) {
                        DeletePanel(metrics, deleteY) {
                            dispatch(PocketPassEvent.OpenDeleteAccount)
                        }
                    }
                    row(contributorsY, SETTINGS_ROW_HEIGHT) {
                        ContributorsPanel(metrics, contributorsY) {
                            dispatch(PocketPassEvent.OpenContributors)
                        }
                    }
                }
            }
        }
    }
}

internal const val SETTINGS_PANEL_GAP = 50f
internal const val SETTINGS_ROW_HEIGHT = 220f
internal const val SETTINGS_TALL_HEIGHT = 446.65f
internal const val THEME_PANEL_HEIGHT = 447f
internal val CREDITS_PANEL_HEIGHT: Float
    get() = CREDITS_PANEL_PADDING * 2f + CREDITS_AVATAR_SIZE + (CreditsRoster.size - 1) * CREDITS_ROW_PITCH
private const val CREDITS_PANEL_PADDING = 52f
private const val CREDITS_AVATAR_SIZE = 124.65f
private const val CREDITS_ROW_PITCH = 179.65f

private class SettingsStack {
    var totalHeight = SETTINGS_PANEL_GAP
        private set

    fun place(height: Float): Float {
        val top = totalHeight
        totalHeight += height + SETTINGS_PANEL_GAP
        return top
    }
}

@Composable
internal fun NearbyPanel(
    metrics: DesignMetrics,
    y: Float,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "nearby_toggle",
        onClick = onToggle,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsNearby,
            title = "Nearby Encounters",
            subtitle = "Encounter via Bluetooth",
        )
        NearbyToggle(
            metrics = metrics,
            enabled = enabled,
        )
    }
}

@Composable
internal fun NearbyToggle(
    metrics: DesignMetrics,
    enabled: Boolean,
    x: Float = metrics.anchoredX(910f, DesignAnchor.End),
    y: Float = 57.3f,
) {
    val progress = animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 680f,
            visibilityThreshold = 0.001f,
        ),
        label = "Nearby encounters toggle",
    )
    val trackShape = RoundedCornerShape(metrics.dp(52.5f))
    val thumbShape = RoundedCornerShape(metrics.dp(42.5f))

    Box(
        Modifier
            .designBounds(metrics, x, y + 9f, 169f, 105f)
            .clip(trackShape)
            .background(Color.Black.copy(alpha = 0.08f)),
    )
    Box(
        Modifier
            .designBounds(metrics, x, y, 169f, 105f)
            .clip(trackShape)
            .background(greyButtonBrush()),
    )
    Box(
        Modifier
            .designBounds(metrics, x, y, 169f, 105f)
            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            .clip(trackShape)
            .background(greenButtonBrush()),
    )
    Box(
        Modifier
            .designBounds(metrics, x, y, 169f, 105f)
            .clip(trackShape)
            .drawWithCache {
                val outerWidth = metrics.dp(9f).toPx()
                val innerWidth = metrics.dp(4.5f).toPx()
                val radius = metrics.dp(52.5f).toPx()
                onDrawBehind {
                    val fraction = progress.value.coerceIn(0f, 1f)
                    drawRoundRect(
                        color = lerp(Color(0xFFB2B2B2), Color(0xFF4BC252), fraction),
                        topLeft = Offset(outerWidth / 2f, outerWidth / 2f),
                        size = Size(size.width - outerWidth, size.height - outerWidth),
                        cornerRadius = CornerRadius(radius - outerWidth / 2f),
                        style = Stroke(outerWidth),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.3f * fraction),
                        topLeft = Offset(innerWidth / 2f, innerWidth / 2f),
                        size = Size(size.width - innerWidth, size.height - innerWidth),
                        cornerRadius = CornerRadius(radius - innerWidth / 2f),
                        style = Stroke(innerWidth),
                    )
                }
            },
    )
    Box(
        Modifier
            .designBounds(metrics, 85f, 85f) {
                Offset(x + 10f + (64f * progress.value.coerceIn(0f, 1f)), y + 15f)
            }
            .clip(thumbShape)
            .background(Color.Black.copy(alpha = 0.12f)),
    )
    Box(
        Modifier
            .designBounds(metrics, 85f, 85f) {
                Offset(x + 10f + (64f * progress.value.coerceIn(0f, 1f)), y + 10f)
            }
            .graphicsLayer {
                val fraction = progress.value.coerceIn(0f, 1f)
                val compression = 1f - 0.035f * (1f - kotlin.math.abs(fraction * 2f - 1f))
                scaleX = compression
                scaleY = 2f - compression
            }
            .clip(thumbShape)
            .background(Color.White)
            .pocketBorder(
                metrics.dp(5.66f),
                Color.White.copy(alpha = 0.6f),
                thumbShape,
            ),
    )
}

internal const val SOUND_PANEL_HEIGHT = 730f

@Composable
internal fun SoundPanel(
    metrics: DesignMetrics,
    y: Float,
    musicLevel: Float,
    sfxLevel: Float,
    onMusicLevelChange: (Float) -> Unit,
    onSfxLevelChange: (Float) -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SOUND_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
        tag = "sound_panel",
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsSound,
            title = "Sound",
            subtitle = "Music and sound effect volumes",
        )
        Box(
            Modifier
                .designBounds(metrics, 52f, 228.65f, 1036f + 2f * metrics.overscanX, 9f)
                .clip(RoundedCornerShape(metrics.dp(4.5f)))
                .background(pocketPalette.borderSoft),
        )
        SliderLabel(metrics, "Music", SOUND_MUSIC_LABEL_Y)
        SoundSlider(
            metrics = metrics,
            y = SOUND_MUSIC_SLIDER_Y,
            level = musicLevel,
            tag = "sound_slider",
            preview = null,
            onLevelChange = onMusicLevelChange,
        )
        SliderLabel(metrics, "Sound Effects", SOUND_SFX_LABEL_Y)
        SoundSlider(
            metrics = metrics,
            y = SOUND_SFX_SLIDER_Y,
            level = sfxLevel,
            tag = "sfx_slider",
            preview = SoundEffect.Navigation,
            onLevelChange = onSfxLevelChange,
        )
    }
}

@Composable
private fun SliderLabel(
    metrics: DesignMetrics,
    text: String,
    y: Float,
) {
    Text(
        text = text,
        modifier = Modifier.designBounds(metrics, 62f, y, 900f, 55f),
        color = pocketPalette.textSecondary,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(45f),
        maxLines = 1,
    )
}

private const val SOUND_MUSIC_LABEL_Y = 258f
private const val SOUND_MUSIC_SLIDER_Y = 318f
private const val SOUND_SFX_LABEL_Y = 486f
private const val SOUND_SFX_SLIDER_Y = 546f
private const val SOUND_STEP = 0.05f

@Composable
private fun SoundSlider(
    metrics: DesignMetrics,
    y: Float,
    level: Float,
    tag: String,
    preview: SoundEffect?,
    onLevelChange: (Float) -> Unit,
) {
    val trackWidth = 1036f + 2f * metrics.overscanX
    val dragging = remember { mutableStateOf(false) }
    val soundEffects = LocalSoundEffects.current
    val latestOnLevelChange = rememberUpdatedState(onLevelChange)
    val animatedLevel = animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = if (dragging.value) {
            snap()
        } else {
            spring(
                dampingRatio = 0.86f,
                stiffness = 620f,
                visibilityThreshold = 0.001f,
            )
        },
        label = "Sound level",
    )
    val thumbScale = animateFloatAsState(
        targetValue = if (dragging.value) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 760f,
            visibilityThreshold = 0.001f,
        ),
        label = "Sound slider thumb press",
    )
    Box(
        modifier = Modifier
            .designBounds(metrics, 52f, y, trackWidth, 138f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging.value = true
                    down.consume()
                    latestOnLevelChange.value(
                        (down.position.x / size.width).coerceIn(0f, 1f),
                    )

                    try {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            latestOnLevelChange.value(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                            )
                            pressed = change.pressed
                            change.consume()
                        }
                    } finally {
                        dragging.value = false
                        preview?.let(soundEffects::play)
                    }
                }
            }
            .testTag(tag),
    ) {
        PocketSliderFace(
            metrics = metrics,
            trackWidth = trackWidth,
            level = animatedLevel,
            thumbScale = thumbScale,
            trackModifier = Modifier.controllerTarget(
                tag,
                cornerRadius = 52.5f,
                onAdjust = { delta ->
                    latestOnLevelChange.value(
                        (level + delta * SOUND_STEP).coerceIn(0f, 1f),
                    )
                    preview?.let(soundEffects::play)
                },
            ) {},
        )
    }
}

@Composable
internal fun PocketSliderFace(
    metrics: DesignMetrics,
    trackWidth: Float,
    level: State<Float>,
    thumbScale: State<Float>,
    trackModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(metrics.dp(52.5f))
    Box(
        Modifier
            .designBounds(metrics, 0f, 16.65f, trackWidth, 105f)
            .then(trackModifier)
            .clip(shape)
            .pocketFrame(
                pocketPalette.surfaceSunken,
                metrics.dp(20.152f),
                pocketPalette.borderGrey,
                shape,
            ),
    )
    Box(
        Modifier
            .graphicsLayer {
                translationX = 0f
                translationY = 16.65f
            }
            .layout { measurable, _ ->
                val fillWidth = trackWidth * level.value.coerceIn(0f, 1f)
                val widthPx = if (fillWidth > 41.5f) {
                    metrics.dp(fillWidth).roundToPx()
                } else {
                    0
                }
                val heightPx = metrics.dp(105f).roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(widthPx, heightPx),
                )
                layout(widthPx, heightPx) { placeable.place(0, 0) }
            }
            .clip(shape)
            .pocketFrame(greenButtonBrush(), metrics.dp(20.152f), Color(0xFF4BC252), shape),
    )
    Box(
        Modifier
            .designBounds(metrics, 83f, 138f) {
                Offset(
                    (trackWidth * level.value.coerceIn(0f, 1f) - 41.5f)
                        .coerceIn(0f, trackWidth - 83f),
                    7f,
                )
            }
            .clip(RoundedCornerShape(metrics.dp(41.5f)))
            .background(Color.Black.copy(alpha = 0.1f)),
    )
    Box(
        Modifier
            .designBounds(metrics, 83f, 138f) {
                Offset(
                    (trackWidth * level.value.coerceIn(0f, 1f) - 41.5f)
                        .coerceIn(0f, trackWidth - 83f),
                    0f,
                )
            }
            .graphicsLayer {
                scaleX = thumbScale.value
                scaleY = thumbScale.value
            }
            .clip(RoundedCornerShape(metrics.dp(41.5f)))
            .pocketFrame(
                Color.White,
                metrics.dp(20.152f),
                Color(0xFFCECECE),
                RoundedCornerShape(metrics.dp(41.5f)),
            ),
    )
}

@Composable
internal fun EditInfoPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "edit_info",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsEditName,
            title = "Edit Info",
            subtitle = "Change your name, age and country",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun EditMiiPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "edit_mii",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsEditMii,
            title = "Edit Piip",
            subtitle = "Change or switch between your Piips",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun SocialPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "social",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsSocial,
            title = "Social",
            subtitle = "Your name, Piip and connected apps",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
private fun SocialBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Social",
            subtitle = "Your name, Piip and connected apps.",
            backTag = "social_back",
        ) { dispatch(PocketPassEvent.Back) }
        val rows = buildList<@Composable (Float) -> Unit> {
            add { y -> EditInfoPanel(metrics, y) { dispatch(PocketPassEvent.OpenEditInfo) } }
            if (state.miiEditorEnabled) add { y -> EditMiiPanel(metrics, y) { dispatch(PocketPassEvent.OpenMiiSlots) } }
            if (state.connectedApps.enabled) add { y -> ConnectedAppsPanel(metrics, y) { dispatch(PocketPassEvent.OpenConnectedApps) } }
        }
        val privacyY = SETTINGS_PANEL_GAP + rows.size * SUBPAGE_ROW_PITCH
        val invitesY = privacyY + MESSAGE_PRIVACY_PANEL_HEIGHT + SETTINGS_PANEL_GAP
        val boardsY = invitesY + SOCIAL_PRIVACY_PANEL_HEIGHT + SETTINGS_PANEL_GAP
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        DesignBox(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch,
            modifier = Modifier.clip(belowHeader).controllerFocusViewport(focusViewport)
                .verticalScroll(scroll).testTag("social_settings_scroll")) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(Modifier.padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                    .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                    .requiredHeight(metrics.dp(boardsY + SOCIAL_PRIVACY_PANEL_HEIGHT + SETTINGS_PANEL_GAP))) {
                    rows.forEachIndexed { index, row -> row(SETTINGS_PANEL_GAP + index * SUBPAGE_ROW_PITCH) }
                    MessagePrivacyPanel(metrics, privacyY, state, dispatch)
                    InvitesPrivacyPanel(metrics, invitesY, state, dispatch)
                    BoardsVisibilityPanel(metrics, boardsY, state, dispatch)
                }
            }
        }
    }
}

internal const val SUBPAGE_FIRST_ROW_Y = 297f
internal const val SUBPAGE_ROW_PITCH = SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP

@Composable
internal fun AppSettingsPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "settings_app",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsApp,
            title = "App Settings",
            subtitle = "Notifications, theme and more",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun AccessibilityPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "accessibility",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsAccessibility,
            title = "Accessibility",
            subtitle = "Tune PocketPass' visual effects",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
private fun AppSettingsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "App Settings",
            subtitle = "Notifications, theme and more.",
            backTag = "app_settings_back",
        ) { dispatch(PocketPassEvent.Back) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        val notificationsY = SETTINGS_PANEL_GAP
        val themeY = notificationsY + SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP
        val accessibilityY = themeY + THEME_PANEL_HEIGHT + SETTINGS_PANEL_GAP
        val widgetsY = accessibilityY + SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP
        val chatColoursY = widgetsY + SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP
        val totalHeight = chatColoursY + SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP
        DesignBox(
            metrics,
            0f,
            0f,
            1240f,
            1080f,
            DesignAnchor.Stretch,
            DesignAnchor.Stretch,
            modifier = Modifier
                .clip(belowHeader)
                .controllerFocusViewport(focusViewport)
                .verticalScroll(scroll)
                .testTag("app_settings_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(
                    modifier = Modifier
                        .padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                        .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                        .requiredHeight(metrics.dp(totalHeight)),
                ) {
                    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + notificationsY, height = SETTINGS_ROW_HEIGHT, order = 1) {
                        NotificationsPanel(metrics, notificationsY) {
                            dispatch(PocketPassEvent.OpenNotificationSettings)
                        }
                    }
                    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + themeY, height = THEME_PANEL_HEIGHT, order = 2) {
                        ThemePanel(
                            metrics = metrics,
                            y = themeY,
                            selected = state.themeMode,
                            expanded = state.themePickerExpanded,
                            onExpand = { dispatch(PocketPassEvent.OpenThemePicker) },
                        ) {
                            dispatch(PocketPassEvent.SetThemeMode(it))
                        }
                    }
                    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + accessibilityY, height = SETTINGS_ROW_HEIGHT, order = 3) {
                        AccessibilityPanel(metrics, accessibilityY) {
                            dispatch(PocketPassEvent.OpenAccessibility)
                        }
                    }
                    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + widgetsY, height = SETTINGS_ROW_HEIGHT, order = 4) {
                        WidgetsPanel(metrics, widgetsY, state.widgetDesigns.size) {
                            dispatch(PocketPassEvent.OpenWidgetMaker)
                        }
                    }
                    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + chatColoursY, height = SETTINGS_ROW_HEIGHT, order = 5) {
                        ChatColoursPanel(metrics, chatColoursY) { dispatch(PocketPassEvent.OpenChatColours) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberBelowSubpageHeaderFocusViewport(metrics: DesignMetrics): ControllerFocusViewport {
    val density = LocalDensity.current
    return remember(metrics, density) {
        ControllerFocusViewport(
            shape = BelowSubpageHeaderShape(metrics),
            topInset = with(density) { metrics.dp(SUBPAGE_CONTENT_TOP).toPx() },
        )
    }
}

@Composable
private fun ChatColoursBottom(state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(metrics, title = "Chat Colours", subtitle = "A colour that's yours.", backTag = "chat_colours_back") {
            dispatch(PocketPassEvent.Back)
        }
        val viewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        val shape = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        DesignBox(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch,
            modifier = Modifier.clip(shape).controllerFocusViewport(viewport).verticalScroll(rememberScrollState()).testTag("chat_colours_scroll")) {
            CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
                Box(Modifier.padding(top = metrics.dp(SUBPAGE_CONTENT_TOP + SETTINGS_PANEL_GAP)).fillMaxWidth()) {
                    ChatColoursContent(metrics, state, dispatch)
                }
            }
        }
    }
}

@Composable
private fun AccessibilityBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Accessibility",
            subtitle = "Tune PocketPass' visual effects.",
            backTag = "accessibility_back",
        ) { dispatch(PocketPassEvent.Back) }
        SubpagePanelPop(y = 297f, height = 220f, order = 1) {
            PocketPanel(
                metrics = metrics,
                x = 50f,
                y = 297f,
                width = 1140f,
                height = 220f,
                borderColor = pocketPalette.borderGrey,
                borderWidth = 20.152f,
                radius = 110f,
                fillBrush = greyPanelBrush(),
                tag = "mood_emoji_toggle",
                onClick = {
                    dispatch(
                        PocketPassEvent.SetMoodEmojisEnabled(!state.moodEmojisEnabled),
                    )
                },
            ) {
                SettingsHeading(
                    metrics = metrics,
                    icon = Assets.SettingsMoodEmojis,
                    title = "Mood Emojis",
                    subtitle = "Float up when you set a mood",
                )
                NearbyToggle(
                    metrics = metrics,
                    enabled = state.moodEmojisEnabled,
                )
            }
        }
        if (state.encounterLedSupported) {
            SubpagePanelPop(y = 567f, height = 220f, order = 2) {
                PocketPanel(
                    metrics = metrics,
                    x = 50f,
                    y = 567f,
                    width = 1140f,
                    height = 220f,
                    borderColor = pocketPalette.borderGrey,
                    borderWidth = 20.152f,
                    radius = 110f,
                    fillBrush = greyPanelBrush(),
                    tag = "encounter_led_toggle",
                    onClick = {
                        dispatch(
                            PocketPassEvent.SetEncounterLedEnabled(!state.encounterLedEnabled),
                        )
                    },
                ) {
                    SettingsHeading(
                        metrics = metrics,
                        icon = Assets.SettingsEncounterLed,
                        title = "Encounter Lights",
                        subtitle = "Pulse when you pass someone",
                    )
                    NearbyToggle(
                        metrics = metrics,
                        enabled = state.encounterLedEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Notifications",
            subtitle = "Which events send alerts.",
            backTag = "notification_settings_back",
        ) { dispatch(PocketPassEvent.Back) }
        val messageRow = NotificationSettingsRow(
            Assets.SettingsMessagePrivacy, "Message Alerts", "New direct and group messages",
            "message_alerts_toggle", state.messageAlertsEnabled,
            PocketPassEvent.SetMessageAlertsEnabled(!state.messageAlertsEnabled),
        ).takeIf { state.messagePushSupported }
        val showBoards = state.showsBoardAlertSettings()
        val laterRows = listOf(
            NotificationSettingsRow(
                Assets.SettingsEncounterAlerts, "Encounter Alerts", "Notify when you pass someone",
                "encounter_alerts_toggle", state.encounterAlertsEnabled,
                PocketPassEvent.SetEncounterAlertsEnabled(!state.encounterAlertsEnabled),
            ),
            NotificationSettingsRow(
                Assets.SettingsVersion, "Update Alerts", "Tell me about new versions",
                "update_alerts_toggle", state.updateAlertsEnabled,
                PocketPassEvent.SetUpdateAlertsEnabled(!state.updateAlertsEnabled),
            ),
        )
        val stack = SettingsStack()
        val messageY = messageRow?.let { stack.place(SETTINGS_ROW_HEIGHT) }
        val boardAlertsY = if (showBoards) stack.place(BOARD_ALERTS_PANEL_HEIGHT) else null
        val boardListY = if (showBoards) stack.place(SETTINGS_ROW_HEIGHT) else null
        val laterY = laterRows.map { stack.place(SETTINGS_ROW_HEIGHT) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        DesignBox(
            metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch,
            modifier = Modifier.clip(belowHeader).controllerFocusViewport(focusViewport)
                .verticalScroll(scroll).testTag("notification_settings_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(Modifier.padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                    .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                    .requiredHeight(metrics.dp(stack.totalHeight))) {
                    var order = 1
                    if (messageRow != null && messageY != null) NotificationToggleRow(metrics, messageRow, messageY, order++, dispatch)
                    if (boardAlertsY != null) {
                        SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + boardAlertsY, height = BOARD_ALERTS_PANEL_HEIGHT, order = order++) {
                            BoardAlertsPanel(
                                metrics = metrics,
                                y = boardAlertsY,
                                enabled = state.boards.pushEnabled,
                                level = state.boards.alertLevel,
                                onToggle = { dispatch(PocketPassEvent.SetBoardAlertsEnabled(!state.boards.pushEnabled)) },
                                onLevel = { dispatch(PocketPassEvent.SetBoardAlertLevel(it)) },
                            )
                        }
                    }
                    if (boardListY != null) {
                        SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + boardListY, height = SETTINGS_ROW_HEIGHT, order = order++) {
                            BoardNotificationsPanel(metrics, boardListY) { dispatch(PocketPassEvent.OpenBoardNotificationSettings) }
                        }
                    }
                    laterRows.forEachIndexed { index, row -> NotificationToggleRow(metrics, row, laterY[index], order++, dispatch) }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    metrics: DesignMetrics,
    row: NotificationSettingsRow,
    y: Float,
    order: Int,
    dispatch: (PocketPassEvent) -> Unit,
) {
    SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + y, height = SETTINGS_ROW_HEIGHT, order = order) {
        PocketPanel(
            metrics = metrics, x = 50f, y = y, width = 1140f, height = SETTINGS_ROW_HEIGHT,
            borderColor = pocketPalette.borderGrey, borderWidth = 20.152f,
            radius = 110f, fillBrush = greyPanelBrush(), tag = row.tag,
            onClick = { dispatch(row.event) },
        ) {
            SettingsHeading(metrics = metrics, icon = row.icon, title = row.title, subtitle = row.subtitle)
            NearbyToggle(metrics = metrics, enabled = row.enabled)
        }
    }
}

private data class NotificationSettingsRow(
    val icon: PocketAsset,
    val title: String,
    val subtitle: String,
    val tag: String,
    val enabled: Boolean,
    val event: PocketPassEvent,
)

@Composable
private fun AppUpdateBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (state.appUpdate.phase is AppUpdatePhase.Idle) {
            dispatch(PocketPassEvent.CheckForAppUpdate)
        }
    }
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "App Update",
            subtitle = "Keep PocketPass fresh.",
            backTag = "app_update_back",
        ) { dispatch(PocketPassEvent.Back) }
        SubpagePanelPop(y = 297f, height = 428f, order = 1) {
            AppUpdateStatusPanel(
                metrics = metrics,
                appUpdate = state.appUpdate,
                y = 297f,
                dispatch = dispatch,
            )
        }
        val notes = state.appUpdate.manifest?.notes
        if (state.appUpdate.updateAvailable && !notes.isNullOrBlank()) {
            SubpagePanelPop(y = 775f, height = 265f, order = 2) {
                PocketPanel(
                    metrics = metrics,
                    x = 50f,
                    y = 775f,
                    width = 1140f,
                    height = 265f,
                    borderColor = pocketPalette.borderGrey,
                    borderWidth = 20.152f,
                    radius = 110f,
                    fillBrush = greyPanelBrush(),
                    tag = "app_update_notes",
                ) {
                    Text(
                        text = "What's New",
                        modifier = Modifier.anchoredBounds(metrics, 70f, 36f, 1000f, 56f, DesignAnchor.Stretch),
                        color = pocketPalette.textPrimary,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(48f),
                        maxLines = 1,
                    )
                    val lines = remember(notes) { releaseNoteLines(notes) }
                    val notesScroll = rememberScrollState()
                    val notesShape = RoundedCornerShape(metrics.dp(24f))
                    val notesViewport = remember(notesShape) {
                        ControllerFocusViewport(shape = notesShape)
                    }
                    Box(
                        modifier = Modifier
                            .anchoredBounds(metrics, 70f, 104f, 1000f, 131f, DesignAnchor.Stretch)
                            .clip(notesShape)
                            .controllerFocusViewport(notesViewport)
                            .verticalScroll(notesScroll),
                    ) {
                        CompositionLocalProvider(
                            LocalControllerFocusViewport provides notesViewport,
                        ) {
                            Column(Modifier.requiredWidth(metrics.dp(1000f))) {
                                lines.forEachIndexed { index, line ->
                                    Text(
                                        text = line,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .controllerTarget(
                                                "app_update_notes_$index",
                                                cornerRadius = 16f,
                                            ) {}
                                            .padding(
                                                horizontal = metrics.dp(10f),
                                                vertical = metrics.dp(3f),
                                            ),
                                        color = pocketPalette.textSecondary,
                                        fontFamily = Rubik,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = metrics.sp(36f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateStatusPanel(
    metrics: DesignMetrics,
    appUpdate: AppUpdateUiState,
    y: Float,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val phase = appUpdate.phase
    val manifest = appUpdate.manifest
    val newVersion = manifest?.versionName ?: manifest?.versionCode?.toString().orEmpty()
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = 428f,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "app_update_status",
    ) {
        val subtitle: String
        val subtitleColor: Color
        when {
            !appUpdate.enabled -> {
                subtitle = "Updates are managed manually in this build."
                subtitleColor = palette.textSecondary
            }

            phase is AppUpdatePhase.Failed -> {
                subtitle = phase.message
                subtitleColor = palette.ink(Color(0xFFB31E3A))
            }

            phase is AppUpdatePhase.Checking || phase is AppUpdatePhase.Idle -> {
                subtitle = "Checking for updates…"
                subtitleColor = palette.textSecondary
            }

            phase is AppUpdatePhase.UpdateAvailable -> {
                subtitle = "Version $newVersion available!"
                subtitleColor = palette.ink(Color(0xFFF07C00)).copy(alpha = 0.56f)
            }

            phase is AppUpdatePhase.Downloading -> {
                subtitle = "Downloading version $newVersion…"
                subtitleColor = palette.textSecondary
            }

            phase is AppUpdatePhase.ReadyToInstall -> {
                subtitle = "Tap Install, then confirm up top."
                subtitleColor = palette.ink(Color(0xFFF07C00)).copy(alpha = 0.56f)
            }

            phase is AppUpdatePhase.Installing -> {
                subtitle = "Installing…"
                subtitleColor = palette.textSecondary
            }

            else -> {
                subtitle = "You're up to date!"
                subtitleColor = palette.textSecondary
            }
        }
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsVersion,
            title = "PocketPass ${LocalAppVersionName.current}",
            subtitle = subtitle,
            subtitleColor = subtitleColor,
        )
        if (!appUpdate.enabled) return@PocketPanel
        if (phase is AppUpdatePhase.Downloading) {
            val trackShape = RoundedCornerShape(metrics.dp(118f))
            Box(
                modifier = Modifier
                    .anchoredBounds(metrics, 52f, 228.65f, 1036f, 166f, DesignAnchor.Stretch)
                    .clip(trackShape)
                    .pocketFrame(
                        pocketPalette.surfaceLow,
                        metrics.dp(20.152f),
                        pocketPalette.borderGrey,
                        trackShape,
                    )
                    .testTag("app_update_progress"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(phase.progress.coerceIn(0f, 1f))
                        .background(greenButtonBrush()),
                )
                Text(
                    text = "${(phase.progress * 100).toInt()}%",
                    color = pocketPalette.teal,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(48f),
                    maxLines = 1,
                )
            }
            return@PocketPanel
        }
        val label: String
        val brush: Brush
        val borderColor: Color
        val labelColor: Color
        val action: PocketPassEvent?
        when {
            phase is AppUpdatePhase.Installing -> {
                label = "INSTALLING…"
                brush = greyButtonBrush()
                borderColor = Color(0xFF7B7B7B)
                labelColor = Color.White
                action = null
            }

            phase is AppUpdatePhase.UpdateAvailable -> {
                label = "DOWNLOAD"
                brush = greenButtonBrush()
                borderColor = Color(0xFF4BC24B)
                labelColor = Color(0xFF0E4A17)
                action = PocketPassEvent.DownloadAppUpdate
            }

            phase is AppUpdatePhase.ReadyToInstall -> {
                label = "INSTALL"
                brush = greenButtonBrush()
                borderColor = Color(0xFF4BC24B)
                labelColor = Color(0xFF0E4A17)
                action = PocketPassEvent.InstallAppUpdate
            }

            phase is AppUpdatePhase.Failed &&
                phase.stage == AppUpdateFailureStage.Download -> {
                label = "RETRY DOWNLOAD"
                brush = greenButtonBrush()
                borderColor = Color(0xFF4BC24B)
                labelColor = Color(0xFF0E4A17)
                action = PocketPassEvent.DownloadAppUpdate
            }

            phase is AppUpdatePhase.Failed &&
                phase.stage == AppUpdateFailureStage.Install -> {
                label = "RETRY INSTALL"
                brush = greenButtonBrush()
                borderColor = Color(0xFF4BC24B)
                labelColor = Color(0xFF0E4A17)
                action = PocketPassEvent.InstallAppUpdate
            }

            phase is AppUpdatePhase.Checking -> {
                label = "CHECKING…"
                brush = greyButtonBrush()
                borderColor = Color(0xFF7B7B7B)
                labelColor = Color.White
                action = null
            }

            else -> {
                label = "CHECK FOR UPDATES"
                brush = greyButtonBrush()
                borderColor = Color(0xFF7B7B7B)
                labelColor = Color.White
                action = PocketPassEvent.CheckForAppUpdate
            }
        }
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 52f, 228.65f, 1036f, 166f, DesignAnchor.Stretch)
                .clip(buttonShape)
                .pocketFrame(brush, metrics.dp(20.152f), borderColor, buttonShape)
                .testTag("app_update_action")
                .let { base ->
                    if (action != null) {
                        base
                            .controllerTarget("app_update_action", layer = 10) {
                                dispatch(action)
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { dispatch(action) }
                    } else {
                        base
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = labelColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(48f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ForceUpdateBottomScreen(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.PanelRise) { metrics ->
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternHomeBottom,
            topColor = pocketPalette.tint(Color(0xFFE9F6F4)),
            bottomColor = pocketPalette.tint(Color(0xFF92EBAE)),
            holdFraction = 0.42f,
            designWidth = BOTTOM_DESIGN_WIDTH,
            designHeight = BOTTOM_DESIGN_HEIGHT,
        )
        Text(
            text = "Update Required",
            modifier = Modifier.designBounds(metrics, 70f, 120f, 1100f, 110f),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(88f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "This version of PocketPass is too old to keep going.",
            modifier = Modifier.designBounds(metrics, 70f, 250f, 1100f, 56f),
            color = pocketPalette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        AppUpdateStatusPanel(
            metrics = metrics,
            appUpdate = state.appUpdate,
            y = 380f,
            dispatch = dispatch,
        )
    }
}

@Composable
fun MiiSlotsOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    showPretendoDialog: Boolean = true,
) {
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    val preparing = state.miiEditor.isEditorPreparing
    val preparingSlot = if (preparing) state.miiEditor.editingSlot else null
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("mii_slots_overlay")
            .clickable(
                enabled = !preparing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseMiiSlots) },
    )
    val panelHeight = if (state.pretendoImportEnabled) 800f else 700f
    Box(
        Modifier
            .designBounds(metrics, 80f, 204f, 1080f, panelHeight)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 190f, 1080f, panelHeight)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("mii_slots_panel"),
    ) {
        Text(
            text = "Your Piips",
            modifier = Modifier.designBounds(metrics, 60f, 48f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            maxLines = 1,
        )
        Text(
            text = "Use sets the Piip friends see.",
            modifier = Modifier.designBounds(metrics, 60f, 142f, 960f, 46f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            maxLines = 1,
        )
        (MII_FIRST_SLOT..MII_SLOT_COUNT).forEach { slot ->
            MiiSlotCard(
                metrics = metrics,
                x = 70f + ((slot - MII_FIRST_SLOT) * 330f),
                summary = state.miiEditor.slots[slot] ?: MiiSlotSummary(slot),
                isActive = state.miiEditor.activeSlot == slot,
                loading = preparingSlot == slot,
                enabled = !preparing && state.miiDeleteSlot == null,
                onEdit = { dispatch(PocketPassEvent.EditMiiSlot(slot)) },
                onUse = { dispatch(PocketPassEvent.SetActiveMiiSlot(slot)) },
                onDelete = { dispatch(PocketPassEvent.OpenDeleteMiiSlot(slot)) },
            )
        }
        if (state.pretendoImportEnabled) {
            PretendoImportButton(
                metrics = metrics,
                enabled = !preparing && state.miiDeleteSlot == null,
                dispatch = dispatch,
            )
        }
    }
    if (state.miiDeleteSlot != null) {
        MiiDeleteConfirmDialog(metrics, state, dispatch)
    }
    if (showPretendoDialog) {
        state.miiEditor.pretendoImport?.let { import ->
            PretendoImportOverlay(metrics, state, import, dispatch)
        }
    }
}

@Composable
internal fun MiiSlotCard(
    metrics: DesignMetrics,
    x: Float,
    summary: MiiSlotSummary,
    isActive: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    val focus = LocalControllerFocus.current
    val shown = remember(summary.slot) { MiiSlotShownState(summary.isEmpty, isActive) }
    LaunchedEffect(summary.isEmpty, isActive) {
        val emptied = summary.isEmpty && !shown.empty
        val activated = isActive && !shown.active
        shown.empty = summary.isEmpty
        shown.active = isActive
        if ((emptied || activated) && focus != null && focus.focusId == null) {
            focus.focus(
                if (emptied) "mii_slot_${summary.slot}" else "mii_slot_edit_${summary.slot}",
                reveal = false,
            )
        }
    }
    val palette = pocketPalette
    val portraitShape = RoundedCornerShape(metrics.dp(56f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, 200f, 280f, 280f)
            .clip(portraitShape)
            .pocketFrame(
                greyPanelBrush(),
                metrics.dp(15f),
                if (isActive) Color(0xFF3CBC29) else pocketPalette.borderGrey,
                portraitShape,
            )
            .testTag("mii_slot_${summary.slot}")
            .then(
                if (summary.isEmpty) {
                    Modifier
                        .controllerTarget("mii_slot_${summary.slot}", layer = 10, cornerRadius = 56f) {
                            if (enabled) onEdit()
                        }
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember(summary.slot) {
                                MutableInteractionSource()
                            },
                            indication = null,
                            onClick = onEdit,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (summary.isEmpty) {
            Text(
                text = "+",
                color = pocketPalette.borderGrey,
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = metrics.sp(112f),
                maxLines = 1,
            )
        } else {
            AsyncImage(
                model = requireNotNull(summary.portraitFilePath).toPath(),
                contentDescription = null,
                modifier = Modifier.requiredSize(metrics.dp(280f)).clip(portraitShape),
                contentScale = ContentScale.Crop,
            )
        }
    }

    val pillShape = RoundedCornerShape(metrics.dp(39f))
    val pillAlpha: () -> Float
    if (loading) {
        val pulse = rememberInfiniteTransition(label = "Mii slot loading")
            .animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "Mii slot loading pulse",
            )
        pillAlpha = { pulse.value }
    } else {
        pillAlpha = { 1f }
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, x, 496f, 280f, 78f)
            .clip(pillShape)
            .background(
                if (loading || isActive) greenButtonBrush() else greyButtonBrush(),
            )
            .then(
                if (isActive || summary.isEmpty || loading) {
                    Modifier
                } else {
                    Modifier
                        .testTag("mii_slot_use_${summary.slot}")
                        .controllerTarget("mii_slot_use_${summary.slot}", layer = 10) {
                            if (enabled) onUse()
                        }
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember(summary.slot) {
                                MutableInteractionSource()
                            },
                            indication = null,
                            onClick = onUse,
                        )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                loading -> "Loading…"
                isActive -> "Active"
                summary.isEmpty -> "Empty"
                else -> "Use"
            },
            modifier = Modifier.graphicsLayer { alpha = pillAlpha() },
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
    }

    if (!summary.isEmpty) {
        MiiSlotIconButton(
            metrics = metrics,
            x = if (isActive) x + 101f else x + 54f,
            borderColor = palette.tealBorder,
            fillBottom = palette.tint(Color(0xFFBDF8CB)),
            enabled = enabled,
            tag = "mii_slot_edit_${summary.slot}",
            onClick = onEdit,
        ) {
            val glyph = palette.teal
            drawLine(
                color = glyph,
                start = Offset(size.width * 0.42f, size.height * 0.58f),
                end = Offset(size.width * 0.66f, size.height * 0.34f),
                strokeWidth = size.width * 0.11f,
                cap = StrokeCap.Round,
            )
            val tip = Path().apply {
                moveTo(size.width * 0.29f, size.height * 0.71f)
                lineTo(size.width * 0.435f, size.height * 0.665f)
                lineTo(size.width * 0.335f, size.height * 0.565f)
                close()
            }
            drawPath(tip, glyph)
        }
        if (!isActive) {
            MiiSlotIconButton(
                metrics = metrics,
                x = x + 148f,
                borderColor = Color(0xFFC24B4B),
                fillBottom = palette.tint(Color(0xFFFFD6D6)),
                enabled = enabled,
                tag = "mii_slot_delete_${summary.slot}",
                onClick = onDelete,
            ) {
                val glyph = palette.ink(Color(0xFFB31E3A))
                val w = size.width
                val stroke = w * 0.075f
                drawLine(
                    color = glyph,
                    start = Offset(w * 0.28f, w * 0.34f),
                    end = Offset(w * 0.72f, w * 0.34f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = glyph,
                    start = Offset(w * 0.42f, w * 0.26f),
                    end = Offset(w * 0.58f, w * 0.26f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = glyph,
                    topLeft = Offset(w * 0.32f, w * 0.43f),
                    size = Size(w * 0.36f, w * 0.31f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = glyph,
                    start = Offset(w * 0.445f, w * 0.50f),
                    end = Offset(w * 0.445f, w * 0.66f),
                    strokeWidth = stroke * 0.7f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = glyph,
                    start = Offset(w * 0.555f, w * 0.50f),
                    end = Offset(w * 0.555f, w * 0.66f),
                    strokeWidth = stroke * 0.7f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private class MiiSlotShownState(var empty: Boolean, var active: Boolean)

@Composable
private fun MiiSlotIconButton(
    metrics: DesignMetrics,
    x: Float,
    borderColor: Color,
    fillBottom: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    glyph: DrawScope.() -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(39f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, 590f, 78f, 78f)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(listOf(pocketPalette.surface, fillBottom)),
                metrics.dp(7f),
                borderColor,
                shape,
            )
            .testTag(tag)
            .controllerTarget(tag, layer = 10) { if (enabled) onClick() }
            .clickable(
                enabled = enabled,
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) { glyph() }
    }
}

@Composable
internal fun MiiDeleteConfirmDialog(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val busy = state.miiDeleteInProgress
    DialogFocusHandoff("mii_delete_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("mii_delete_overlay")
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseDeleteMiiSlot) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("mii_delete_panel"),
    ) {
        Text(
            text = "Delete this Piip?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "This frees the slot for a new Piip. It cannot be undone.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 96f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
        )
        state.miiDeleteError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 90f, 248f, 900f, 36f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(28f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(
                    cancelButtonBrush(),
                    metrics.dp(20.152f),
                    Color(0xFF8A8A8A),
                    buttonShape,
                )
                .testTag("mii_delete_cancel")
                .controllerTarget("mii_delete_cancel", layer = 20) {
                    if (!busy) dispatch(PocketPassEvent.CloseDeleteMiiSlot)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy,
                ) { dispatch(PocketPassEvent.CloseDeleteMiiSlot) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 550f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), Color(0xFFC24B4B), buttonShape)
                .testTag("mii_delete_confirm")
                .controllerTarget("mii_delete_confirm", layer = 20) {
                    if (!busy) dispatch(PocketPassEvent.ConfirmDeleteMiiSlot)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy,
                ) { dispatch(PocketPassEvent.ConfirmDeleteMiiSlot) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (busy) "Deleting..." else "Delete",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun FriendProfileBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("profile_bottom_dismiss")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !state.profileViewer.actionInProgress,
            ) { dispatch(PocketPassEvent.CloseUserProfile) },
    )
    if (state.removeFriendPromptVisible) {
        RemoveFriendConfirmDialog(metrics, state, dispatch)
    }
}

@Composable
internal fun RemoveFriendConfirmDialog(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val name = state.profileViewer.profile?.displayName?.takeIf { it.isNotBlank() } ?: "This friend"
    DialogFocusHandoff("remove_friend_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("remove_friend_overlay")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseRemoveFriend) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("remove_friend_panel"),
    ) {
        Text(
            text = "Remove this friend?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "$name will be removed from your friends. Add them again later.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 130f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(
                    cancelButtonBrush(),
                    metrics.dp(20.152f),
                    Color(0xFF8A8A8A),
                    buttonShape,
                )
                .testTag("remove_friend_cancel")
                .controllerTarget("remove_friend_cancel", layer = 20) {
                    dispatch(PocketPassEvent.CloseRemoveFriend)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseRemoveFriend) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 550f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), Color(0xFFC24B4B), buttonShape)
                .testTag("remove_friend_confirm")
                .controllerTarget("remove_friend_confirm", layer = 20) {
                    dispatch(PocketPassEvent.RemoveProfileFriend)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.RemoveProfileFriend) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Remove",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FriendStat(
    metrics: DesignMetrics,
    modifier: Modifier,
    icon: PocketAsset,
    iconWidth: Float,
    iconHeight: Float,
    value: Int?,
    pending: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(36.323f)),
    ) {
        FigmaAsset(
            resource = icon,
            modifier = Modifier.requiredSize(
                metrics.dp(iconWidth),
                metrics.dp(iconHeight),
            ),
        )
        Text(
            text = value?.toString() ?: if (pending) "" else "–",
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(121.128f),
            maxLines = 1,
        )
    }
}

@Composable
fun DeleteAccountOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val busy = state.deleteAccountInProgress
    DialogFocusHandoff("delete_account_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("delete_account_overlay")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !busy,
            ) { dispatch(PocketPassEvent.CloseDeleteAccount) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 254f, 1080f, 600f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 240f, 1080f, 600f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("delete_account_panel"),
    ) {
        Text(
            text = "Delete Account?",
            modifier = Modifier.designBounds(metrics, 60f, 52f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "This erases your profile, Piips, friends and messages " +
                "for everyone. It cannot be undone.",
            modifier = Modifier.designBounds(metrics, 90f, 158f, 900f, 130f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
        state.deleteAccountError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 90f, 296f, 900f, 50f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 372f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(
                    cancelButtonBrush(),
                    metrics.dp(20.152f),
                    Color(0xFF8A8A8A),
                    buttonShape,
                )
                .testTag("delete_account_cancel")
                .controllerTarget("delete_account_cancel", layer = 20) {
                    if (!busy) dispatch(PocketPassEvent.CloseDeleteAccount)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy,
                ) { dispatch(PocketPassEvent.CloseDeleteAccount) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 550f, 372f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), Color(0xFFC24B4B), buttonShape)
                .testTag("delete_account_confirm")
                .controllerTarget("delete_account_confirm", layer = 20) {
                    if (!busy) dispatch(PocketPassEvent.ConfirmDeleteAccount)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy,
                ) { dispatch(PocketPassEvent.ConfirmDeleteAccount) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (busy) "Deleting..." else "Delete Forever",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun NotificationsPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "settings_notifications",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsNotifications,
            title = "Notifications",
            subtitle = "Which events send alerts",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

private const val THEME_PANEL_TAG = "theme_panel"
private const val THEME_CHOICE_LAYER = 5
private const val THEME_CHOICE_Y = 297.65f
private const val THEME_CHOICE_WIDTH = 315.33f
private const val THEME_CHOICE_HEIGHT = 105f
private const val THEME_CHOICE_RADIUS = 52.5f
private const val THEME_CHOICE_BORDER = 9f
internal val ThemeChoiceGrey = Color(0xFFB2B2B2)
internal val ThemeChoiceGreen = Color(0xFF8EF29A)

private fun themeChoiceTag(mode: ThemeMode) = "theme_${mode.name.lowercase()}"

private fun themeChoiceX(mode: ThemeMode): Float = when (mode) {
    ThemeMode.Light -> 52f
    ThemeMode.System -> 413.33f
    ThemeMode.Dark -> 774.66f
}

@Composable
private fun Modifier.themeChoiceBounds(metrics: DesignMetrics, mode: ThemeMode): Modifier =
    anchoredBounds(
        metrics,
        themeChoiceX(mode),
        THEME_CHOICE_Y,
        THEME_CHOICE_WIDTH,
        THEME_CHOICE_HEIGHT,
        DesignAnchor.Center,
        DesignAnchor.Center,
    )

internal fun Modifier.themeChoiceBorder(metrics: DesignMetrics, color: Color): Modifier = drawWithCache {
    val borderWidth = metrics.dp(THEME_CHOICE_BORDER).toPx()
    val radius = metrics.dp(THEME_CHOICE_RADIUS).toPx()
    onDrawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius),
            style = Stroke(borderWidth * 2f),
        )
    }
}

@Composable
internal fun ThemePanel(
    metrics: DesignMetrics,
    y: Float,
    selected: ThemeMode,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    val focus = LocalControllerFocus.current
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            focus?.focus(themeChoiceTag(selected))
        } else if (wasExpanded) {
            focus?.focus(THEME_PANEL_TAG)
        }
        wasExpanded = expanded
    }
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = THEME_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
        tag = THEME_PANEL_TAG,
        onControllerActivate = onExpand,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsTheme,
            title = "Theme",
            subtitle = "PocketPass’ color scheme",
        )
        Box(
            Modifier
                .anchoredBounds(metrics, 52f, 228.65f, 1036f, 9f, DesignAnchor.Stretch, DesignAnchor.Center)
                .clip(RoundedCornerShape(metrics.dp(4.5f)))
                .background(pocketPalette.borderSoft),
        )
        ThemeChoice(metrics, ThemeMode.Light, expanded, onSelect)
        ThemeChoice(metrics, ThemeMode.System, expanded, onSelect)
        ThemeChoice(metrics, ThemeMode.Dark, expanded, onSelect)
        ThemeSelection(metrics, selected)
        ThemeChoiceLabel(metrics, ThemeMode.Light, "Light", Assets.ThemeLight)
        ThemeChoiceLabel(metrics, ThemeMode.System, "System", Assets.ThemeSystem)
        ThemeChoiceLabel(metrics, ThemeMode.Dark, "Dark", Assets.ThemeDark)
    }
}

@Composable
private fun BoxScope.ThemeChoice(
    metrics: DesignMetrics,
    mode: ThemeMode,
    focusable: Boolean,
    onSelect: (ThemeMode) -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(THEME_CHOICE_RADIUS))
    val interaction = remember(mode) { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .themeChoiceBounds(metrics, mode)
            .clip(shape)
            .testTag(themeChoiceTag(mode))
            .then(
                if (focusable) {
                    Modifier.controllerTarget(
                        themeChoiceTag(mode),
                        layer = THEME_CHOICE_LAYER,
                    ) { onSelect(mode) }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onSelect(mode) }
            .background(greyButtonBrush())
            .themeChoiceBorder(metrics, ThemeChoiceGrey),
    )
}

@Composable
private fun BoxScope.ThemeSelection(metrics: DesignMetrics, selected: ThemeMode) {
    val x = remember { Animatable(themeChoiceX(selected)) }
    LaunchedEffect(selected) {
        val target = themeChoiceX(selected)
        if (!platformAnimationsEnabled()) {
            x.snapTo(target)
            return@LaunchedEffect
        }
        if (target == x.value) return@LaunchedEffect
        x.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = FOCUS_SLIDE_DAMPING_RATIO,
                stiffness = FOCUS_SLIDE_STIFFNESS,
                visibilityThreshold = 0.5f,
            ),
        )
    }
    val shape = RoundedCornerShape(metrics.dp(THEME_CHOICE_RADIUS))
    val originX = LocalDesignOrigin.current.x
    Box(
        Modifier
            .graphicsLayer {
                translationX = x.value - originX
                translationY = THEME_CHOICE_Y
            }
            .requiredSize(metrics.dp(THEME_CHOICE_WIDTH), metrics.dp(THEME_CHOICE_HEIGHT))
            .clip(shape)
            .background(greenButtonBrush())
            .themeChoiceBorder(metrics, ThemeChoiceGreen)
            .pocketBorder(metrics.dp(4f), Color.White.copy(alpha = 0.34f), shape),
    )
}

@Composable
private fun BoxScope.ThemeChoiceLabel(
    metrics: DesignMetrics,
    mode: ThemeMode,
    label: String,
    icon: PocketAsset,
) {
    Row(
        modifier = Modifier.themeChoiceBounds(metrics, mode),
        horizontalArrangement = Arrangement.spacedBy(
            space = metrics.dp(15f),
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaAsset(
            resource = icon,
            modifier = Modifier.requiredSize(metrics.dp(46.2f)),
        )
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun VersionPanel(
    metrics: DesignMetrics,
    y: Float,
    appUpdate: AppUpdateUiState,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "settings_version",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsVersion,
            title = "App Version",
            subtitle = if (appUpdate.updateAvailable) {
                "${LocalAppVersionName.current} - Update Available!"
            } else {
                LocalAppVersionName.current
            },
            subtitleColor = if (appUpdate.updateAvailable) {
                Color(0x8FF07C00)
            } else {
                pocketPalette.textSecondary
            },
        )
    }
}

@Composable
internal fun LogoutPanel(
    metrics: DesignMetrics,
    y: Float,
    onLogout: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "log_out",
        onClick = onLogout,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsLogout,
            title = "Log Out",
            subtitle = "Sign out of PocketPass",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

internal data class Credit(val name: String, val role: String, val avatar: PocketAsset)

internal val CreditsRoster: List<Credit> = listOf(
    Credit("simply", "Creator, Code, UI Integration", Assets.SettingsCreditsAvatarSimply),
    Credit("BrocoDev", "UI Redesign", Assets.SettingsCreditsAvatarBrocoDev),
    Credit("ariankordi", "Piip Creator", Assets.SettingsCreditsAvatarAriankordi),
    Credit("k0o1", "Official Soundtrack", Assets.SettingsCreditsAvatarK0o1),
    Credit("saby", "Official soundtrack, SFX", Assets.SettingsCreditsAvatarSaby),
)

@Composable
internal fun ContributorsPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "contributors",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsContributors,
            title = "Contributors",
            subtitle = "The people behind PocketPass",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
private fun ContributorsBottom(dispatch: (PocketPassEvent) -> Unit) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Contributors",
            subtitle = "The people behind PocketPass.",
            backTag = "contributors_back",
        ) { dispatch(PocketPassEvent.Back) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        DesignBox(
            metrics,
            0f,
            0f,
            1240f,
            1080f,
            DesignAnchor.Stretch,
            DesignAnchor.Stretch,
            modifier = Modifier
                .clip(belowHeader)
                .verticalScroll(scroll)
                .testTag("contributors_scroll"),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                    .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                    .requiredHeight(metrics.dp(CREDITS_PANEL_HEIGHT + 2f * SETTINGS_PANEL_GAP)),
            ) {
                SubpagePanelPop(y = SUBPAGE_FIRST_ROW_Y, height = CREDITS_PANEL_HEIGHT, order = 1) {
                    CreditsPanel(metrics, SETTINGS_PANEL_GAP)
                }
            }
        }
    }
}

internal class BelowSubpageHeaderShape(private val metrics: DesignMetrics) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = with(density) {
        Outline.Rectangle(Rect(0f, metrics.dp(SUBPAGE_CONTENT_TOP).toPx(), size.width, size.height))
    }
}

internal const val SUBPAGE_CONTENT_TOP = 247f

@Composable
internal fun CreditsPanel(metrics: DesignMetrics, y: Float) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = CREDITS_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
    ) {
        CreditsRoster.forEachIndexed { index, credit ->
            val name = credit.name
            val role = credit.role
            val rowY = CREDITS_PANEL_PADDING + index * CREDITS_ROW_PITCH
            val avatarShape = RoundedCornerShape(metrics.dp(CREDITS_AVATAR_SIZE / 2f))
            FigmaAsset(
                resource = credit.avatar,
                modifier = Modifier
                    .designBounds(metrics, CREDITS_PANEL_PADDING, rowY, CREDITS_AVATAR_SIZE, CREDITS_AVATAR_SIZE)
                    .clip(avatarShape)
                    .pocketBorder(
                        metrics.dp(9f),
                        Color(0x335F5F5F),
                        avatarShape,
                    ),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = name,
                modifier = Modifier.designBounds(metrics, 210f, rowY - 5f, 767f, 75f),
                color = pocketPalette.textPrimary,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(64f),
                maxLines = 1,
            )
            Text(
                text = role,
                modifier = Modifier.anchoredBounds(metrics, 210f, rowY + 70f, 820f, 55f, DesignAnchor.Stretch),
                color = pocketPalette.textSecondary,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(45f),
                maxLines = 1,
            )
            if (index < CreditsRoster.lastIndex) {
                Box(
                    Modifier
                        .anchoredBounds(metrics, 52f, rowY + 147.65f, 1036f, 9f, DesignAnchor.Stretch)
                        .clip(RoundedCornerShape(metrics.dp(4.5f)))
                        .background(pocketPalette.borderSoft),
                )
            }
        }
    }
}

@Composable
internal fun DeletePanel(
    metrics: DesignMetrics,
    y: Float,
    onDelete: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_TALL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsDelete,
            title = "Delete Account",
            subtitle = "Erase your account (CANNOT UNDO)",
        )
        val interaction = remember { MutableInteractionSource() }
        val shape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 52f, 228.65f, 1036f, 166f, DesignAnchor.Stretch)
                .clip(shape)
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), Color(0xFFC24B4B), shape)
                .testTag("delete_account")
                .controllerTarget("delete_account") { onDelete() }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "DELETE ACCOUNT",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(48f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SubpageHeader(
    metrics: DesignMetrics,
    title: String,
    subtitle: String,
    backTag: String,
    onBack: () -> Unit,
) {
    MotionLayer(
        modifier = Modifier.fillMaxSize(),
        entrance = EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
        transformOrigin = TransformOrigin(
            SUBPAGE_HEADER_PIVOT_X / BOTTOM_DESIGN_WIDTH,
            SUBPAGE_HEADER_PIVOT_Y / BOTTOM_DESIGN_HEIGHT,
        ),
    ) {
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier
                .anchoredBounds(metrics, 62f, 88f, 40.372f, 68.725f, DesignAnchor.Start)
                .graphicsLayer { scaleX = -1f },
        )
        Text(
            text = title,
            modifier = Modifier.anchoredBounds(metrics, 142f, 72f, 800f, 100f, DesignAnchor.Start),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(88f),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = metrics.sp(56f), maxFontSize = metrics.sp(88f), stepSize = metrics.sp(2f)),
        )
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 38f, 50f, 760f, 150f, DesignAnchor.Start)
                .testTag(backTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
        )
        Text(
            text = subtitle,
            modifier = Modifier.anchoredBounds(metrics, 145f, 190f, 900f, 52f, DesignAnchor.Start),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun SubpagePanelPop(
    y: Float,
    height: Float,
    order: Int,
    content: @Composable BoxScope.() -> Unit,
) {
    RestingFocusMotionLayer(
        entrance = EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + order * OVERLAY_POP_STAGGER_MILLIS,
        transformOrigin = TransformOrigin(0.5f, (y + height / 2f) / BOTTOM_DESIGN_HEIGHT),
        content = content,
    )
}

@Composable
private fun SettingsRowReveal(
    reveal: Int,
    order: Int,
    y: Float,
    height: Float,
    totalHeight: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    RestingFocusMotionLayer(
        entrance = if (reveal == 0) EntranceMotion.None else EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + order * OVERLAY_POP_STAGGER_MILLIS,
        transformOrigin = TransformOrigin(0.5f, (y + height / 2f) / totalHeight),
        replayKey = reveal,
        content = content,
    )
}

@Composable
private fun RestingFocusMotionLayer(
    entrance: EntranceMotion,
    delayMillis: Int,
    transformOrigin: TransformOrigin,
    replayKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val frame = remember { ControllerRestFrame() }
    Box(Modifier.fillMaxSize().controllerRestFrameOutside(frame)) {
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = entrance,
            delayMillis = delayMillis,
            transformOrigin = transformOrigin,
            replayKey = replayKey,
        ) {
            Box(Modifier.matchParentSize().controllerRestFrameInside(frame))
            CompositionLocalProvider(LocalControllerRestFrame provides frame) {
                content()
            }
        }
    }
}

private const val SUBPAGE_HEADER_PIVOT_X = 541.5f
private const val SUBPAGE_HEADER_PIVOT_Y = 146f

@Composable
internal fun BoxScope.SettingsHeading(
    metrics: DesignMetrics,
    icon: PocketAsset,
    title: String,
    subtitle: String,
    subtitleColor: Color? = null,
    subtitleSize: Float = 45f,
    centerTitle: Boolean = false,
    subtitleOverflow: TextOverflow = TextOverflow.Clip,
) {
    FigmaAsset(
        resource = icon,
        modifier = Modifier.designBounds(metrics, 43f, 38.675f, 142.65f, 142.65f),
    )
    Text(
        text = title,
        modifier = Modifier.anchoredBounds(metrics, 210f, if (centerTitle) (SETTINGS_ROW_HEIGHT - 76f) / 2f else 42f, 767f, 76f, DesignAnchor.Stretch),
        color = pocketPalette.textPrimary,
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(64f),
        maxLines = 1,
    )
    Text(
        text = subtitle,
        modifier = Modifier.anchoredBounds(metrics, 210f, 117f, 820f, 55f, DesignAnchor.Stretch),
        color = subtitleColor ?: pocketPalette.textSecondary,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(subtitleSize),
        maxLines = 1,
        overflow = subtitleOverflow,
    )
}

@Composable
internal fun chevronTint(): ColorFilter? {
    val palette = pocketPalette
    return if (palette.isDark) ColorFilter.tint(palette.textSecondary) else null
}

@Composable
internal fun greyPanelBrush(): Brush {
    val palette = pocketPalette
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0f to palette.surface,
            0.626f to palette.surface,
            1f to palette.surfaceLower,
        ),
    )
}

internal fun greenButtonBrush() = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFF57E25F),
        0.5f to Color(0xFF5EED6F),
        0.553f to Color(0xFF57E25F),
        1f to Color(0xFF3CBC29),
    ),
)

internal fun greyButtonBrush() = Brush.verticalGradient(
    listOf(Color(0xFF9F9F9F), Color(0xFF646464)),
)

internal fun cancelButtonBrush() = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFF9F9F9F),
        0.5f to Color(0xFFA9A9A9),
        0.553f to Color(0xFF9F9F9F),
        1f to Color(0xFF646464),
    ),
)

internal fun redButtonBrush() = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFFE25757),
        0.5f to Color(0xFFED5E5E),
        0.553f to Color(0xFFE25757),
        1f to Color(0xFFBC2929),
    ),
)

@Composable
internal fun BottomPage(
    entrance: EntranceMotion,
    content: @Composable (DesignMetrics) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val surfaceMetrics = LocalDesignMetrics.current
        val metrics = surfaceMetrics ?: remember(density) { DesignMetrics(density) }
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = entrance,
            delayMillis = 55,
        ) {
            content(metrics)
        }
    }
}
