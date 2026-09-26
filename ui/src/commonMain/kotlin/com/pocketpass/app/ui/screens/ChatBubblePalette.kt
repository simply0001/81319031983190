package com.pocketpass.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pocketpass.app.domain.model.ChatBubbleColour
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.domain.state.accountIdOrNull
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.PocketAsset

internal class BubblePalette(
    val border: Color,
    val fill: Array<Pair<Float, Color>>,
    val text: Color = Color.White,
    val tail: PocketAsset? = null,
)

internal fun PocketPassUiState.messageColour(senderId: UserId): ChatBubbleColour =
    if (senderId == profile?.userId) profile?.chatBubbleColour ?: ChatBubbleColour.Default
    else messageAuthorColours[senderId] ?: ChatBubbleColour.Default

internal data class TypingIndicatorStyle(val label: String?, val colour: ChatBubbleColour)

internal fun PocketPassUiState.typingIndicatorStyles(conversation: ConversationSummary?): List<TypingIndicatorStyle> {
    val self = sessionState.accountIdOrNull() ?: profile?.userId
    val typing = typingUserIds.filterNot { it == self }.sortedBy { it.value }
    if (typing.isEmpty()) {
        if (typingUserIds.isNotEmpty()) return emptyList()
        val partner = conversation?.takeUnless { it.isGroup }?.othersThan(self)?.singleOrNull()
        return listOf(TypingIndicatorStyle(null, partner?.let { messageColour(it.userId) } ?: ChatBubbleColour.Default))
    }
    return typing.map { senderId ->
        TypingIndicatorStyle(
            label = conversation?.takeIf { it.isGroup }?.member(senderId)?.displayName,
            colour = messageColour(senderId),
        )
    }
}

internal fun chatBubblePalette(colour: ChatBubbleColour, outgoing: Boolean = true): BubblePalette {
    if (colour == ChatBubbleColour.Default) return if (outgoing) BubblePalette(
        Color(0xFF4B5FC2), arrayOf(0f to Color(0xFF5EA3ED), 0.16477f to Color(0xFF5EA3ED), 1f to Color(0xFF0073FF)),
        tail = Assets.MessageTailOutgoing,
    ) else BubblePalette(
        Color(0xFFC2B04B), arrayOf(0f to Color(0xFFEDD85E), 0.16477f to Color(0xFFEDD85E), 1f to Color(0xFFFF9900)),
        tail = Assets.MessageTailIncoming,
    )
    val (top, bottom) = when (colour) {
        ChatBubbleColour.Blue -> Color(0xFF9ECCFF) to Color(0xFF5D9DE8)
        ChatBubbleColour.Purple -> Color(0xFFD9BEFF) to Color(0xFFA182DE)
        ChatBubbleColour.Pink -> Color(0xFFFFBBE1) to Color(0xFFE77EB9)
        ChatBubbleColour.Red -> Color(0xFFFFA6A0) to Color(0xFFEA766E)
        ChatBubbleColour.Orange -> Color(0xFFFFCE8E) to Color(0xFFF3A34C)
        ChatBubbleColour.Yellow -> Color(0xFFFFED91) to Color(0xFFF0C84F)
        ChatBubbleColour.Green -> Color(0xFFA9E5AF) to Color(0xFF67BF80)
        ChatBubbleColour.Teal -> Color(0xFF9BE4DF) to Color(0xFF58B8B3)
        ChatBubbleColour.Default -> error("Default is resolved above")
    }
    return BubblePalette(lerp(bottom, Color.Black, 0.2f), arrayOf(0f to top, 0.16477f to top, 1f to bottom), Color(0xFF132A37))
}
