package com.pocketpass.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.pocketpass.app.ui.Sudofont

data class SudofontGlyph(
    val codePoint: Int,
    val pua: Int,
    val name: String,
) {
    val text: String = codePointToString(codePoint)
}

val SudofontGlyphs: List<SudofontGlyph> = listOf(
    SudofontGlyph(0x1F603, 0xE008, "happy"),
    SudofontGlyph(0x1F620, 0xE009, "angry"),
    SudofontGlyph(0x1F614, 0xE00A, "sad"),
    SudofontGlyph(0x1F611, 0xE00B, "neutral"),
    SudofontGlyph(0x1F62D, 0xE029, "crying"),
    SudofontGlyph(0x2600, 0xE00C, "sun"),
    SudofontGlyph(0x2601, 0xE00D, "cloud"),
    SudofontGlyph(0x2614, 0xE00E, "umbrella"),
    SudofontGlyph(0x26C4, 0xE00F, "snowman"),
    SudofontGlyph(0x2757, 0xE010, "exclamation"),
    SudofontGlyph(0x2753, 0xE011, "question"),
    SudofontGlyph(0x2709, 0xE012, "envelope"),
    SudofontGlyph(0x1F4F1, 0xE013, "phone"),
    SudofontGlyph(0x2660, 0xE015, "spade"),
    SudofontGlyph(0x2666, 0xE016, "diamond"),
    SudofontGlyph(0x2665, 0xE017, "heart"),
    SudofontGlyph(0x2663, 0xE018, "club"),
    SudofontGlyph(0x27A1, 0xE019, "right"),
    SudofontGlyph(0x2B05, 0xE01A, "left"),
    SudofontGlyph(0x2B06, 0xE01B, "up"),
    SudofontGlyph(0x2B07, 0xE01C, "down"),
    SudofontGlyph(0x24B6, 0xE000, "a"),
    SudofontGlyph(0x24B7, 0xE001, "b"),
    SudofontGlyph(0x24CD, 0xE002, "x"),
    SudofontGlyph(0x24CE, 0xE003, "y"),
    SudofontGlyph(0x24C1, 0xE004, "l"),
    SudofontGlyph(0x24C7, 0xE005, "r"),
    SudofontGlyph(0x2795, 0xE006, "dpad"),
    SudofontGlyph(0x23F0, 0xE007, "alarm"),
    SudofontGlyph(0x2715, 0xE028, "cross"),
)

private val sudofontCodePoints: Set<Int> = buildSet {
    SudofontGlyphs.forEach { glyph ->
        add(glyph.codePoint)
        add(glyph.pua)
    }
}

fun isSudofontCodePoint(codePoint: Int): Boolean = codePoint in sudofontCodePoints

data class TextRun(val start: Int, val end: Int)

fun sudofontRuns(text: CharSequence): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    var index = 0
    var runStart = -1
    while (index < text.length) {
        val (codePoint, width) = codePointAt(text, index)
        if (isSudofontCodePoint(codePoint)) {
            if (runStart < 0) runStart = index
        } else if (runStart >= 0) {
            runs += TextRun(runStart, index)
            runStart = -1
        }
        index += width
    }
    if (runStart >= 0) runs += TextRun(runStart, text.length)
    return runs
}

fun sudofontSpanStyle(family: FontFamily): SpanStyle = SpanStyle(
    fontFamily = family,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
)

fun withSudofont(text: AnnotatedString, family: FontFamily): AnnotatedString {
    val runs = sudofontRuns(text.text)
    if (runs.isEmpty()) return text
    val style = sudofontSpanStyle(family)
    return buildAnnotatedString {
        append(text)
        runs.forEach { run -> addStyle(style, run.start, run.end) }
    }
}

class SudofontVisualTransformation(private val family: FontFamily) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(withSudofont(text, family), OffsetMapping.Identity)
}

@Composable
fun rememberSudofontTransformation(): VisualTransformation {
    val family = Sudofont
    return remember(family) { SudofontVisualTransformation(family) }
}

fun String.dropLastCodePoint(): String {
    if (isEmpty()) return this
    val pair = length >= 2 && this[length - 1].isLowSurrogate() && this[length - 2].isHighSurrogate()
    return dropLast(if (pair) 2 else 1)
}

fun codePointToString(codePoint: Int): String {
    if (codePoint < 0x10000) return codePoint.toChar().toString()
    val offset = codePoint - 0x10000
    return charArrayOf(
        (0xD800 + (offset shr 10)).toChar(),
        (0xDC00 + (offset and 0x3FF)).toChar(),
    ).concatToString()
}

private fun codePointAt(text: CharSequence, index: Int): Pair<Int, Int> {
    val high = text[index]
    if (high.isHighSurrogate() && index + 1 < text.length) {
        val low = text[index + 1]
        if (low.isLowSurrogate()) {
            val codePoint = 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            return codePoint to 2
        }
    }
    return high.code to 1
}
