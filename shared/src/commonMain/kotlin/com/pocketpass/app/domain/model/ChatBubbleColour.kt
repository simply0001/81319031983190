package com.pocketpass.app.domain.model

import kotlin.time.Instant

enum class ChatBubbleColour(val key: String) {
    Default("default"), Blue("blue"), Purple("purple"), Pink("pink"), Red("red"),
    Orange("orange"), Yellow("yellow"), Green("green"), Teal("teal");

    companion object {
        fun fromKey(key: String?): ChatBubbleColour = entries.firstOrNull { it.key == key } ?: Default
    }
}

data class SetChatBubbleColourCommand(
    val accountId: UserId,
    val colour: ChatBubbleColour,
    val changedAt: Instant,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
)
