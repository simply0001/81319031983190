package com.pocketpass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.showsPocketPassApp
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.ui.auth.AccountBanBottomScreen
import com.pocketpass.app.ui.auth.AuthBottomScreen
import com.pocketpass.app.ui.auth.AuthTopScreen
import com.pocketpass.app.ui.auth.NearbyPermissionBottomScreen
import com.pocketpass.app.ui.components.BottomTabBar
import com.pocketpass.app.ui.components.ExitingOverlay
import com.pocketpass.app.ui.components.LocalRouteRevealGeneration
import com.pocketpass.app.ui.components.LocalTextStyle
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.StatusPills
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.controller.ControllerSwapHint
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.ui.mii.MiiEditorBottomScreen
import com.pocketpass.app.ui.mii.MiiEditorTopScreen
import com.pocketpass.app.ui.screens.AchievementsBottomOverlay
import com.pocketpass.app.ui.screens.BioEditorBottomOverlay
import com.pocketpass.app.ui.screens.BottomScreen
import com.pocketpass.app.ui.screens.ConnectedAppsOverlay
import com.pocketpass.app.ui.screens.DeleteAccountOverlay
import com.pocketpass.app.ui.screens.ForceUpdateBottomScreen
import com.pocketpass.app.ui.screens.FriendProfileBottomOverlay
import com.pocketpass.app.ui.screens.FriendsAddFriendOverlay
import com.pocketpass.app.ui.screens.GameBottomOverlay
import com.pocketpass.app.ui.screens.GamesBottomOverlay
import com.pocketpass.app.ui.screens.LeaderboardBottomOverlay
import com.pocketpass.app.ui.screens.MiiSlotsOverlay
import com.pocketpass.app.ui.screens.EditInfoBottomOverlays
import com.pocketpass.app.ui.screens.NotificationDrawer
import com.pocketpass.app.ui.screens.OAuthConsentOverlay
import com.pocketpass.app.ui.screens.OAuthConsentTopScreen
import com.pocketpass.app.ui.screens.ShopBottomOverlay
import com.pocketpass.app.ui.screens.TopActiveGame
import com.pocketpass.app.ui.screens.TopGames
import com.pocketpass.app.ui.screens.TopGroupComposer
import com.pocketpass.app.ui.screens.TopLeaderboard
import com.pocketpass.app.ui.screens.TopMessageThread
import com.pocketpass.app.ui.screens.TopProfileViewer
import com.pocketpass.app.ui.screens.TopScreen
import com.pocketpass.app.ui.screens.TopShop
import com.pocketpass.app.ui.setup.AccountSetupBottomScreen
import com.pocketpass.app.ui.theme.LocalPocketPalette
import com.pocketpass.app.ui.theme.paletteFor
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.theme.resolveDarkTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun TopDisplayContent(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    miiEditorController: MiiEditorController? = null,
) {
    PocketPassTheme(state.themeMode) {
        val savedCanonical by rememberUpdatedState(state.miiEditor.savedCanonicalBase64)
        val renderSurface = LocalMiiRenderSurface.current
        val miiLiveSurface = remember(miiEditorController, renderSurface) {
            movableContentOf {
                if (miiEditorController != null) {
                    renderSurface?.invoke(
                        miiEditorController,
                        savedCanonical,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (state.integrityCompromised) {
            IntegrityBlockScreen()
        } else if (state.showsAccountBan()) {
            DesignSurface(
                designWidth = TOP_DESIGN_WIDTH,
                designHeight = TOP_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                AuthTopScreen(metrics, state.status)
            }
        } else if (state.requiresForcedUpdate()) {
            DesignSurface(
                designWidth = TOP_DESIGN_WIDTH,
                designHeight = TOP_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                ForceUpdateTopScreen(metrics, state)
            }
        } else if (state.requiresAccountSetup()) {
            DesignSurface(
                designWidth = TOP_DESIGN_WIDTH,
                designHeight = TOP_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                AuthTopScreen(metrics, state.status)
            }
        } else if (state.requiresMiiGate()) {
            MiiEditorTopScreen(
                state = state.miiEditor,
                status = state.status,
                onEvent = { dispatch(PocketPassEvent.Mii(it)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (
                    state.miiEditor.isEditorVisible &&
                    miiEditorController != null
                ) {
                    miiLiveSurface()
                }
            }
        } else {
            if (state.miiEditor.isEditorPreparing && miiEditorController != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0f },
                ) {
                    miiLiveSurface()
                }
            }
            DesignSurface(
                designWidth = TOP_DESIGN_WIDTH,
                designHeight = TOP_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                if (
                    state.sessionState.showsPocketPassApp() &&
                    state.nearbyPermissionUi.visible
                ) {
                    AuthTopScreen(metrics, state.status)
                } else if (state.sessionState.showsPocketPassApp()) {
                    var profileViewerPresenting by remember { mutableStateOf(false) }
                    var threadPresenting by remember { mutableStateOf(false) }
                    var composerPresenting by remember { mutableStateOf(false) }
                    TopDestinationBackground(metrics, state.rootDestination)
                    TopScreen(
                        destination = state.rootDestination,
                        state = state,
                        dispatch = dispatch,
                        profileViewerPresenting = profileViewerPresenting,
                        threadPresenting = threadPresenting || composerPresenting,
                    )
                    TopMessageThread(
                        metrics = metrics,
                        state = state,
                        dispatch = dispatch,
                        onPresentingChanged = { threadPresenting = it },
                    )
                    TopGroupComposer(
                        metrics = metrics,
                        state = state,
                        onPresentingChanged = { composerPresenting = it },
                    )
                    TopShop(metrics = metrics, state = state)
                    TopGames(metrics = metrics, state = state)
                    TopActiveGame(metrics = metrics, state = state)
                    TopLeaderboard(metrics = metrics, state = state)
                    TopProfileViewer(
                        metrics = metrics,
                        state = state.profileViewer,
                        dispatch = dispatch,
                        onPresentingChanged = { profileViewerPresenting = it },
                    )
                    StatusPills(metrics, state.status)
                    ControllerSwapHint(
                        metrics,
                        belowStatus = state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities,
                    )
                    NotificationDrawer(
                        metrics = metrics,
                        state = state,
                        visible =
                            state.rootDestination ==
                            com.pocketpass.app.model.PocketPassDestination.Home &&
                                state.friendsOverlay == FriendsOverlay.Notifications,
                        dispatch = dispatch,
                    )
                    if(state.oauthConsent.visible) OAuthConsentTopScreen(metrics, state)
                } else {
                    AuthTopScreen(metrics, state.status)
                }
            }
        }
    }
}

@Composable
fun BottomDisplayContent(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    PocketPassTheme(state.themeMode) {
        val focus = LocalControllerFocus.current
        PlatformBackHandler(enabled = state.hasDismissableLayer()) {
            if (focus?.exitToParent() != true) dispatch(PocketPassEvent.Back)
        }
        if (state.integrityCompromised) {
            IntegrityBlockScreen()
        } else if (state.showsAccountBan()) {
            DesignSurface(
                designWidth = BOTTOM_DESIGN_WIDTH,
                designHeight = BOTTOM_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                state.accountBan?.let { ban -> AccountBanBottomScreen(metrics, ban, dispatch) }
            }
        } else if (state.requiresForcedUpdate()) {
            DesignSurface(
                designWidth = BOTTOM_DESIGN_WIDTH,
                designHeight = BOTTOM_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) {
                ForceUpdateBottomScreen(state = state, dispatch = dispatch)
            }
        } else if (state.requiresAccountSetup()) {
            DesignSurface(
                designWidth = BOTTOM_DESIGN_WIDTH,
                designHeight = BOTTOM_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                AccountSetupBottomScreen(
                    metrics = metrics,
                    state = state.accountSetup,
                    dispatch = dispatch,
                )
            }
        } else if (state.requiresMiiGate()) {
            MiiEditorBottomScreen(
                state = state.miiEditor,
                onEvent = { dispatch(PocketPassEvent.Mii(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DesignSurface(
                designWidth = BOTTOM_DESIGN_WIDTH,
                designHeight = BOTTOM_DESIGN_HEIGHT,
                modifier = Modifier.fillMaxSize(),
            ) { metrics ->
                if (
                    state.sessionState.showsPocketPassApp() &&
                    state.nearbyPermissionUi.visible
                ) {
                    NearbyPermissionBottomScreen(
                        metrics = metrics,
                        isRepair = state.nearbyPermissionUi.isRepair,
                        error = state.nearbyPermissionUi.error,
                        onContinue = {
                            dispatch(PocketPassEvent.RequestNearbyPermissions)
                        },
                    )
                } else if (state.sessionState.showsPocketPassApp()) {
                    BottomDestinationBackground(metrics, state.rootDestination)
                    BottomRouteStack(
                        routes = state.routes,
                        root = { RootBottomContent(metrics, state, dispatch) },
                        pushed = { route ->
                            BottomScreen(
                                route = route,
                                state = state,
                                dispatch = dispatch,
                            )
                            if (route == PocketPassRoute.Social) {
                                SocialBottomOverlays(metrics, state, dispatch)
                            }
                            if (route == PocketPassRoute.EditInfo) {
                                EditInfoBottomOverlays(metrics, state, dispatch)
                            }
                        },
                    )
                } else {
                    AuthBottomScreen(
                        metrics = metrics,
                        sessionState = state.sessionState,
                        state = state.auth,
                        dispatch = { dispatch(PocketPassEvent.Auth(it)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun IntegrityBlockScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1D2B33)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "This build isn’t genuine",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "PocketPass will not connect to your account from a copy that was " +
                    "repackaged or re-signed. Reinstall the official build to continue.",
                color = Color(0xFFB6C6CE),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun PocketPassUiState.requiresAccountSetup(): Boolean {
    if (!sessionState.showsPocketPassApp()) return false
    return !accountSetup.resolved || accountSetup.required
}

internal fun PocketPassUiState.showsAccountBan(): Boolean =
    accountBan != null && sessionState.showsPocketPassApp()

internal fun PocketPassUiState.requiresForcedUpdate(): Boolean =
    appUpdate.enabled && appUpdate.updateRequired

@Composable
private fun ForceUpdateTopScreen(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val palette = pocketPalette
    val homeTop = palette.background(com.pocketpass.app.model.PocketPassDestination.Home, top = true)
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeTop,
        topColor = homeTop.top,
        bottomColor = homeTop.bottom,
        holdFraction = 0.5f,
        designWidth = TOP_DESIGN_WIDTH,
        designHeight = TOP_DESIGN_HEIGHT,
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Update Required",
                color = palette.teal,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(120f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Update PocketPass on the bottom screen to keep going.",
                color = palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(52f),
                textAlign = TextAlign.Center,
            )
        }
    }
    StatusPills(metrics, state.status)
}

internal fun PocketPassUiState.requiresMiiGate(): Boolean {
    if (!miiEditorEnabled) return false
    val accountKey = when (val session = sessionState) {
        is SessionState.Authenticated -> session.userId.value
        is SessionState.OfflineWithCachedSession -> session.userId.value
        else -> return false
    }
    return miiEditor.activeAccountKey != accountKey ||
        !miiEditor.isInitialized ||
        miiEditor.mode == MiiEditorMode.Loading ||
        miiEditor.isEditorPresented
}

@Composable
private fun TopDestinationBackground(
    metrics: DesignMetrics,
    destination: com.pocketpass.app.model.PocketPassDestination,
) {
    if(destination == com.pocketpass.app.model.PocketPassDestination.Messages) {
        com.pocketpass.app.ui.screens.BoardBackdrop(metrics)
        return
    }
    val palette = pocketPalette.background(destination, top = true)
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeTop,
        topColor = palette.top,
        bottomColor = palette.bottom,
        holdFraction = 0.5f,
        designWidth = TOP_DESIGN_WIDTH,
        designHeight = TOP_DESIGN_HEIGHT,
    )
}

@Composable
private fun BottomDestinationBackground(
    metrics: DesignMetrics,
    destination: com.pocketpass.app.model.PocketPassDestination,
) {
    if(destination == com.pocketpass.app.model.PocketPassDestination.Messages) {
        com.pocketpass.app.ui.screens.BoardBackdrop(metrics)
        return
    }
    val palette = pocketPalette.background(destination, top = false)
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeBottom,
        topColor = palette.top,
        bottomColor = palette.bottom,
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
}

@Composable
fun PocketPassTheme(
    themeMode: com.pocketpass.app.model.ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val palette = paletteFor(dark)
    CompositionLocalProvider(
        LocalPocketPalette provides palette,
        LocalTextStyle provides TextStyle(platformStyle = pocketPlatformTextStyle()),
    ) {
        Box(Modifier.fillMaxSize(), content = { content() })
    }
}

@Composable
private fun RootBottomContent(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomScreen(
        route = PocketPassRoute.Root(state.rootDestination),
        state = state,
        dispatch = dispatch,
    )
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.shop.visible, snapshot = state, releaseFocusOnExit = true) { shown ->
            ShopBottomOverlay(metrics, shown, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.games.visible, snapshot = Unit, releaseFocusOnExit = true) {
            GamesBottomOverlay(metrics, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.leaderboard.visible, snapshot = state, releaseFocusOnExit = true) { shown ->
            LeaderboardBottomOverlay(metrics, shown, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.achievements.visible, snapshot = state) { shown ->
            AchievementsBottomOverlay(metrics, shown)
        }
    }
    if (state.rootDestination != com.pocketpass.app.model.PocketPassDestination.Messages) {
        BottomTabBar(
            metrics = metrics,
            current = state.rootDestination,
            onSelect = { dispatch(PocketPassEvent.SelectDestination(it)) },
        )
    }
    if (
        state.rootDestination ==
        com.pocketpass.app.model.PocketPassDestination.Activities &&
        state.games.activeGame != null
    ) {
        GameBottomOverlay(metrics, state, dispatch)
    }
    if (
        state.rootDestination ==
        com.pocketpass.app.model.PocketPassDestination.Friends &&
        state.friendsOverlay == FriendsOverlay.AddFriend
    ) {
        FriendsAddFriendOverlay(metrics, state, dispatch)
    }
    if (
        state.rootDestination ==
        com.pocketpass.app.model.PocketPassDestination.Home
    ) {
        BioEditorBottomOverlay(metrics, state, dispatch)
    }
    if (
        state.rootDestination ==
        com.pocketpass.app.model.PocketPassDestination.Settings &&
        state.routes.lastOrNull() is PocketPassRoute.Root
    ) {
        SocialBottomOverlays(metrics, state, dispatch)
    }
    if (
        state.rootDestination ==
        com.pocketpass.app.model.PocketPassDestination.Settings &&
        state.deleteAccountVisible
    ) {
        DeleteAccountOverlay(metrics, state, dispatch)
    }
    if (state.profileViewer.visible) {
        FriendProfileBottomOverlay(metrics, state, dispatch)
    }
    if (state.oauthConsent.visible) {
        OAuthConsentOverlay(metrics, state, dispatch, detailsOnTop = true)
    }
}

@Composable
private fun SocialBottomOverlays(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    if (state.miiSlotsVisible) {
        MiiSlotsOverlay(metrics, state, dispatch)
    }
    if (state.connectedApps.visible) {
        ConnectedAppsOverlay(metrics, state, dispatch)
    }
}

@Composable
private fun BottomRouteStack(
    routes: List<PocketPassRoute>,
    root: @Composable () -> Unit,
    pushed: @Composable (PocketPassRoute) -> Unit,
) {
    val pushedRoute = routes.last().takeUnless { it is PocketPassRoute.Root }
    val focus = LocalControllerFocus.current
    val display = LocalFocusDisplay.current
    val reveal = remember { RouteRevealTracker() }
    remember(pushedRoute) {
        reveal.arrive(routes.size, focus?.focusId)
        if (pushedRoute == null && reveal.presented) reveal.generation++
        reveal.presented = pushedRoute != null
        reveal
    }
    LaunchedEffect(pushedRoute) {
        val returnFocusId = reveal.returnFocusId
        reveal.returnFocusId = null
        returnFocusId?.let { focus?.focus(it, reveal = false) }
        if (focus != null && returnFocusId == null && pushedRoute?.opensOnFirstRow() == true) {
            val first = snapshotFlow { focus.firstTarget(display) }.filterNotNull().first()
            if (focus.focusId == null) focus.focus(first, reveal = false)
        }
    }
    val presenting = pushedRoute != null
    CompositionLocalProvider(
        LocalControllerFocus provides focus.takeUnless { presenting },
        LocalRouteRevealGeneration provides reveal.generation,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (presenting) 0f else 1f },
        ) {
            root()
        }
    }
    if (pushedRoute == null) return
    val blocker = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = blocker, indication = null) {},
    )
    pushed(pushedRoute)
}

private class RouteRevealTracker {
    var generation = 0
    var presented = false
    var returnFocusId: String? = null
    private var depth = 0
    private val openerFocusByDepth = mutableMapOf<Int, String>()

    fun arrive(newDepth: Int, focusedId: String?) {
        if (newDepth > depth) {
            if (depth > 0 && focusedId != null) openerFocusByDepth[depth] = focusedId
            returnFocusId = null
        } else {
            returnFocusId = openerFocusByDepth[newDepth]
        }
        openerFocusByDepth.keys.removeAll { it >= newDepth }
        depth = newDepth
    }
}

private fun PocketPassRoute.opensOnFirstRow(): Boolean =
    this !is PocketPassRoute.MessageDetail && this != PocketPassRoute.NewGroup
