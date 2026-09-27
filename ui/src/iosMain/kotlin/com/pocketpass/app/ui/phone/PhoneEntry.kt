package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.media.IosImageAttachmentPicker
import com.pocketpass.app.mii.renderer.IosMiiEditorRenderSurface
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.audio.backgroundMusicTrack
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.state.IosAppContainer
import com.pocketpass.app.state.IosBackgroundRefresh
import com.pocketpass.app.state.IosStatusFeed
import com.pocketpass.app.state.PocketPassStore
import com.pocketpass.app.widget.IosWidgetReload
import com.pocketpass.app.ui.IosBackHandlers
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.PocketPassTheme
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import com.pocketpass.app.boards.BoardBrandingPicker
import com.pocketpass.app.media.ImageAttachmentPreparation

private val container by lazy { IosAppContainer() }
private val store by lazy {
    PocketPassStore(
        container = container,
        statusFeed = IosStatusFeed(),
        scope = container.applicationScope,
    )
}

private val backgroundRefresh by lazy { IosBackgroundRefresh(container) }

private var rootViewController: UIViewController? = null
private var boardImageRequest: BoardBrandingPicker.Request? = null
private val boardImagePicker by lazy {
    IosImageAttachmentPicker(onPicked = { source ->
        val request = boardImageRequest; boardImageRequest = null
        container.applicationScope.launch {
            try {
                when(val result = container.imageAttachmentPreparer.prepare(source)) {
                    is ImageAttachmentPreparation.Ready -> {
                        try { request?.complete(okio.FileSystem.SYSTEM.read(result.attachment.path.toPath()) { readByteArray() }) }
                        finally { container.imageAttachmentPreparer.discard(result.attachment.path) }
                    }
                    is ImageAttachmentPreparation.Failed -> request?.fail(result.message)
                }
            } catch(_: Exception) { request?.fail("This image could not be opened") }
            finally { container.imageAttachmentPreparer.discard(source) }
        }
    }, onFailed = { boardImageRequest?.fail("This image could not be opened"); boardImageRequest = null },
        onCancelled = { boardImageRequest?.complete(null); boardImageRequest = null })
}

private val imageAttachmentPicker by lazy {
    IosImageAttachmentPicker(
        onPicked = container::sendPickedImage,
        onFailed = container::reportImagePickFailure,
    )
}

private fun bundleVersionName(): String =
    NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String
        ?: ""

fun PhoneAppDidLaunch() {
    backgroundRefresh.register()
    backgroundRefresh.schedule()
}

fun PhoneAppViewController(): UIViewController = ComposeUIViewController {
    IosPhoneApp()
}.also { rootViewController = it }

fun PhoneAppHandleUrl(url: String) {
    if (url.startsWith(AUTH_CALLBACK_PREFIX, ignoreCase = true)) {
        store.handleAuthCallback(url)
    }
}

fun PhoneAppSetWidgetReloader(reloader: () -> Unit) {
    IosWidgetReload.handler = reloader
}

fun PhoneAppSetMessagePushHandler(handler: (String) -> Unit) {
    com.pocketpass.app.push.IosMessagePushBridge.handler = handler
}

fun PhoneAppMessagePushState(token: String, allowed: Boolean) {
    com.pocketpass.app.push.IosMessagePushBridge.update(token, allowed)
}

fun PhoneAppMessageNotificationTapped(data: Map<String, String>) {
    container.handleMessageNotification(data)
}

private const val AUTH_CALLBACK_PREFIX = "pocketpass://auth/callback"

@Composable
private fun IosPhoneApp() {
    val state by store.state.collectAsState()
    LaunchedEffect(Unit) {
        BoardBrandingPicker.requests.collect { request ->
            boardImageRequest = request
            rootViewController?.let(boardImagePicker::present) ?: request.complete(null)
        }
    }
    LaunchedEffect(Unit) {
        store.state
            .map { current -> backgroundMusicTrack(current) to current.soundLevel }
            .distinctUntilChanged()
            .collect { (track, level) ->
                container.backgroundMusic.update(track, level)
            }
    }
    LaunchedEffect(Unit) {
        container.messages.imageAttachmentRequested.collect {
            rootViewController?.let(imageAttachmentPicker::present)
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
                                    if (!IosBackHandlers.handleBack() && store.state.value.hasDismissableLayer()) {
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
                    )
                }
            }
        }
    }
}
