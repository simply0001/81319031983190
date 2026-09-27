package com.pocketpass.app.mii.renderer

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pocketpass.app.mii.MiiEditorCamera
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiRendererCommand
import com.pocketpass.app.mii.MiiRendererSaveArtifact
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun MiiEditorRenderSurface(
    editorController: MiiEditorController,
    modifier: Modifier = Modifier,
    initialCanonicalBase64: String? = null,
) {
    val renderer = rememberMiiRenderController()
    val appContext = LocalContext.current.applicationContext

    BindMiiEditorRenderer(
        editorController = editorController,
        renderer = renderer,
        appContext = appContext,
    )
    MiiRenderSurface(
        controller = renderer,
        modifier = modifier,
        initialCanonicalBase64 = initialCanonicalBase64
            ?: MiiRenderController.DEFAULT_MII_BASE64,
    )
}

@Composable
private fun BindMiiEditorRenderer(
    editorController: MiiEditorController,
    renderer: MiiRenderController,
    appContext: Context,
) {
    LaunchedEffect(editorController, renderer, appContext) {
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
                                val encodedMii = renderer
                                    .applyAppearance(command.appearance)
                                    .decodeCanonicalMii()
                                val portrait = renderer.exportPortrait(
                                    destination = newPortraitFile(appContext),
                                )
                                editorController.dispatch(
                                    MiiEditorEvent.RendererSaveReady(
                                        requestId = command.requestId,
                                        artifact = MiiRendererSaveArtifact(
                                            encodedMii = encodedMii,
                                            portraitFilePath = portrait.absolutePath,
                                            rendererVersion = MiiRenderController.RENDERER_VERSION,
                                        ),
                                    ),
                                )
                            }
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        when (command) {
                            is MiiRendererCommand.CaptureForSave -> {
                                editorController.dispatch(
                                    MiiEditorEvent.RendererSaveFailed(
                                        requestId = command.requestId,
                                        message = "Your Piip portrait could not be rendered.",
                                    ),
                                )
                            }

                            else -> editorController.dispatch(
                                MiiEditorEvent.RendererError("The Piip preview could not be updated."),
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
                                        MiiRenderController.RENDERER_VERSION,
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

private fun String.decodeCanonicalMii(): ByteArray =
    Base64.decode(this, Base64.DEFAULT)

private fun newPortraitFile(context: Context): File {
    val directory = File(context.filesDir, "mii/portraits")
    check(directory.exists() || directory.mkdirs()) {
        "Unable to create the private Mii portrait directory"
    }
    return File(directory, "portrait-${UUID.randomUUID()}.png")
}
