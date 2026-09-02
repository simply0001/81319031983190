package com.pocketpass.app.nearby

import com.pocketpass.app.data.local.dao.NearbyEncounterDao
import com.pocketpass.app.data.local.entity.NearbyCredentialEntity
import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.security.SecureStringStore
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NearbyCredentialPool(
    private val dao: NearbyEncounterDao,
    private val remote: EncounterRemoteDataSource,
    private val secureStore: SecureStringStore,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    suspend fun acquire(accountId: UserId): RepositoryResult<NearbyCredential> =
        mutex.withLock {
            val refillFailure = when (val refill = refillLocked(accountId)) {
                is RepositoryResult.Failure -> refill
                is RepositoryResult.Success -> null
            }
            repeat(MAX_ACQUIRE_ATTEMPTS) {
                val now = clock.now()
                val row = dao.getAvailableCredential(
                    accountId = accountId.value,
                    nowEpochMillis = now.toEpochMilliseconds(),
                ) ?: return@withLock refillFailure ?: unavailable()
                val encoded = secureStore.get(row.secureEntryKey)
                if (encoded == null) {
                    dao.claimCredential(
                        accountId.value,
                        row.tokenHash,
                        now.toEpochMilliseconds(),
                    )
                    return@repeat
                }
                if (
                    dao.claimCredential(
                        accountId = accountId.value,
                        tokenHash = row.tokenHash,
                        claimedAtEpochMillis = now.toEpochMilliseconds(),
                    ) != 1
                ) {
                    return@repeat
                }
                return@withLock RepositoryResult.Success(decodeCredential(encoded))
            }
            refillFailure ?: unavailable()
        }

    suspend fun refill(accountId: UserId): RepositoryResult<Unit> =
        mutex.withLock { refillLocked(accountId) }

    /**
     * Returns an acquired pass when the exchange ended before its token ever
     * left the device; a claimed pass otherwise counts as spent and the server
     * would keep it in the account's inventory for a week.
     */
    suspend fun release(accountId: UserId, credential: NearbyCredential) {
        mutex.withLock {
            dao.releaseCredential(
                accountId = accountId.value,
                tokenHash = encode(NearbyCrypto.sha256(credential.token)),
            )
        }
    }

    private suspend fun refillLocked(accountId: UserId): RepositoryResult<Unit> {
        val now = clock.now()
        dao.getExpiredOrClaimedCredentials(
            accountId.value,
            now.toEpochMilliseconds(),
        ).forEach { credential ->
            secureStore.remove(credential.secureEntryKey)
        }
        dao.deleteExpiredOrClaimed(accountId.value, now.toEpochMilliseconds())
        val available = dao.availableCredentialCount(accountId.value, now.toEpochMilliseconds())
        if (available >= REFILL_THRESHOLD) return RepositoryResult.Success(Unit)

        val requested = (TARGET_POOL_SIZE - available).coerceIn(1, MAX_ISSUE_BATCH)
        val keyPairs = List(requested) { NearbyCrypto.generateSigningKeyPair() }
        val publicKeys = keyPairs.map { pair -> encode(pair.publicKeyDer) }
        val issued = when (
            val result = remote.issueCredentials(accountId, publicKeys)
        ) {
            is RepositoryResult.Failure ->
                return cachedCredentialFallback(available, result)
            is RepositoryResult.Success -> result.value
        }
        if (issued.size != keyPairs.size) return unavailable()

        val keyByPublic = keyPairs.associateBy { pair -> encode(pair.publicKeyDer) }
        val rows = mutableListOf<NearbyCredentialEntity>()
        issued.forEach { credential ->
            val pair = keyByPublic[credential.signingPublicKey] ?: return unavailable()
            val tokenBytes = NearbyEncoding.uuidStringToBytes(credential.token)
            val tokenHash = encode(NearbyCrypto.sha256(tokenBytes))
            val secureEntryKey = secureEntryKey(accountId, tokenHash)
            secureStore.put(
                secureEntryKey,
                encodeCredential(
                    NearbyCredential(
                        token = tokenBytes,
                        signingPublicKey = pair.publicKeyDer,
                        signingPrivateKey = pair.privateKeyDer,
                        expiresAt = credential.expiresAt,
                    ),
                ),
            )
            rows += NearbyCredentialEntity(
                accountId = accountId.value,
                tokenHash = tokenHash,
                secureEntryKey = secureEntryKey,
                expiresAtEpochMillis = credential.expiresAt.toEpochMilliseconds(),
                claimedAtEpochMillis = null,
            )
        }
        dao.upsertCredentials(rows)
        return RepositoryResult.Success(Unit)
    }

    private fun encodeCredential(credential: NearbyCredential): String =
        listOf(
            CREDENTIAL_VERSION,
            encode(credential.token),
            encode(credential.signingPublicKey),
            encode(credential.signingPrivateKey),
            credential.expiresAt.toEpochMilliseconds().toString(),
        ).joinToString(CREDENTIAL_SEPARATOR)

    private fun decodeCredential(value: String): NearbyCredential {
        val parts = value.split(CREDENTIAL_SEPARATOR)
        require(parts.size == 5 && parts[0] == CREDENTIAL_VERSION)
        return NearbyCredential(
            token = decode(parts[1]),
            signingPublicKey = decode(parts[2]),
            signingPrivateKey = decode(parts[3]),
            expiresAt = Instant.fromEpochMilliseconds(parts[4].toLong()),
        )
    }

    private fun secureEntryKey(accountId: UserId, tokenHash: String): String =
        "nearby.${encode(NearbyCrypto.sha256(accountId.value.encodeToByteArray())).take(22)}." +
            tokenHash.take(43)

    private fun unavailable(): RepositoryResult.Failure = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Unavailable,
            message = "No anonymous encounter passes are available",
        ),
    )

    companion object {
        fun encode(bytes: ByteArray): String = NearbyEncoding.encode(bytes)

        fun decode(value: String): ByteArray = NearbyEncoding.decode(value)

        private const val CREDENTIAL_VERSION = "1"
        private const val CREDENTIAL_SEPARATOR = "|"
        private const val REFILL_THRESHOLD = 8
        private const val TARGET_POOL_SIZE = 24
        private const val MAX_ISSUE_BATCH = 32
        private const val MAX_ACQUIRE_ATTEMPTS = 3
    }
}

fun cachedCredentialFallback(
    available: Int,
    failure: RepositoryResult.Failure,
): RepositoryResult<Unit> {
    require(available >= 0)
    return if (available > 0) RepositoryResult.Success(Unit) else failure
}
