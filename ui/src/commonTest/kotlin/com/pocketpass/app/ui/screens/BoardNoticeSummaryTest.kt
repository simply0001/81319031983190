package com.pocketpass.app.ui.screens

import com.pocketpass.app.boards.BoardMention
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

    @Test fun personalAlertsSayWhatHappenedToYou() {
        fun notice(kind: String) = BoardNotice("event", "board", "Sketch Club", "thread", kind,
            subject = "Weekend plans", subjectType = "text", threadAuthorName = "Ada")
        assertEquals("Mentioned you in “Weekend plans”", notice("mention").summary())
        assertEquals("Replied to you in “Weekend plans”", notice("reply").summary())
        assertEquals("Gave you a Yeah in “Weekend plans”", notice("yeah").summary())
        assertEquals("New note: “Weekend plans”", notice("note").summary())
        assertEquals("Mentioned you", BoardNotice("event", "board", "Sketch Club", "thread", "mention").summary())
    }

    @Test fun mentionsAreBoldAndOpenTheProfile() {
        val opened = mutableListOf<String>()
        val text = boardBodyText("Hi @Petah and @sam", listOf(BoardMention("p", "Petah"), BoardMention("s", "Sam")),
            androidx.compose.ui.graphics.Color.Blue) { opened += it }
        assertEquals(listOf(3 to 9, 14 to 18), text.spanStyles.map { it.start to it.end })
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(2, links.size)
        (links.first().item as androidx.compose.ui.text.LinkAnnotation.Clickable).linkInteractionListener?.onClick(links.first().item)
        assertEquals(listOf("p"), opened)
    }
}
