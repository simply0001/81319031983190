package com.pocketpass.app.ui.screens

import com.pocketpass.app.boards.BoardNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BoardNoticeSummaryTest {
    @Test fun showsTheThreadSubjectAndAuthor() {
        val notice = BoardNotice("event", "board", "Sketch Club", "thread", "activity",
            subject = "Weekend plans", subjectType = "text", threadAuthorName = "Ada")
        assertEquals("Activity on Ada’s note: “Weekend plans”", notice.summary())
    }

    @Test fun spoilerAndRemovedNotesDoNotExposeTheirBody() {
        for (type in listOf("spoiler", "removed")) {
            val notice = BoardNotice("event", "board", "Sketch Club", "thread", "activity",
                subject = "Private words", subjectType = type)
            assertFalse(notice.summary().contains("Private words"))
        }
        assertEquals("A report needs review", BoardNotice("report", "board", "Sketch Club", kind = "report").summary())
    }
}
