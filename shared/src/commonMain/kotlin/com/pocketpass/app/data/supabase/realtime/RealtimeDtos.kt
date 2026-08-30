package com.pocketpass.app.data.supabase.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageInvalidationDto(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("message_id")
    val messageId: String,
    val operation: MessageChangeOperation,
    @SerialName("sender_id")
    val senderId: String? = null,
)

@Serializable
enum class MessageChangeOperation {
    Insert,
    Update,
    Delete,
}

@Serializable
data class PresenceStateDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("active_conversation_id")
    val activeConversationId: String? = null,
    @SerialName("is_typing")
    val isTyping: Boolean = false,
)

@Serializable
data class MessageBroadcastRecordDto(
    val id: String? = null,
    @SerialName("conversation_id")
    val conversationId: String? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("left_at")
    val leftAt: String? = null,
    val title: String? = null,
)

enum class ConversationChangeTable {
    Members,
    Conversation,
}

data class ConversationInvalidationDto(
    val conversationId: String,
    val table: ConversationChangeTable,
    val operation: String,
)

@Serializable
data class MessageChangeBroadcastDto(
    val operation: String,
    val table: String,
    val schema: String,
    val record: MessageBroadcastRecordDto? = null,
    @SerialName("old_record")
    val oldRecord: MessageBroadcastRecordDto? = null,
)

@Serializable
data class NotificationBroadcastRecordDto(
    val id: String,
    @SerialName("recipient_id")
    val recipientId: String,
)

@Serializable
data class NotificationChangeBroadcastDto(
    val operation: String,
    val table: String,
    val schema: String,
    val record: NotificationBroadcastRecordDto? = null,
    @SerialName("old_record")
    val oldRecord: NotificationBroadcastRecordDto? = null,
)

fun NotificationChangeBroadcastDto.isFor(userId: String): Boolean {
    if (schema != NOTIFICATION_SCHEMA || table != NOTIFICATION_TABLE) return false
    return (record ?: oldRecord)?.recipientId == userId
}

sealed interface ConversationRealtimeEvent {
    data class MessageInvalidated(
        val invalidation: MessageInvalidationDto,
    ) : ConversationRealtimeEvent

    data class ConversationInvalidated(
        val invalidation: ConversationInvalidationDto,
    ) : ConversationRealtimeEvent

    data class PresenceChanged(
        val presences: List<PresenceStateDto>,
    ) : ConversationRealtimeEvent
}

fun MessageChangeBroadcastDto.toMessageInvalidation(
    expectedConversationId: String,
): MessageInvalidationDto? {
    if (schema != MESSAGE_SCHEMA || table != MESSAGE_TABLE) return null
    val mappedOperation = when (operation.uppercase()) {
        INSERT_EVENT -> MessageChangeOperation.Insert
        UPDATE_EVENT -> MessageChangeOperation.Update
        DELETE_EVENT -> MessageChangeOperation.Delete
        else -> return null
    }
    val changedRecord = record ?: oldRecord ?: return null
    val conversationId = changedRecord.conversationId ?: return null
    val messageId = changedRecord.id ?: return null
    if (conversationId != expectedConversationId) return null
    return MessageInvalidationDto(
        conversationId = conversationId,
        messageId = messageId,
        operation = mappedOperation,
        senderId = changedRecord.senderId,
    )
}

fun MessageChangeBroadcastDto.toConversationRealtimeEvent(
    expectedConversationId: String,
): ConversationRealtimeEvent? {
    if (schema != MESSAGE_SCHEMA) return null
    val changedRecord = record ?: oldRecord ?: return null
    return when (table) {
        MESSAGE_TABLE -> toMessageInvalidation(expectedConversationId)
            ?.let(ConversationRealtimeEvent::MessageInvalidated)

        CONVERSATION_MEMBERS_TABLE -> changedRecord.conversationId
            .takeIf { it == expectedConversationId }
            ?.let { conversationId ->
                ConversationRealtimeEvent.ConversationInvalidated(
                    ConversationInvalidationDto(
                        conversationId = conversationId,
                        table = ConversationChangeTable.Members,
                        operation = operation.uppercase(),
                    ),
                )
            }

        CONVERSATIONS_TABLE -> changedRecord.id
            .takeIf { it == expectedConversationId }
            ?.let { conversationId ->
                ConversationRealtimeEvent.ConversationInvalidated(
                    ConversationInvalidationDto(
                        conversationId = conversationId,
                        table = ConversationChangeTable.Conversation,
                        operation = operation.uppercase(),
                    ),
                )
            }

        else -> null
    }
}

const val INSERT_EVENT = "INSERT"
const val UPDATE_EVENT = "UPDATE"
const val DELETE_EVENT = "DELETE"
const val MEMBERSHIP_EVENT = "membership"
const val CONVERSATION_EVENT = "conversation"
private const val MESSAGE_SCHEMA = "public"
private const val MESSAGE_TABLE = "messages"
private const val CONVERSATION_MEMBERS_TABLE = "conversation_members"
private const val CONVERSATIONS_TABLE = "conversations"
private const val NOTIFICATION_SCHEMA = "public"
private const val NOTIFICATION_TABLE = "notifications"
