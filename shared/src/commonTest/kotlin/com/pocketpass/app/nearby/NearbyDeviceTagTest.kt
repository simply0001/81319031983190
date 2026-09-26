package com.pocketpass.app.nearby

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class NearbyDeviceTagTest {
    private val secret = ByteArray(NearbyDeviceTag.SECRET_BYTES) { (it * 7 + 3).toByte() }
    private val otherSecret = ByteArray(NearbyDeviceTag.SECRET_BYTES) { (it * 11 + 5).toByte() }
    private val noon = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun daysAreCountedInUtc() {
        assertEquals(20_704L, NearbyDeviceTag.dayNumber(noon))
        assertEquals(20_704L, NearbyDeviceTag.dayNumber(Instant.parse("2026-09-08T23:59:59Z")))
        assertEquals(20_705L, NearbyDeviceTag.dayNumber(Instant.parse("2026-09-09T00:00:00Z")))
        assertEquals(12 * 3_600_000L, NearbyDeviceTag.millisUntilNextDay(noon))
    }

    @Test
    fun tagsAreStablePerSecretAndDayAndDifferOtherwise() {
        val today = NearbyDeviceTag.dayNumber(noon)
        assertEquals(NearbyDeviceTag.tag(secret, today), NearbyDeviceTag.tag(secret, today))
        assertNotEquals(NearbyDeviceTag.tag(secret, today), NearbyDeviceTag.tag(secret, today + 1))
        assertNotEquals(NearbyDeviceTag.tag(secret, today), NearbyDeviceTag.tag(otherSecret, today))
        assertFailsWith<IllegalArgumentException> { NearbyDeviceTag.tag(ByteArray(16), today) }
    }

    @Test
    fun acceptedTagsCoverTheNeighbouringDays() {
        val today = NearbyDeviceTag.dayNumber(noon)
        val accepted = NearbyDeviceTag.acceptedTags(secret, noon)
        assertEquals(3, accepted.size)
        assertTrue(NearbyDeviceTag.tag(secret, today - 1) in accepted)
        assertTrue(NearbyDeviceTag.tag(secret, today) in accepted)
        assertTrue(NearbyDeviceTag.tag(secret, today + 1) in accepted)
        assertFalse(NearbyDeviceTag.tag(secret, today + 2) in accepted)
        assertFalse(NearbyDeviceTag.tag(otherSecret, today) in accepted)
    }

    @Test
    fun theTagRidesInTheLowHalfOfTheInvitationNonce() {
        val tag = NearbyDeviceTag.tag(secret, NearbyDeviceTag.dayNumber(noon))
        val nonce = NearbyDeviceTag.composeInvitationNonce(0x1234_5678L, tag)
        assertEquals(tag, NearbyDeviceTag.tagOf(nonce))
        assertEquals(0x1234_5678L, nonce ushr 32)
        assertEquals(-1, NearbyDeviceTag.tagOf(NearbyDeviceTag.composeInvitationNonce(0L, -1)))
    }

    @Test
    fun taggedNoncesRecogniseTheirOwnAccountAndOnlyThat() {
        val nonce = NearbyDeviceTag.invitationNonce(secret, noon)
        val other = NearbyDeviceTag.invitationNonce(otherSecret, noon)
        assertTrue(NearbyDeviceTag.tagOf(nonce) in NearbyDeviceTag.acceptedTags(secret, noon))
        assertFalse(NearbyDeviceTag.tagOf(other) in NearbyDeviceTag.acceptedTags(secret, noon))
        assertNotEquals(nonce ushr 32, NearbyDeviceTag.invitationNonce(secret, noon) ushr 32)
    }

    @Test
    fun withoutASecretTheNonceStaysFullyRandom() {
        val first = NearbyDeviceTag.invitationNonce(null, noon)
        val second = NearbyDeviceTag.invitationNonce(null, noon)
        assertNotEquals(first, second)
    }
}
