package com.pocketpass.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.pocketpass.app.ui.components.NotificationListMotion
import com.pocketpass.app.ui.components.PinListToNewestNotification
import com.pocketpass.app.ui.components.notificationMotion
import com.pocketpass.app.ui.components.rememberNotificationListMotion
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.screens.announcesNewFriend
import com.pocketpass.app.ui.screens.avatarResourceForKey
import com.pocketpass.app.ui.screens.greenButtonBrush
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.screens.redButtonBrush
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun PhoneNotificationsPage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val motion = rememberNotificationListMotion(state.notifications)
    Column(
        Modifier
            .fillMaxSize()
            .testTag("notification_drawer")
            .padding(top = metrics.dp(insets.top + 24f), bottom = metrics.dp(insets.bottom + 40f)),
    ) {
        PhonePageHeader(
            metrics = metrics,
            title = "Notifications",
            subtitle = state.unreadNotificationCount.takeIf { it > 0 }?.let { "$it new" },
            backTag = "notifications_back",
            onBack = { dispatch(PocketPassEvent.CloseFriendsOverlay) },
        )
        PhoneNotificationList(
            metrics = metrics,
            state = state,
            motion = motion,
            dispatch = dispatch,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalPadding = PHONE_CONTENT_MARGIN,
        )
        PhoneNotificationFooter(metrics, state, motion, dispatch, Modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)))
    }
}

@Composable
internal fun PhoneNotificationsSheet(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val panes = widePanes(metrics.designWidth, insets.start, insets.end)
    val motion = rememberNotificationListMotion(state.notifications)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pocketPalette.scrim)
                .testTag("notification_drawer_outside")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseFriendsOverlay) },
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(metrics.dp(panes.deck + panes.margin + insets.end))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.18f),
                            (24f / (panes.deck + panes.margin + insets.end)) to pocketPalette.chrome,
                            1f to pocketPalette.chrome,
                        ),
                    ),
                )
                .testTag("notification_drawer")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(
                    start = metrics.dp(PHONE_CONTENT_MARGIN + 24f),
                    end = metrics.dp(PHONE_CONTENT_MARGIN + insets.end),
                    top = metrics.dp(insets.top + 40f),
                    bottom = metrics.dp(insets.bottom + 40f),
                ),
        ) {
            PhoneSectionHeader(
                metrics = metrics,
                title = "Notifications",
                color = pocketPalette.teal,
                horizontalPadding = 0f,
                subtitle = state.unreadNotificationCount.takeIf { it > 0 }?.let { "$it new" },
                subtitleColor = pocketPalette.tealSoft,
            ) {
                PhoneRoundAction(
                    metrics = metrics,
                    borderColor = pocketPalette.tealSoft,
                    tint = pocketPalette.surface,
                    tag = "notifications_back",
                    onClick = { dispatch(PocketPassEvent.CloseFriendsOverlay) },
                ) { CloseGlyph(metrics, pocketPalette.teal) }
            }
            Spacer(Modifier.height(metrics.dp(16f)))
            PhoneNotificationList(
                metrics = metrics,
                state = state,
                motion = motion,
                dispatch = dispatch,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalPadding = 0f,
            )
            PhoneNotificationFooter(metrics, state, motion, dispatch, Modifier)
        }
    }
}

@Composable
private fun PhoneNotificationList(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    motion: NotificationListMotion,
    dispatch: (PocketPassEvent) -> Unit,
    modifier: Modifier,
    horizontalPadding: Float,
) {
    val listState = rememberLazyListState()
    val shown = motion.shown(state.notifications)
    PinListToNewestNotification(listState, shown)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = metrics.dp(horizontalPadding), vertical = metrics.dp(24f)),
    ) {
        if (shown.isEmpty()) {
            item(key = "empty") { PhoneNotificationEmpty(metrics) }
        } else {
            items(shown, key = { it.id.value }) { notification ->
                Box(Modifier.notificationMotion(motion, notification.id.value, gap = metrics.dp(20f))) {
                    PhoneNotificationCard(
                        metrics = metrics,
                        notification = notification,
                        onOpen = { dispatch(PocketPassEvent.OpenNotification(notification.id.value)) },
                        onAccept = { dispatch(PocketPassEvent.RespondToNotificationFriendRequest(notification.id.value, accept = true)) },
                        onDecline = { dispatch(PocketPassEvent.RespondToNotificationFriendRequest(notification.id.value, accept = false)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneNotificationFooter(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    motion: NotificationListMotion,
    dispatch: (PocketPassEvent) -> Unit,
    modifier: Modifier,
) {
    val shown = motion.shown(state.notifications)
    Column(modifier.fillMaxWidth()) {
        PhoneButton(
            metrics = metrics,
            label = "Clear All",
            modifier = Modifier.fillMaxWidth(),
            fill = greyPanelBrush(),
            borderColor = pocketPalette.borderGrey,
            textColor = pocketPalette.textPrimary,
            enabled = !motion.clearing && shown.any { it.canDelete },
            height = 128f,
            fontSize = 48f,
            tag = "clear_all_notifications",
            onClick = { motion.clearAll(shown) { dispatch(PocketPassEvent.ClearAllNotifications) } },
        )
        state.notificationOperationError?.let { error ->
            Spacer(Modifier.height(metrics.dp(12f)))
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
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
private fun PhoneNotificationEmpty(metrics: DesignMetrics) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    PhoneNotificationShell(
        metrics,
        Modifier
            .testTag("notifications_empty")
            .graphicsLayer { alpha = reveal.value },
    ) {
        Text(
            text = "No notifications",
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(50f),
            maxLines = 1,
        )
        Text(
            text = "You're all caught up.",
            color = pocketPalette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30.295f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PhoneNotificationShell(
    metrics: DesignMetrics,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .phoneShadow(metrics, 60f, 14f)
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
            )
            .padding(horizontal = metrics.dp(39f), vertical = metrics.dp(27f)),
        content = content,
    )
}

@Composable
private fun PhoneNotificationCard(
    metrics: DesignMetrics,
    notification: PocketPassNotification,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val pendingRequest = notification.kind == NotificationKind.FriendRequest &&
        notification.friendRequestStatus == FriendRequestNotificationStatus.Pending
    val actorCard = pendingRequest || (notification.actor != null && notification.announcesNewFriend)
    val actor = notification.actor
    val title = when {
        actorCard -> actor?.displayName?.trim()?.ifBlank { null } ?: notification.title
        notification.eventCount > 1 -> "${notification.title} ×${notification.eventCount.coerceAtMost(99)}"
        else -> notification.title
    }
    val body = when {
        pendingRequest -> "Sent a friend request!"
        actorCard -> "Is now your friend!"
        else -> notification.body
    }
    PhoneNotificationShell(
        metrics = metrics,
        modifier = Modifier
            .testTag("notification_${notification.id.value}")
            .clickable(
                interactionSource = remember(notification.id) { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actorCard) {
                PhoneNotificationAvatar(metrics, actor, title)
                Spacer(Modifier.width(metrics.dp(20f)))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = pocketPalette.teal,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = body,
                    color = pocketPalette.tealSoft,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(30.295f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.isUnread) {
                Box(
                    modifier = Modifier
                        .padding(start = metrics.dp(16f))
                        .requiredSize(metrics.dp(76f), metrics.dp(34f))
                        .clip(RoundedCornerShape(metrics.dp(17f)))
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
        }
        if (pendingRequest) {
            Spacer(Modifier.height(metrics.dp(22f)))
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16f))) {
                PhoneRequestButton(metrics, "Accept", Assets.NotificationAccept, 35.8f, 25.988f, greenButtonBrush(), Color(0xFF4FC24B), "accept_friend_request", onAccept)
                PhoneRequestButton(metrics, "Decline", Assets.NotificationDecline, 28.58f, 28.58f, redButtonBrush(), Color(0xFFC24B4B), "decline_friend_request", onDecline)
            }
        }
    }
}

@Composable
private fun PhoneRequestButton(
    metrics: DesignMetrics,
    label: String,
    icon: Int,
    iconWidth: Float,
    iconHeight: Float,
    fill: Brush,
    borderColor: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(57.927f))
    Row(
        modifier = Modifier
            .width(metrics.dp(260f))
            .height(metrics.dp(88f))
            .phoneShadow(metrics, 57.927f, 6f, 0.11f)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(9.893f), borderColor, shape)
            .testTag(tag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaAsset(resource = icon, modifier = Modifier.requiredSize(metrics.dp(iconWidth), metrics.dp(iconHeight)))
        Spacer(Modifier.width(metrics.dp(20f)))
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(28f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PhoneNotificationAvatar(metrics: DesignMetrics, actor: UserProfile?, displayName: String) {
    val shape = RoundedCornerShape(metrics.dp(35f))
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(121f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFBDF8CB)).copy(alpha = 0.72f))))
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
