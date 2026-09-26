package com.pocketpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.ui.components.Text
import okio.Path.Companion.toPath

internal expect fun messageImageDialogProperties(): DialogProperties

@Composable
internal fun MessageImageViewer(attachment: MessageAttachment, onDismiss: () -> Unit) {
    var scale by remember(attachment) { mutableFloatStateOf(1f) }
    var offset by remember(attachment) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(attachment) { mutableStateOf(false) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val limitX = viewport.width * (scale - 1f) / 2f
        val limitY = viewport.height * (scale - 1f) / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
    }
    Dialog(onDismissRequest = onDismiss, properties = messageImageDialogProperties()) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("message_image_viewer")) {
            AsyncImage(
                model = attachment.localPath?.toPath() ?: attachment.remotePath,
                contentDescription = if (attachment.mimeType == "image/gif") "Animated message image" else "Message image",
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                onSuccess = { failed = false },
                modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it }
                    .transformable(transform)
                    .pointerInput(attachment) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            if (failed) Text("Couldn’t load this image.", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            Box(
                Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
                    .size(48.dp).background(Color(0xCC303030), CircleShape)
                    .testTag("close_message_image")
                    .semantics { contentDescription = "Close image" }
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = Color.White, fontSize = 32.sp) }
        }
    }
}
