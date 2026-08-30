package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class MarkNotificationReadCommand(
    val accountId: UserId,
    val notificationId: NotificationId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val readAt: Instant,
)

data class MarkAllNotificationsReadCommand(
    val accountId: UserId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val readAt: Instant,
)

data class DeleteNotificationCommand(
    val accountId: UserId,
    val notificationId: NotificationId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val deletedAt: Instant,
)
