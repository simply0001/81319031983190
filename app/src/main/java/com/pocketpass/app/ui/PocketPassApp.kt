package com.pocketpass.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketpass.app.input.hasDismissableLayer
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.ui.auth.AuthBottomScreen
import com.pocketpass.app.ui.auth.AuthTopScreen
import com.pocketpass.app.ui.auth.NearbyPermissionBottomScreen
import com.pocketpass.app.ui.components.BottomTabBar
import com.pocketpass.app.ui.components.ExitingOverlay
import com.pocketpass.app.ui.components.LocalRouteRevealGeneration
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.StatusPills
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.theme.LocalPocketPalette
import com.pocketpass.app.ui.theme.paletteFor
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.theme.resolveDarkTheme
import com.pocketpass.app.ui.screens.AchievementsBottomOverlay
import com.pocketpass.app.ui.screens.BioEditorBottomOverlay
import com.pocketpass.app.ui.screens.NameEditorBottomOverlay
import com.pocketpass.app.ui.screens.BottomScreen
import com.pocketpass.app.ui.screens.TopGroupComposer
import com.pocketpass.app.ui.screens.DeleteAccountOverlay
import com.pocketpass.app.ui.screens.ForceUpdateBottomScreen
import com.pocketpass.app.ui.screens.FriendProfileBottomOverlay
import com.pocketpass.app.ui.screens.FriendsAddFriendOverlay
import com.pocketpass.app.ui.screens.GameBottomOverlay
import com.pocketpass.app.ui.screens.GamesBottomOverlay
import com.pocketpass.app.ui.screens.LeaderboardBottomOverlay
import com.pocketpass.app.ui.screens.ConnectedAppsOverlay
import com.pocketpass.app.ui.screens.MiiSlotsOverlay
import com.pocketpass.app.ui.screens.OAuthConsentOverlay
import com.pocketpass.app.ui.screens.ShopBottomOverlay
import com.pocketpass.app.ui.screens.TopMessageThread
import com.pocketpass.app.ui.screens.TopActiveGame
import com.pocketpass.app.ui.screens.TopGames
import com.pocketpass.app.ui.screens.TopLeaderboard
import com.pocketpass.app.ui.screens.TopShop
import com.pocketpass.app.ui.screens.TopProfileViewer
import com.pocketpass.app.ui.screens.TopScreen
import com.pocketpass.app.ui.screens.NotificationDrawer
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.renderer.MiiEditorRenderSurface
import com.pocketpass.app.ui.mii.MiiEditorBottomScreen
import com.pocketpass.app.ui.mii.MiiEditorTopScreen
import com.pocketpass.app.ui.setup.AccountSetupBottomScreen

@Composable
fun TopDisplayApp(
    viewModel: PocketPassViewModel,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(
        com.pocketpass.app.audio.LocalSoundEffects provides viewModel.soundEffects,
        com.pocketpass.app.ui.controller.LocalControllerFocus provides viewModel.controllerFocus,
        com.pocketpass.app.ui.controller.LocalFocusDisplay provides
            com.pocketpass.app.ui.controller.FocusDisplay.Top,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            TopDisplayContent(
                state = state,
                dispatch = viewModel::dispatch,
                extensions = extensions,
                miiEditorController = viewModel.miiEditorController,
            )
            com.pocketpass.app.ui.controller.ControllerFocusHighlight(
                viewModel.controllerFocus,
                com.pocketpass.app.ui.controller.FocusDisplay.Top,
            )
        }
    }
}

@Composable
fun TopDisplayContent(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
    miiEditorController: MiiEditorController? = null,
) {
    PocketPassTheme(state.themeMode) {
        val savedCanonical by rememberUpdatedState(state.miiEditor.savedCanonicalBase64)
        val miiLiveSurface = remember(miiEditorController) {
            movableContentOf {
                if (miiEditorController != null) {
                    MiiEditorRenderSurface(
                        editorController = miiEditorController,
                        modifier = Modifier.fillMaxSize(),
                        initialCanonicalBase64 = savedCanonical,
                    )
                }
            }
        }
        if (state.integrityCompromised) {
            IntegrityBlockScreen()
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
                        extensions = extensions,
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
                    NotificationDrawer(
                        metrics = metrics,
                        state = state,
                        visible =
                            state.rootDestination ==
                            com.pocketpass.app.model.PocketPassDestination.Home &&
                                state.friendsOverlay == FriendsOverlay.Notifications,
                        dispatch = dispatch,
                    )
                } else {
                    AuthTopScreen(metrics, state.status)
                }
            }
        }
    }
}

@Composable
fun BottomDisplayApp(
    viewModel: PocketPassViewModel,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(
        com.pocketpass.app.audio.LocalSoundEffects provides viewModel.soundEffects,
        com.pocketpass.app.ui.controller.LocalControllerFocus provides viewModel.controllerFocus,
        com.pocketpass.app.ui.controller.LocalFocusDisplay provides
            com.pocketpass.app.ui.controller.FocusDisplay.Bottom,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            BottomDisplayContent(state, viewModel::dispatch, extensions)
            com.pocketpass.app.ui.controller.ControllerFocusHighlight(
                viewModel.controllerFocus,
                com.pocketpass.app.ui.controller.FocusDisplay.Bottom,
            )
        }
    }
}

@Composable
fun BottomDisplayContent(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    PocketPassTheme(state.themeMode) {
        BackHandler(enabled = state.hasDismissableLayer()) {
            dispatch(PocketPassEvent.Back)
        }
        if (state.integrityCompromised) {
            IntegrityBlockScreen()
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
                    val currentRoute = state.routes.last()
                    BottomDestinationBackground(metrics, state.rootDestination)
                    BottomRouteStack(
                        route = currentRoute,
                        root = { RootBottomContent(metrics, state, dispatch, extensions) },
                        pushed = { route ->
                            BottomScreen(
                                route = route,
                                state = state,
                                dispatch = dispatch,
                                extensions = extensions,
                            )
                            if (route == PocketPassRoute.Social) {
                                SocialBottomOverlays(metrics, state, dispatch)
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

internal fun SessionState.showsPocketPassApp(): Boolean =
    this is SessionState.Authenticated ||
        this is SessionState.OfflineWithCachedSession

@Composable
private fun TopDestinationBackground(
    metrics: DesignMetrics,
    destination: com.pocketpass.app.model.PocketPassDestination,
) {
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
internal fun PocketPassTheme(
    themeMode: com.pocketpass.app.model.ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val palette = paletteFor(dark)
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalPocketPalette provides palette,
            LocalTextStyle provides TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
                content = content,
            )
        }
    }
}

@Composable
private fun RootBottomContent(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    BottomScreen(
        route = PocketPassRoute.Root(state.rootDestination),
        state = state,
        dispatch = dispatch,
        extensions = extensions,
    )
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.shop.visible, snapshot = state) { shown ->
            ShopBottomOverlay(metrics, shown, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.games.visible, snapshot = Unit) {
            GamesBottomOverlay(metrics, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.leaderboard.visible, snapshot = state) { shown ->
            LeaderboardBottomOverlay(metrics, shown, dispatch)
        }
    }
    if (state.rootDestination == com.pocketpass.app.model.PocketPassDestination.Activities) {
        ExitingOverlay(metrics, visible = state.achievements.visible, snapshot = state) { shown ->
            AchievementsBottomOverlay(metrics, shown)
        }
    }
    BottomTabBar(
        metrics = metrics,
        current = state.rootDestination,
        onSelect = { dispatch(PocketPassEvent.SelectDestination(it)) },
    )
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
        OAuthConsentOverlay(metrics, state, dispatch)
    }
}

@Composable
private fun SocialBottomOverlays(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    NameEditorBottomOverlay(metrics, state, dispatch)
    if (state.miiSlotsVisible) {
        MiiSlotsOverlay(metrics, state, dispatch)
    }
    if (state.connectedApps.visible) {
        ConnectedAppsOverlay(metrics, state, dispatch)
    }
}

@Composable
private fun BottomRouteStack(
    route: PocketPassRoute,
    root: @Composable () -> Unit,
    pushed: @Composable (PocketPassRoute) -> Unit,
) {
    val pushedRoute = route.takeUnless { it is PocketPassRoute.Root }
    val focus = LocalControllerFocus.current
    val reveal = remember { RouteRevealTracker() }
    remember(pushedRoute) {
        if (pushedRoute != null) {
            reveal.rootFocusId = focus?.focusId
        } else if (reveal.presented) {
            reveal.generation++
        }
        reveal.presented = pushedRoute != null
        reveal
    }
    LaunchedEffect(pushedRoute) {
        if (pushedRoute == null) {
            reveal.rootFocusId?.let { focus?.focus(it, reveal = false) }
            reveal.rootFocusId = null
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
    var rootFocusId: String? = null
}
