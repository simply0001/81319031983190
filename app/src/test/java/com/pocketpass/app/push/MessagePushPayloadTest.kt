package com.pocketpass.app.push

import org.junit.Assert.*
import org.junit.Test

class MessagePushPayloadTest {
    private val account = "00000000-0000-4000-8000-000000000001"
    private val binding = "00000000-0000-4000-8000-000000000002"
    private val data = mapOf(
        "type" to "message", "version" to "1", "recipient_id" to account, "binding_id" to binding,
        "conversation_id" to "00000000-0000-4000-8000-000000000003",
        "notification_id" to "00000000-0000-4000-8000-000000000004",
        "event_count" to "5", "title" to "Friends group", "body" to "Sam: Hello!",
    )

    @Test fun parsesDirectAndGroupPreviews() {
        assertEquals("Sam: Hello!", MessagePushPayload.parse(data)?.body)
        assertEquals("Sam", MessagePushPayload.parse(data + ("title" to "Sam"))?.title)
    }

    @Test fun rejectsUnknownTypesAndVersions() {
        assertNull(MessagePushPayload.parse(data + ("type" to "system")))
        assertNull(MessagePushPayload.parse(data + ("version" to "2")))
    }

    @Test fun rejectsMissingOrMalformedIdentifiers() {
        for (key in listOf("recipient_id", "binding_id", "conversation_id", "notification_id")) {
            assertNull(MessagePushPayload.parse(data - key))
            assertNull(MessagePushPayload.parse(data + (key to "invalid")))
        }
    }

    @Test fun rejectsInvalidCountsAndEmptyTitle() {
        for (count in listOf("0", "-1", "abc", "9223372036854775808")) {
            assertNull(MessagePushPayload.parse(data + ("event_count" to count)))
        }
        assertNull(MessagePushPayload.parse(data + ("title" to "  ")))
    }

    @Test fun boundsPreviewLengths() {
        val payload = requireNotNull(MessagePushPayload.parse(data + mapOf("title" to "x".repeat(500), "body" to "x".repeat(900))))
        assertEquals(120, payload.title.length)
        assertEquals(300, payload.body.length)
    }

    @Test fun onlyNewBackgroundMessagesForCurrentBindingAreShown() {
        val payload = requireNotNull(MessagePushPayload.parse(data))
        assertTrue(payload.shouldShow(account, binding, true, false, 4))
        assertFalse(payload.shouldShow(account, binding, true, false, 5))
        assertFalse(payload.shouldShow(account, binding, true, false, 6))
        assertFalse(payload.shouldShow(account, binding, true, true, 0))
        assertFalse(payload.shouldShow(account, binding, false, false, 0))
        assertFalse(payload.shouldShow(null, binding, true, false, 0))
        assertFalse(payload.shouldShow("different-account", binding, true, false, 0))
        assertFalse(payload.shouldShow(account, "old-binding", true, false, 0))
    }
}
