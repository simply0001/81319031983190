package com.pocketpass.app.ui.phone

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageDraftEditorTest {
    @Test fun staleDraftAndIntermediateAcknowledgmentsKeepLatestComposingText() {
        val editor = MessageDraftEditor("")
        editor.edit(TextFieldValue("H", TextRange(1), TextRange(0, 1)))
        val current = TextFieldValue("HELLO", TextRange(5), TextRange(0, 5))
        editor.edit(current)
        for (draft in listOf("", "H", "HELLO", "HELLO")) {
            editor.synchronize(draft)
            assertEquals(current, editor.value)
        }
    }

    @Test fun selectionAndCompositionUpdatesDoNotDispatchDraftChanges() {
        val editor = MessageDraftEditor("HELLO")
        val selected = TextFieldValue("HELLO", TextRange(1, 4), TextRange(0, 5))
        assertNull(editor.edit(selected))
        editor.synchronize("HELLO")
        assertEquals(selected, editor.value)
        assertNull(editor.edit(selected.copy(composition = null)))
        assertNull(editor.value.composition)
    }

    @Test fun clearingAfterSendingAndReplacingForEditingStillWork() {
        val editor = MessageDraftEditor("")
        editor.edit(TextFieldValue("HELLO", TextRange(5), TextRange(0, 5)))
        editor.synchronize("HELLO")
        editor.synchronize("")
        assertEquals(TextFieldValue(""), editor.value)
        editor.synchronize("Edit this message")
        assertEquals(TextFieldValue("Edit this message", TextRange(17)), editor.value)
    }

    @Test fun inputLengthIsLimitedBeforeDispatchWithValidSelectionAndComposition() {
        val editor = MessageDraftEditor("A".repeat(3_999))
        assertEquals("A".repeat(4_000), editor.edit(TextFieldValue("A".repeat(4_002), TextRange(4_002), TextRange(3_999, 4_002))))
        assertEquals(TextRange(4_000), editor.value.selection)
        assertEquals(TextRange(3_999, 4_000), editor.value.composition)
        assertNull(editor.edit(TextFieldValue("A".repeat(4_001), TextRange(4_001))))
    }
}
