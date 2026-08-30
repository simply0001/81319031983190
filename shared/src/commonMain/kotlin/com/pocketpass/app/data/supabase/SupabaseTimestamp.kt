package com.pocketpass.app.data.supabase

import kotlin.time.Instant

fun parseSupabaseInstant(value: String): Instant {
    val trimmed = value.trim()
    val dateTimeSeparated = if (
        trimmed.length > DATE_LENGTH &&
        trimmed[DATE_LENGTH] == ' '
    ) {
        trimmed.replaceRange(DATE_LENGTH, DATE_LENGTH + 1, "T")
    } else {
        trimmed
    }
    val normalized = when {
        COMPACT_HOUR_OFFSET.containsMatchIn(dateTimeSeparated) -> "$dateTimeSeparated:00"
        COMPACT_MINUTE_OFFSET.containsMatchIn(dateTimeSeparated) ->
            dateTimeSeparated.replace(
                COMPACT_MINUTE_OFFSET,
                "$1:$2",
            )

        else -> dateTimeSeparated
    }

    return Instant.parse(normalized)
}

private const val DATE_LENGTH = 10
private val COMPACT_HOUR_OFFSET = Regex("""[+-]\d{2}$""")
private val COMPACT_MINUTE_OFFSET = Regex("""([+-]\d{2})(\d{2})$""")
