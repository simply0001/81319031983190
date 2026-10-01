package com.pocketpass.app.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.IntegrityBlockScreen
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.controller.ControllerOverlayFocus
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.controller.LocalControllerFocusLayer
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.requiresAccountSetup
import com.pocketpass.app.ui.requiresForcedUpdate
import com.pocketpass.app.ui.requiresMiiGate
import com.pocketpass.app.ui.showsAccountBan
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.domain.state.showsPocketPassApp
import com.pocketpass.app.ui.theme.BackgroundPair
import com.pocketpass.app.ui.theme.PocketPalette
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.screens.BoardNavigationRail
import com.pocketpass.app.ui.screens.BoardBackdrop
import com.pocketpass.app.ui.screens.LocalBoardNavigationRail

@Composable
fun PhoneRoot(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    miiEditorController: MiiEditorController?,
) {
    val savedCanonical by rememberUpdatedState(state.miiEditor.savedCanonicalBase64)
    val renderSurface = LocalMiiRenderSurface.current
    val miiLiveSurface = remember(miiEditorController, renderSurface) {
        movableContentOf {
            if (miiEditorController != null) {
                renderSurface?.invoke(miiEditorController, savedCanonical, Modifier.fillMaxSize())
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (state.miiEditor.isEditorPreparing && miiEditorController != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0f }) {
                miiLiveSurface()
            }
        }
        when {
            state.integrityCompromised -> IntegrityBlockScreen()
            state.showsAccountBan() -> state.accountBan?.let { ban -> PhoneAccountBanScreen(metrics, ban, dispatch) }
            state.requiresForcedUpdate() -> PhoneForceUpdateScreen(metrics, state, dispatch)
            state.requiresAccountSetup() -> PhoneAccountSetupScreen(metrics, state.accountSetup) {
                dispatch(PocketPassEvent.AccountSetup(it))
            }
            state.requiresMiiGate() -> PhoneMiiGate(metrics, state, dispatch) {
                if (state.miiEditor.isEditorVisible && miiEditorController != null) miiLiveSurface()
            }
            !state.sessionState.showsPocketPassApp() -> PhoneAuthScreen(metrics, state.sessionState, state.auth) {
                dispatch(PocketPassEvent.Auth(it))
            }
            state.nearbyPermissionUi.visible -> PhoneNearbyPermissionScreen(metrics, state.nearbyPermissionUi) {
                dispatch(PocketPassEvent.RequestNearbyPermissions)
            }
            else -> PhoneShell(metrics, state, dispatch)
        }
    }
}

@Composable
private fun PhoneShell(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val layout = phoneLayout(metrics.designWidth - insets.start - insets.end, metrics.designHeight)
    val backdrop = phoneBackdrop(state, pocketPalette)
    ControllerRouteFocus(state.rootDestination, state.routes.size)
    Box(Modifier.fillMaxSize()) {
        if(state.rootDestination == PocketPassDestination.Messages) BoardBackdrop(metrics)
        else PhoneBackdrop(metrics, backdrop.top, backdrop.bottom)
        when (layout) {
            PhoneLayout.Compact -> PhoneCompactShell(metrics, state, dispatch)
            PhoneLayout.Wide -> PhoneWideShell(metrics, state, dispatch)
        }
        PhoneDialogs(metrics, state, dispatch)
    }
}

private fun phoneBackdrop(state: PocketPassUiState, palette: PocketPalette): BackgroundPair {
    val root = state.rootDestination
    val activities = root == PocketPassDestination.Activities
    return when {
        activities && state.shop.visible ->
            BackgroundPair(palette.tint(Color(0xFFF6EEE9)), palette.tint(Color(0xFFFCDCBC)))
        activities && state.games.visible ->
            BackgroundPair(palette.tint(Color(0xFFE9F6EA)), palette.tint(Color(0xFFBCFCC2)))
        activities && (state.leaderboard.visible || state.achievements.visible) ->
            BackgroundPair(palette.tint(Color(0xFFF6F4E9)), palette.tint(Color(0xFFFCF0BC)))
        state.routes.lastOrNull().let { it is PocketPassRoute.MessageDetail || it is PocketPassRoute.NewGroup } ->
            BackgroundPair(palette.tint(Color(0xFFE9F1F6)), palette.tint(Color(0xFFD1EDFB)))
        else -> palette.background(root, top = false)
    }
}

@Composable
private fun PhoneCompactShell(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val destination = state.rootDestination
    val backdrop = phoneBackdrop(state, pocketPalette)
    val showsTabBar = destination != PocketPassDestination.Messages
    val tabBarClearance = if (showsTabBar) PHONE_TAB_BAR_HEIGHT + insets.bottom else 0f
    val aboveTabBar = remember(metrics, tabBarClearance) { AboveTabBarShape(metrics, tabBarClearance) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (showsTabBar) Modifier.clip(aboveTabBar) else Modifier)
                .padding(start = metrics.dp(insets.start), end = metrics.dp(insets.end)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .widthIn(max = metrics.dp(PHONE_DECK_WIDTH))
                    .fillMaxHeight(),
            ) {
                key(destination) {
                    MotionLayer(
                        modifier = Modifier.fillMaxSize(),
                        entrance = destination.entrance(),
                        delayMillis = 55,
                    ) {
                        CompositionLocalProvider(LocalPhoneTabBarClearance provides tabBarClearance) {
                            PhoneTab(metrics, null, state, dispatch)
                        }
                    }
                }
            }
            if(showsTabBar) PhoneTopFade(metrics, backdrop.top, Modifier.align(Alignment.TopCenter))
        }
        if(showsTabBar) {
            PhoneTabBar(
                metrics,
                destination,
                onSelect = { dispatch(PocketPassEvent.SelectDestination(it)) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    PhonePageLayer(metrics, backdrop, visible = state.routes.lastOrNull().let { it != null && it !is PocketPassRoute.Root }, fromEnd = true, focusLayer = PHONE_ROUTE_FOCUS_LAYER) {
        PhoneRoutePage(metrics, state, dispatch)
    }
    PhonePageLayer(metrics, null, visible = destination == PocketPassDestination.Activities && state.games.activeGame != null, fromEnd = false, focusLayer = PHONE_GAME_FOCUS_LAYER) {
        PhoneGamePage(metrics, state, dispatch)
    }
    PhonePageLayer(metrics, backdrop.takeUnless { state.profileViewer.source == ProfileViewerSource.Board }, visible = state.profileViewer.visible, fromEnd = false, focusLayer = PHONE_PROFILE_FOCUS_LAYER) {
        PhoneDeck(metrics) { PhoneProfilePage(metrics, state, dispatch) }
    }
    PhonePageLayer(metrics, backdrop, visible = destination == PocketPassDestination.Home && state.friendsOverlay == FriendsOverlay.Notifications, fromEnd = true, focusLayer = PHONE_NOTIFICATIONS_FOCUS_LAYER) {
        PhoneDeck(metrics) { PhoneNotificationsPage(metrics, state, dispatch) }
    }
}

@Composable
private fun PhoneWideShell(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val destination = state.rootDestination
    val backdrop = phoneBackdrop(state, pocketPalette)
    val panes = widePanes(metrics.designWidth, insets.start, insets.end)
    Row(Modifier.fillMaxSize()) {
        if(destination == PocketPassDestination.Messages && state.boardsVisible) {
            BoardNavigationRail(metrics, state, dispatch, Modifier.width(metrics.dp(panes.rail))
                .padding(start = metrics.dp(insets.start), top = metrics.dp(insets.top), bottom = metrics.dp(insets.bottom)))
        } else {
            PhoneNavRail(metrics, destination, onSelect = { dispatch(PocketPassEvent.SelectDestination(it)) })
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = metrics.dp(panes.margin), end = metrics.dp(panes.margin + insets.end)),
            ) {
                key(destination) {
                    MotionLayer(
                        modifier = Modifier.fillMaxSize(),
                        entrance = destination.entrance(),
                        delayMillis = 55,
                    ) {
                        CompositionLocalProvider(LocalBoardNavigationRail provides (destination == PocketPassDestination.Messages && state.boardsVisible)) {
                            PhoneTab(metrics, panes, state, dispatch)
                        }
                    }
                }
            }
            if(destination != PocketPassDestination.Messages) PhoneTopFade(metrics, backdrop.top, Modifier.align(Alignment.TopCenter))
            PhonePageLayer(metrics, null, visible = destination == PocketPassDestination.Activities && state.games.activeGame != null, fromEnd = false, focusLayer = PHONE_GAME_FOCUS_LAYER) {
                PhoneGamePage(metrics, state, dispatch)
            }
            val messagesPage = state.routes.lastOrNull()
                ?.takeIf { it is PocketPassRoute.MessageDetail || it is PocketPassRoute.NewGroup }
            val shownMessagesPage = remember { mutableStateOf(messagesPage) }
            if (messagesPage != null) shownMessagesPage.value = messagesPage
            PhonePageLayer(metrics, backdrop, visible = messagesPage != null, fromEnd = true, focusLayer = PHONE_ROUTE_FOCUS_LAYER) {
                Box(Modifier.fillMaxSize().padding(end = metrics.dp(insets.end))) {
                    if (shownMessagesPage.value is PocketPassRoute.NewGroup) {
                        PhoneNewGroupPage(metrics, state, dispatch)
                    } else {
                        PhoneThread(metrics, state, dispatch)
                    }
                }
            }
            PhonePageLayer(metrics, null, visible = destination == PocketPassDestination.Home && state.friendsOverlay == FriendsOverlay.Notifications, fromEnd = true, focusLayer = PHONE_NOTIFICATIONS_FOCUS_LAYER) {
                PhoneNotificationsSheet(metrics, state, dispatch)
            }
        }
    }
}

@Composable
private fun PhoneTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    when (state.rootDestination) {
        PocketPassDestination.Home -> PhoneHomeTab(metrics, panes, state, dispatch)
        PocketPassDestination.Friends -> PhoneFriendsTab(metrics, panes, state, dispatch)
        PocketPassDestination.Messages -> PhoneMessagesTab(metrics, panes, state, dispatch)
        PocketPassDestination.Activities -> PhoneActivitiesTab(metrics, panes, state, dispatch)
        PocketPassDestination.Settings -> PhoneSettingsTab(metrics, panes, state, dispatch)
    }
}

private fun PocketPassDestination.entrance(): EntranceMotion = when (this) {
    PocketPassDestination.Home, PocketPassDestination.Friends -> EntranceMotion.PanelRise
    PocketPassDestination.Messages -> EntranceMotion.None
    PocketPassDestination.Activities, PocketPassDestination.Settings -> EntranceMotion.None
}

@Composable
internal fun PhonePageLayer(
    metrics: DesignMetrics,
    backdrop: BackgroundPair?,
    visible: Boolean,
    fromEnd: Boolean,
    focusLayer: Int = PHONE_ROUTE_FOCUS_LAYER,
    content: @Composable BoxScope.() -> Unit,
) {
    ControllerOverlayFocus(visible)
    CompositionLocalProvider(
        LocalControllerFocusLayer provides LocalControllerFocusLayer.current + focusLayer,
        LocalControllerFocus provides LocalControllerFocus.current?.takeIf { visible },
    ) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + if (fromEnd) {
            slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 }
        } else {
            slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 6 }
        },
        exit = fadeOut(tween(180)) + if (fromEnd) {
            slideOutHorizontally(tween(220)) { it / 5 }
        } else {
            slideOutVertically(tween(220)) { it / 6 }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .controllerFocusBarrier("phone_page_$focusLayer", layer = 0)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (backdrop != null) PhoneBackdrop(metrics, backdrop.top, backdrop.bottom)
            content()
            if (backdrop != null) PhoneTopFade(metrics, backdrop.top, Modifier.align(Alignment.TopCenter))
        }
    }
    }
}

@Composable
private fun ControllerRouteFocus(destination: PocketPassDestination, depth: Int) {
    val focus = LocalControllerFocus.current ?: return
    val display = LocalFocusDisplay.current
    val tracker = remember { RouteFocusTracker(destination, depth) }
    val restore = remember(focus, destination, depth) { tracker.arrive(destination, depth, focus.focusId) }
    LaunchedEffect(focus, destination, depth) {
        if (restore != null) {
            focus.focus(restore, reveal = false)
        } else {
            withFrameNanos { }
            withFrameNanos { }
            focus.ensureFocus(display)
        }
    }
}

private class RouteFocusTracker(private var destination: PocketPassDestination, private var depth: Int) {
    private val openers = mutableMapOf<Int, String>()

    fun arrive(nextDestination: PocketPassDestination, nextDepth: Int, focusedId: String?): String? {
        val sameTab = nextDestination == destination
        val previousDepth = depth
        destination = nextDestination
        depth = nextDepth
        if (!sameTab) {
            openers.clear()
            return null
        }
        if (nextDepth > previousDepth) {
            focusedId?.let { openers[previousDepth] = it }
            return null
        }
        if (nextDepth == previousDepth) return null
        openers.keys.removeAll { it > nextDepth }
        return openers.remove(nextDepth)
    }
}

internal const val PHONE_ROUTE_FOCUS_LAYER = 100
internal const val PHONE_GAME_FOCUS_LAYER = 200
internal const val PHONE_PROFILE_FOCUS_LAYER = 300
internal const val PHONE_NOTIFICATIONS_FOCUS_LAYER = 400
