package com.pocketpass.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.boards.BoardMention
import com.pocketpass.app.boards.BoardMentionCandidate
import com.pocketpass.app.boards.boardMentionRanges
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun BoardMentionChips(m: DesignMetrics, candidates: List<BoardMentionCandidate>, onPick: (BoardMentionCandidate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("board_mention_chips"),
        horizontalArrangement = Arrangement.spacedBy(m.dp(18f)),
    ) {
        candidates.forEach { candidate ->
            BoardButton(m, "@" + candidate.displayName, "board_mention_${candidate.userId}", compact = true, confirmSound = true) {
                onPick(candidate)
            }
        }
    }
}

internal fun boardBodyText(body: String, mentions: List<BoardMention>, color: Color, openProfile: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(body)
        boardMentionRanges(body, mentions).forEach { range ->
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), range.start, range.end)
            addLink(LinkAnnotation.Clickable("mention_${range.userId}") { openProfile(range.userId) }, range.start, range.end)
        }
    }

@Composable
internal fun BoardBodyLabel(m: DesignMetrics, body: String, mentions: List<BoardMention>, size: Float, openProfile: (String) -> Unit) {
    if (mentions.isEmpty()) {
        BoardLabel(m, body, size)
        return
    }
    val color = pocketPalette.teal
    val latestOpenProfile = rememberUpdatedState(openProfile)
    val text = remember(body, mentions, color) { boardBodyText(body, mentions, color) { latestOpenProfile.value(it) } }
    Text(
        text,
        fontFamily = Rubik,
        fontWeight = FontWeight.Normal,
        fontSize = m.sp(size),
        color = pocketPalette.textPrimary,
        overflow = TextOverflow.Ellipsis,
    )
}
