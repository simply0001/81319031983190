package com.pocketpass.app.domain.model

const val GROUP_MESSAGES_BLOCKED = "This person is not accepting group invitations."

data class SetMessagePrivacyCommand(
    val accountId: UserId,
    val blocked: Boolean,
)

data class SetInvitesPrivacyCommand(
    val accountId: UserId,
    val blocked: Boolean,
)
