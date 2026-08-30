package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class EditMessageCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val body: String,
    val editedAt: Instant,
) {
    init {
        require(body.isNotBlank()) { "Edited message body must not be blank" }
    }
}

data class DeleteMessageCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val deletedAt: Instant,
)

const val MAX_GROUP_MEMBERS = 20
const val GROUP_TITLE_MAX_LENGTH = 40

data class CreateGroupConversationCommand(
    val accountId: UserId,
    val title: String,
    val memberIds: List<UserId>,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
) {
    init {
        require(title.isNotBlank()) { "Group title cannot be blank" }
        require(memberIds.isNotEmpty()) { "A group needs at least one other member" }
        require(memberIds.distinct().size == memberIds.size) { "Group member ids must be unique" }
        require(accountId !in memberIds) { "The creator is already a member" }
        require(memberIds.size < MAX_GROUP_MEMBERS) { "Groups hold at most $MAX_GROUP_MEMBERS members" }
    }
}

data class AddGroupMembersCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val memberIds: List<UserId>,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
) {
    init {
        require(memberIds.isNotEmpty()) { "At least one member id is required" }
        require(memberIds.distinct().size == memberIds.size) { "Group member ids must be unique" }
        require(accountId !in memberIds) { "The caller is already a member" }
    }
}

data class RemoveGroupMemberCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val userId: UserId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
) {
    init {
        require(userId != accountId) { "Leave the group instead of removing yourself" }
    }
}

data class LeaveGroupConversationCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
)

data class RenameGroupConversationCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val title: String,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
) {
    init {
        require(title.isNotBlank()) { "Group title cannot be blank" }
    }
}
