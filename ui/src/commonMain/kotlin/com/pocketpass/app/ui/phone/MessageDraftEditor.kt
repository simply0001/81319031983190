package com.pocketpass.app.ui.phone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keeps the IME's selection and composing region while the draft flows back from the store. */
internal class MessageDraftEditor(initialText: String) {
    var value by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set
    private var observedText = initialText
    private val pendingTexts = mutableListOf<String>()

    fun synchronize(text: String) {
        if (text == observedText) return
        observedText = text
        val acknowledged = pendingTexts.indexOfLast { it == text }
        if (acknowledged >= 0) {
            repeat(acknowledged + 1) { pendingTexts.removeAt(0) }
            return
        }
        pendingTexts.clear()
        if (text != value.text) value = TextFieldValue(text, TextRange(text.length))
    }

    /** Returns a draft to dispatch only when the text changed, never for cursor/composition edits. */
    fun edit(next: TextFieldValue): String? {
        val limited = next.copy(text = next.text.take(4_000))
        val previousText = value.text
        value = limited
        if (limited.text == previousText) return null
        pendingTexts.add(limited.text)
        return limited.text
    }
}
