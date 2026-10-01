package com.pocketpass.app.boards

import kotlin.test.*

class BoardPushPayloadTest {
    private fun id(n: Int) = "99290000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun data() = mapOf("type" to "board", "version" to "1", "recipient_id" to id(1), "binding_id" to id(2),
        "board_id" to id(3), "thread_id" to id(4), "notification_id" to id(5), "event_count" to "2")
    @Test fun notificationIsBoundToTheCurrentAccountSessionAndCount() {
        val payload = requireNotNull(BoardPushPayload.parse(data()))
        assertEquals(BoardDestination(id(3), id(4)), payload.destination)
        assertTrue(payload.shouldShow(id(1), id(2), false, 1))
        assertFalse(payload.shouldShow(id(9), id(2), false, 0))
        assertFalse(payload.shouldShow(id(1), id(9), false, 0))
        assertFalse(payload.shouldShow(id(1), id(2), true, 0))
        assertFalse(payload.shouldShow(id(1), id(2), false, 2))
    }
    @Test fun malformedTargetsVersionsAndCountsNeverNavigate() {
        for((key, value) in listOf("board_id" to "https://example.com", "thread_id" to "invalid", "event_count" to "-1", "version" to "2"))
            assertNull(BoardPushPayload.parse(data() + (key to value)))
        assertEquals(null, BoardPushPayload.parse(data() + ("thread_id" to ""))?.destination?.threadId)
    }
    @Test fun alertsSayWhoDidWhatInWhichBoard() {
        fun text(kind: String, count: String = "1", actor: String = "Petah") =
            requireNotNull(BoardPushPayload.parse(data() + mapOf("kind" to kind, "actor_name" to actor, "board_name" to "Pixel Art", "event_count" to count)))
        assertEquals("Pixel Art", text("mention").title)
        assertEquals("Petah mentioned you", text("mention").text)
        assertEquals("Petah replied to your note", text("reply").text)
        assertEquals("3 replies to your note, latest from Petah", text("reply", "3").text)
        assertEquals("Petah gave your note a Yeah", text("yeah").text)
        assertEquals("New note from Petah", text("note").text)
        assertEquals("New activity from Petah", text("activity").text)
        assertEquals("2 new updates, latest from Petah", text("activity", "2").text)
        assertEquals("There is new activity in your boards.", text("mention", actor = " ").text)
        val old = requireNotNull(BoardPushPayload.parse(data()))
        assertEquals("PocketPass Boards", old.title)
        assertEquals("There is new activity in your boards.", old.text)
    }
}
