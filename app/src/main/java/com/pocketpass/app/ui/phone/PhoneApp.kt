package com.pocketpass.app.ui.phone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.MiiRenderSurfaceFillingHeight
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.controller.ControllerFocusHighlight
import com.pocketpass.app.ui.controller.FocusDisplay
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
fun PhoneApp(
    viewModel: PocketPassViewModel,
    gamepadActive: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = viewModel.controllerFocus.takeIf { gamepadActive }
    CompositionLocalProvider(
        LocalSoundEffects provides viewModel.soundEffects,
        LocalAppVersionName provides BuildConfig.VERSION_NAME,
        LocalMiiRenderSurface provides MiiRenderSurfaceFillingHeight,
        LocalControllerFocus provides focus,
        LocalFocusDisplay provides FocusDisplay.Bottom,
    ) {
        PocketPassTheme(state.themeMode) {
            PhoneSystemBars()
            BackHandler(enabled = state.hasDismissableLayer()) { viewModel.dispatch(PocketPassEvent.Back) }
            Box(Modifier.fillMaxSize()) {
                PhoneSurface { metrics ->
                    PhoneRoot(
                        metrics = metrics,
                        state = state,
                        dispatch = viewModel::dispatch,
                        miiEditorController = viewModel.miiEditorController,
                    )
                }
                if (focus != null) ControllerFocusHighlight(focus, FocusDisplay.Bottom)
            }
        }
    }
}

@Composable
private fun PhoneSystemBars() {
    val view = LocalView.current
    val dark = pocketPalette.isDark
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
