package com.pocketpass.app.nearby

import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.security.SecureStringStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NearbyDeviceTagStore(
    private val remote: EncounterRemoteDataSource,
    private val secureStore: SecureStringStore,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ByteArray>()

    suspend fun secret(accountId: UserId): ByteArray? = mutex.withLock {
        cache[accountId.value]?.let { return@withLock it }
        val key = entryKey(accountId)
        val stored = runCatching { secureStore.get(key) }.getOrNull()?.let(::decode)
        if (stored != null) {
            cache[accountId.value] = stored
            return@withLock stored
        }
        when (val fetched = remote.fetchDeviceTagSecret(accountId)) {
            is RepositoryResult.Success -> {
                val secret = fetched.value.takeIf { it.size == NearbyDeviceTag.SECRET_BYTES }
                    ?: return@withLock null
                runCatching { secureStore.put(key, NearbyEncoding.encode(secret)) }
                cache[accountId.value] = secret
                secret
            }

            is RepositoryResult.Failure -> null
        }
    }

    suspend fun forget(accountId: UserId) {
        mutex.withLock {
            cache.remove(accountId.value)
            secureStore.remove(entryKey(accountId))
        }
    }

    private fun decode(value: String): ByteArray? =
        runCatching { NearbyEncoding.decode(value) }
            .getOrNull()
            ?.takeIf { it.size == NearbyDeviceTag.SECRET_BYTES }

    private fun entryKey(accountId: UserId): String =
        "nearby.tag." +
            NearbyEncoding.encode(NearbyCrypto.sha256(accountId.value.encodeToByteArray())).take(22)
}
