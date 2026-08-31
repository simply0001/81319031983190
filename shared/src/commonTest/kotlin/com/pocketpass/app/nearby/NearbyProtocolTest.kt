package com.pocketpass.app.nearby

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NearbyProtocolTest {
    @Test
    fun helloRoundTripsWithoutChangingTranscriptBytes() {
        val hello = hello(invitationNonce = 42)

        val encoded = NearbyWireProtocol.encodeHello(hello)
        val decoded = NearbyWireProtocol.decodeHello(encoded)

        assertEquals(hello.invitationNonce, decoded.invitationNonce)
        assertContentEquals(hello.credentialToken, decoded.credentialToken)
        assertContentEquals(hello.signingPublicKey, decoded.signingPublicKey)
        assertContentEquals(hello.agreementPublicKey, decoded.agreementPublicKey)
        assertContentEquals(hello.challenge, decoded.challenge)
        assertContentEquals(encoded, NearbyWireProtocol.encodeHello(decoded))
    }

    @Test
    fun helloEncodingMatchesTheGoldenVector() {
        val hello = NearbyHello(
            invitationNonce = 0x0102030405060708L,
            credentialToken = ByteArray(16) { 0x11 },
            signingPublicKey = ByteArray(64) { 0x22 },
            agreementPublicKey = ByteArray(64) { 0x33 },
            challenge = ByteArray(16) { 0x44 },
        )

        val expected = "50504E31" + "02" + "01" +
            "09" + "0807060504030201" +
            "12" + "10" + "11".repeat(16) +
            "1A" + "40" + "22".repeat(64) +
            "22" + "40" + "33".repeat(64) +
            "2A" + "10" + "44".repeat(16)

        assertEquals(expected, NearbyWireProtocol.encodeHello(hello).toHex())
    }

    @Test
    fun decodingToleratesUnknownTrailingFields() {
        val original = NearbyWireProtocol.helloPacket(hello(invitationNonce = 2))
        val extended = NearbyWireProtocol.decodeHelloPacket(
            original.bytes + byteArrayOf(0x78, 0x01),
        )

        assertEquals(original.hello.invitationNonce, extended.hello.invitationNonce)
        assertContentEquals(original.hello.credentialToken, extended.hello.credentialToken)
        assertContentEquals(original.hello.signingPublicKey, extended.hello.signingPublicKey)
        assertContentEquals(original.hello.agreementPublicKey, extended.hello.agreementPublicKey)
        assertContentEquals(original.hello.challenge, extended.hello.challenge)
    }

    @Test
    fun signatureAndConfirmationPacketsRoundTrip() {
        val signature = ByteArray(64) { index -> index.toByte() }
        assertContentEquals(
            signature,
            NearbyWireProtocol.decodeSignature(NearbyWireProtocol.encodeSignature(signature)),
        )

        val occurredAt = kotlin.time.Instant.fromEpochSeconds(123)
        val transcriptHash = ByteArray(32) { 0x55 }
        val ownToken = ByteArray(16) { 0x66 }
        val peerToken = ByteArray(16) { 0x77 }
        val confirmation = NearbyWireProtocol.decodeConfirmation(
            NearbyWireProtocol.encodeConfirmation(
                ownToken = ownToken,
                peerToken = peerToken,
                occurredAt = occurredAt,
                transcriptHash = transcriptHash,
            ),
        )

        assertContentEquals(ownToken, confirmation.ownToken)
        assertContentEquals(peerToken, confirmation.peerToken)
        assertEquals(occurredAt, confirmation.occurredAt)
        assertContentEquals(transcriptHash, confirmation.transcriptHash)
    }

    @Test
    fun framingRoundTripsAtTheDefaultTwentyByteAttPayload() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 99))
        val fragments = NearbyBleFraming.fragment(
            messageId = 5,
            packet = packet,
            attPayloadBytes = NearbyBleFraming.DEFAULT_ATT_PAYLOAD_BYTES,
        )
        val reassembler = NearbyBleFraming.Reassembler()
        var decoded: ByteArray? = null

        fragments.forEach { fragment ->
            decoded = reassembler.accept(fragment) ?: decoded
        }

        assertTrue(fragments.size > 1)
        assertContentEquals(packet, decoded)
    }

    @Test
    fun packetsWithBadMagicAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))
        packet[0] = 0

        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.decodeHello(packet)
        }
    }

    @Test
    fun packetsFromOtherProtocolVersionsAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))
        packet[4] = 1

        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.decodeHello(packet)
        }
        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.packetType(packet)
        }
    }

    @Test
    fun packetsOfUnexpectedTypeAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))

        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.decodeSignature(packet)
        }
    }

    @Test
    fun helloWithMissingFieldsIsRejected() {
        val header = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1)).copyOf(6)

        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.decodeHello(header)
        }
    }

    @Test
    fun oversizedSignaturesAreRejected() {
        val header = NearbyWireProtocol.encodeSignature(ByteArray(64)).copyOf(6)
        val oversized = header + byteArrayOf(0x0A, 0x81.toByte(), 0x02) + ByteArray(257)

        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.decodeSignature(oversized)
        }
        assertFailsWith<IllegalArgumentException> {
            NearbyWireProtocol.encodeSignature(ByteArray(257))
        }
    }

    private fun hello(invitationNonce: Long): NearbyHello = NearbyHello(
        invitationNonce = invitationNonce,
        credentialToken = Random.nextBytes(NearbyCredential.TOKEN_BYTES),
        signingPublicKey = Random.nextBytes(91),
        agreementPublicKey = Random.nextBytes(91),
        challenge = Random.nextBytes(NearbyHello.CHALLENGE_BYTES),
    )

    private fun ByteArray.toHex(): String =
        joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
}
