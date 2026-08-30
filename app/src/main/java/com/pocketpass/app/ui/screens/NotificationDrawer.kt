package com.pocketpass.app.ui.screens

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.NotificationKind
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.notificationMotion
import com.pocketpass.app.ui.components.rememberNotificationListMotion
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.math.roundToInt

private const val NOTIFICATION_OVERLAY_WIDTH = 928f
private const val NOTIFICATION_BACKDROP_WHITE_STOP = 0.69231f
private const val NOTIFICATION_COLUMN_X = 330f
private const val NOTIFICATION_LIST_X = 40f
private const val NOTIFICATION_LIST_WIDTH = 518f
private const val NOTIFICATION_CARD_HEIGHT = 169f
private const val FRIEND_REQUEST_CARD_HEIGHT = 290f
private const val NOTIFICATION_CARD_SHADOW = 14f
private const val ACTOR_TEXT_WIDTH = 333f
private const val ACTOR_TEXT_WIDTH_WITH_BADGE = 229f
private const val TITLE_TEXT_WIDTH = 430f
private const val TITLE_TEXT_WIDTH_WITH_BADGE = 351f
private const val UNREAD_BADGE_X = 402f
private const val UNREAD_BADGE_Y = 22f
private const val UNREAD_BADGE_WIDTH = 76f
private const val UNREAD_BADGE_HEIGHT = 34f
private const val NOTIFICATION_FOCUS_LAYER = 10
private const val CLEAR_ALL_FOCUS_TAG = "clear_all_notifications"

internal val PocketPassNotification.announcesNewFriend: Boolean
    get() = kind == NotificationKind.FriendAccepted ||
        (
            kind == NotificationKind.FriendRequest &&
                friendRequestStatus == FriendRequestNotificationStatus.Accepted
            )

private val AcceptFill = Brush.verticalGradient(
    colorStops = arrayOf(
        0.19231f to Color(0xFF5CE257),
        0.50962f to Color(0xFF5EED60),
        0.55288f to Color(0xFF57E257),
        1f to Color(0xFF29BC2B),
    ),
)

private val DeclineFill = Brush.verticalGradient(
    colorStops = arrayOf(
        0.19231f to Color(0xFFE25757),
        0.50962f to Color(0xFFED5E5E),
        0.55288f to Color(0xFFE25757),
        1f to Color(0xFFBC2929),
    ),
)

@Composable
fun NotificationDrawer(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    visible: Boolean,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val slide = remember { Animatable(NOTIFICATION_OVERLAY_WIDTH) }
    LaunchedEffect(visible) {
        slide.animateTo(
            targetValue = if (visible) 0f else NOTIFICATION_OVERLAY_WIDTH,
            animationSpec = tween(
                durationMillis = if (visible) 380 else 300,
                easing = if (visible) FastOutSlowInEasing else FastOutLinearInEasing,
            ),
        )
    }
    val dismissed = remember(visible) {
        derivedStateOf { !visible && slide.value >= NOTIFICATION_OVERLAY_WIDTH - 0.5f }
    }
    if (dismissed.value) return

    val focus = LocalControllerFocus.current
    LaunchedEffect(visible) {
        if (visible) {
            val first = state.notifications.firstOrNull()?.let { "notification_${it.id.value}" }
            focus?.focus(first ?: CLEAR_ALL_FOCUS_TAG, reveal = false)
        }
    }
    DisposableEffect(focus) {
        onDispose { focus?.focus("notifications", reveal = false) }
    }

    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 0f, 0f, 992f, 1080f, DesignAnchor.Start, DesignAnchor.Stretch)
            .testTag("notification_drawer_outside")
            .clickable(
                enabled = visible,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseFriendsOverlay) },
    )

    DesignBox(
        metrics = metrics,
        x = 992f,
        y = 0.5f,
        width = NOTIFICATION_OVERLAY_WIDTH,
        height = 1080f,
        vertical = DesignAnchor.Stretch,
        modifier = Modifier
            .offset { IntOffset(slide.value.roundToInt(), 0) }
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        NOTIFICATION_BACKDROP_WHITE_STOP to palette.chrome,
                        1f to palette.chrome,
                    ),
                ),
            )
            .testTag("notification_drawer"),
    ) {
        CompositionLocalProvider(LocalControllerFocus provides focus.takeIf { visible }) {
            NotificationColumn(
                metrics = metrics,
                state = state,
                dispatch = dispatch,
            )
        }
    }
}

@Composable
private fun NotificationColumn(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, NOTIFICATION_COLUMN_X, 0f, 598f, 1080f)
            .controllerFocusBarrier("notification_drawer", layer = NOTIFICATION_FOCUS_LAYER),
    ) {
        Text(
            text = "Notifications",
            modifier = Modifier.designBounds(metrics, 40f, 40f, 518f, 83f),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Right,
            maxLines = 1,
        )

        val scrollState = rememberScrollState()
        val motion = rememberNotificationListMotion(state.notifications)
        val shown = motion.shown(state.notifications)
        if (shown.isEmpty()) {
            EmptyNotificationCard(metrics)
        } else {
            Column(
                modifier = Modifier
                    .designBounds(metrics, NOTIFICATION_LIST_X, 157f, NOTIFICATION_LIST_WIDTH, 695f)
                    .verticalScroll(scrollState),
            ) {
                shown.forEach { notification ->
                    key(notification.id.value) {
                        Box(Modifier.notificationMotion(motion, notification.id.value, gap = metrics.dp(20f))) {
                            NotificationCard(
                                metrics = metrics,
                                notification = notification,
                                onOpen = {
                                    dispatch(PocketPassEvent.OpenNotification(notification.id.value))
                                },
                                onAccept = {
                                    dispatch(
                                        PocketPassEvent.RespondToNotificationFriendRequest(
                                            notificationId = notification.id.value,
                                            accept = true,
                                        ),
                                    )
                                },
                                onDecline = {
                                    dispatch(
                                        PocketPassEvent.RespondToNotificationFriendRequest(
                                            notificationId = notification.id.value,
                                            accept = false,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (shown.isNotEmpty()) {
            Box(
                Modifier
                    .designBounds(metrics, NOTIFICATION_LIST_X, 742f, NOTIFICATION_LIST_WIDTH, 110f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val whiteAtDrawerX = NOTIFICATION_BACKDROP_WHITE_STOP * NOTIFICATION_OVERLAY_WIDTH
                        val leftDrawerX = NOTIFICATION_COLUMN_X + NOTIFICATION_LIST_X
                        val leftAlpha = (leftDrawerX / whiteAtDrawerX).coerceIn(0f, 1f)
                        val fullWhiteFraction =
                            ((whiteAtDrawerX - leftDrawerX) / NOTIFICATION_LIST_WIDTH).coerceIn(0f, 1f)
                        val backdrop = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to palette.chrome.copy(alpha = leftAlpha),
                                fullWhiteFraction to palette.chrome,
                                1f to palette.chrome,
                            ),
                        )
                        val fade = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                1f to Color.Black,
                            ),
                        )
                        onDrawBehind {
                            if (scrollState.canScrollForward) {
                                drawRect(backdrop)
                                drawRect(fade, blendMode = BlendMode.DstIn)
                            }
                        }
                    },
            )
        }

        val canClear = !motion.clearing && shown.any(PocketPassNotification::canDelete)
        val clearShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            Modifier
                .designBounds(metrics, 40f, 885.674f, 518f, 128f)
                .pocketShadow(metrics, 118f),
        )
        Box(
            modifier = Modifier
                .designBounds(metrics, 40f, 870f, 518f, 128f)
                .clip(clearShape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to palette.surface,
                            0.62606f to palette.surface,
                            1f to palette.surfaceLower,
                        ),
                    ),
                    metrics.dp(15f),
                    palette.borderGrey,
                    clearShape,
                )
                .testTag(CLEAR_ALL_FOCUS_TAG)
                .then(
                    if (canClear) {
                        Modifier.controllerTarget(
                            CLEAR_ALL_FOCUS_TAG,
                            layer = NOTIFICATION_FOCUS_LAYER,
                            cornerRadius = 118f,
                        ) { motion.clearAll(shown) { dispatch(PocketPassEvent.ClearAllNotifications) } }
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    enabled = canClear,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { motion.clearAll(shown) { dispatch(PocketPassEvent.ClearAllNotifications) } },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Clear All",
                color = if (canClear) palette.textPrimary else palette.textMuted,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(48f),
                maxLines = 1,
            )
        }

        state.notificationOperationError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 48f, 1008f, 502f, 62f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(24f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyNotificationCard(metrics: DesignMetrics) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, 40f, 157f, 518f, 183f)
            .graphicsLayer { alpha = reveal.value },
    ) {
        NotificationCardShell(metrics) {
            Text(
                text = "No notifications",
                modifier = Modifier.designBounds(metrics, 39f, 27f, 440f, 66f),
                color = pocketPalette.teal,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(50f),
                maxLines = 1,
            )
            Text(
                text = "You're all caught up.",
                modifier = Modifier.designBounds(metrics, 39f, 96f, 440f, 42f),
                color = pocketPalette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30.295f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NotificationCard(
    metrics: DesignMetrics,
    notification: PocketPassNotification,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    if (
        notification.kind == NotificationKind.FriendRequest &&
        notification.friendRequestStatus == FriendRequestNotificationStatus.Pending
    ) {
        FriendRequestNotificationCard(
            metrics = metrics,
            notification = notification,
            onOpen = onOpen,
            onAccept = onAccept,
            onDecline = onDecline,
        )
        return
    }

    if (notification.actor != null && notification.announcesNewFriend) {
        FriendAcceptedNotificationCard(
            metrics = metrics,
            notification = notification,
            onOpen = onOpen,
        )
        return
    }

    val displayTitle = if (notification.eventCount > 1) {
        "${notification.title} ×${notification.eventCount.coerceAtMost(99)}"
    } else {
        notification.title
    }
    val interaction = remember(notification.id) { MutableInteractionSource() }

    NotificationCardShell(
        metrics = metrics,
        modifier = Modifier
            .testTag("notification_${notification.id.value}")
            .controllerTarget(
                "notification_${notification.id.value}",
                layer = NOTIFICATION_FOCUS_LAYER,
                cornerRadius = 60f,
            ) { onOpen() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Text(
            text = displayTitle,
            modifier = Modifier.designBounds(
                metrics,
                39f,
                27f,
                if (notification.isUnread) TITLE_TEXT_WIDTH_WITH_BADGE else TITLE_TEXT_WIDTH,
                66f,
            ),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = notification.body,
            modifier = Modifier.designBounds(metrics, 39f, 96f, 430f, 42f),
            color = pocketPalette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30.295f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (notification.isUnread) {
            NotificationUnreadBadge(metrics)
        }
    }
}

@Composable
private fun FriendAcceptedNotificationCard(
    metrics: DesignMetrics,
    notification: PocketPassNotification,
    onOpen: () -> Unit,
) {
    val interaction = remember(notification.id) { MutableInteractionSource() }
    val actor = notification.actor
    val displayName = actor?.displayName?.trim()?.ifBlank { null } ?: notification.title

    NotificationCardShell(
        metrics = metrics,
        modifier = Modifier
            .testTag("notification_${notification.id.value}")
            .controllerTarget(
                "notification_${notification.id.value}",
                layer = NOTIFICATION_FOCUS_LAYER,
                cornerRadius = 60f,
            ) { onOpen() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            ),
    ) {
        NotificationActorHeader(
            metrics = metrics,
            actor = actor,
            displayName = displayName,
            body = "Is now your friend!",
            textWidth =
                if (notification.isUnread) ACTOR_TEXT_WIDTH_WITH_BADGE else ACTOR_TEXT_WIDTH,
        )
        if (notification.isUnread) {
            NotificationUnreadBadge(metrics)
        }
    }
}

@Composable
private fun NotificationUnreadBadge(metrics: DesignMetrics) {
    val shape = RoundedCornerShape(metrics.dp(UNREAD_BADGE_HEIGHT / 2f))
    Box(
        Modifier
            .designBounds(
                metrics,
                UNREAD_BADGE_X,
                UNREAD_BADGE_Y,
                UNREAD_BADGE_WIDTH,
                UNREAD_BADGE_HEIGHT,
            )
            .clip(shape)
            .background(Color(0xFF1FC1B3)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "NEW",
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(21f),
            maxLines = 1,
        )
    }
}

@Composable
private fun FriendRequestNotificationCard(
    metrics: DesignMetrics,
    notification: PocketPassNotification,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val interaction = remember(notification.id) { MutableInteractionSource() }
    val actor = notification.actor
    val displayName = actor?.displayName?.trim()?.ifBlank { null } ?: notification.title
    val cardFocusId = "notification_${notification.id.value}"
    val acceptFocusId = "accept_friend_request_${notification.id.value}"
    val declineFocusId = "decline_friend_request_${notification.id.value}"

    NotificationCardShell(
        metrics = metrics,
        modifier = Modifier
            .testTag(cardFocusId)
            .controllerTarget(
                cardFocusId,
                layer = NOTIFICATION_FOCUS_LAYER,
                cornerRadius = 60f,
                neighbors = mapOf(FocusDirection.Down to acceptFocusId),
            ) { onOpen() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            ),
        cardHeight = FRIEND_REQUEST_CARD_HEIGHT,
    ) {
        NotificationActorHeader(
            metrics = metrics,
            actor = actor,
            displayName = displayName,
            body = "Sent a friend request!",
            textWidth = ACTOR_TEXT_WIDTH,
        )
        FriendRequestActionButton(
            metrics = metrics,
            x = 24f,
            label = "Accept",
            labelX = 100.527f,
            labelWidth = 91f,
            icon = Assets.NotificationAccept,
            iconX = 39.2f,
            iconY = 27.506f,
            iconWidth = 35.8f,
            iconHeight = 25.988f,
            borderColor = Color(0xFF4FC24B),
            fill = AcceptFill,
            tag = "accept_friend_request",
            focusId = acceptFocusId,
            neighbors = mapOf(
                FocusDirection.Up to cardFocusId,
                FocusDirection.Right to declineFocusId,
            ),
            onClick = onAccept,
        )
        FriendRequestActionButton(
            metrics = metrics,
            x = 263.273f,
            label = "Decline",
            labelX = 94.917f,
            labelWidth = 95f,
            icon = Assets.NotificationDecline,
            iconX = 40.81f,
            iconY = 26.21f,
            iconWidth = 28.58f,
            iconHeight = 28.58f,
            borderColor = Color(0xFFC24B4B),
            fill = DeclineFill,
            tag = "decline_friend_request",
            focusId = declineFocusId,
            neighbors = mapOf(
                FocusDirection.Up to cardFocusId,
                FocusDirection.Left to acceptFocusId,
            ),
            onClick = onDecline,
        )
    }
}

@Composable
private fun NotificationActorHeader(
    metrics: DesignMetrics,
    actor: UserProfile?,
    displayName: String,
    body: String,
    textWidth: Float,
) {
    NotificationActorAvatar(
        metrics = metrics,
        actor = actor,
        displayName = displayName,
    )
    Text(
        text = displayName,
        modifier = Modifier.designBounds(metrics, 161f, 34f, textWidth, 65f),
        color = pocketPalette.teal,
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = body,
        modifier = Modifier.designBounds(metrics, 161f, 99f, textWidth, 36f),
        color = pocketPalette.tealSoft,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(30.295f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NotificationActorAvatar(
    metrics: DesignMetrics,
    actor: UserProfile?,
    displayName: String,
) {
    val shape = RoundedCornerShape(metrics.dp(35f))
    Box(
        modifier = Modifier
            .designBounds(metrics, 24f, 24f, 121f, 121f)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFBDF8CB)).copy(alpha = 0.72f)),
                ),
            )
            .pocketBorder(metrics.dp(5f), Color(0xFF4D8C9F).copy(alpha = 0.27f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.firstOrNull()?.uppercase() ?: "?",
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(55f),
            maxLines = 1,
        )
        val model = when (val avatar = actor?.avatar) {
            is AvatarReference.Remote -> avatar.url
            is AvatarReference.Bundled -> avatarResourceForKey(avatar.key)
            null -> null
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .requiredSize(metrics.dp(121f))
                    .clip(shape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun FriendRequestActionButton(
    metrics: DesignMetrics,
    x: Float,
    label: String,
    labelX: Float,
    labelWidth: Float,
    @RawRes icon: Int,
    iconX: Float,
    iconY: Float,
    iconWidth: Float,
    iconHeight: Float,
    borderColor: Color,
    fill: Brush,
    tag: String,
    focusId: String,
    neighbors: Map<FocusDirection, String>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(57.927f))
    Box(
        Modifier
            .designBounds(metrics, x, 190.891f, 230.727f, 81f)
            .pocketShadow(metrics, 57.927f, 0.11f, 6f),
    )
    Box(
        modifier = Modifier
            .designBounds(metrics, x, 185f, 230.727f, 81f)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(9.893f), borderColor, shape)
            .testTag(tag)
            .controllerTarget(
                focusId,
                layer = NOTIFICATION_FOCUS_LAYER,
                cornerRadius = 57.927f,
                neighbors = neighbors,
            ) { onClick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        FigmaAsset(
            resource = icon,
            modifier = Modifier.designBounds(
                metrics,
                iconX,
                iconY,
                iconWidth,
                iconHeight,
            ),
        )
        Text(
            text = label,
            modifier = Modifier.designBounds(metrics, labelX, 25f, labelWidth, 31f),
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(25.92f),
            maxLines = 1,
        )
    }
}

@Composable
private fun NotificationCardShell(
    metrics: DesignMetrics,
    modifier: Modifier = Modifier,
    cardHeight: Float = NOTIFICATION_CARD_HEIGHT,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    Box(
        modifier = Modifier.requiredSize(
            metrics.dp(518f),
            metrics.dp(cardHeight + NOTIFICATION_CARD_SHADOW),
        ),
    ) {
        Box(
            Modifier
                .designBounds(metrics, 0f, NOTIFICATION_CARD_SHADOW, 518f, cardHeight)
                .pocketShadow(metrics, 60f),
        )
        Box(
            modifier = modifier
                .requiredSize(metrics.dp(518f), metrics.dp(cardHeight))
                .clip(shape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to pocketPalette.surface,
                            0.39364f to pocketPalette.surface,
                            1f to pocketPalette.tint(Color(0xFFBDF8CB)),
                        ),
                    ),
                    metrics.dp(15f),
                    pocketPalette.tealBorder,
                    shape,
                ),
        ) {
            content()
        }
    }
}
