package com.pocketpass.app.data.repository

import com.pocketpass.app.data.supabase.realtime.*
import com.pocketpass.app.domain.model.*
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ChatBubbleColourTest {
    @Test fun allPresetsRoundTripThroughTheOfflinePayload() {
        for (colour in ChatBubbleColour.entries) {
            val command = SetChatBubbleColourCommand(UserId("account"), colour, Instant.fromEpochMilliseconds(1234))
            assertEquals(command, ProductionOperationPayloadCodec.decodeChatColour(ProductionOperationPayloadCodec.encode(command), 1))
        }
    }

    @Test fun unknownServerColoursUseDefaultButInvalidQueuedWritesAreRejected() {
        assertEquals(ChatBubbleColour.Default, ChatBubbleColour.fromKey("future-colour"))
        assertEquals(ChatBubbleColour.Default, ChatBubbleColour.fromKey(null))
        val command = SetChatBubbleColourCommand(UserId("account"), ChatBubbleColour.Blue, Instant.fromEpochMilliseconds(1234))
        val payload = ProductionOperationPayloadCodec.encode(command)
        assertFailsWith<IllegalArgumentException> { ProductionOperationPayloadCodec.decodeChatColour(payload.replace("blue", "invalid"), 1) }
        assertFailsWith<IllegalArgumentException> { ProductionOperationPayloadCodec.decodeChatColour(payload, 2) }
    }

    @Test fun colourSignalIsScopedAndCannotBecomeANewMessage() {
        val change = MessageChangeBroadcastDto("UPDATE", "profile_chat_colours", "public", MessageBroadcastRecordDto(userId = "sender", conversationId = "chat"))
        assertEquals(ConversationRealtimeEvent.ChatColourChanged("sender"), change.toConversationRealtimeEvent("chat"))
        assertNull(change.toConversationRealtimeEvent("other-chat"))
        assertNull(change.copy(operation = "INSERT").toConversationRealtimeEvent("chat"))
        assertNull(change.toMessageInvalidation("chat"))
    }

    @Test fun fixtureChangesOnlyTheChosenAccountsColour() = runTest {
        val original = FixtureData.currentProfile
        val repo = FixtureProfileRepository()
        repo.setChatBubbleColour(SetChatBubbleColourCommand(original.userId, ChatBubbleColour.Pink, original.updatedAt))
        assertEquals(original.copy(chatBubbleColour = ChatBubbleColour.Pink), repo.observeProfile(original.userId).first())
        assertEquals(FixtureData.spobProfile, repo.observeProfile(FixtureData.spobProfile.userId).first())
    }
}
