package com.pocketpass.app.nearby

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class NearbyHandshakeSessionTest {
    @Test
    fun twoSessionsCompleteAnExchangeAndAgreeOnTheProof() {
        val centralNonce = 9L
        val peripheralNonce = 4L
        val central = NearbyHandshakeSession(
            role = NearbyLinkRole.Central,
            invitationNonce = centralNonce,
            advertisedPeerNonce = peripheralNonce,
        )
        val peripheral = NearbyHandshakeSession(
            role = NearbyLinkRole.Peripheral,
            invitationNonce = peripheralNonce,
            advertisedPeerNonce = null,
        )

        val outcome = pump(central, peripheral) {
            val toPeripheral = ArrayDeque<ByteArray>()
            val toCentral = ArrayDeque<ByteArray>()
            record(central, central.attachCredential(credential()), toPeripheral)
            record(peripheral, peripheral.attachCredential(credential()), toCentral)
            record(central, central.onTransportReady(), toPeripheral)
            toPeripheral to toCentral
        }

        val centralProof = assertNotNull(outcome.proofs[central])
        val peripheralProof = assertNotNull(outcome.proofs[peripheral])
        assertContentEquals(centralProof.ownToken, peripheralProof.peerToken)
        assertContentEquals(centralProof.peerToken, peripheralProof.ownToken)
        assertContentEquals(centralProof.transcriptHash, peripheralProof.transcriptHash)
        assertContentEquals(
            centralProof.ownTranscriptSignature,
            peripheralProof.peerTranscriptSignature,
        )
        assertTrue(
            NearbyCrypto.verify(
                centralProof.peerSigningPublicKey,
                centralProof.transcriptHash,
                centralProof.peerTranscriptSignature,
            ),
        )
        assertTrue(
            NearbyCrypto.verify(
                peripheralProof.peerSigningPublicKey,
                peripheralProof.transcriptHash,
                peripheralProof.peerTranscriptSignature,
            ),
        )
        assertEquals(0, outcome.failures.size)
    }

    @Test
    fun aNonceMismatchAgainstTheAdvertisementFailsTheSession() {
        val central = NearbyHandshakeSession(
            role = NearbyLinkRole.Central,
            invitationNonce = 9L,
            advertisedPeerNonce = 555L,
        )
        val peripheral = NearbyHandshakeSession(
            role = NearbyLinkRole.Peripheral,
            invitationNonce = 4L,
            advertisedPeerNonce = null,
        )

        val outcome = pump(central, peripheral) {
            val toPeripheral = ArrayDeque<ByteArray>()
            val toCentral = ArrayDeque<ByteArray>()
            record(central, central.attachCredential(credential()), toPeripheral)
            record(peripheral, peripheral.attachCredential(credential()), toCentral)
            record(central, central.onTransportReady(), toPeripheral)
            toPeripheral to toCentral
        }

        assertTrue(outcome.proofs.isEmpty())
        assertTrue(outcome.failures.isNotEmpty())
    }

    @Test
    fun anExpiredCredentialFailsImmediately() {
        val session = NearbyHandshakeSession(
            role = NearbyLinkRole.Central,
            invitationNonce = 1L,
            advertisedPeerNonce = null,
        )

        val events = session.attachCredential(
            credential(expiresAt = Clock.System.now() - 1.hours),
        )

        assertTrue(events.single() is NearbyHandshakeSession.Event.Failed)
    }

    private class Outcome(
        val proofs: MutableMap<NearbyHandshakeSession, NearbyEncounterProof> = mutableMapOf(),
        val failures: MutableList<String> = mutableListOf(),
    )

    private var outcome = Outcome()

    private fun record(
        session: NearbyHandshakeSession,
        events: List<NearbyHandshakeSession.Event>,
        outbox: ArrayDeque<ByteArray>,
    ) {
        events.forEach { event ->
            when (event) {
                is NearbyHandshakeSession.Event.SendPacket -> outbox.addLast(event.packet)
                is NearbyHandshakeSession.Event.ProofReady ->
                    outcome.proofs[session] = event.proof

                is NearbyHandshakeSession.Event.Failed -> outcome.failures += event.message
            }
        }
    }

    private fun pump(
        central: NearbyHandshakeSession,
        peripheral: NearbyHandshakeSession,
        seed: () -> Pair<ArrayDeque<ByteArray>, ArrayDeque<ByteArray>>,
    ): Outcome {
        outcome = Outcome()
        val (toPeripheral, toCentral) = seed()
        var budget = 32
        while ((toPeripheral.isNotEmpty() || toCentral.isNotEmpty()) && budget-- > 0) {
            toPeripheral.removeFirstOrNull()?.let { packet ->
                record(peripheral, peripheral.onPacket(packet), toCentral)
            }
            toCentral.removeFirstOrNull()?.let { packet ->
                record(central, central.onPacket(packet), toPeripheral)
            }
        }
        return outcome
    }

    private fun credential(
        expiresAt: kotlin.time.Instant = Clock.System.now() + 1.hours,
    ): NearbyCredential {
        val keyPair = NearbyCrypto.generateSigningKeyPair()
        return NearbyCredential(
            token = NearbyCrypto.randomBytes(NearbyCredential.TOKEN_BYTES),
            signingPublicKey = keyPair.publicKeyDer,
            signingPrivateKey = keyPair.privateKeyDer,
            expiresAt = expiresAt,
        )
    }
}
