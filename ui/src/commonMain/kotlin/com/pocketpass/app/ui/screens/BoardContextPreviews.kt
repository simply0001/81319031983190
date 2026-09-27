package com.pocketpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.boards.BoardPost
import com.pocketpass.app.boards.BoardsUiState
import com.pocketpass.app.domain.model.IMAGE_MESSAGE_PLACEHOLDER_BODY
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.AvatarCollage
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
private fun BoardPreviewLayout(
    m: DesignMetrics,
    modifier: Modifier,
    summary: @Composable (Boolean) -> Unit,
    detail: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(m.dp(50f))) {
        if(maxWidth > maxHeight * 1.15f) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(m.dp(50f)),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(.8f), verticalArrangement = Arrangement.spacedBy(m.dp(24f))) { summary(false) }
                Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(m.dp(24f))) {
                summary(true)
                Box(Modifier.weight(1f)) { detail() }
            }
        }
    }
}

@Composable
internal fun BoardConversationPreview(m: DesignMetrics, state: PocketPassUiState, modifier: Modifier = Modifier) {
    val conversation = state.conversations.firstOrNull { it.id == state.previewConversationId }
        ?: state.conversations.firstOrNull()
    if(conversation == null) {
        BoardPreviewEmpty(m, "Private messages", "Choose a friend to start a conversation.", modifier)
        return
    }
    val palette = pocketPalette
    val other = conversation.othersThan(state.profile?.userId).firstOrNull()
    val online = !conversation.isGroup && state.friends.any { it.profile.userId == other?.userId && it.isOnline }
    val status = when {
        conversation.id.value in state.typingConversationIds -> "Typing…"
        conversation.isGroup -> "${conversation.memberCount} members"
        online -> "Online"
        else -> "Private conversation"
    }
    val recent = state.previewMessages.takeIf { state.previewConversationId == conversation.id }.orEmpty()
    BoardPreviewLayout(m, modifier.testTag("board_conversation_preview"), summary = { compact ->
        val avatar: @Composable () -> Unit = {
            val size = if(compact) 140f else 270f
            Box(Modifier.size(m.dp(size)).clip(RoundedCornerShape(m.dp(32f)))
                .background(palette.surface).border(m.dp(3f), palette.borderSoft, RoundedCornerShape(m.dp(32f))),
                contentAlignment = Alignment.Center) {
                if(conversation.isGroup) AvatarCollage(m, conversation.othersThan(state.profile?.userId), size,
                    palette.teal, palette.surface, palette.borderSoft)
                else {
                    BoardLabel(m, conversation.title.trim().firstOrNull()?.uppercase() ?: "?", size * .4f, true, color = palette.teal)
                    DynamicAvatar(conversation.avatar ?: other?.avatar, fallbackResource = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val heading: @Composable () -> Unit = {
            BoardLabel(m, conversation.title, if(compact) 46f else 58f, true, maxLines = 2, color = palette.teal)
            BoardLabel(m, status, 32f, color = palette.textSecondary, maxLines = 1)
            if(conversation.unreadCount > 0) BoardLabel(m, "${conversation.unreadCount} unread", 30f, true, color = palette.teal)
        }
        if(compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
            avatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(10f))) { heading() }
        } else {
            avatar()
            heading()
        }
    }, detail = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(m.dp(18f))) {
            BoardLabel(m, "Recent messages", 32f, true, color = palette.textSecondary)
            if(recent.isEmpty()) BoardCard(m) {
                BoardLabel(m, conversation.latestMessagePreview.ifBlank { "No messages yet" }, 40f, maxLines = 5)
            } else recent.takeLast(3).forEach { message ->
                BoardCard(m, Modifier.weight(1f), contentPadding = 24f) {
                    val sender = if(message.senderId == state.profile?.userId) "You"
                        else conversation.member(message.senderId)?.displayName ?: if(conversation.isGroup) "Former member" else conversation.title
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(20f))) {
                        BoardLabel(m, sender, 30f, true, modifier = Modifier.weight(1f), maxLines = 1, color = palette.teal)
                        BoardLabel(m, relativeTime(message.createdAt), 26f, color = palette.textSecondary, maxLines = 1)
                    }
                    BoardLabel(m, message.previewText(), 36f, modifier = Modifier.weight(1f, fill = false), maxLines = 3)
                }
            }
        }
    })
}

private fun Message.previewText(): String = when {
    deletedAt != null -> "Message removed"
    attachment != null -> {
        val kind = if(attachment?.mimeType.equals("image/gif", ignoreCase = true)) "GIF" else "Photo"
        val caption = body.takeUnless { it == IMAGE_MESSAGE_PLACEHOLDER_BODY }.orEmpty()
        if(caption.isBlank()) kind else "$kind · $caption"
    }
    else -> body
}

@Composable
internal fun BoardDirectoryPreview(m: DesignMetrics, state: BoardsUiState, modifier: Modifier = Modifier) {
    val board = state.boards.firstOrNull { it.id == state.directoryFocusId } ?: state.boards.firstOrNull()
    if(board == null) {
        BoardPreviewEmpty(m, if(state.explore) "Explore boards" else "Your boards",
            if(state.explore) "Find a community for your next note." else "Join a board to share notes and drawings.", modifier)
        return
    }
    val latest = state.directoryLatest?.takeIf { it.boardId == board.id }
    BoardPreviewLayout(m, modifier.testTag("board_directory_preview"), summary = { compact ->
        if(!compact) BoardBranding(m, board, state.assets, cover = true, coverHeight = 240f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
            BoardBranding(m, board, state.assets)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(10f))) {
                BoardLabel(m, board.name, if(compact) 46f else 56f, true, maxLines = 2, color = pocketPalette.teal)
                BoardLabel(m, "${board.memberCount} members · ${if(board.visibility == "private") "Private" else "Public"}", 30f,
                    color = pocketPalette.textSecondary, maxLines = 1)
            }
        }
        if(board.description.isNotBlank()) BoardLabel(m, board.description, 36f, maxLines = if(compact) 2 else 4)
        if(board.archived) BoardLabel(m, "Archived", 30f, color = pocketPalette.textSecondary)
    }, detail = {
        BoardCard(m, Modifier.fillMaxSize()) {
            BoardLabel(m, "Latest note", 32f, true, color = pocketPalette.textSecondary)
            if(latest == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    BoardLabel(m, when {
                        state.directoryPreviewLoading -> "Loading note…"
                        state.directoryPreviewError -> "Preview unavailable"
                        else -> "No notes yet"
                    }, 42f, color = pocketPalette.textSecondary)
                }
            } else {
                BoardLabel(m, latest.authorName, 38f, true, maxLines = 1, color = pocketPalette.teal)
                BoardLatestNote(m, latest, Modifier.weight(1f))
            }
        }
    })
}

@Composable
private fun BoardLatestNote(m: DesignMetrics, post: BoardPost, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(m.dp(18f))) {
        if(post.removed || post.spoiler) {
            BoardLabel(m, if(post.removed) "This note was removed." else "Spoiler · Open the board to reveal", 38f,
                color = pocketPalette.textSecondary, maxLines = 3)
        } else {
            post.drawing?.let { drawing ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BoardDrawingCanvas(drawing, Modifier.aspectRatio(4f/3f, matchHeightConstraintsFirst = true), paper = post.stationery?.artwork)
                }
            }
            if(post.body.isNotBlank()) BoardLabel(m, post.body, 38f, maxLines = if(post.drawing != null) 2 else 9)
            BoardLabel(m, "${post.yeahCount} Yeah · ${post.replyCount} replies", 28f, color = pocketPalette.textSecondary, maxLines = 1)
        }
    }
}

@Composable
internal fun BoardDrawingToolPreview(m: DesignMetrics, state: BoardsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(16f))) {
            Box(Modifier.size(m.dp(36f)).background(boardInk(state.ink), CircleShape).border(m.dp(2f), pocketPalette.borderSoft, CircleShape))
            val pen = when(state.pen) { "eraser" -> "Eraser"; "smooth" -> "Smooth pen"; else -> "Pixel pen" }
            BoardLabel(m, "$pen · ${state.penSize.toInt()}", 30f, color = pocketPalette.textSecondary, maxLines = 1)
        }
        BoardLabel(m, if(state.canvasViewport.zoom > 1f) "${(state.canvasViewport.zoom * 100).toInt()}% · Editing area outlined"
            else "Full paper · 100%", 30f, color = pocketPalette.textSecondary, maxLines = 2)
    }
}

@Composable
private fun BoardPreviewEmpty(m: DesignMetrics, title: String, description: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(m.dp(60f)), verticalArrangement = Arrangement.spacedBy(m.dp(24f), Alignment.CenterVertically)) {
        BoardLabel(m, title, 58f, true, color = pocketPalette.teal, maxLines = 2)
        BoardLabel(m, description, 40f, color = pocketPalette.textSecondary, maxLines = 3)
    }
}
