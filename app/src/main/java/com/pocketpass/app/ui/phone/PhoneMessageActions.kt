package com.pocketpass.app.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.domain.model.IMAGE_MESSAGE_PLACEHOLDER_BODY
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.screens.relativeTime
import com.pocketpass.app.ui.theme.pocketPalette

private val SheetInk = Color(0xFF22677C)
private val SheetInkSoft = Color(0xFF5591A4)
private val SheetBorder = listOf(Color(0xFF76B3C1), Color(0xFF5A96A9), Color(0xFF22677C))
private val PreviewFill = listOf(Color(0xFF5EA3ED), Color(0xFF0073FF))
private val PreviewBorder = Color(0xFF4B5FC2)
private val EditFill = listOf(Color(0xFF7CC4D2), Color(0xFF2E7F94))
private val EditBorder = Color(0xFF22677C)
private val DeleteFill = listOf(Color(0xFFF07A6E), Color(0xFFC23A32))
private val DeleteBorder = Color(0xFF8E2A24)
private val CancelInk = Color(0xFF9D3131)

@Composable
internal fun PhoneMessageActionsSheet(
    metrics: DesignMetrics,
    visible: Boolean,
    message: Message?,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val retained = remember { mutableStateOf(message) }
    if (message != null) retained.value = message
    val shown = retained.value ?: return
    val insets = LocalPhoneInsets.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pocketPalette.scrim)
                    .testTag("message_actions_scrim")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { dispatch(PocketPassEvent.CloseMessageActions) },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 360f)) { it } +
                fadeIn(tween(160)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(140)),
        ) {
            val shape = RoundedCornerShape(
                topStart = metrics.dp(96f),
                topEnd = metrics.dp(96f),
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .pocketFrame(
                        Brush.verticalGradient(
                            listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFD1EDFB))),
                        ),
                        metrics.dp(15f),
                        Brush.verticalGradient(SheetBorder),
                        shape,
                    )
                    .testTag("message_actions_panel")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(
                        start = metrics.dp(insets.start + 60f),
                        end = metrics.dp(insets.end + 60f),
                        top = metrics.dp(26f),
                        bottom = metrics.dp(insets.bottom + 48f),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(metrics.dp(120f))
                        .height(metrics.dp(12f))
                        .clip(RoundedCornerShape(metrics.dp(6f)))
                        .background(SheetInkSoft.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.height(metrics.dp(30f)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Your message",
                        color = pocketPalette.ink(SheetInk),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(46f),
                        maxLines = 1,
                    )
                    Text(
                        text = "Sent ${relativeTime(shown.createdAt).lowercase()}",
                        color = pocketPalette.ink(SheetInkSoft),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Medium,
                        fontSize = metrics.sp(30f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(metrics.dp(24f)))
                PhoneMessagePreview(metrics, shown)
                Spacer(Modifier.height(metrics.dp(34f)))
                PhoneMessageActionRow(
                    metrics = metrics,
                    icon = Icons.Rounded.Edit,
                    title = "Edit",
                    subtitle = "Change the text",
                    fill = EditFill,
                    border = EditBorder,
                    tag = "message_action_edit",
                ) { dispatch(PocketPassEvent.EditSelectedMessage) }
                Spacer(Modifier.height(metrics.dp(20f)))
                PhoneMessageActionRow(
                    metrics = metrics,
                    icon = Icons.Rounded.Delete,
                    title = "Delete",
                    subtitle = "Remove for everyone",
                    fill = DeleteFill,
                    border = DeleteBorder,
                    tag = "message_action_delete",
                ) { dispatch(PocketPassEvent.DeleteSelectedMessage) }
            }
        }
    }
}

@Composable
private fun PhoneMessagePreview(metrics: DesignMetrics, message: Message) {
    val shape = RoundedCornerShape(metrics.dp(64f))
    val caption = message.body.takeUnless { it == IMAGE_MESSAGE_PLACEHOLDER_BODY }.orEmpty()
    val preview = when {
        message.attachment == null -> message.body
        caption.isEmpty() -> "📷 Photo"
        else -> "📷 $caption"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .pocketFrame(Brush.verticalGradient(PreviewFill), metrics.dp(12f), PreviewBorder, shape)
            .padding(horizontal = metrics.dp(44f), vertical = metrics.dp(26f)),
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
private fun PhoneMessageActionRow(
    metrics: DesignMetrics,
    icon: ImageVector,
    title: String,
    subtitle: String,
    fill: List<Color>,
    border: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(170f))
            .clip(shape)
            .pocketFrame(Brush.verticalGradient(fill), metrics.dp(14f), border, shape)
            .testTag(tag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = metrics.dp(40f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.dp(96f))
                .clip(RoundedCornerShape(metrics.dp(48f)))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(metrics.dp(56f)),
            )
        }
        Spacer(Modifier.width(metrics.dp(28f)))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.86f),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(26f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(metrics.dp(56f)),
        )
    }
}

@Composable
internal fun PhoneMessageEditingChip(
    metrics: DesignMetrics,
    onCancel: () -> Unit,
) {
    val chipShape = RoundedCornerShape(metrics.dp(26f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = metrics.dp(50f), end = metrics.dp(50f), bottom = metrics.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(chipShape)
                .background(pocketPalette.ink(SheetInk).copy(alpha = 0.14f))
                .padding(horizontal = metrics.dp(22f), vertical = metrics.dp(8f)),
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
                text = "Editing message",
                color = pocketPalette.ink(SheetInk),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(27f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Cancel",
            modifier = Modifier
                .testTag("message_edit_cancel")
                .clip(chipShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                )
                .padding(horizontal = metrics.dp(22f), vertical = metrics.dp(8f)),
            color = pocketPalette.ink(CancelInk),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(27f),
            maxLines = 1,
        )
    }
}
