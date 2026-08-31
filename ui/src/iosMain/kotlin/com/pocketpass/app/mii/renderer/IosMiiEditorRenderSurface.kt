package com.pocketpass.app.mii.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.pocketpass.app.mii.MiiEditorCamera
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiRendererCommand
import com.pocketpass.app.mii.MiiRendererSaveArtifact
import com.pocketpass.app.mii.iosPortraitsDirectory
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import platform.Foundation.NSUUID

@Composable
fun IosMiiEditorRenderSurface(
    editorController: MiiEditorController,
    initialCanonicalBase64: String?,
    modifier: Modifier = Modifier,
) {
    val renderer = IosMiiRenderController.shared
    val webView = remember(renderer) { renderer.createWebView() }

    DisposableEffect(webView, renderer) {
        renderer.attach(
            webView,
            initialCanonicalBase64 ?: IosMiiRenderController.DEFAULT_MII_BASE64,
        )
        onDispose { renderer.detach(webView) }
    }

    BindMiiEditorRenderer(editorController, renderer)
    UIKitView(factory = { webView }, modifier = modifier)
}

@Composable
private fun BindMiiEditorRenderer(
    editorController: MiiEditorController,
    renderer: IosMiiRenderController,
) {
    LaunchedEffect(editorController, renderer) {
        coroutineScope {
            val queue = Channel<MiiRendererCommand>(Channel.UNLIMITED)
            launch(start = CoroutineStart.UNDISPATCHED) {
                editorController.rendererCommands.collect { queue.send(it) }
            }
            launch {
                suspend fun handle(command: MiiRendererCommand) {
                    runCatching {
                        when (command) {
                            is MiiRendererCommand.LoadAppearance -> {
                                renderer.applyAppearance(command.appearance)
                                editorController.dispatch(
                                    MiiEditorEvent.RendererAppearanceLoaded,
                                )
                            }

                            is MiiRendererCommand.ApplyAppearance -> {
                                renderer.applyAppearance(command.appearance)
                            }

                            is MiiRendererCommand.SetCamera -> {
                                renderer.setCamera(
                                    camera = command.camera.toRenderCamera(),
                                    transitionMillis = command.transitionMillis,
                                )
                            }

                            is MiiRendererCommand.CaptureForSave -> {
                                val encodedMii = Base64.decode(
                                    renderer.applyAppearance(command.appearance),
                                )
                                val portraitPath = renderer.exportPortraitToFile(
                                    destinationPath =
                                    "${iosPortraitsDirectory()}/portrait-${NSUUID().UUIDString}.png",
                                )
                                editorController.dispatch(
                                    MiiEditorEvent.RendererSaveReady(
                                        requestId = command.requestId,
                                        artifact = MiiRendererSaveArtifact(
                                            encodedMii = encodedMii,
                                            portraitFilePath = portraitPath,
                                            rendererVersion =
                                            IosMiiRenderController.RENDERER_VERSION,
                                        ),
                                    ),
                                )
                            }
                        }
                    }.onFailure { error ->
                        when (command) {
                            is MiiRendererCommand.CaptureForSave -> {
                                editorController.dispatch(
                                    MiiEditorEvent.RendererSaveFailed(
                                        requestId = command.requestId,
                                        message = "Your Mii portrait could not be rendered.",
                                    ),
                                )
                            }

                            else -> editorController.dispatch(
                                MiiEditorEvent.RendererError(
                                    error.message
                                        ?.takeIf(String::isNotBlank)
                                        ?: "The Mii preview could not be updated.",
                                ),
                            )
                        }
                    }
                }

                while (true) {
                    var command = queue.receive()
                    var following: MiiRendererCommand? = null
                    while (command is MiiRendererCommand.ApplyAppearance) {
                        val queued = queue.tryReceive().getOrNull() ?: break
                        if (queued is MiiRendererCommand.ApplyAppearance) {
                            command = queued
                        } else {
                            following = queued
                            break
                        }
                    }
                    handle(command)
                    following?.let { handle(it) }
                }
            }

            launch {
                var readyReported = false
                renderer.status.collect { status ->
                    when (status) {
                        MiiRenderStatus.Detached,
                        MiiRenderStatus.Loading,
                        -> readyReported = false

                        is MiiRenderStatus.Ready -> {
                            if (!readyReported) {
                                readyReported = true
                                editorController.dispatch(
                                    MiiEditorEvent.RendererReady(
                                        IosMiiRenderController.RENDERER_VERSION,
                                    ),
                                )
                            }
                        }

                        is MiiRenderStatus.Error -> {
                            readyReported = false
                            editorController.dispatch(
                                MiiEditorEvent.RendererError(status.message),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MiiEditorCamera.toRenderCamera(): MiiRenderCamera = when (this) {
    MiiEditorCamera.WholeHead -> MiiRenderCamera.WholeHead
    MiiEditorCamera.FullBody -> MiiRenderCamera.FullBody
}
