package com.pocketpass.app.nearby

/**
 * A P-256 key pair in the wire encodings the whole system speaks: the public
 * key as X.509 SubjectPublicKeyInfo DER (what HELLOs carry and the server
 * verifies against), the private key as PKCS#8 DER (never leaves the device).
 */
class NearbyKeyPair(
    val publicKeyDer: ByteArray,
    val privateKeyDer: ByteArray,
)

// The platform primitives behind NearbyCrypto. Both actuals must be
// byte-compatible: NearbyCryptoTest runs the same vectors on every target.
internal expect object NearbyCryptoPrimitives {
    fun randomBytes(size: Int): ByteArray
    fun sha256(value: ByteArray): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    fun generateP256KeyPair(): NearbyKeyPair
    fun signP256(privateKeyDer: ByteArray, message: ByteArray): ByteArray
    fun verifyP256(publicKeyDer: ByteArray, message: ByteArray, signature: ByteArray): Boolean
    fun ecdhSharedSecret(privateKeyDer: ByteArray, peerPublicKeyDer: ByteArray): ByteArray
    fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): NearbyEncryptedPacket
    fun aesGcmDecrypt(key: ByteArray, packet: NearbyEncryptedPacket, aad: ByteArray): ByteArray
}

object NearbyCrypto {
    fun generateSigningKeyPair(): NearbyKeyPair = NearbyCryptoPrimitives.generateP256KeyPair()

    fun generateAgreementKeyPair(): NearbyKeyPair = NearbyCryptoPrimitives.generateP256KeyPair()

    fun randomBytes(size: Int): ByteArray = NearbyCryptoPrimitives.randomBytes(size)

    fun randomNonce(): Long {
        val bytes = NearbyCryptoPrimitives.randomBytes(Long.SIZE_BYTES)
        var nonce = 0L
        bytes.forEach { nonce = (nonce shl 8) or (it.toLong() and 0xFF) }
        return nonce
    }

    fun sign(privateKeyDer: ByteArray, message: ByteArray): ByteArray =
        NearbyCryptoPrimitives.signP256(privateKeyDer, message)

    fun verify(
        publicKeyDer: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean = runCatching {
        NearbyCryptoPrimitives.verifyP256(publicKeyDer, message, signature)
    }.getOrDefault(false)

    fun transcript(first: NearbyHelloPacket, second: NearbyHelloPacket): ByteArray {
        val ordered = if (
            first.hello.invitationNonce.toULong() <= second.hello.invitationNonce.toULong()
        ) {
            first to second
        } else {
            second to first
        }
        return TRANSCRIPT_DOMAIN + ordered.first.bytes + ordered.second.bytes
    }

    fun sha256(value: ByteArray): ByteArray = NearbyCryptoPrimitives.sha256(value)

    fun deriveSessionKey(
        ownPrivateKeyDer: ByteArray,
        peerPublicKeyDer: ByteArray,
        transcriptHash: ByteArray,
    ): ByteArray {
        val sharedSecret = NearbyCryptoPrimitives.ecdhSharedSecret(
            privateKeyDer = ownPrivateKeyDer,
            peerPublicKeyDer = peerPublicKeyDer,
        )
        return try {
            hkdfSha256(
                inputKeyMaterial = sharedSecret,
                salt = transcriptHash,
                info = SESSION_KEY_INFO,
                outputLength = AES_KEY_BYTES,
            )
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): NearbyEncryptedPacket {
        require(key.size == AES_KEY_BYTES)
        return NearbyCryptoPrimitives.aesGcmEncrypt(key, plaintext, aad)
    }

    fun decrypt(
        key: ByteArray,
        packet: NearbyEncryptedPacket,
        aad: ByteArray,
    ): ByteArray {
        require(key.size == AES_KEY_BYTES)
        return NearbyCryptoPrimitives.aesGcmDecrypt(key, packet, aad)
    }

    fun confirmationAad(transcriptHash: ByteArray, senderNonce: Long): ByteArray {
        val nonceBytes = ByteArray(Long.SIZE_BYTES) { index ->
            (senderNonce ushr ((Long.SIZE_BYTES - 1 - index) * 8)).toByte()
        }
        return transcriptHash + nonceBytes
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        val extract = NearbyCryptoPrimitives.hmacSha256(salt, inputKeyMaterial)
        return try {
            var output = ByteArray(0)
            var previous = ByteArray(0)
            var counter = 1
            while (output.size < outputLength) {
                previous = NearbyCryptoPrimitives.hmacSha256(
                    extract,
                    previous + info + byteArrayOf(counter.toByte()),
                )
                output += previous
                counter += 1
            }
            output.copyOf(outputLength)
        } finally {
            extract.fill(0)
        }
    }

    const val GCM_IV_BYTES = 12
    private const val AES_KEY_BYTES = 32
    private val TRANSCRIPT_DOMAIN = "PocketPassEncounterV2".encodeToByteArray()
    private val SESSION_KEY_INFO = "PocketPass BLE session key".encodeToByteArray()
}
