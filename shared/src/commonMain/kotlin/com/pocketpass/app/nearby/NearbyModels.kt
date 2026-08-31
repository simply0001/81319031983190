@file:OptIn(ExperimentalSerializationApi::class)

package com.pocketpass.app.nearby

import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType

enum class NearbyRuntimeStatus {
    Disabled,
    NeedsOnboarding,
    NeedsPermissions,
    BluetoothOff,
    Starting,
    Running,
    Unsupported,
    Error,
}

data class NearbyRuntimeState(
    val status: NearbyRuntimeStatus = NearbyRuntimeStatus.Disabled,
    val detail: String? = null,
    val activeExchangeCount: Int = 0,
    val lastEncounterAt: Instant? = null,
) {
    val isOperational: Boolean
        get() = status == NearbyRuntimeStatus.Running
}

data class NearbyCredential(
    val token: ByteArray,
    val signingPublicKey: ByteArray,
    val signingPrivateKey: ByteArray,
    val expiresAt: Instant,
) {
    init {
        require(token.size == TOKEN_BYTES) { "Encounter token must be 128 bits" }
        require(signingPublicKey.isNotEmpty()) { "Signing public key is required" }
        require(signingPrivateKey.isNotEmpty()) { "Signing private key is required" }
    }

    fun isUsableAt(now: Instant): Boolean = expiresAt > now

    companion object {
        const val TOKEN_BYTES = 16
    }
}

@Serializable
data class NearbyHello(
    @ProtoNumber(1) @ProtoType(ProtoIntegerType.FIXED) val invitationNonce: Long,
    @ProtoNumber(2) val credentialToken: ByteArray,
    @ProtoNumber(3) val signingPublicKey: ByteArray,
    @ProtoNumber(4) val agreementPublicKey: ByteArray,
    @ProtoNumber(5) val challenge: ByteArray,
) {
    init {
        require(credentialToken.size == NearbyCredential.TOKEN_BYTES)
        require(signingPublicKey.size in 64..MAX_PUBLIC_KEY_BYTES)
        require(agreementPublicKey.size in 64..MAX_PUBLIC_KEY_BYTES)
        require(challenge.size == CHALLENGE_BYTES)
    }

    companion object {
        const val CHALLENGE_BYTES = 16
        const val MAX_PUBLIC_KEY_BYTES = 256
    }
}

data class NearbyHelloPacket(
    val hello: NearbyHello,
    val bytes: ByteArray,
)

@Serializable
internal data class NearbySignature(
    @ProtoNumber(1) val signature: ByteArray,
) {
    init {
        require(signature.size in 1..MAX_BYTES)
    }

    companion object {
        const val MAX_BYTES = 256
    }
}

@Serializable
data class NearbyConfirmation(
    @ProtoNumber(1) val ownToken: ByteArray,
    @ProtoNumber(2) val peerToken: ByteArray,
    @ProtoNumber(3) val occurredAtEpochMillis: Long,
    @ProtoNumber(4) val transcriptHash: ByteArray,
) {
    init {
        require(ownToken.size == NearbyCredential.TOKEN_BYTES)
        require(peerToken.size == NearbyCredential.TOKEN_BYTES)
        require(transcriptHash.size == 32)
    }

    val occurredAt: Instant
        get() = Instant.fromEpochMilliseconds(occurredAtEpochMillis)
}

data class NearbyEncounterProof(
    val encounterId: String,
    val ownToken: ByteArray,
    val peerToken: ByteArray,
    val ownSigningPublicKey: ByteArray,
    val peerSigningPublicKey: ByteArray,
    val ownTranscriptSignature: ByteArray,
    val peerTranscriptSignature: ByteArray,
    val transcriptHash: ByteArray,
    val occurredAt: Instant,
) {
    init {
        require(encounterId.isNotBlank())
        require(ownToken.size == NearbyCredential.TOKEN_BYTES)
        require(peerToken.size == NearbyCredential.TOKEN_BYTES)
        require(transcriptHash.size == 32)
    }
}

@Serializable
data class NearbyEncryptedPacket(
    @ProtoNumber(1) val iv: ByteArray,
    @ProtoNumber(2) val ciphertext: ByteArray,
) {
    init {
        require(iv.size == 12)
        require(ciphertext.size in 16..NearbyWireProtocol.MAX_PACKET_BYTES)
    }
}
