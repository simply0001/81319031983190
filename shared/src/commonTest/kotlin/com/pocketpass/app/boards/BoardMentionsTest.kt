package com.pocketpass.app.boards

import kotlin.test.*

class BoardMentionsTest {
    private val petah = BoardMentionCandidate("99290000-0000-4000-8000-000000000002", "Petah Griffin")
    private val sam = BoardMentionCandidate("99290000-0000-4000-8000-000000000003", "Sam")

    @Test fun anAtSignStartsAQueryOnlyAtTheStartOfAWord() {
        assertEquals("", trailingMentionQuery("Hello @"))
        assertEquals("pet", trailingMentionQuery("Hello @pet"))
        assertEquals("Petah G", trailingMentionQuery("@Petah G"))
        assertNull(trailingMentionQuery("mail me@home"))
        assertNull(trailingMentionQuery("Hello @ there"))
        assertNull(trailingMentionQuery("@pet\nnext"))
        assertNull(trailingMentionQuery("@" + "a".repeat(33)))
        assertNull(trailingMentionQuery("Hi @Petah Griffin thanks", listOf(BoardMention(petah.userId, "Petah Griffin"))))
    }

    @Test fun pickingAMemberReplacesTheQueryAndRecordsThem() {
        val content = BoardDraftContent(body = "Hey @pe")
        val picked = assertNotNull(content.withMention(petah, 1000))
        assertEquals("Hey @Petah Griffin ", picked.body)
        assertEquals(listOf(BoardMention(petah.userId, "Petah Griffin")), picked.mentions)
        assertNull(picked.withMention(sam, 1000))
        assertNull(BoardDraftContent(body = "Hey @pe").withMention(petah, 10))
        assertNull(BoardDraftContent(body = "No query").withMention(petah, 1000))
    }

    @Test fun mentionsStopAtTenPeople() {
        val ten = List(BOARD_MENTION_LIMIT) { BoardMention("id-$it", "Person $it") }
        assertNull(BoardDraftContent(body = "@s", mentions = ten).withMention(sam, 1000))
    }

    @Test fun deletingTheNameDropsTheMention() {
        val mentions = listOf(BoardMention(petah.userId, "Petah Griffin"), BoardMention(sam.userId, "Sam"))
        assertEquals(listOf(BoardMention(sam.userId, "Sam")), pruneBoardMentions("Thanks @sam and @Petah Griff", mentions))
    }

    @Test fun rangesFindEveryMentionAndPreferTheLongerName() {
        val mentions = listOf(BoardMention("short", "Pat"), BoardMention("long", "Pat Lee"))
        val ranges = boardMentionRanges("@Pat Lee met @pat", mentions)
        assertEquals(listOf(BoardMentionRange(0, 8, "long"), BoardMentionRange(13, 17, "short")), ranges)
    }

    @Test fun mentionsStayOutOfDraftsWithoutThem() {
        assertFalse("mentions" in BoardJson.encodeToString(BoardDraftContent.serializer(), BoardDraftContent(body = "Hi")))
        val encoded = BoardJson.encodeToString(BoardDraftContent.serializer(), BoardDraftContent(body = "@Sam", mentions = listOf(BoardMention(sam.userId, "Sam"))))
        assertTrue("\"user_id\":\"${sam.userId}\"" in encoded)
    }
}
