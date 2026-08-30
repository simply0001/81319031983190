package com.pocketpass.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreEncryptedStringStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCE_NAME,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecureStringStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun put(key: String, value: String) {
        requireValidEntryKey(key)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val envelope = try {
                    encrypt(key, value)
                } catch (error: KeyPermanentlyInvalidatedException) {
                    deleteKey()
                    encrypt(key, value)
                } catch (error: GeneralSecurityException) {
                    throw SecureStorageException("Unable to encrypt auth storage entry", error)
                }
                if (!preferences.edit().putString(key, envelope).commit()) {
                    throw SecureStorageException("Unable to persist auth storage entry")
                }
            }
        }
    }

    override suspend fun get(key: String): String? {
        requireValidEntryKey(key)
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val envelope = preferences.getString(key, null) ?: return@withLock null
                try {
                    decrypt(key, envelope)
                } catch (error: Throwable) {
                    if (!isRecoverableStorageFailure(error)) throw error
                    val cleared = preferences.edit().remove(key).commit()
                    if (error is KeyPermanentlyInvalidatedException) deleteKey()
                    if (!cleared) {
                        throw SecureStorageException(
                            "Encrypted auth storage was invalid and could not be cleared",
                            error,
                        )
                    }
                    throw SecureStorageException(
                        "Encrypted auth storage was invalid and has been cleared",
                        error,
                    )
                }
            }
        }
    }

    override suspend fun remove(key: String) {
        requireValidEntryKey(key)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!preferences.edit().remove(key).commit()) {
                    throw SecureStorageException("Unable to remove auth storage entry")
                }
            }
        }
    }

    private fun encrypt(entryKey: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad(entryKey))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            ENVELOPE_VERSION,
            encode(cipher.iv),
            encode(ciphertext),
        ).joinToString(ENVELOPE_SEPARATOR)
    }

    private fun decrypt(entryKey: String, envelope: String): String {
        val pieces = envelope.split(ENVELOPE_SEPARATOR)
        require(pieces.size == 3 && pieces[0] == ENVELOPE_VERSION) {
            "Unsupported encrypted auth storage envelope"
        }
        val iv = decode(pieces[1])
        require(iv.size == GCM_IV_BYTES) { "Invalid encrypted auth storage IV" }
        val ciphertext = decode(pieces[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(entryKey))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey =
        synchronized(KEY_LOCK) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return@synchronized it }

            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        }

    private fun deleteKey() {
        synchronized(KEY_LOCK) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
    }

    private fun aad(entryKey: String): ByteArray =
        "$AAD_NAMESPACE:$ENVELOPE_VERSION:$entryKey".toByteArray(StandardCharsets.UTF_8)

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.NO_PADDING)

    private fun requireValidEntryKey(key: String) {
        require(key.isNotBlank()) { "Secure storage entry key must not be blank" }
        require(key.length <= MAX_ENTRY_KEY_LENGTH) { "Secure storage entry key is too long" }
    }

    private fun isRecoverableStorageFailure(error: Throwable): Boolean =
        error is GeneralSecurityException ||
            error is IllegalArgumentException

    companion object {
        const val DEFAULT_PREFERENCE_NAME = "pocketpass_secure_auth"
        const val DEFAULT_KEY_ALIAS = "com.pocketpass.app.auth.aes.v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val ENVELOPE_VERSION = "1"
        private const val ENVELOPE_SEPARATOR = ":"
        private const val AAD_NAMESPACE = "PocketPassAuth"
        private const val MAX_ENTRY_KEY_LENGTH = 128
        private val KEY_LOCK = Any()
    }
}
