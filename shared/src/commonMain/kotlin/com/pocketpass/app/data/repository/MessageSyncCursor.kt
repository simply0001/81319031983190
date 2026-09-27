package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.Message
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal const val MESSAGE_CURSOR_STREAM_PREFIX = "messages:"

internal val MESSAGE_CURSOR_OVERLAP = 2.minutes

internal fun messageCursorStream(conversationId: ConversationId): String =
    MESSAGE_CURSOR_STREAM_PREFIX + conversationId.value

internal fun Message.latestChangeAt(): Instant =
    listOfNotNull(createdAt, editedAt, deletedAt).max()

internal fun parseCursorOrNull(cursor: String): Instant? =
    runCatching { Instant.parse(cursor) }.getOrNull()
