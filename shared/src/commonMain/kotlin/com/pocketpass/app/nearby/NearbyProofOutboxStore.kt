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
import com.pocketpass.app.sync.OUTBOX_BASE64
import com.pocketpass.app.sync.readLengthPrefixedUtf
import com.pocketpass.app.sync.writeLengthPrefixedUtf
import kotlin.time.Instant
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

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
        val buffer = Buffer().apply {
            writeInt(VERSION)
            writeLengthPrefixedUtf(command.accountId.value)
            writeLengthPrefixedUtf(command.encounterId.value)
            writeLengthPrefixedUtf(command.clientOperationId.value)
            writeLengthPrefixedUtf(command.ownToken)
            writeLengthPrefixedUtf(command.peerToken)
            writeLengthPrefixedUtf(command.ownSigningPublicKey)
            writeLengthPrefixedUtf(command.peerSigningPublicKey)
            writeLengthPrefixedUtf(command.transcriptHash)
            writeLengthPrefixedUtf(command.ownSignature)
            writeLengthPrefixedUtf(command.peerSignature)
            writeLong(command.occurredAt.toEpochMilliseconds())
        }
        return OUTBOX_BASE64.encode(buffer.readByteArray())
    }

    fun decode(encoded: String): SubmitNearbyEncounterCommand {
        val input = Buffer().apply { write(OUTBOX_BASE64.decode(encoded)) }
        require(input.readInt() == VERSION)
        val command = SubmitNearbyEncounterCommand(
            accountId = UserId(input.readLengthPrefixedUtf()),
            encounterId = EncounterId(input.readLengthPrefixedUtf()),
            clientOperationId = ClientOperationId(input.readLengthPrefixedUtf()),
            ownToken = input.readLengthPrefixedUtf(),
            peerToken = input.readLengthPrefixedUtf(),
            ownSigningPublicKey = input.readLengthPrefixedUtf(),
            peerSigningPublicKey = input.readLengthPrefixedUtf(),
            transcriptHash = input.readLengthPrefixedUtf(),
            ownSignature = input.readLengthPrefixedUtf(),
            peerSignature = input.readLengthPrefixedUtf(),
            occurredAt = Instant.fromEpochMilliseconds(input.readLong()),
        )
        require(input.exhausted())
        return command
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
        ownToken = NearbyEncoding.bytesToUuidString(ownToken),
        peerToken = NearbyEncoding.bytesToUuidString(peerToken),
        ownSigningPublicKey = NearbyEncoding.encode(ownSigningPublicKey),
        peerSigningPublicKey = NearbyEncoding.encode(peerSigningPublicKey),
        transcriptHash = NearbyEncoding.encode(transcriptHash),
        ownSignature = NearbyEncoding.encode(ownTranscriptSignature),
        peerSignature = NearbyEncoding.encode(peerTranscriptSignature),
        occurredAt = occurredAt,
    )
}
