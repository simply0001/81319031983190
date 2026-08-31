@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

// The Keychain is the iOS counterpart of the Android Keystore-encrypted
// preferences: items are hardware-encrypted at rest and survive app updates.
class KeychainSecureStringStore(
    private val service: String = DEFAULT_SERVICE,
) : SecureStringStore {

    override suspend fun put(key: String, value: String) {
        remove(key)
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { pinned ->
            NSData.create(
                bytes = if (bytes.isEmpty()) null else pinned.addressOf(0),
                length = bytes.size.convert(),
            )
        }
        val status = bridging(service, key, data) { (serviceRef, accountRef, dataRef) ->
            withQuery(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to dataRef,
            ) { query -> SecItemAdd(query, null) }
        }
        if (status != errSecSuccess) {
            throw SecureStorageException("Keychain write failed with status $status")
        }
    }

    override suspend fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = bridging(service, key) { (serviceRef, accountRef) ->
            withQuery(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            ) { query -> SecItemCopyMatching(query, result.ptr) }
        }
        when (status) {
            errSecSuccess -> {
                val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                data.toByteArray().decodeToString()
            }

            errSecItemNotFound -> null
            else -> throw SecureStorageException("Keychain read failed with status $status")
        }
    }

    override suspend fun remove(key: String) {
        val status = bridging(service, key) { (serviceRef, accountRef) ->
            withQuery(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
            ) { query -> SecItemDelete(query) }
        }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw SecureStorageException("Keychain delete failed with status $status")
        }
    }

    // Bridges Kotlin values to CFTypeRefs for the duration of the block; the
    // CFType-callback dictionary retains what it needs, so releasing here is safe.
    private inline fun <T> bridging(vararg values: Any?, block: (List<CFTypeRef?>) -> T): T {
        val refs = values.map { CFBridgingRetain(it) }
        return try {
            block(refs)
        } finally {
            refs.forEach { CFBridgingRelease(it) }
        }
    }

    private inline fun <T> withQuery(
        vararg entries: Pair<CFTypeRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T = memScoped {
        val query = CFDictionaryCreateMutable(
            null,
            entries.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        entries.forEach { (entryKey, entryValue) ->
            CFDictionaryAddValue(query, entryKey as CFStringRef?, entryValue)
        }
        try {
            block(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
    }

    private companion object {
        const val DEFAULT_SERVICE = "xyz.pocketpass.securestore"
    }
}
