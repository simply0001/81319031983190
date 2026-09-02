package com.pocketpass.app.nearby

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class NearbyLinkRole {
    Central,
    Peripheral,
}

/**
 * The transport-independent half of one street-pass exchange: hello and
 * signature exchange, session-key agreement, encrypted confirmation, proof.
 * Each BLE link owns one session; the transport feeds reassembled packets in
 * and carries the returned packets out. Callers must not interleave calls to
 * one session from different threads.
 */
class NearbyHandshakeSession(
    private val role: NearbyLinkRole,
    private val invitationNonce: Long,
    private val advertisedPeerNonce: Long?,
    private val clock: Clock = Clock.System,
) {
    sealed interface Event {
        class SendPacket(val packet: ByteArray) : Event
        class ProofReady(val proof: NearbyEncounterProof) : Event
        class Failed(val message: String) : Event
    }

    private var credential: NearbyCredential? = null
    private var agreementKeyPair: NearbyKeyPair? = null
    private var localHello: NearbyHelloPacket? = null
    private var remoteHello: NearbyHelloPacket? = null
    private var localHelloSent = false
    private var transcriptHash: ByteArray? = null
    private var localSignature: ByteArray? = null
    private var remoteSignature: ByteArray? = null
    private var sessionKey: ByteArray? = null
    private var localConfirmationSent = false
    private var remoteConfirmation: NearbyConfirmation? = null
    private var proofEmitted = false
    private var transportReady = false

    fun attachCredential(credential: NearbyCredential): List<Event> {
        if (!credential.isUsableAt(clock.now())) {
            return listOf(Event.Failed("The anonymous encounter pass expired."))
        }
        this.credential = credential
        val keyPair = NearbyCrypto.generateAgreementKeyPair()
        agreementKeyPair = keyPair
        localHello = NearbyWireProtocol.helloPacket(
            NearbyHello(
                invitationNonce = invitationNonce,
                credentialToken = credential.token,
                signingPublicKey = credential.signingPublicKey,
                agreementPublicKey = keyPair.publicKeyDer,
                challenge = NearbyCrypto.randomBytes(NearbyHello.CHALLENGE_BYTES),
            ),
        )
        return progress()
    }

    fun onTransportReady(): List<Event> {
        transportReady = true
        return progress()
    }

    fun onPacket(packet: ByteArray): List<Event> {
        try {
            when (NearbyWireProtocol.packetType(packet)) {
                PACKET_HELLO -> {
                    val hello = NearbyWireProtocol.decodeHelloPacket(packet)
                    if (
                        advertisedPeerNonce != null &&
                        hello.hello.invitationNonce != advertisedPeerNonce
                    ) {
                        throw IllegalArgumentException("Invitation nonce mismatch")
                    }
                    remoteHello = hello
                }

                PACKET_SIGNATURE -> {
                    val signature = NearbyWireProtocol.decodeSignature(packet)
                    val hash = requireNotNull(transcriptHash)
                    val remoteKey = requireNotNull(remoteHello).hello.signingPublicKey
                    require(NearbyCrypto.verify(remoteKey, hash, signature))
                    remoteSignature = signature
                }

                PACKET_ENCRYPTED -> {
                    val remote = requireNotNull(remoteHello).hello
                    val local = requireNotNull(localHello).hello
                    val hash = requireNotNull(transcriptHash)
                    val key = requireNotNull(sessionKey)
                    val plaintext = NearbyCrypto.decrypt(
                        key = key,
                        packet = NearbyWireProtocol.decodeEncrypted(packet),
                        aad = NearbyCrypto.confirmationAad(hash, remote.invitationNonce),
                    )
                    val confirmation = NearbyWireProtocol.decodeConfirmation(plaintext)
                    require(confirmation.ownToken.contentEquals(remote.credentialToken))
                    require(confirmation.peerToken.contentEquals(local.credentialToken))
                    require(confirmation.transcriptHash.contentEquals(hash))
                    require(
                        (clock.now() - confirmation.occurredAt).absoluteValue <= MAX_CLOCK_SKEW,
                    )
                    remoteConfirmation = confirmation
                }

                else -> throw IllegalArgumentException("Unknown packet type")
            }
        } catch (_: Throwable) {
            return listOf(Event.Failed("The encrypted PocketPass handshake was rejected."))
        }
        return progress()
    }

    fun close() {
        sessionKey?.fill(0)
        sessionKey = null
    }

    /** The attached pass while its token has not been sent to anyone yet. */
    fun unexposedCredential(): NearbyCredential? = if (localHelloSent) null else credential

    @OptIn(ExperimentalUuidApi::class)
    private fun progress(): List<Event> {
        val events = mutableListOf<Event>()
        val localHelloPacket = localHello ?: return events
        if (role == NearbyLinkRole.Central && !transportReady) return events
        val remoteHelloPacket = remoteHello

        if (!localHelloSent) {
            if (role == NearbyLinkRole.Central || remoteHelloPacket != null) {
                localHelloSent = true
                events += Event.SendPacket(localHelloPacket.bytes)
            } else {
                return events
            }
        }
        if (remoteHelloPacket == null) return events
        val local = localHelloPacket.hello
        val remote = remoteHelloPacket.hello

        if (transcriptHash == null) {
            transcriptHash = NearbyCrypto.sha256(
                NearbyCrypto.transcript(localHelloPacket, remoteHelloPacket),
            )
        }
        val hash = requireNotNull(transcriptHash)
        if (localSignature == null) {
            val activeCredential = requireNotNull(credential)
            localSignature = NearbyCrypto.sign(activeCredential.signingPrivateKey, hash)
            events += Event.SendPacket(
                NearbyWireProtocol.encodeSignature(requireNotNull(localSignature)),
            )
        }
        if (remoteSignature == null) return events

        if (sessionKey == null) {
            sessionKey = NearbyCrypto.deriveSessionKey(
                ownPrivateKeyDer = requireNotNull(agreementKeyPair).privateKeyDer,
                peerPublicKeyDer = remote.agreementPublicKey,
                transcriptHash = hash,
            )
        }
        if (!localConfirmationSent) {
            localConfirmationSent = true
            val confirmation = NearbyWireProtocol.encodeConfirmation(
                ownToken = local.credentialToken,
                peerToken = remote.credentialToken,
                occurredAt = clock.now(),
                transcriptHash = hash,
            )
            val encrypted = NearbyCrypto.encrypt(
                key = requireNotNull(sessionKey),
                plaintext = confirmation,
                aad = NearbyCrypto.confirmationAad(hash, local.invitationNonce),
            )
            events += Event.SendPacket(NearbyWireProtocol.encodeEncrypted(encrypted))
        }
        if (remoteConfirmation != null && !proofEmitted) {
            proofEmitted = true
            events += Event.ProofReady(
                NearbyEncounterProof(
                    encounterId = Uuid.random().toString(),
                    ownToken = local.credentialToken,
                    peerToken = remote.credentialToken,
                    ownSigningPublicKey = local.signingPublicKey,
                    peerSigningPublicKey = remote.signingPublicKey,
                    ownTranscriptSignature = requireNotNull(localSignature),
                    peerTranscriptSignature = requireNotNull(remoteSignature),
                    transcriptHash = hash,
                    occurredAt = requireNotNull(remoteConfirmation).occurredAt,
                ),
            )
        }
        return events
    }

    private companion object {
        const val PACKET_HELLO = 1
        const val PACKET_SIGNATURE = 2
        const val PACKET_ENCRYPTED = 4
        val MAX_CLOCK_SKEW = 5.minutes
    }
}
