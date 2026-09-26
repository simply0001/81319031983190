package com.pocketpass.app.nearby

import kotlin.time.Clock
import kotlin.time.Instant

object NearbyDeviceTag {
    const val SECRET_BYTES: Int = 32
    const val TAG_BYTES: Int = 4

    private const val DAY_MILLIS = 86_400_000L
    private const val LABEL = "pocketpass-nearby-device-tag:"
    private const val LOW_MASK = 0xFFFF_FFFFL

    fun dayNumber(now: Instant): Long = now.toEpochMilliseconds().floorDiv(DAY_MILLIS)

    fun millisUntilNextDay(now: Instant): Long =
        (dayNumber(now) + 1) * DAY_MILLIS - now.toEpochMilliseconds()

    fun tag(secret: ByteArray, dayNumber: Long): Int {
        require(secret.size == SECRET_BYTES) { "Device tag secrets are $SECRET_BYTES bytes" }
        val mac = NearbyCryptoPrimitives.hmacSha256(secret, (LABEL + dayNumber).encodeToByteArray())
        var tag = 0
        for (index in 0 until TAG_BYTES) {
            tag = (tag shl 8) or (mac[index].toInt() and 0xFF)
        }
        return tag
    }

    fun acceptedTags(secret: ByteArray, now: Instant): Set<Int> {
        val today = dayNumber(now)
        return setOf(tag(secret, today - 1), tag(secret, today), tag(secret, today + 1))
    }

    fun composeInvitationNonce(randomHigh: Long, tag: Int): Long =
        (randomHigh shl 32) or (tag.toLong() and LOW_MASK)

    fun tagOf(invitationNonce: Long): Int = invitationNonce.toInt()

    fun invitationNonce(secret: ByteArray?, now: Instant = Clock.System.now()): Long {
        val random = NearbyCrypto.randomNonce()
        if (secret == null) return random
        return composeInvitationNonce(random ushr 32, tag(secret, dayNumber(now)))
    }
}
