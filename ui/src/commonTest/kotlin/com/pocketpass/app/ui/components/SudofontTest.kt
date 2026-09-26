package com.pocketpass.app.ui.components

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SudofontTest {
    private val family = FontFamily.Monospace

    @Test
    fun tableHasThirtyDistinctSingleCodePointGlyphs() {
        assertEquals(30, SudofontGlyphs.size)
        assertEquals(30, SudofontGlyphs.map { it.codePoint }.toSet().size)
        assertEquals(30, SudofontGlyphs.map { it.pua }.toSet().size)
        SudofontGlyphs.forEach { glyph ->
            assertEquals(listOf(TextRun(0, glyph.text.length)), sudofontRuns(glyph.text), glyph.name)
            assertTrue(glyph.text.length == 1 || glyph.text.length == 2, glyph.name)
        }
    }

    @Test
    fun cryingUsesCustomFontWithoutChangingMessageText() {
        val crying = codePointToString(0x1F62D)
        assertEquals(crying, SudofontGlyphs.single { it.name == "crying" }.text)
        assertTrue(isSudofontCodePoint(0xE029))
        val source = "oh $crying\uFE0F!"
        val styled = withSudofont(AnnotatedString(source), family)
        assertEquals(source, styled.text)
        assertEquals(listOf(TextRun(3, 5)), sudofontRuns(source))
        assertEquals(family, styled.spanStyles.single().item.fontFamily)
        assertEquals("oh ", ("oh " + crying).dropLastCodePoint())
    }

    @Test
    fun spansCoverExactlyTheGlyphRuns() {
        val happy = codePointToString(0x1F603)
        val text = AnnotatedString("hi $happy☀ there")

        val styled = withSudofont(text, family)

        assertEquals(text.text, styled.text)
        assertEquals(1, styled.spanStyles.size)
        val span = styled.spanStyles.single()
        assertEquals(3, span.start)
        assertEquals(3 + happy.length + 1, span.end)
        assertEquals(family, span.item.fontFamily)
    }

    @Test
    fun aTrailingVariationSelectorStaysOutsideTheRun() {
        val styled = withSudofont(AnnotatedString("☀️!"), family)

        assertEquals(listOf(TextRun(0, 1)), sudofontRuns(styled.text))
        assertEquals(0, styled.spanStyles.single().start)
        assertEquals(1, styled.spanStyles.single().end)
    }

    @Test
    fun privateUseAliasesAreCovered() {
        assertTrue(isSudofontCodePoint(0xE008))
        assertTrue(isSudofontCodePoint(0xE028))
        assertEquals(listOf(TextRun(0, 2)), sudofontRuns(""))
    }

    @Test
    fun plainTextComesBackUntouched() {
        val text = AnnotatedString("just words, 123 and a smile :)")

        assertSame(text, withSudofont(text, family))
    }

    @Test
    fun existingAnnotationsSurvive() {
        val caretTag = "androidx.compose.foundation.text.inlineContent"
        val text = buildAnnotatedString {
            append("a")
            appendInlineContent("caret", "|")
            append(codePointToString(0x1F603))
        }

        val styled = withSudofont(text, family)

        assertEquals(1, styled.getStringAnnotations(caretTag, 0, styled.length).size)
        assertEquals(1, styled.spanStyles.size)
        assertEquals(2, styled.spanStyles.single().start)
    }

    @Test
    fun dropLastCodePointRemovesAWholeSurrogatePair() {
        assertEquals("a", ("a" + codePointToString(0x1F603)).dropLastCodePoint())
        assertEquals("a", "ab".dropLastCodePoint())
        assertEquals("", "".dropLastCodePoint())
    }
}
