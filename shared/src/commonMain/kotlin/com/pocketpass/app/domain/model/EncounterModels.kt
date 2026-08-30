package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class IssuedNearbyCredential(
    val token: String,
    val signingPublicKey: String,
    val expiresAt: Instant,
)

data class SubmitNearbyEncounterCommand(
    val accountId: UserId,
    val encounterId: EncounterId,
    val clientOperationId: ClientOperationId,
    val ownToken: String,
    val peerToken: String,
    val ownSigningPublicKey: String,
    val peerSigningPublicKey: String,
    val transcriptHash: String,
    val ownSignature: String,
    val peerSignature: String,
    val occurredAt: Instant,
)
