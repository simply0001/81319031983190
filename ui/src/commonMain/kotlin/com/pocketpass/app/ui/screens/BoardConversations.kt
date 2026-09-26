package com.pocketpass.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.platformAnimationsEnabled
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.theme.pocketPalette
import kotlinx.coroutines.delay

@Composable
internal fun BoardConversations(m: DesignMetrics, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(focus?.focusId, state.conversations.map { it.id }, state.routes) {
        val highlighted = state.conversations.firstOrNull { "message_${it.id.value}" == focus?.focusId }
            ?: state.conversations.firstOrNull { it.id == state.previewConversationId } ?: state.conversations.firstOrNull()
        dispatch(PocketPassEvent.PreviewMessage(highlighted?.id?.value.takeIf { state.routes.lastOrNull() is PocketPassRoute.Root }))
    }
    DisposableEffect(Unit) { onDispose { dispatch(PocketPassEvent.PreviewMessage(null)) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
        Column(Modifier.weight(1f)) {
            BoardLabel(m, "Private messages", 44f, true)
            BoardLabel(m, if(state.unreadConversationCount > 0) "${state.unreadConversationCount} unread" else "Friends and group chats", 32f, color = pocketPalette.textSecondary)
        }
        BoardButton(m, "New group", "messages_new_group", icon = Assets.MessageActionAdd) { dispatch(PocketPassEvent.OpenNewGroup) }
    }
    state.conversationNotice?.let { notice ->
        LaunchedEffect(notice) { delay(4_000); dispatch(PocketPassEvent.DismissConversationNotice) }
        BoardCard(m) { BoardLabel(m, notice, 36f) }
    }
    if(state.conversations.isEmpty()) {
        BoardCard(m, Modifier.testTag("messages_empty")) {
            BoardLabel(m, "No messages yet", 44f, true)
            BoardLabel(m, "Open a friend's profile and tap Message to start chatting.", color = pocketPalette.textSecondary)
        }
    } else {
        val shape = RoundedCornerShape(m.dp(32f))
        val motion = platformAnimationsEnabled()
        Column(Modifier.fillMaxWidth().pocketFrame(pocketPalette.surface, m.dp(4f), pocketPalette.tealBorder, shape)
            .clip(shape).padding(vertical = m.dp(13f))) {
            state.conversations.forEachIndexed { index, conversation ->
                key(conversation.id) {
                val visible = remember { MutableTransitionState(!motion || index == 0) }
                LaunchedEffect(Unit) {
                    if(motion && index > 0) delay(index.coerceAtMost(6) * 45L)
                    visible.targetState = true
                }
                AnimatedVisibility(visible,
                    enter = if(motion) expandVertically(tween(180, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(140)) else EnterTransition.None) {
                Box(Modifier.fillMaxWidth().height(m.dp(187f))) {
                MessageRow(m, 0f, conversation, messageListRowPalette(),
                    onClick = { dispatch(PocketPassEvent.OpenMessage(conversation.id.value)) }, selfId = state.profile?.userId,
                    focusLayer = LocalBoardFocusLayer.current, neighbors = buildMap {
                        put(FocusDirection.Up, state.conversations.getOrNull(index - 1)?.let { "message_${it.id.value}" } ?: "messages_new_group")
                        state.conversations.getOrNull(index + 1)?.let { put(FocusDirection.Down, "message_${it.id.value}") }
                    })
                }
                }
                }
            }
        }
    }
}
