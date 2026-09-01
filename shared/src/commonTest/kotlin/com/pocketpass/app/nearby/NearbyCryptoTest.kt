package com.pocketpass.app.nearby

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class NearbyCryptoTest {
    @Test
    fun transcriptOrderIsIndependentOfConnectionRole() {
        val first = NearbyWireProtocol.helloPacket(hello(invitationNonce = 4))
        val second = NearbyWireProtocol.helloPacket(hello(invitationNonce = 9))

        assertContentEquals(
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
        assertContentEquals(original.hello.credentialToken, extended.hello.credentialToken)
        assertContentEquals(original.hello.signingPublicKey, extended.hello.signingPublicKey)
        assertContentEquals(original.hello.agreementPublicKey, extended.hello.agreementPublicKey)
        assertContentEquals(original.hello.challenge, extended.hello.challenge)
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
                signingPublicKey = firstSigning.publicKeyDer,
                agreementPublicKey = firstAgreement.publicKeyDer,
            ),
        )
        val second = NearbyWireProtocol.helloPacket(
            hello(
                invitationNonce = 7,
                signingPublicKey = secondSigning.publicKeyDer,
                agreementPublicKey = secondAgreement.publicKeyDer,
            ),
        )
        val transcriptHash = NearbyCrypto.sha256(NearbyCrypto.transcript(first, second))
        val firstSignature = NearbyCrypto.sign(firstSigning.privateKeyDer, transcriptHash)
        val secondSignature = NearbyCrypto.sign(secondSigning.privateKeyDer, transcriptHash)

        assertTrue(NearbyCrypto.verify(firstSigning.publicKeyDer, transcriptHash, firstSignature))
        assertTrue(NearbyCrypto.verify(secondSigning.publicKeyDer, transcriptHash, secondSignature))
        assertFalse(
            NearbyCrypto.verify(
                secondSigning.publicKeyDer,
                transcriptHash,
                firstSignature,
            ),
        )
        assertContentEquals(
            firstSignature,
            NearbyWireProtocol.decodeSignature(NearbyWireProtocol.encodeSignature(firstSignature)),
        )

        val firstKey = NearbyCrypto.deriveSessionKey(
            firstAgreement.privateKeyDer,
            secondAgreement.publicKeyDer,
            transcriptHash,
        )
        val secondKey = NearbyCrypto.deriveSessionKey(
            secondAgreement.privateKeyDer,
            firstAgreement.publicKeyDer,
            transcriptHash,
        )
        assertContentEquals(firstKey, secondKey)

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
        assertContentEquals(first.hello.credentialToken, confirmation.ownToken)
        assertContentEquals(second.hello.credentialToken, confirmation.peerToken)
        assertEquals(occurredAt, confirmation.occurredAt)
        assertContentEquals(transcriptHash, confirmation.transcriptHash)
    }

    private fun hello(
        invitationNonce: Long,
        signingPublicKey: ByteArray = NearbyCrypto.generateSigningKeyPair().publicKeyDer,
        agreementPublicKey: ByteArray =
            NearbyCrypto.generateAgreementKeyPair().publicKeyDer,
    ): NearbyHello = NearbyHello(
        invitationNonce = invitationNonce,
        credentialToken = NearbyCrypto.randomBytes(NearbyCredential.TOKEN_BYTES),
        signingPublicKey = signingPublicKey,
        agreementPublicKey = agreementPublicKey,
        challenge = NearbyCrypto.randomBytes(NearbyHello.CHALLENGE_BYTES),
    )
}
