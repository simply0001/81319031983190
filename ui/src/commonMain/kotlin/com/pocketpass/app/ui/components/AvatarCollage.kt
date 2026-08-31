package com.pocketpass.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.screens.DynamicAvatar

internal data class CollageSlot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val isQuadrant: Boolean
        get() = width < 1f && height < 1f
}

internal fun collageSlots(count: Int): List<CollageSlot> = when (count.coerceIn(0, MAX_COLLAGE_TILES)) {
    0, 1 -> listOf(CollageSlot(0f, 0f, 1f, 1f))
    2 -> listOf(CollageSlot(0f, 0f, 0.5f, 1f), CollageSlot(0.5f, 0f, 0.5f, 1f))
    3 -> listOf(
        CollageSlot(0f, 0f, 0.5f, 1f),
        CollageSlot(0.5f, 0f, 0.5f, 0.5f),
        CollageSlot(0.5f, 0.5f, 0.5f, 0.5f),
    )
    else -> listOf(
        CollageSlot(0f, 0f, 0.5f, 0.5f),
        CollageSlot(0.5f, 0f, 0.5f, 0.5f),
        CollageSlot(0f, 0.5f, 0.5f, 0.5f),
        CollageSlot(0.5f, 0.5f, 0.5f, 0.5f),
    )
}

@Composable
fun AvatarCollage(
    metrics: DesignMetrics,
    members: List<ConversationMember>,
    size: Float,
    initialColor: Color,
    tileFill: Color,
    divider: Color,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val shown = members.take(MAX_COLLAGE_TILES)
    val slots = collageSlots(shown.size)
    Box(
        modifier = modifier
            .requiredSize(metrics.dp(size))
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .drawWithContent {
                drawContent()
                val stroke = metrics.dp(COLLAGE_DIVIDER).toPx()
                val half = this.size.width / 2f
                val full = this.size.width
                if (shown.size >= 2) {
                    drawLine(divider, Offset(half, 0f), Offset(half, full), stroke)
                }
                if (shown.size == 3) {
                    drawLine(divider, Offset(half, half), Offset(full, half), stroke)
                }
                if (shown.size >= 4) {
                    drawLine(divider, Offset(0f, half), Offset(full, half), stroke)
                }
            },
    ) {
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .designBounds(metrics, 0f, 0f, size, size)
                    .background(tileFill)
                    .testTag("collage_tile_0"),
                contentAlignment = Alignment.Center,
            ) {
                CollageInitial(metrics, "?", initialColor, size * 0.46f)
            }
        }
        shown.forEachIndexed { index, member ->
            val slot = slots[index]
            val avatarSize = if (slot.isQuadrant) size / 2f else size
            Box(
                modifier = Modifier
                    .designBounds(
                        metrics,
                        slot.x * size,
                        slot.y * size,
                        slot.width * size,
                        slot.height * size,
                    )
                    .clipToBounds()
                    .background(tileFill)
                    .testTag("collage_tile_$index"),
                contentAlignment = Alignment.Center,
            ) {
                CollageInitial(
                    metrics = metrics,
                    initial = member.displayName.trim().firstOrNull()?.uppercase() ?: "?",
                    color = initialColor,
                    fontSize = avatarSize * 0.46f,
                )
                DynamicAvatar(
                    avatar = member.avatar,
                    fallbackResource = null,
                    modifier = Modifier.requiredSize(metrics.dp(avatarSize)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun CollageInitial(
    metrics: DesignMetrics,
    initial: String,
    color: Color,
    fontSize: Float,
) {
    Text(
        text = initial,
        color = color,
        fontFamily = Rubik,
        fontWeight = FontWeight.Black,
        fontSize = metrics.sp(fontSize),
        maxLines = 1,
    )
}

internal const val MAX_COLLAGE_TILES = 4
private const val COLLAGE_DIVIDER = 4f
