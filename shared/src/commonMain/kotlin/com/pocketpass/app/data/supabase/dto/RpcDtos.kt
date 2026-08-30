package com.pocketpass.app.data.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SendFriendRequestRpc(
    @SerialName("p_addressee_id")
    val addresseeId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class ResolveFriendCodeRpc(
    @SerialName("p_code")
    val code: String,
)

@Serializable
data class MarkNotificationReadRpc(
    @SerialName("p_notification_id")
    val notificationId: String,
    @SerialName("p_read_at")
    val readAt: String,
)

@Serializable
data class MarkAllNotificationsReadRpc(
    @SerialName("p_read_at")
    val readAt: String,
)

@Serializable
data class DeleteNotificationRpc(
    @SerialName("p_notification_id")
    val notificationId: String,
    @SerialName("p_deleted_at")
    val deletedAt: String,
)

@Serializable
data class RespondToFriendRequestRpc(
    @SerialName("p_request_id")
    val requestId: String,
    @SerialName("p_accept")
    val accept: Boolean,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class RemoveFriendRpc(
    @SerialName("p_friend_id")
    val friendId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class BuyShopItemRpc(
    @SerialName("p_item_id")
    val itemId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class SetUserBlockRpc(
    @SerialName("p_user_id")
    val userId: String,
    @SerialName("p_blocked")
    val blocked: Boolean,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class GetOrCreateDirectConversationRpc(
    @SerialName("p_other_user_id")
    val otherUserId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class SendMessageRpc(
    @SerialName("p_message_id")
    val messageId: String,
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
    @SerialName("p_body")
    val body: String,
    @SerialName("p_reply_to_id")
    val replyToId: String? = null,
    @SerialName("p_metadata")
    val metadata: JsonObject,
)

@Serializable
data class MarkConversationReadRpc(
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_read_at")
    val readAt: String,
)

@Serializable
data class EditMessageRpc(
    @SerialName("p_message_id")
    val messageId: String,
    @SerialName("p_body")
    val body: String,
)

@Serializable
data class DeleteMessageRpc(
    @SerialName("p_message_id")
    val messageId: String,
)

@Serializable
data class CreateGroupConversationRpc(
    @SerialName("p_title")
    val title: String,
    @SerialName("p_member_ids")
    val memberIds: List<String>,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class AddGroupMembersRpc(
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_member_ids")
    val memberIds: List<String>,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class RemoveGroupMemberRpc(
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_user_id")
    val userId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class LeaveGroupConversationRpc(
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class RenameGroupConversationRpc(
    @SerialName("p_conversation_id")
    val conversationId: String,
    @SerialName("p_title")
    val title: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
data class RecordInteractionEventRpc(
    @SerialName("p_event_id")
    val eventId: String,
    @SerialName("p_subject_user_id")
    val subjectUserId: String? = null,
    @SerialName("p_event_type")
    val eventType: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
    @SerialName("p_payload")
    val payload: JsonObject,
    @SerialName("p_occurred_at")
    val occurredAt: String,
)
