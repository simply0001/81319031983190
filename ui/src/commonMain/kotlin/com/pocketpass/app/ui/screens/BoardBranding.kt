package com.pocketpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.pocketpass.app.boards.Board
import com.pocketpass.app.domain.model.ChatBubbleColour
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.components.FigmaAsset

@Composable
internal fun BoardBranding(m: DesignMetrics, board: Board, assets: Map<String, ByteArray>, cover: Boolean = false, coverHeight: Float = 340f) {
    val colour = ChatBubbleColour.entries.firstOrNull { it.name.equals(board.accent, ignoreCase = true) } ?: ChatBubbleColour.Blue
    val palette = chatBubblePalette(colour)
    val drawing = if(cover) board.coverDrawing else board.iconDrawing
    val id = if(cover) board.coverAssetId else board.iconAssetId
    if(cover && drawing == null && assets[id] == null) return
    Box(if(cover) Modifier.fillMaxWidth().height(m.dp(coverHeight)) else Modifier.width(m.dp(140f)).aspectRatio(4f/3f)
        .clip(RoundedCornerShape(m.dp(24f))).background(Brush.verticalGradient(*palette.fill)), contentAlignment = Alignment.Center) {
    val size = if(cover) Modifier.fillMaxHeight().aspectRatio(4f/3f) else Modifier.fillMaxSize()
    if(drawing != null) BoardDrawingCanvas(drawing, size)
    else id?.let { assets[it] }?.let { bytes ->
        AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(bytes)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
            contentDescription = if(cover) "Board cover" else "Board icon", modifier = size, contentScale = ContentScale.Fit)
    } ?: FigmaAsset(Assets.SettingsSocial, Modifier.size(m.dp(100f)), contentScale = ContentScale.Fit)
    }
}
