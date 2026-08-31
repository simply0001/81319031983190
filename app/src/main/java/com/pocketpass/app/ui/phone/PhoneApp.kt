package com.pocketpass.app.ui.phone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.MiiRenderSurfaceFromWebView
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
fun PhoneApp(
    viewModel: PocketPassViewModel,
    extensions: PocketPassExtensions = PocketPassExtensions.None,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalSoundEffects provides viewModel.soundEffects,
        LocalAppVersionName provides BuildConfig.VERSION_NAME,
        LocalMiiRenderSurface provides MiiRenderSurfaceFromWebView,
    ) {
        PocketPassTheme(state.themeMode) {
            PhoneSystemBars()
            BackHandler(enabled = state.hasDismissableLayer()) { viewModel.dispatch(PocketPassEvent.Back) }
            PhoneSurface { metrics ->
                PhoneRoot(
                    metrics = metrics,
                    state = state,
                    dispatch = viewModel::dispatch,
                    miiEditorController = viewModel.miiEditorController,
                    extensions = extensions,
                )
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
