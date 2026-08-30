package com.pocketpass.app.security

interface SecureStringStore {
    suspend fun put(key: String, value: String)

    suspend fun get(key: String): String?

    suspend fun remove(key: String)
}

class SecureStorageException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
