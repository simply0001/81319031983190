package com.pocketpass.app.nearby

import com.pocketpass.app.data.local.dao.OutboxDao
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.data.repository.PendingOperationScheduler
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.security.SecureStringStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.time.Instant
import java.util.Base64

class NearbyProofOutboxStore(
    private val outboxDao: OutboxDao,
    private val secureStore: SecureStringStore,
    private val scheduler: PendingOperationScheduler = PendingOperationScheduler.None,
) {
    suspend fun enqueue(
        accountId: UserId,
        proof: NearbyEncounterProof,
    ): Boolean {
        val command = proof.toCommand(accountId)
        val operationId = command.clientOperationId.value
        val secureEntryKey = secureEntryKey(operationId)
        secureStore.put(secureEntryKey, NearbyProofPayloadCodec.encode(command))

        val inserted = outboxDao.enqueueOperation(
            PendingOperationEntity(
                operationId = operationId,
                accountId = accountId.value,
                idempotencyKey = operationId,
                kind = OPERATION_KIND,
                aggregateId = command.encounterId.value,
                payload = secureEntryKey,
                payloadVersion = PAYLOAD_REFERENCE_VERSION,
                state = LocalOutboxStates.PENDING,
                attemptCount = 0,
                createdAtEpochMillis = command.occurredAt.toEpochMilliseconds(),
                nextAttemptAtEpochMillis = command.occurredAt.toEpochMilliseconds(),
                leaseUntilEpochMillis = null,
                leaseToken = null,
                completedAtEpochMillis = null,
                lastErrorCode = null,
                lastErrorMessage = null,
            ),
        )
        if (inserted == -1L && outboxDao.get(operationId) == null) {
            secureStore.remove(secureEntryKey)
            return false
        }
        scheduler.schedule(accountId)
        return true
    }

    companion object {
        const val OPERATION_KIND = "SUBMIT_NEARBY_ENCOUNTER"
        const val PAYLOAD_REFERENCE_VERSION = 1
        private const val SECURE_ENTRY_PREFIX = "nearby.proof."

        fun requireSecureEntryKey(operation: PendingOperationEntity): String {
            require(operation.payloadVersion == PAYLOAD_REFERENCE_VERSION)
            require(operation.payload == secureEntryKey(operation.operationId))
            return operation.payload
        }

        private fun secureEntryKey(operationId: String): String =
            "$SECURE_ENTRY_PREFIX$operationId"
    }
}

object NearbyProofPayloadCodec {
    private const val VERSION = 1

    fun encode(command: SubmitNearbyEncounterCommand): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(VERSION)
                output.writeUTF(command.accountId.value)
                output.writeUTF(command.encounterId.value)
                output.writeUTF(command.clientOperationId.value)
                output.writeUTF(command.ownToken)
                output.writeUTF(command.peerToken)
                output.writeUTF(command.ownSigningPublicKey)
                output.writeUTF(command.peerSigningPublicKey)
                output.writeUTF(command.transcriptHash)
                output.writeUTF(command.ownSignature)
                output.writeUTF(command.peerSignature)
                output.writeLong(command.occurredAt.toEpochMilliseconds())
            }
            buffer.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(encoded: String): SubmitNearbyEncounterCommand {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == VERSION)
            val command = SubmitNearbyEncounterCommand(
                accountId = UserId(input.readUTF()),
                encounterId = EncounterId(input.readUTF()),
                clientOperationId = ClientOperationId(input.readUTF()),
                ownToken = input.readUTF(),
                peerToken = input.readUTF(),
                ownSigningPublicKey = input.readUTF(),
                peerSigningPublicKey = input.readUTF(),
                transcriptHash = input.readUTF(),
                ownSignature = input.readUTF(),
                peerSignature = input.readUTF(),
                occurredAt = Instant.fromEpochMilliseconds(input.readLong()),
            )
            require(input.available() == 0)
            command
        }
    }
}

private fun NearbyEncounterProof.toCommand(
    accountId: UserId,
): SubmitNearbyEncounterCommand {
    val operationId = ClientOperationId(encounterId)
    return SubmitNearbyEncounterCommand(
        accountId = accountId,
        encounterId = EncounterId(encounterId),
        clientOperationId = operationId,
        ownToken = NearbyCredentialPool.bytesToUuid(ownToken).toString(),
        peerToken = NearbyCredentialPool.bytesToUuid(peerToken).toString(),
        ownSigningPublicKey = NearbyCredentialPool.encode(ownSigningPublicKey),
        peerSigningPublicKey = NearbyCredentialPool.encode(peerSigningPublicKey),
        transcriptHash = NearbyCredentialPool.encode(transcriptHash),
        ownSignature = NearbyCredentialPool.encode(ownTranscriptSignature),
        peerSignature = NearbyCredentialPool.encode(peerTranscriptSignature),
        occurredAt = occurredAt,
    )
}
