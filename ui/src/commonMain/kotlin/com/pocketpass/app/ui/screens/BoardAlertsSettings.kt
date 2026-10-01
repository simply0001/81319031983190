package com.pocketpass.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.boards.Board
import com.pocketpass.app.boards.BoardAlertLevel
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

internal const val BOARD_ALERTS_PANEL_HEIGHT = 502f
private const val BOARD_ALERTS_LABEL_Y = 258f
private const val BOARD_ALERTS_SLIDER_Y = 318f
private val BoardAlertStops = listOf(BoardAlertLevel.Mentions, BoardAlertLevel.Personal, BoardAlertLevel.All)

internal fun boardAlertSubtitle(enabled: Boolean, level: BoardAlertLevel): String = when {
    !enabled -> "No alerts from Boards"
    level == BoardAlertLevel.All -> "Everything from your boards"
    level == BoardAlertLevel.Personal -> "Mentions, replies and Yeahs"
    else -> "Only when you're mentioned"
}

private fun BoardAlertLevel.label(): String = when (this) {
    BoardAlertLevel.All -> "All"
    BoardAlertLevel.Personal -> "Replies & Yeahs"
    BoardAlertLevel.Mentions -> "Mentions"
}

internal fun PocketPassUiState.showsBoardAlertSettings(): Boolean =
    boardPushSupported && boards.enabled && boardsVisible

@Composable
internal fun BoardAlertsPanel(
    metrics: DesignMetrics,
    y: Float,
    enabled: Boolean,
    level: BoardAlertLevel,
    onToggle: () -> Unit,
    onLevel: (BoardAlertLevel) -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = BOARD_ALERTS_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
        tag = "board_alerts_panel",
        onControllerActivate = onToggle,
    ) {
        Box(
            Modifier
                .designBounds(metrics, 0f, 0f, 1140f + 2f * metrics.overscanX, SETTINGS_ROW_HEIGHT)
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(enabled)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
                .testTag("board_alerts_toggle"),
        )
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsSocial,
            title = "Board Alerts",
            subtitle = boardAlertSubtitle(enabled, level),
        )
        NearbyToggle(metrics = metrics, enabled = enabled)
        Box(
            Modifier
                .designBounds(metrics, 52f, 228.65f, 1036f + 2f * metrics.overscanX, 9f)
                .clip(RoundedCornerShape(metrics.dp(4.5f)))
                .background(pocketPalette.borderSoft),
        )
        BoardAlertStopLabels(metrics, enabled, level, onLevel)
        BoardAlertLevelSlider(metrics, enabled, level, onLevel)
    }
}

@Composable
private fun BoardAlertStopLabels(
    metrics: DesignMetrics,
    enabled: Boolean,
    level: BoardAlertLevel,
    onLevel: (BoardAlertLevel) -> Unit,
) {
    Box(
        Modifier
            .designBounds(metrics, 62f, BOARD_ALERTS_LABEL_Y, 1016f + 2f * metrics.overscanX, 55f)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        BoardAlertStops.forEachIndexed { index, stop ->
            val alignment = when (index) {
                0 -> Alignment.CenterStart
                BoardAlertStops.lastIndex -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Text(
                text = stop.label(),
                modifier = Modifier
                    .align(alignment)
                    .then(if (enabled) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLevel(stop) } else Modifier)
                    .testTag("board_alerts_level_${stop.wire}"),
                color = if (stop == level) pocketPalette.textPrimary else pocketPalette.textSecondary,
                fontFamily = Rubik,
                fontWeight = if (stop == level) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = metrics.sp(45f),
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    BoardAlertStops.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoardAlertLevelSlider(
    metrics: DesignMetrics,
    enabled: Boolean,
    level: BoardAlertLevel,
    onLevel: (BoardAlertLevel) -> Unit,
) {
    val trackWidth = 1036f + 2f * metrics.overscanX
    val index = BoardAlertStops.indexOf(level).coerceAtLeast(0)
    val dragging = remember { mutableStateOf(false) }
    val dragPosition = remember { mutableFloatStateOf(0f) }
    val latestIndex = rememberUpdatedState(index)
    val latestOnLevel = rememberUpdatedState(onLevel)
    val stopCount = BoardAlertStops.lastIndex.toFloat()
    val position = animateFloatAsState(
        targetValue = if (dragging.value) dragPosition.floatValue else index / stopCount,
        animationSpec = if (dragging.value) snap() else spring(dampingRatio = 0.86f, stiffness = 620f, visibilityThreshold = 0.001f),
        label = "Board alert level",
    )
    val thumbScale = animateFloatAsState(
        targetValue = if (dragging.value) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 760f, visibilityThreshold = 0.001f),
        label = "Board alert thumb press",
    )
    fun choose(fraction: Float) {
        val next = (fraction.coerceIn(0f, 1f) * stopCount).let { kotlin.math.round(it).toInt() }
        if (next != latestIndex.value) latestOnLevel.value(BoardAlertStops[next])
    }
    Box(
        Modifier
            .designBounds(metrics, 52f, BOARD_ALERTS_SLIDER_Y, trackWidth, 138f)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (enabled) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging.value = true
                    down.consume()
                    dragPosition.floatValue = (down.position.x / size.width).coerceIn(0f, 1f)
                    try {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dragPosition.floatValue = (change.position.x / size.width).coerceIn(0f, 1f)
                            pressed = change.pressed
                            change.consume()
                        }
                    } finally {
                        choose(dragPosition.floatValue)
                        dragging.value = false
                    }
                }
            } else Modifier)
            .semantics { stateDescription = level.label() }
            .testTag("board_alerts_level"),
    ) {
        PocketSliderFace(
            metrics = metrics,
            trackWidth = trackWidth,
            level = position,
            thumbScale = thumbScale,
            trackModifier = if (enabled) {
                Modifier.controllerTarget(
                    "board_alerts_level",
                    cornerRadius = 52.5f,
                    onAdjust = { delta ->
                        val next = (latestIndex.value + delta).coerceIn(0, BoardAlertStops.lastIndex)
                        if (next != latestIndex.value) latestOnLevel.value(BoardAlertStops[next])
                    },
                ) {}
            } else {
                Modifier
            },
        )
    }
}

@Composable
internal fun BoardNotificationsPanel(
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
        tag = "board_notifications_row",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsNotifications,
            title = "Board Notifications",
            subtitle = "Choose alerts for each board",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun BoardNotificationRow(
    metrics: DesignMetrics,
    y: Float,
    board: Board,
    alertsOn: Boolean,
    onToggle: () -> Unit,
) {
    val shown = alertsOn && board.pushEnabled
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
        tag = "board_alerts_board_${board.id}",
        onClick = onToggle,
        enabled = alertsOn,
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(shown)
        },
    ) {
        Box(Modifier.matchParentSize().alpha(if (alertsOn) 1f else 0.45f)) {
            SettingsHeading(
                metrics = metrics,
                icon = Assets.SettingsSocial,
                title = board.name,
                subtitle = when {
                    !alertsOn -> "Board Alerts are off"
                    board.pushEnabled -> "Alerts on"
                    else -> "Alerts off"
                },
            )
            NearbyToggle(metrics = metrics, enabled = shown)
        }
    }
}

internal fun boardNotificationsMessage(state: PocketPassUiState): String? {
    val boards = state.boards.notificationBoards
    return when {
        boards == null && state.boards.notificationBoardsFailed -> "Couldn't load your boards. Try again later."
        boards == null -> "Loading your boards…"
        boards.isEmpty() -> "You're not in any boards yet."
        else -> null
    }
}

@Composable
internal fun BoardNotificationsMessagePanel(metrics: DesignMetrics, y: Float, message: String) {
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
        tag = "board_notifications_message",
    ) {
        Text(
            text = message,
            modifier = Modifier.anchoredBounds(metrics, 60f, 82f, 1020f, 58f, DesignAnchor.Stretch),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun BoardNotificationSettingsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Board Notifications",
            subtitle = "Alerts for each board.",
            backTag = "board_notification_settings_back",
        ) { dispatch(PocketPassEvent.Back) }
        val boards = state.boards.notificationBoards.orEmpty()
        val message = boardNotificationsMessage(state)
        val rowCount = if (message != null) 1 else boards.size
        val focus = LocalControllerFocus.current
        val display = LocalFocusDisplay.current
        LaunchedEffect(focus, message, boards.size) {
            withFrameNanos { }
            withFrameNanos { }
            focus?.ensureFocus(display)
        }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        DesignBox(
            metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch,
            modifier = Modifier.clip(belowHeader).controllerFocusViewport(focusViewport)
                .verticalScroll(scroll).testTag("board_notification_settings_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(
                    Modifier.padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                        .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                        .requiredHeight(metrics.dp(SETTINGS_PANEL_GAP + rowCount * (SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP))),
                ) {
                    if (message != null) {
                        SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + SETTINGS_PANEL_GAP, height = SETTINGS_ROW_HEIGHT, order = 1) {
                            BoardNotificationsMessagePanel(metrics, SETTINGS_PANEL_GAP, message)
                        }
                    } else {
                        boards.forEachIndexed { index, board ->
                            val y = SETTINGS_PANEL_GAP + index * (SETTINGS_ROW_HEIGHT + SETTINGS_PANEL_GAP)
                            SubpagePanelPop(y = SUBPAGE_CONTENT_TOP + y, height = SETTINGS_ROW_HEIGHT, order = index + 1) {
                                BoardNotificationRow(metrics, y, board, state.boards.pushEnabled) {
                                    dispatch(PocketPassEvent.SetBoardPushEnabled(board.id, !board.pushEnabled))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
