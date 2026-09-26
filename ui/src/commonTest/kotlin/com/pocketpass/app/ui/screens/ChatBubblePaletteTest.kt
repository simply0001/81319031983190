package com.pocketpass.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.pocketpass.app.domain.model.ChatBubbleColour
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.model.PocketPassUiState
import kotlin.test.*

class ChatBubblePaletteTest {
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val conversation = ConversationSummary(
        ConversationId("chat"), "Chat", null, "", null, 0,
        kind = ConversationKind.Group,
        members = listOf(alice, bob).map { ConversationMember(it, it.value, null, joinedAt = kotlin.time.Instant.fromEpochMilliseconds(0)) },
    )

    @Test fun concurrentTypistsKeepTheirOwnColoursAndFollowProfileChanges() {
        val state = PocketPassUiState(
            typingUserIds = setOf(bob, alice),
            messageAuthorColours = mapOf(alice to ChatBubbleColour.Pink, bob to ChatBubbleColour.Teal),
        )
        assertEquals(listOf(TypingIndicatorStyle("alice", ChatBubbleColour.Pink), TypingIndicatorStyle("bob", ChatBubbleColour.Teal)), state.typingIndicatorStyles(conversation))
        val changed = state.copy(messageAuthorColours = mapOf(alice to ChatBubbleColour.Purple))
        assertEquals(listOf(TypingIndicatorStyle("alice", ChatBubbleColour.Purple), TypingIndicatorStyle("bob", ChatBubbleColour.Default)), changed.typingIndicatorStyles(conversation))
    }

    @Test fun directTypingUsesThePartnerAndNeverTheLocalUsersColour() {
        val state = PocketPassUiState(
            profile = UserProfile(alice, "Alice", null, updatedAt = kotlin.time.Instant.fromEpochMilliseconds(0), chatBubbleColour = ChatBubbleColour.Red),
            typingUserIds = setOf(alice, bob),
            messageAuthorColours = mapOf(bob to ChatBubbleColour.Green),
        )
        val direct = conversation.copy(kind = ConversationKind.Direct)
        assertEquals(listOf(TypingIndicatorStyle(null, ChatBubbleColour.Green)), state.typingIndicatorStyles(direct))
        assertEquals(listOf(TypingIndicatorStyle(null, ChatBubbleColour.Green)), state.copy(typingUserIds = emptySet()).typingIndicatorStyles(direct))
        assertTrue(state.copy(typingUserIds = setOf(alice)).typingIndicatorStyles(direct).isEmpty())
    }

    @Test fun presetsAreIdenticalForSenderAndRecipientAndKeepTextReadable() {
        for (colour in ChatBubbleColour.entries.filterNot { it == ChatBubbleColour.Default }) {
            val sent = chatBubblePalette(colour, true)
            val received = chatBubblePalette(colour, false)
            assertEquals(sent.fill.toList(), received.fill.toList())
            assertEquals(sent.border, received.border)
            assertEquals(sent.text, received.text)
            assertNull(sent.tail)
            for (step in 0..20) {
                val fill = lerp(sent.fill.first().second, sent.fill.last().second, step / 20f)
                val contrast = (maxOf(fill.luminance(), sent.text.luminance()) + 0.05f) / (minOf(fill.luminance(), sent.text.luminance()) + 0.05f)
                assertTrue(contrast >= 4.5f, "$colour contrast is $contrast")
            }
        }
    }

    @Test fun defaultKeepsTheExistingBlueAndYellowBubbles() {
        assertEquals(Color(0xFF0073FF), chatBubblePalette(ChatBubbleColour.Default, true).fill.last().second)
        assertEquals(Color(0xFFFF9900), chatBubblePalette(ChatBubbleColour.Default, false).fill.last().second)
        assertNotNull(chatBubblePalette(ChatBubbleColour.Default).tail)
    }

    @Test fun ownPendingColourWinsAndUnknownAuthorsUseDefault() {
        val own = UserId("self")
        val former = UserId("former-group-member")
        val state = PocketPassUiState(
            profile = UserProfile(own, "Self", null, updatedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
                chatBubbleColour = ChatBubbleColour.Pink, chatColourPending = true),
            messageAuthorColours = mapOf(own to ChatBubbleColour.Blue, former to ChatBubbleColour.Teal),
        )
        assertEquals(ChatBubbleColour.Pink, state.messageColour(own))
        assertEquals(ChatBubbleColour.Teal, state.messageColour(former))
        assertEquals(ChatBubbleColour.Default, state.messageColour(UserId("unavailable")))
        val switched = PocketPassUiState()
        assertEquals(ChatBubbleColour.Default, switched.messageColour(own))
    }
}
