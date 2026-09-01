package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.mii.renderer.IosMiiEditorRenderSurface
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.audio.backgroundMusicTrack
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.state.IosAppContainer
import com.pocketpass.app.state.IosBackgroundRefresh
import com.pocketpass.app.state.IosStatusFeed
import com.pocketpass.app.state.PocketPassStore
import com.pocketpass.app.widget.IosWidgetReload
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.PocketPassTheme
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

private val container by lazy { IosAppContainer() }
private val store by lazy {
    PocketPassStore(
        container = container,
        statusFeed = IosStatusFeed(),
        scope = container.applicationScope,
    )
}

private val backgroundRefresh by lazy { IosBackgroundRefresh(container) }

private fun bundleVersionName(): String =
    NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String
        ?: ""

// Called first thing from the Swift AppDelegate: BGTaskScheduler handlers
// must be registered before didFinishLaunching returns.
fun PhoneAppDidLaunch() {
    backgroundRefresh.register()
    backgroundRefresh.schedule()
}

// The iOS application's root, called from the Swift AppDelegate.
fun PhoneAppViewController(): UIViewController = ComposeUIViewController {
    IosPhoneApp()
}

// Called from the Swift AppDelegate when the app is opened through its URL
// scheme. Only the sign-in callback carries anything to act on; widget taps
// (pocketpass://home) just bring the app forward.
fun PhoneAppHandleUrl(url: String) {
    if (url.startsWith(AUTH_CALLBACK_PREFIX, ignoreCase = true)) {
        store.handleAuthCallback(url)
    }
}

// WidgetKit is Swift-only, so the AppDelegate registers the reload call here.
fun PhoneAppSetWidgetReloader(reloader: () -> Unit) {
    IosWidgetReload.handler = reloader
}

private const val AUTH_CALLBACK_PREFIX = "pocketpass://auth/callback"

@Composable
private fun IosPhoneApp() {
    val state by store.state.collectAsState()
    LaunchedEffect(Unit) {
        snapshotFlow {
            val current = store.state.value
            backgroundMusicTrack(current) to current.soundLevel
        }.collect { (track, level) ->
            container.backgroundMusic.update(track, level)
        }
    }
    CompositionLocalProvider(
        LocalSoundEffects provides container.soundEffects,
        LocalAppVersionName provides bundleVersionName(),
        LocalMiiRenderSurface provides { controller, initialCanonicalBase64, modifier ->
            IosMiiEditorRenderSurface(controller, initialCanonicalBase64, modifier)
        },
    ) {
        PocketPassTheme(state.themeMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    // An edge swipe stands in for Android's system back gesture.
                    .pointerInput(Unit) {
                        val edge = 24.dp.toPx()
                        val trigger = 60.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x > edge) return@awaitEachGesture
                            var total = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                total += change.positionChange().x
                                if (total > trigger) {
                                    if (store.state.value.hasDismissableLayer()) {
                                        store.dispatch(PocketPassEvent.Back)
                                    }
                                    change.consume()
                                    break
                                }
                            }
                        }
                    },
            ) {
                PhoneSurface { metrics ->
                    PhoneRoot(
                        metrics = metrics,
                        state = state,
                        dispatch = store::dispatch,
                        miiEditorController = container.miiEditor,
                        extensions = PocketPassExtensions.None,
                    )
                }
            }
        }
    }
}
