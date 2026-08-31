package com.pocketpass.app.nearby

import kotlin.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCryptoTest {
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
}
