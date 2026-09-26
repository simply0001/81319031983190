package com.pocketpass.app.ui.screens

import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.platformAnimationsEnabled
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.phone.PhoneRoundAction
import com.pocketpass.app.ui.phone.PhoneSectionHeader
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.boards.*
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.pocketShadow

// Boards occupies its own shell, including the space used by the main app tabs.
internal const val BOARDS_THOR_TOP = 0f
internal val LocalBoardNavigationRail = staticCompositionLocalOf { false }
internal val LocalBoardKeyboardOpen = staticCompositionLocalOf { false }
// The shell is a page, not a modal: keep it on the app's base layer so that
// message actions and other overlays can take focus above the navigation rail.
internal val LocalBoardFocusLayer = staticCompositionLocalOf { 0 }
private val LocalBoardTargetsEnabled = staticCompositionLocalOf { true }
internal val LocalBoardFocusParent = staticCompositionLocalOf<String?> { null }
internal val LocalBoardFocusOrder = staticCompositionLocalOf<List<String>?> { null }

/** Keep every board control on the same layer, with an outline matching its shape. */
internal fun Modifier.boardControllerTarget(
    id: String, radius: Float = 28f, enabled: Boolean = true,
    neighbors: Map<FocusDirection, String> = emptyMap(), onActivate: () -> Unit,
): Modifier = composed {
    if(enabled && LocalBoardTargetsEnabled.current) {
        controllerTarget(id, layer = LocalBoardFocusLayer.current, cornerRadius = radius,
            revealKey = LocalBoardFocusOrder.current,
            parentId = LocalBoardFocusParent.current, neighbors = neighbors, onActivate = onActivate)
    } else this
}

/** Use the same reveal for About, filter options and settings sections. */
@Composable
internal fun BoardReveal(m: DesignMetrics, visible: Boolean, modifier: Modifier = Modifier, topGap: Float = 0f,
    content: @Composable ColumnScope.() -> Unit) {
    val motion = platformAnimationsEnabled()
    val targetsEnabled = LocalBoardTargetsEnabled.current && visible
    AnimatedVisibility(visible, modifier,
        enter = if(motion) expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(240)) else EnterTransition.None,
        exit = if(motion) shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(180)) else ExitTransition.None) {
        CompositionLocalProvider(LocalBoardTargetsEnabled provides targetsEnabled) {
            // Frames paint outside their layout bounds. Reserve room inside the
            // animated clip so all four edges appear together, including on exit.
            Column(Modifier.fillMaxWidth().padding(start = m.dp(8f), end = m.dp(8f), top = m.dp(8f + topGap), bottom = m.dp(8f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(24f)), content = content)
        }
    }
}

@Composable
internal fun BoardInlineReveal(m: DesignMetrics, visible: Boolean, content: @Composable BoxScope.() -> Unit) {
    val motion = platformAnimationsEnabled()
    val targetsEnabled = LocalBoardTargetsEnabled.current && visible
    AnimatedVisibility(visible,
        enter = if(motion) expandHorizontally(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) + fadeIn(tween(240)) else EnterTransition.None,
        exit = if(motion) shrinkHorizontally(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) + fadeOut(tween(180)) else ExitTransition.None) {
        CompositionLocalProvider(LocalBoardTargetsEnabled provides targetsEnabled) {
            Box(Modifier.padding(start = m.dp(20f), end = m.dp(8f), top = m.dp(8f), bottom = m.dp(8f)), content = content)
        }
    }
}

@Composable
private fun BoardEllipsis(m: DesignMetrics, modifier: Modifier = Modifier, color: Color = pocketPalette.teal) {
    Canvas(modifier.size(m.dp(42f), m.dp(12f))) {
        for(dot in 0..2) drawCircle(color, radius = size.height / 3f,
            center = Offset(size.width * (dot + .5f) / 3f, size.height / 2f))
    }
}

@Composable
internal fun BoardMoreAction(m: DesignMetrics, tag: String, expanded: Boolean, onClick: () -> Unit) {
    val activate = boardConfirmAction(onClick)
    val shape = RoundedCornerShape(m.dp(24f))
    Box(Modifier.size(m.dp(80f)).testTag(tag)
        .pocketFrame(if(expanded) greenButtonBrush() else greyPanelBrush(), m.dp(3f),
            if(expanded) ThemeChoiceGreen else pocketPalette.borderSoft, shape)
        .boardControllerTarget(tag, radius = 24f, onActivate = activate)
        .semantics { contentDescription = "Note options"; stateDescription = if(expanded) "Expanded" else "Collapsed" }
        .clickable(role = Role.Button, onClick = activate), contentAlignment = Alignment.Center) {
        BoardEllipsis(m, color = if(expanded) Color.White else pocketPalette.teal)
    }
}

internal class BoardInlineBackStack {
    private val handlers = mutableStateListOf<Pair<Any, () -> Unit>>()
    val canGoBack get() = handlers.isNotEmpty()
    fun add(key: Any, action: () -> Unit) { handlers.removeAll { it.first == key }; handlers.add(key to action) }
    fun remove(key: Any) { handlers.removeAll { it.first == key } }
    fun back(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handlers.remove(handler)
        handler.second()
        return true
    }
}
internal val LocalBoardInlineBack = staticCompositionLocalOf<BoardInlineBackStack?> { null }

@Composable
internal fun BoardInlineBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val stack = LocalBoardInlineBack.current
    val key = remember { Any() }
    val callback by rememberUpdatedState(onBack)
    DisposableEffect(stack, enabled) {
        if(enabled) stack?.add(key) { callback() }
        onDispose { stack?.remove(key) }
    }
}

/** Quiet rounded tiles give this space its own identity without extra image assets. */
@Composable
internal fun BoardBackdrop(m: DesignMetrics, modifier: Modifier = Modifier) {
    val palette = pocketPalette
    val motion = platformAnimationsEnabled()
    val drift = if(motion) rememberInfiniteTransition(label = "Board tiles").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "Board tile drift")
    else remember { mutableFloatStateOf(0f) }
    Canvas(modifier.fillMaxSize().background(if(palette.isDark) palette.surfaceLower else Color(0xFFF1F4EF))) {
        val tile = m.dp(148f).toPx()
        val gap = m.dp(10f).toPx()
        val radius = CornerRadius(m.dp(22f).toPx())
        // Read animation state only while drawing; the board UI doesn't recompose
        // for each frame. Moving one tile on both axes gives a seamless loop.
        val shift = tile * drift.value
        for(row in -1..(size.height / tile).toInt()) {
            for(column in -1..(size.width / tile).toInt()) {
                if((row + column) % 2 == 0) drawRoundRect(palette.surface.copy(alpha = .45f),
                    Offset(column * tile + gap / 2f + shift, row * tile + gap / 2f + shift), Size(tile - gap, tile - gap), radius)
            }
        }
    }
}

@Composable
internal fun BoardHeader(m: DesignMetrics, title: String, back: (() -> Unit)?, options: (() -> Unit)? = null, exit: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = m.dp(84f)).testTag("boards_header")
        .padding(horizontal = m.dp(50f)), verticalAlignment = Alignment.CenterVertically) {
        if(back != null) {
            BoardRoundAction(m, if(exit) "Back to PocketPass" else "Back", if(exit) "boards_exit" else "boards_back", back) {
                // A chevron's ink is heavier on the open side; compensate after mirroring.
                FigmaAsset(Assets.SettingsArrow, Modifier.align(Alignment.Center).offset(x = m.dp(-3f))
                    .width(m.dp(25f)).height(m.dp(43f))
                    .graphicsLayer { scaleX = -1f }, contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(m.dp(26f)))
        }
        PhoneSectionHeader(m, title, pocketPalette.teal, Modifier.weight(1f), horizontalPadding = 0f) {
            if(options != null) BoardRoundAction(m, "Board options", "boards_options", options) {
                BoardEllipsis(m, Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Miiverse-inspired rail replaces the main app navigation on wide screens. */
@Composable
internal fun BoardNavigationRail(m: DesignMetrics, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit, modifier: Modifier = Modifier) {
    val screen = state.boards.screen
    val inConversation = state.routes.lastOrNull().let { it is PocketPassRoute.MessageDetail || it is PocketPassRoute.NewGroup }
    val navigate: (BoardAction) -> Unit = { action ->
        if(state.routes.lastOrNull() !is PocketPassRoute.Root) dispatch(PocketPassEvent.SelectDestination(PocketPassDestination.Messages))
        dispatch(PocketPassEvent.Boards(action))
    }
    val tabs = listOf(
        BoardTab("Boards", "boards_directory", !inConversation && screen !in listOf(BoardsScreen.Chats, BoardsScreen.Inbox, BoardsScreen.Drafts), Assets.SettingsSocial) { navigate(BoardAction.Directory()) },
        BoardTab("Messages", "boards_chats", inConversation || screen == BoardsScreen.Chats, Assets.NavMessages,
            state.unreadConversationCount.takeIf { it > 0 }?.toString()) { navigate(BoardAction.OpenChats) },
        BoardTab("Activity", "boards_inbox", !inConversation && screen == BoardsScreen.Inbox, Assets.SettingsNotifications) { navigate(BoardAction.Inbox) },
        BoardTab("Drafts", "boards_rail_drafts", !inConversation && screen == BoardsScreen.Drafts, Assets.MessageActionAdd) { navigate(BoardAction.Drafts) },
    )
    val viewport = remember { ControllerFocusViewport() }
    CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
    Column(modifier.fillMaxHeight().background(pocketPalette.surface.copy(alpha = .92f))
        .testTag("boards_side_navigation").controllerFocusViewport(viewport).verticalScroll(rememberScrollState()).padding(m.dp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.dp(20f))) {
        BoardLabel(m, "PocketPass", 30f, true, color = pocketPalette.teal)
        BoardLabel(m, "Boards", 48f, true, color = pocketPalette.teal)
        Spacer(Modifier.height(m.dp(12f)))
        tabs.forEach { tab ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(m.dp(24f))).testTag(tab.tag)
                .background(if(tab.selected) greenButtonBrush() else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                .boardControllerTarget(tab.tag, radius = 24f, onActivate = tab.click)
                .selectable(tab.selected, role = Role.Tab, onClick = tab.click)
                .padding(vertical = m.dp(22f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                tab.icon?.let { FigmaAsset(it, Modifier.size(m.dp(62f)), contentScale = ContentScale.Fit) }
                BoardLabel(m, tab.label, 36f, true, color = if(tab.selected) Color.White else pocketPalette.textPrimary)
                tab.badge?.let { BoardLabel(m, it, 28f, true, color = if(tab.selected) Color.White else pocketPalette.teal) }
            }
        }
        Spacer(Modifier.height(m.dp(24f)))
        val exit = { dispatch(PocketPassEvent.SelectDestination(PocketPassDestination.Home)) }
        Column(Modifier.fillMaxWidth().testTag("boards_exit").boardControllerTarget("boards_exit", radius = 24f, onActivate = exit)
            .clickable(role = Role.Button, onClick = exit).padding(vertical = m.dp(24f)), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
            FigmaAsset(Assets.SettingsArrow, Modifier.size(m.dp(36f)).graphicsLayer { scaleX = -1f }, contentScale = ContentScale.Fit)
            BoardLabel(m, "PocketPass", 34f, true, color = pocketPalette.teal)
        }
    }
    }
}

/** One navigation strip for communities, private conversations and board activity. */
@Composable
internal fun BoardHubNavigation(m: DesignMetrics, state: PocketPassUiState, send: (BoardAction) -> Unit) {
    val screen = state.boards.screen
    val boardBelow = when {
        screen == BoardsScreen.Board && LocalBoardCompactComposer.current -> mapOf(FocusDirection.Down to "board_about")
        screen == BoardsScreen.Directory -> mapOf(FocusDirection.Down to if(state.boards.explore) "boards_explore" else "boards_joined")
        else -> emptyMap()
    }
    BoardTabs(m, listOf(
        BoardTab("Boards", "boards_directory", screen !in listOf(BoardsScreen.Chats, BoardsScreen.Inbox), Assets.SettingsSocial, neighbors = boardBelow) { send(BoardAction.Directory()) },
        BoardTab("Messages", "boards_chats", screen == BoardsScreen.Chats, Assets.NavMessages,
            state.unreadConversationCount.takeIf { it > 0 }?.toString(), neighbors = boardBelow) { send(BoardAction.OpenChats) },
        BoardTab("Activity", "boards_inbox", screen == BoardsScreen.Inbox, Assets.SettingsNotifications) { send(BoardAction.Inbox) },
    ), Modifier.padding(horizontal = m.dp(50f)).testTag("boards_navigation"))
}

/** Opt-in feedback for silent controls; never intercepts existing app sounds. */
@Composable
internal fun boardConfirmAction(onClick: () -> Unit, enabled: Boolean = true): () -> Unit {
    val sounds = LocalSoundEffects.current
    return {
        if(enabled) sounds.play(SoundEffect.Confirm)
        onClick()
    }
}

internal data class BoardTab(val label: String, val tag: String, val selected: Boolean,
    val icon: PocketAsset? = null, val badge: String? = null,
    val neighbors: Map<FocusDirection, String> = emptyMap(), val click: () -> Unit)

@Composable
internal fun BoardTabs(m: DesignMetrics, tabs: List<BoardTab>, modifier: Modifier = Modifier, confirmSound: Boolean = false) {
    val shape = RoundedCornerShape(m.dp(26f))
    val selectedIndex = tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
    val motion = platformAnimationsEnabled()
    val position by animateFloatAsState(selectedIndex.toFloat(),
        tween(if(motion) 240 else 0, easing = FastOutSlowInEasing), label = "Board tab selection")
    Box(modifier.fillMaxWidth().background(pocketPalette.surface, shape)
        .border(m.dp(3f), pocketPalette.borderSoft, shape).padding(m.dp(5f))) {
        BoxWithConstraints(Modifier.matchParentSize()) {
            val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
            if(tabs.any { it.selected }) Box(Modifier.offset(x = tabWidth * position)
                .width(tabWidth).fillMaxHeight().testTag("${tabs.first().tag}_selection")
                .background(greenButtonBrush(), RoundedCornerShape(m.dp(20f))))
        }
        Row(Modifier.fillMaxWidth()) {
        tabs.forEach { tab ->
            val activate = boardConfirmAction(tab.click, confirmSound)
            Column(Modifier.weight(1f).heightIn(min = m.dp(96f)).testTag(tab.tag)
                .clip(RoundedCornerShape(m.dp(20f)))
                .boardControllerTarget(tab.tag, radius = 20f, neighbors = tab.neighbors, onActivate = activate)
                .selectable(tab.selected, role = Role.Tab, onClick = activate)
                .padding(horizontal = m.dp(8f), vertical = m.dp(18f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                    tab.icon?.let { FigmaAsset(it, Modifier.size(m.dp(42f)), contentScale = ContentScale.Fit) }
                    BoardLabel(m, tab.label, 36f, true, maxLines = 1,
                        color = if(tab.selected) Color.White else pocketPalette.textPrimary)
                    tab.badge?.let { BoardLabel(m, it, 28f, true,
                        Modifier.background(pocketPalette.teal, RoundedCornerShape(m.dp(16f))).padding(horizontal = m.dp(10f), vertical = m.dp(2f)),
                        color = Color.White) }
                }
            }
        }
        }
    }
}

/** The frame is outside the drawing; exported notes remain plain 4:3 paper. */
@Composable
internal fun BoardPaper(m: DesignMetrics, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.pocketShadow(m, 12f, alpha = .10f, blurRadius = 12f)
        .background(pocketPalette.surfaceLow, RoundedCornerShape(m.dp(12f)))
        .border(m.dp(3f), pocketPalette.borderSoft, RoundedCornerShape(m.dp(12f)))
        .padding(m.dp(14f)), content = content)
}

@Composable
internal fun BoardRoundAction(m: DesignMetrics, label: String, tag: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    PhoneRoundAction(m, pocketPalette.tealBorder, pocketPalette.tint(Color(0xFFBDF8CB)), tag, onClick,
        Modifier.semantics { contentDescription = label }.boardControllerTarget(tag, radius = 40f, onActivate = onClick), content = content)
}

@Composable
internal fun BoardNavigationRow(m: DesignMetrics, title: String, subtitle: String?, tag: String,
    icon: PocketAsset? = null, leading: (@Composable () -> Unit)? = null,
    neighbors: Map<FocusDirection, String> = emptyMap(), onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = m.dp(142f)).testTag(tag)
        .boardControllerTarget(tag, radius = 12f, neighbors = neighbors, onActivate = onClick)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = m.dp(10f), vertical = m.dp(22f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(26f))) {
        if(leading != null) leading()
        else icon?.let { FigmaAsset(it, Modifier.size(m.dp(100f)), contentScale = ContentScale.Fit) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
            BoardLabel(m, title, 48f, true, maxLines = 2)
            if(!subtitle.isNullOrBlank()) BoardLabel(m, subtitle, 34f, color = pocketPalette.textSecondary)
        }
        FigmaAsset(Assets.SettingsArrow, Modifier.width(m.dp(25f)).height(m.dp(43f)), contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun BoardDivider(m: DesignMetrics) {
    Spacer(Modifier.fillMaxWidth().height(m.dp(3f)).background(pocketPalette.borderSoft))
}

@Composable
internal fun BoardToggle(m: DesignMetrics, title: String, subtitle: String?, checked: Boolean, tag: String,
    enabled: Boolean = true, neighbors: Map<FocusDirection, String> = emptyMap(), onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = m.dp(132f)).testTag(tag)
        .boardControllerTarget(tag, radius = 28f, enabled = enabled, neighbors = neighbors, onActivate = onToggle)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
        .padding(vertical = m.dp(12f)), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
            BoardLabel(m, title, 42f, true)
            if(!subtitle.isNullOrBlank()) BoardLabel(m, subtitle, 32f, color = pocketPalette.textSecondary)
        }
        Box(Modifier.size(m.dp(169f), m.dp(105f))
            .graphicsLayer { alpha = if(enabled) 1f else .45f }) {
            NearbyToggle(m, checked, x = 0f, y = 0f)
        }
    }
}

@Composable
internal fun BoardDisclosure(m: DesignMetrics, title: String, tag: String, subtitle: String? = null,
    initiallyOpen: Boolean = false, firstTarget: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(tag) { mutableStateOf(initiallyOpen) }
    val toggle = boardConfirmAction({ expanded = !expanded })
    BoardInlineBackHandler(expanded) { expanded = false }
    val motion = platformAnimationsEnabled()
    val rotation by animateFloatAsState(if(expanded) 90f else 0f,
        tween(if(motion) 240 else 0, easing = FastOutSlowInEasing), label = "$tag chevron")
    BoardCard(m, contentPadding = 0f) {
        Column {
        Row(Modifier.fillMaxWidth().testTag(tag).boardControllerTarget(tag, radius = 28f,
            neighbors = if(expanded && firstTarget != null) mapOf(FocusDirection.Down to firstTarget) else emptyMap(), onActivate = toggle)
            .clickable(role = Role.Button, onClick = toggle)
            .padding(horizontal = m.dp(30f), vertical = m.dp(42f)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
            Column(Modifier.weight(1f)) {
                BoardLabel(m, title, 48f, true)
                if(subtitle != null) BoardLabel(m, subtitle, 32f, color = pocketPalette.textSecondary)
            }
            FigmaAsset(Assets.SettingsArrow, Modifier.width(m.dp(25f)).height(m.dp(43f))
                .graphicsLayer { rotationZ = rotation }, contentScale = ContentScale.Fit)
        }
        BoardReveal(m, expanded, Modifier.padding(horizontal = m.dp(30f))) {
                BoardDivider(m)
                content()
                Spacer(Modifier.height(m.dp(22f)))
        }
        }
    }
}

@Composable
internal fun BoardAboutButton(m: DesignMetrics, expanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val activate = boardConfirmAction(onClick)
    val shape = RoundedCornerShape(m.dp(28f))
    val rotation by animateFloatAsState(if(expanded) 90f else 0f,
        tween(if(platformAnimationsEnabled()) 240 else 0), label = "About chevron")
    Row(modifier.heightIn(min = m.dp(104f)).testTag("board_about")
        .pocketFrame(if(expanded) greenButtonBrush() else androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFDDF2E3)))),
            m.dp(4f), if(expanded) ThemeChoiceGreen else pocketPalette.tealBorder, shape)
        .boardControllerTarget("board_about", neighbors = mapOf(
            FocusDirection.Up to if(expanded) "board_rules" else "boards_directory",
            FocusDirection.Down to "board_sort_newest", FocusDirection.Right to "board_compose"), onActivate = activate)
        .clickable(role = Role.Button, onClick = activate)
        .padding(horizontal = m.dp(24f), vertical = m.dp(20f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(18f))) {
        FigmaAsset(Assets.SettingsSocial, Modifier.size(m.dp(46f)), contentScale = ContentScale.Fit)
        BoardLabel(m, "About this board", 36f, true, Modifier.weight(1f), maxLines = 1,
            color = if(expanded) Color.White else pocketPalette.teal)
        FigmaAsset(Assets.SettingsArrow, Modifier.width(m.dp(20f)).height(m.dp(34f))
            .graphicsLayer { rotationZ = rotation }, contentScale = ContentScale.Fit)
    }
}
