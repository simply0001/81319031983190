package com.pocketpass.app.boards

const val BOARD_MENTION_LIMIT = 10
private const val BOARD_MENTION_QUERY_LIMIT = 32

data class BoardMentionRange(val start: Int, val end: Int, val userId: String)

fun trailingMentionQuery(text: String, mentions: List<BoardMention> = emptyList()): String? {
    val at = text.lastIndexOf('@')
    if (at < 0 || (at > 0 && !text[at - 1].isWhitespace())) return null
    val query = text.substring(at + 1)
    if (query.length > BOARD_MENTION_QUERY_LIMIT || '\n' in query || query.startsWith(' ')) return null
    if (mentions.any { query.startsWith(it.name, ignoreCase = true) }) return null
    return query
}

fun pruneBoardMentions(text: String, mentions: List<BoardMention>): List<BoardMention> =
    mentions.filter { text.contains("@" + it.name, ignoreCase = true) }

fun BoardDraftContent.withMention(candidate: BoardMentionCandidate, limit: Int): BoardDraftContent? {
    val query = trailingMentionQuery(body, mentions) ?: return null
    val others = mentions.filterNot { it.userId == candidate.userId }
    if (others.size >= BOARD_MENTION_LIMIT || candidate.displayName.isBlank()) return null
    val next = body.dropLast(query.length + 1) + "@" + candidate.displayName + " "
    if (next.length > limit) return null
    return copy(body = next, mentions = others + BoardMention(candidate.userId, candidate.displayName))
}

fun boardMentionRanges(body: String, mentions: List<BoardMention>): List<BoardMentionRange> {
    val ranges = mutableListOf<BoardMentionRange>()
    mentions.filter { it.name.isNotBlank() }.sortedByDescending { it.name.length }.forEach { mention ->
        val token = "@" + mention.name
        var from = 0
        while (true) {
            val start = body.indexOf(token, from, ignoreCase = true)
            if (start < 0) break
            val end = start + token.length
            if (ranges.none { start < it.end && end > it.start }) ranges += BoardMentionRange(start, end, mention.userId)
            from = end
        }
    }
    return ranges.sortedBy { it.start }
}
