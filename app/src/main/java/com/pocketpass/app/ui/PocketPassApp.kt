package com.pocketpass.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.renderer.MiiEditorRenderSurface
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.controller.ControllerFocusHighlight
import com.pocketpass.app.ui.controller.FocusDisplay
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface

// The live Mii editor renders through the app's WebView stack; the multiplatform screens
// only see it through LocalMiiRenderSurface.
internal val MiiRenderSurfaceFromWebView:
    @Composable (MiiEditorController, String?, Modifier) -> Unit =
    { controller, initialCanonicalBase64, modifier ->
        MiiEditorRenderSurface(
            editorController = controller,
            modifier = modifier,
            initialCanonicalBase64 = initialCanonicalBase64,
        )
    }

@Composable
fun TopDisplayApp(
    viewModel: PocketPassViewModel,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalSoundEffects provides viewModel.soundEffects,
        LocalAppVersionName provides BuildConfig.VERSION_NAME,
        LocalControllerFocus provides viewModel.controllerFocus,
        LocalFocusDisplay provides FocusDisplay.Top,
        LocalMiiRenderSurface provides MiiRenderSurfaceFromWebView,
    ) {
        Box(Modifier.fillMaxSize()) {
            TopDisplayContent(
                state = state,
                dispatch = viewModel::dispatch,
                extensions = extensions,
                miiEditorController = viewModel.miiEditorController,
            )
            ControllerFocusHighlight(viewModel.controllerFocus, FocusDisplay.Top)
        }
    }
}

@Composable
fun BottomDisplayApp(
    viewModel: PocketPassViewModel,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalSoundEffects provides viewModel.soundEffects,
        LocalAppVersionName provides BuildConfig.VERSION_NAME,
        LocalControllerFocus provides viewModel.controllerFocus,
        LocalFocusDisplay provides FocusDisplay.Bottom,
        LocalMiiRenderSurface provides MiiRenderSurfaceFromWebView,
    ) {
        Box(Modifier.fillMaxSize()) {
            BottomDisplayContent(state, viewModel::dispatch, extensions)
            ControllerFocusHighlight(viewModel.controllerFocus, FocusDisplay.Bottom)
        }
    }
}
