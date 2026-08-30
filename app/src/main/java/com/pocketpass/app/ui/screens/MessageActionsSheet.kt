package com.pocketpass.app.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.domain.model.IMAGE_MESSAGE_PLACEHOLDER_BODY
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import kotlinx.coroutines.launch

private const val SHEET_HEIGHT = 640f
private const val SHEET_TOP = 1080f - SHEET_HEIGHT
private const val SHEET_RADIUS = 96f
private const val TILE_RADIUS = 64f
private val SheetInk = Color(0xFF22677C)
private val SheetInkSoft = Color(0xFF5591A4)
private val SheetBorder = listOf(Color(0xFF76B3C1), Color(0xFF5A96A9), Color(0xFF22677C))
private val PreviewFill = listOf(Color(0xFF5EA3ED), Color(0xFF0073FF))
private val PreviewBorder = Color(0xFF4B5FC2)
private val EditFill = listOf(Color(0xFF7CC4D2), Color(0xFF2E7F94))
private val EditBorder = Color(0xFF22677C)
private val DeleteFill = listOf(Color(0xFFF07A6E), Color(0xFFC23A32))
private val DeleteBorder = Color(0xFF8E2A24)

@Composable
internal fun MessageActionsSheet(
    metrics: DesignMetrics,
    message: Message,
    visible: Boolean,
    dispatch: (PocketPassEvent) -> Unit,
    onHidden: () -> Unit,
) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(Unit) { focus?.focus("message_action_edit", reveal = false) }
    val entrance = remember { Animatable(SHEET_HEIGHT) }
    val scrim = remember { Animatable(0f) }
    val latestOnHidden = rememberUpdatedState(onHidden)
    LaunchedEffect(visible) {
        when {
            !ValueAnimator.areAnimatorsEnabled() -> {
                entrance.snapTo(if (visible) 0f else SHEET_HEIGHT)
                scrim.snapTo(if (visible) 1f else 0f)
            }

            visible -> {
                launch { scrim.animateTo(1f, tween(180)) }
                entrance.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 360f,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }

            else -> {
                launch { scrim.animateTo(0f, tween(200)) }
                entrance.animateTo(
                    targetValue = SHEET_HEIGHT,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            }
        }
        if (!visible) latestOnHidden.value()
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .graphicsLayer { alpha = scrim.value }
            .background(pocketPalette.scrim)
            .testTag("message_actions_overlay")
            .clickable(
                enabled = visible,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseMessageActions) },
    )
    val sheetShape = RoundedCornerShape(
        topStart = metrics.dp(SHEET_RADIUS),
        topEnd = metrics.dp(SHEET_RADIUS),
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, SHEET_TOP + 18f, 1240f, SHEET_HEIGHT)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, SHEET_RADIUS),
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, SHEET_TOP, 1240f, SHEET_HEIGHT)
            .graphicsLayer { translationY = entrance.value }
            .clip(sheetShape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFD1EDFB))),
                ),
                metrics.dp(18f),
                Brush.verticalGradient(SheetBorder),
                sheetShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("message_actions_panel"),
    ) {
        Box(
            Modifier
                .designBounds(metrics, 560f, 26f, 120f, 12f)
                .clip(RoundedCornerShape(metrics.dp(6f)))
                .background(SheetInkSoft.copy(alpha = 0.55f)),
        )
        Text(
            text = "Your message",
            modifier = Modifier.designBounds(metrics, 80f, 60f, 600f, 56f),
            color = pocketPalette.ink(SheetInk),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(46f),
            maxLines = 1,
        )
        Text(
            text = "Sent ${relativeTime(message.createdAt).lowercase()}",
            modifier = Modifier.designBounds(metrics, 640f, 70f, 520f, 40f),
            color = pocketPalette.ink(SheetInkSoft),
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        MessagePreviewBubble(
            metrics = metrics,
            message = message,
            modifier = Modifier.designBounds(metrics, 80f, 134f, 1080f, 176f),
        )
        MessageActionTile(
            metrics = metrics,
            modifier = Modifier.designBounds(metrics, 80f, 346f, 520f, 208f),
            icon = Icons.Rounded.Edit,
            title = "Edit",
            subtitle = "Change the text",
            fill = EditFill,
            border = EditBorder,
            tag = "message_action_edit",
            enabled = visible,
        ) { dispatch(PocketPassEvent.EditSelectedMessage) }
        MessageActionTile(
            metrics = metrics,
            modifier = Modifier.designBounds(metrics, 640f, 346f, 520f, 208f),
            icon = Icons.Rounded.Delete,
            title = "Delete",
            subtitle = "Remove for everyone",
            fill = DeleteFill,
            border = DeleteBorder,
            tag = "message_action_delete",
            enabled = visible,
        ) { dispatch(PocketPassEvent.DeleteSelectedMessage) }
        Text(
            text = "B  ·  back",
            modifier = Modifier.designBounds(metrics, 80f, 580f, 1080f, 36f),
            color = pocketPalette.ink(SheetInkSoft),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(26f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun MessagePreviewBubble(
    metrics: DesignMetrics,
    message: Message,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(metrics.dp(64f))
    val caption = message.body.takeUnless { it == IMAGE_MESSAGE_PLACEHOLDER_BODY }.orEmpty()
    val preview = when {
        message.attachment == null -> message.body
        caption.isEmpty() -> "📷 Photo"
        else -> "📷 $caption"
    }
    Box(
        modifier = modifier
            .clip(shape)
            .pocketFrame(Brush.verticalGradient(PreviewFill), metrics.dp(12f), PreviewBorder, shape)
            .padding(horizontal = metrics.dp(44f), vertical = metrics.dp(24f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = preview,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(38f),
            lineHeight = metrics.sp(46f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageActionTile(
    metrics: DesignMetrics,
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    fill: List<Color>,
    border: Color,
    tag: String,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(TILE_RADIUS))
    Row(
        modifier = modifier
            .clip(shape)
            .pocketFrame(Brush.verticalGradient(fill), metrics.dp(16f), border, shape)
            .testTag(tag)
            .then(
                if (enabled) {
                    Modifier.controllerTarget(
                        tag,
                        layer = 20,
                        cornerRadius = TILE_RADIUS,
                        onActivate = onActivate,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onActivate,
            )
            .padding(horizontal = metrics.dp(38f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.dp(104f))
                .clip(RoundedCornerShape(metrics.dp(52f)))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(metrics.dp(62f)),
            )
        }
        Spacer(Modifier.width(metrics.dp(30f)))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(48f),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.86f),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(27f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MessageEditingChip(
    metrics: DesignMetrics,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(metrics.dp(26f))
    Row(
        modifier = modifier
            .clip(shape)
            .background(pocketPalette.ink(SheetInk).copy(alpha = 0.14f))
            .padding(horizontal = metrics.dp(22f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            tint = pocketPalette.ink(SheetInk),
            modifier = Modifier.size(metrics.dp(30f)),
        )
        Spacer(Modifier.width(metrics.dp(14f)))
        Text(
            text = "Editing message  ·  B to cancel",
            color = pocketPalette.ink(SheetInk),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(27f),
            maxLines = 1,
        )
    }
}
