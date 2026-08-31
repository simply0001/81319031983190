package com.pocketpass.app.ui.phone

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.MessageComposerAction
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensionTarget
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.AvatarCollage
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.IdleMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.screens.DynamicAvatar
import com.pocketpass.app.ui.screens.MessageArrivalTracker
import com.pocketpass.app.ui.screens.MessageBubble
import com.pocketpass.app.ui.screens.isEditable
import com.pocketpass.app.ui.screens.MessageRow
import com.pocketpass.app.ui.screens.MessageRowPalette
import com.pocketpass.app.ui.screens.TypingIndicatorBubble
import com.pocketpass.app.ui.screens.groupSubtitle
import com.pocketpass.app.ui.screens.relativeTime
import com.pocketpass.app.ui.screens.senderLabelFor
import com.pocketpass.app.ui.screens.typingNames
import com.pocketpass.app.ui.theme.pocketPalette
import kotlinx.coroutines.delay

private const val MESSAGE_ROW_HEIGHT = 187f
private const val MESSAGE_ROW_INSET = 13f
private const val COMPOSER_HEIGHT = 161.5f
private const val RAIL_EXPANDED_HEIGHT = 330f

@Composable
fun PhoneMessagesTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    if (panes == null) {
        PhoneConversationList(metrics, state, dispatch)
    } else {
        PhonePanes(
            metrics = metrics,
            panes = panes,
            stage = { PhoneStageScroll(metrics) { PhoneMessagesBadge(metrics, state.messageBadgeText) } },
            deck = { PhoneConversationList(metrics, state, dispatch) },
        )
    }
}

@Composable
private fun PhoneConversationList(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val palette = pocketPalette
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = metrics.dp(insets.top + 40f), bottom = metrics.dp(60f)),
    ) {
        PhoneSectionHeader(
            metrics = metrics,
            title = "All Messages (${state.messageTotalCount})",
            color = palette.teal,
            subtitle = if (state.unreadConversationCount > 0) "${state.unreadConversationCount} unread" else null,
        ) {
            PhoneRoundAction(
                metrics = metrics,
                borderColor = palette.tealBorder,
                tint = palette.tint(Color(0xFFD1EDFB)),
                tag = "messages_new_group",
                onClick = { dispatch(PocketPassEvent.OpenNewGroup) },
            ) { PlusGlyph(metrics, palette.teal) }
        }
        Spacer(Modifier.height(metrics.dp(40f)))
        val notice = state.conversationNotice
        if (notice != null) {
            LaunchedEffect(notice) {
                delay(4_000)
                dispatch(PocketPassEvent.DismissConversationNotice)
            }
            PhoneConversationNotice(metrics, notice) { dispatch(PocketPassEvent.DismissConversationNotice) }
            Spacer(Modifier.height(metrics.dp(30f)))
        }
        val conversations = state.conversations
        if (conversations.isEmpty()) {
            PhoneEmptyRow(
                metrics = metrics,
                icon = Assets.NavMessages,
                title = "No messages yet",
                subtitle = "Open a profile and tap Message to start chatting",
                tag = "messages_empty",
                modifier = Modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
            )
            return@Column
        }
        val shape = RoundedCornerShape(metrics.dp(104f))
        Box(
            modifier = Modifier
                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))
                .width(metrics.dp(1140f))
                .height(metrics.dp(MESSAGE_ROW_INSET * 2f + MESSAGE_ROW_HEIGHT * conversations.size))
                .phoneShadow(metrics, 104f)
                .clip(shape)
                .pocketFrame(
                    palette.surface,
                    metrics.dp(PHONE_PANEL_BORDER),
                    Brush.verticalGradient(listOf(Color(0xFF76B3C1), Color(0xFF5E9AAC), Color(0xFF22677C))),
                    shape,
                ),
        ) {
            conversations.forEachIndexed { index, conversation ->
                MessageRow(
                    metrics = metrics,
                    y = MESSAGE_ROW_INSET + index * MESSAGE_ROW_HEIGHT,
                    conversation = conversation,
                    palette = if (index % 2 == 0) {
                        MessageRowPalette(
                            name = palette.ink(Color(0xFFC99E1B)),
                            preview = palette.ink(Color(0xFFE5AA00)),
                            count = Color(0xFFF4B900),
                            tintBottom = palette.tint(Color(0xFFFFF0B9)),
                            avatarBorder = palette.tint(Color(0xFFFFF0BD)),
                        )
                    } else {
                        MessageRowPalette(
                            name = palette.ink(Color(0xFF2365D3)),
                            preview = palette.ink(Color(0xFF5B83E5)),
                            count = Color(0xFF1371F5),
                            tintBottom = palette.tint(Color(0xFFDDE7FC)),
                            avatarBorder = palette.tint(Color(0xFFE2E4F0)),
                        )
                    },
                    onClick = { dispatch(PocketPassEvent.OpenMessage(conversation.id.value)) },
                    selfId = state.profile?.userId,
                )
            }
        }
    }
}

@Composable
private fun PhoneConversationNotice(
    metrics: DesignMetrics,
    text: String,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    Box(
        modifier = Modifier
            .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))
            .fillMaxWidth()
            .phoneShadow(metrics, 60f, 10f, 0.1f)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFD1EDFB)))),
                metrics.dp(10f),
                Color(0xFF76B3C1),
                shape,
            )
            .testTag("conversation_notice")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(horizontal = metrics.dp(44f), vertical = metrics.dp(26f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = pocketPalette.ink(Color(0xFF386F7F)),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PhoneMessagesBadge(metrics: DesignMetrics, count: String) {
    val autoSize = remember(metrics) {
        TextAutoSize.StepBased(
            minFontSize = metrics.sp(48f),
            maxFontSize = metrics.sp(331.378f),
            stepSize = metrics.sp(1f),
        )
    }
    MotionLayer(idle = IdleMotion.MessageFloat) {
        Box(Modifier.requiredSize(metrics.dp(632.327f), metrics.dp(597.997f))) {
            Box(
                Modifier
                    .offset(x = metrics.dp(31.379f), y = metrics.dp(34.064f))
                    .requiredSize(metrics.dp(569.569f), metrics.dp(529.868f))
                    .graphicsLayer { rotationZ = -7.31f },
            ) {
                FigmaAsset(
                    resource = Assets.MessagesBadge,
                    modifier = Modifier.requiredSize(metrics.dp(569.569f), metrics.dp(552.167f)),
                )
                Box(
                    modifier = Modifier
                        .offset(x = metrics.dp(142.5f), y = metrics.dp(12.4f))
                        .requiredSize(metrics.dp(284.622f), metrics.dp(420.847f))
                        .graphicsLayer { rotationZ = -0.4f },
                    contentAlignment = Alignment.Center,
                ) {
                    val base = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    BasicText(
                        text = count,
                        autoSize = autoSize,
                        style = base.copy(
                            color = pocketPalette.ink(Color(0xFF2F6CA5)),
                            drawStyle = Stroke(width = 18f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                        ),
                        maxLines = 1,
                    )
                    BasicText(text = count, autoSize = autoSize, style = base.copy(color = Color.White), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PhoneThread(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    val insets = LocalPhoneInsets.current
    val retained = remember { mutableStateOf<Pair<ConversationSummary, List<Message>>?>(null) }
    state.selectedConversation?.let { retained.value = it to state.selectedMessages }
    val conversation = state.selectedConversation ?: retained.value?.first ?: return
    val conversationId = conversation.id.value
    val currentUserId = when (val session = state.sessionState) {
        is SessionState.Authenticated -> session.userId
        is SessionState.OfflineWithCachedSession -> session.userId
        else -> null
    }
    val messages = if (state.selectedConversation != null) state.selectedMessages else retained.value?.second.orEmpty()
    val partnerTyping = conversationId in state.typingConversationIds
    val arrivalTracker = remember(conversationId) { MessageArrivalTracker() }
    SideEffect { arrivalTracker.primed = true }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id?.value, partnerTyping) {
        val lastIndex = messages.lastIndex + if (partnerTyping) 1 else 0
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }
    Column(
        Modifier
            .fillMaxSize()
            .testTag("top_message_thread")
            .padding(
                top = metrics.dp(insets.top + 20f),
                bottom = metrics.dp(maxOf(insets.bottom, insets.ime) + 24f),
            ),
    ) {
        if (insets.ime <= 0f || metrics.designHeight - insets.ime > 900f) {
            PhoneThreadHeader(
                metrics = metrics,
                state = state,
                conversation = conversation,
                onBack = { dispatch(PocketPassEvent.Back) },
                onOpenInfo = { dispatch(PocketPassEvent.OpenGroupInfo) },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("message_thread"),
            contentPadding = PaddingValues(
                start = metrics.dp(100f),
                end = metrics.dp(100f),
                top = metrics.dp(24f),
                bottom = metrics.dp(110f),
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(48f), Alignment.Bottom),
        ) {
            itemsIndexed(items = messages, key = { _, message -> message.id.value }) { index, message ->
                val arrivalPop = remember(message.id.value) { arrivalTracker.markSeen(message.id.value) }
                val mine = message.senderId == currentUserId
                MessageBubble(
                    metrics = metrics,
                    message = message,
                    outgoing = mine,
                    onRetry = { dispatch(PocketPassEvent.RetryMessage(message.id.value)) },
                    arrivalPop = arrivalPop,
                    onLongPress = if (state.selectedConversation != null && mine && message.isEditable()) {
                        { dispatch(PocketPassEvent.OpenMessageActions(message.id.value)) }
                    } else {
                        null
                    },
                    selected = message.id.value == state.messageActionMessageId,
                    senderLabel = senderLabelFor(conversation, messages.getOrNull(index - 1), message, currentUserId),
                )
            }
            if (partnerTyping) {
                item(key = "typing_indicator") {
                    TypingIndicatorBubble(
                        metrics = metrics,
                        label = conversation.takeIf { it.isGroup }?.let { typingNames(it, state.typingUserIds) },
                    )
                }
            }
        }
        PhoneComposer(metrics, state, conversationId, dispatch, extensions)
    }
}

@Composable
private fun PhoneThreadHeader(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    conversation: ConversationSummary,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val selfId = state.profile?.userId
    val group = conversation.isGroup
    val partnerMessages = if (selfId == null) emptyList() else state.selectedMessages.filter { it.senderId != selfId }
    val partner = if (group) {
        null
    } else {
        conversation.othersThan(selfId).firstOrNull()
            ?.let { member -> state.friends.firstOrNull { it.profile.userId == member.userId } }
    }
    val partnerLastActive = listOfNotNull(
        partner?.profile?.lastSeenAt,
        partnerMessages.maxByOrNull { it.createdAt }?.createdAt,
    ).maxOrNull()
    val status = when {
        group -> groupSubtitle(conversation, state.typingUserIds)
        conversation.id.value in state.typingConversationIds -> "typing…"
        partner?.isOnline == true -> "now"
        partnerLastActive != null -> relativeTime(partnerLastActive).lowercase()
        else -> ""
    }
    val ink = pocketPalette.ink(Color(0xFF386F7F))
    val avatarShape = RoundedCornerShape(metrics.dp(85f))
    val chevron: @Composable (Modifier) -> Unit = { modifier ->
        FigmaAsset(
            resource = Assets.SettingsArrow,
            modifier = modifier
                .requiredSize(metrics.dp(40.372f), metrics.dp(68.725f))
                .graphicsLayer { scaleX = -1f },
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink),
        )
    }
    val identity: @Composable RowScope.() -> Unit = {
        Box(
            modifier = Modifier
                .requiredSize(metrics.dp(150f))
                .clip(avatarShape)
                .pocketFrame(pocketPalette.tint(Color(0xFFD1EDFB)), metrics.dp(10f), Color(0xFF76B3C1), avatarShape),
            contentAlignment = Alignment.Center,
        ) {
            if (group) {
                AvatarCollage(
                    metrics = metrics,
                    members = conversation.othersThan(selfId),
                    size = 150f,
                    initialColor = ink,
                    tileFill = pocketPalette.tint(Color(0xFFD1EDFB)),
                    divider = Color(0xFF76B3C1),
                )
            } else {
                Text(
                    text = conversation.title.trim().firstOrNull()?.uppercase() ?: "?",
                    color = ink,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    fontSize = metrics.sp(70f),
                    maxLines = 1,
                )
                DynamicAvatar(
                    avatar = conversation.avatar,
                    fallbackResource = null,
                    modifier = Modifier
                        .requiredSize(metrics.dp(150f))
                        .clip(avatarShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = metrics.dp(30f)),
        ) {
            Text(
                text = conversation.title,
                color = ink,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    color = pocketPalette.ink(Color(0xFF5591A4)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (group) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN), vertical = metrics.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chevron(
                Modifier
                    .testTag("message_back")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    )
                    .padding(end = metrics.dp(30f), top = metrics.dp(30f), bottom = metrics.dp(30f)),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_group_info")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenInfo,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                content = identity,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("message_back")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                )
                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN), vertical = metrics.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chevron(Modifier.padding(end = metrics.dp(30f)))
            identity()
        }
    }
}

@Composable
private fun PhoneComposer(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    conversationId: String,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    val canSend = state.messageDraft.trim().isNotEmpty() &&
        state.messageDraft.length <= 4_000 &&
        !state.messageSendInProgress
    val railProgress by animateFloatAsState(
        targetValue = if (state.messageActionRailExpanded) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "messageActionRail",
    )
    val send = { if (canSend) dispatch(PocketPassEvent.SendMessage) }
    val editing = state.editingMessageId != null
    Column(Modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))) {
        if (editing) {
            PhoneMessageEditingChip(metrics) { dispatch(PocketPassEvent.CancelMessageEdit) }
        }
        state.messageOperationError?.let { error ->
            Text(
                text = error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = metrics.dp(50f), bottom = metrics.dp(8f)),
                color = pocketPalette.ink(Color(0xFF9D3131)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(26f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            PhoneTextField(
                metrics = metrics,
                value = state.messageDraft,
                onValueChange = { dispatch(PocketPassEvent.UpdateMessageDraft(it.take(4_000))) },
                modifier = Modifier.weight(1f),
                placeholder = if (editing) "Edit message" else "Message",
                fontSize = 50f,
                fontWeight = FontWeight.Bold,
                textColor = pocketPalette.teal,
                placeholderColor = pocketPalette.ink(Color(0xFF2F948C)),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                singleLine = false,
                maxLines = 4,
                minHeight = COMPOSER_HEIGHT,
                radius = 80.75f,
                borderColor = Color(0xFF5A96A9),
                fill = Brush.verticalGradient(listOf(pocketPalette.surface, pocketPalette.tint(Color(0xFFBDF8CB)))),
                horizontalPadding = 44f,
                verticalPadding = 28f,
                tag = "message_composer",
            )
            Spacer(Modifier.width(metrics.dp(20f)))
            FigmaAsset(
                resource = Assets.MessagesSendButton,
                modifier = Modifier
                    .requiredSize(metrics.dp(158.452f), metrics.dp(177.174f))
                    .offset(y = metrics.dp(8f))
                    .alpha(if (canSend) 1f else 0.72f)
                    .testTag("message_send")
                    .clickable(
                        enabled = canSend,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = send,
                    ),
            )
            Spacer(Modifier.width(metrics.dp(20f)))
            PhoneActionRail(
                metrics = metrics,
                progress = { railProgress },
                expanded = state.messageActionRailExpanded,
                onToggle = { dispatch(PocketPassEvent.ToggleMessageActions) },
                onAction = { action ->
                    dispatch(PocketPassEvent.SelectMessageAction(action))
                    if (action == MessageComposerAction.File) {
                        extensions.open(PocketPassExtensionTarget.MessageComposer(conversationId, action))
                    }
                },
            )
        }
    }
}

@Composable
private fun PhoneActionRail(
    metrics: DesignMetrics,
    progress: () -> Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (MessageComposerAction) -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(79.226f))
    val fill = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to pocketPalette.surface,
            0.6265f to pocketPalette.surface,
            1f to pocketPalette.tint(Color(0xFFBDF8CB)),
        ),
    )
    val height = COMPOSER_HEIGHT + (RAIL_EXPANDED_HEIGHT - COMPOSER_HEIGHT) * progress()
    val lift = height - RAIL_EXPANDED_HEIGHT
    Box(
        Modifier
            .width(metrics.dp(158.452f))
            .height(metrics.dp(COMPOSER_HEIGHT)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        run {
            Box(
                Modifier
                    .width(metrics.dp(158.452f))
                    .height(metrics.dp(height))
                    .clip(shape)
                    .pocketFrame(
                        fill,
                        metrics.dp(18f),
                        Brush.verticalGradient(listOf(Color(0xFF5A96A9), Color(0xFF286C81))),
                        shape,
                    ),
            ) {
                val handover = { (progress() / 0.45f).coerceIn(0f, 1f) }
                FigmaAsset(
                    resource = Assets.MessageActionImage,
                    modifier = Modifier
                        .offset(x = metrics.dp(38.07f), y = metrics.dp(lift + 40f))
                        .requiredSize(metrics.dp(81.86f))
                        .graphicsLayer { alpha = handover() }
                        .testTag("message_action_image")
                        .clickable(
                            enabled = expanded,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAction(MessageComposerAction.Image) },
                )
                FigmaAsset(
                    resource = Assets.MessageActionFile,
                    modifier = Modifier
                        .offset(x = metrics.dp(41f), y = metrics.dp(lift + 160f))
                        .requiredSize(metrics.dp(76f), metrics.dp(94f))
                        .graphicsLayer { alpha = handover() }
                        .testTag("message_action_file")
                        .clickable(
                            enabled = expanded,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAction(MessageComposerAction.File) },
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(metrics.dp(COMPOSER_HEIGHT))
                        .testTag("message_actions")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggle,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FigmaAsset(
                        resource = Assets.MessageActionAdd,
                        modifier = Modifier
                            .requiredSize(metrics.dp(66.111f), metrics.dp(67.535f))
                            .graphicsLayer {
                                val p = progress()
                                rotationZ = 45f * p
                                alpha = 1f - 0.35f * p
                            },
                    )
                }
            }
        }
    }
}
