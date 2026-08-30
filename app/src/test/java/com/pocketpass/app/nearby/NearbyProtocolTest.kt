package com.pocketpass.app.nearby

import kotlin.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyProtocolTest {
    @Test
    fun helloRoundTripsWithoutChangingTranscriptBytes() {
        val hello = hello(invitationNonce = 42)

        val encoded = NearbyWireProtocol.encodeHello(hello)
        val decoded = NearbyWireProtocol.decodeHello(encoded)

        assertEquals(hello.invitationNonce, decoded.invitationNonce)
        assertArrayEquals(hello.credentialToken, decoded.credentialToken)
        assertArrayEquals(hello.signingPublicKey, decoded.signingPublicKey)
        assertArrayEquals(hello.agreementPublicKey, decoded.agreementPublicKey)
        assertArrayEquals(hello.challenge, decoded.challenge)
        assertArrayEquals(encoded, NearbyWireProtocol.encodeHello(decoded))
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
    fun transcriptOrderIsIndependentOfConnectionRole() {
        val first = NearbyWireProtocol.helloPacket(hello(invitationNonce = 4))
        val second = NearbyWireProtocol.helloPacket(hello(invitationNonce = 9))

        assertArrayEquals(
            NearbyCrypto.transcript(first, second),
            NearbyCrypto.transcript(second, first),
        )
    }

    @Test
    fun transcriptSignsTheRawHelloBytesAndToleratesUnknownFields() {
        val peer = NearbyWireProtocol.helloPacket(hello(invitationNonce = 1))
        val original = NearbyWireProtocol.helloPacket(hello(invitationNonce = 2))
        val extended = NearbyWireProtocol.decodeHelloPacket(
            original.bytes + byteArrayOf(0x78, 0x01),
        )

        assertEquals(original.hello.invitationNonce, extended.hello.invitationNonce)
        assertArrayEquals(original.hello.credentialToken, extended.hello.credentialToken)
        assertArrayEquals(original.hello.signingPublicKey, extended.hello.signingPublicKey)
        assertArrayEquals(original.hello.agreementPublicKey, extended.hello.agreementPublicKey)
        assertArrayEquals(original.hello.challenge, extended.hello.challenge)
        assertFalse(
            NearbyCrypto.transcript(peer, original)
                .contentEquals(NearbyCrypto.transcript(peer, extended)),
        )
    }

    @Test
    fun signaturesAndSessionEncryptionAreMutuallyVerifiable() {
        val firstSigning = NearbyCrypto.generateSigningKeyPair()
        val secondSigning = NearbyCrypto.generateSigningKeyPair()
        val firstAgreement = NearbyCrypto.generateAgreementKeyPair()
        val secondAgreement = NearbyCrypto.generateAgreementKeyPair()
        val first = NearbyWireProtocol.helloPacket(
            hello(
                invitationNonce = 3,
                signingPublicKey = firstSigning.public.encoded,
                agreementPublicKey = firstAgreement.public.encoded,
            ),
        )
        val second = NearbyWireProtocol.helloPacket(
            hello(
                invitationNonce = 7,
                signingPublicKey = secondSigning.public.encoded,
                agreementPublicKey = secondAgreement.public.encoded,
            ),
        )
        val transcriptHash = NearbyCrypto.sha256(NearbyCrypto.transcript(first, second))
        val firstSignature = NearbyCrypto.sign(firstSigning.private, transcriptHash)
        val secondSignature = NearbyCrypto.sign(secondSigning.private, transcriptHash)

        assertTrue(NearbyCrypto.verify(firstSigning.public, transcriptHash, firstSignature))
        assertTrue(NearbyCrypto.verify(secondSigning.public, transcriptHash, secondSignature))
        assertFalse(
            NearbyCrypto.verify(
                secondSigning.public,
                transcriptHash,
                firstSignature,
            ),
        )
        assertArrayEquals(
            firstSignature,
            NearbyWireProtocol.decodeSignature(NearbyWireProtocol.encodeSignature(firstSignature)),
        )

        val firstKey = NearbyCrypto.deriveSessionKey(
            firstAgreement.private,
            secondAgreement.public,
            transcriptHash,
        )
        val secondKey = NearbyCrypto.deriveSessionKey(
            secondAgreement.private,
            firstAgreement.public,
            transcriptHash,
        )
        assertArrayEquals(firstKey, secondKey)

        val occurredAt = Instant.fromEpochSeconds(123)
        val plaintext = NearbyWireProtocol.encodeConfirmation(
            ownToken = first.hello.credentialToken,
            peerToken = second.hello.credentialToken,
            occurredAt = occurredAt,
            transcriptHash = transcriptHash,
        )
        val aad = NearbyCrypto.confirmationAad(transcriptHash, first.hello.invitationNonce)
        val encrypted = NearbyWireProtocol.decodeEncrypted(
            NearbyWireProtocol.encodeEncrypted(NearbyCrypto.encrypt(firstKey, plaintext, aad)),
        )
        val confirmation = NearbyWireProtocol.decodeConfirmation(
            NearbyCrypto.decrypt(secondKey, encrypted, aad),
        )
        assertArrayEquals(first.hello.credentialToken, confirmation.ownToken)
        assertArrayEquals(second.hello.credentialToken, confirmation.peerToken)
        assertEquals(occurredAt, confirmation.occurredAt)
        assertArrayEquals(transcriptHash, confirmation.transcriptHash)
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
        assertArrayEquals(packet, decoded)
    }

    @Test
    fun packetsWithBadMagicAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))
        packet[0] = 0

        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.decodeHello(packet)
        }
    }

    @Test
    fun packetsFromOtherProtocolVersionsAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))
        packet[4] = 1

        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.decodeHello(packet)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.packetType(packet)
        }
    }

    @Test
    fun packetsOfUnexpectedTypeAreRejected() {
        val packet = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1))

        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.decodeSignature(packet)
        }
    }

    @Test
    fun helloWithMissingFieldsIsRejected() {
        val header = NearbyWireProtocol.encodeHello(hello(invitationNonce = 1)).copyOf(6)

        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.decodeHello(header)
        }
    }

    @Test
    fun oversizedSignaturesAreRejected() {
        val header = NearbyWireProtocol.encodeSignature(ByteArray(64)).copyOf(6)
        val oversized = header + byteArrayOf(0x0A, 0x81.toByte(), 0x02) + ByteArray(257)

        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.decodeSignature(oversized)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbyWireProtocol.encodeSignature(ByteArray(257))
        }
    }

    private fun hello(
        invitationNonce: Long,
        signingPublicKey: ByteArray = NearbyCrypto.generateSigningKeyPair().public.encoded,
        agreementPublicKey: ByteArray =
            NearbyCrypto.generateAgreementKeyPair().public.encoded,
    ): NearbyHello = NearbyHello(
        invitationNonce = invitationNonce,
        credentialToken = NearbyCrypto.randomBytes(NearbyCredential.TOKEN_BYTES),
        signingPublicKey = signingPublicKey,
        agreementPublicKey = agreementPublicKey,
        challenge = NearbyCrypto.randomBytes(NearbyHello.CHALLENGE_BYTES),
    )

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
}
