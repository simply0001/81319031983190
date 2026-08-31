package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProductionOperationPayloadCodecTest {
    @Test
    fun profileUpdateRoundTripsAllBackendNeutralFields() {
        val command = UpdateProfileCommand(
            accountId = ACCOUNT,
            profile = profile(),
            clientOperationId = OPERATION,
            changedAt = NOW.plus((1).seconds),
        )

        val decoded = ProductionOperationPayloadCodec.decodeProfileUpdate(
            payload = ProductionOperationPayloadCodec.encode(command),
            version = ProductionOperationPayloadCodec.VERSION,
        )

        assertEquals(command, decoded)
    }

    @Test
    fun friendRequestResponseRoundTripsDecisionAndProfile() {
        val command = RespondToFriendRequestCommand(
            accountId = ACCOUNT,
            requestId = "request-one",
            requester = profile().copy(userId = UserId("friend-one")),
            accept = true,
            clientOperationId = OPERATION,
            respondedAt = NOW,
        )

        val decoded = ProductionOperationPayloadCodec.decodeRespondToFriendRequest(
            payload = ProductionOperationPayloadCodec.encode(command),
            version = ProductionOperationPayloadCodec.VERSION,
        )

        assertEquals(command, decoded)
    }

    @Test
    fun notificationMutationsRoundTripStableIdsAndTimestamps() {
        val markOne = MarkNotificationReadCommand(
            accountId = ACCOUNT,
            notificationId = NotificationId("notification-one"),
            clientOperationId = OPERATION,
            readAt = NOW,
        )
        val markAll = MarkAllNotificationsReadCommand(
            accountId = ACCOUNT,
            clientOperationId = OPERATION,
            readAt = NOW.plus((1).seconds),
        )
        val delete = DeleteNotificationCommand(
            accountId = ACCOUNT,
            notificationId = NotificationId("notification-two"),
            clientOperationId = OPERATION,
            deletedAt = NOW.plus((2).seconds),
        )

        assertEquals(
            markOne,
            ProductionOperationPayloadCodec.decodeMarkNotificationRead(
                ProductionOperationPayloadCodec.encode(markOne),
                ProductionOperationPayloadCodec.VERSION,
            ),
        )
        assertEquals(
            markAll,
            ProductionOperationPayloadCodec.decodeMarkAllNotificationsRead(
                ProductionOperationPayloadCodec.encode(markAll),
                ProductionOperationPayloadCodec.VERSION,
            ),
        )
        assertEquals(
            delete,
            ProductionOperationPayloadCodec.decodeDeleteNotification(
                ProductionOperationPayloadCodec.encode(delete),
                ProductionOperationPayloadCodec.VERSION,
            ),
        )
    }

    @Test
    fun shopPurchaseRoundTripsItemPriceAndOperation() {
        val command = PurchaseShopItemCommand(
            accountId = ACCOUNT,
            itemId = "item-top-hat",
            priceTokens = 120,
            clientOperationId = OPERATION,
            requestedAt = NOW.plus((5).seconds),
        )

        val decoded = ProductionOperationPayloadCodec.decodePurchaseShopItem(
            payload = ProductionOperationPayloadCodec.encode(command),
            version = ProductionOperationPayloadCodec.VERSION,
        )

        assertEquals(command, decoded)
    }

    private fun profile() = UserProfile(
        userId = ACCOUNT,
        displayName = "Pocket User",
        avatar = AvatarReference.Remote("account-one/avatar.webp"),
        bio = "Nearby and ready",
        age = 24,
        countryCode = "SE",
        locationLabel = "Stockholm",
        lastSeenAt = NOW.minus((2).seconds),
        presence = PresenceStatus.Online,
        updatedAt = NOW,
    )

    private companion object {
        val ACCOUNT = UserId("account-one")
        val OPERATION = ClientOperationId("operation-one")
        val NOW: Instant = Instant.parse("2026-07-27T12:46:00Z")
    }
}
