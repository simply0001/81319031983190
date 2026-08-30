package com.pocketpass.app.nearby

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object NearbyCrypto {
    private val random = SecureRandom()

    fun generateSigningKeyPair(): KeyPair = generateP256KeyPair()

    fun generateAgreementKeyPair(): KeyPair = generateP256KeyPair()

    fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(random::nextBytes)

    fun randomNonce(): Long = random.nextLong()

    fun signingPublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance(EC_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(encoded))

    fun signingPrivateKey(encoded: ByteArray): PrivateKey =
        KeyFactory.getInstance(EC_ALGORITHM)
            .generatePrivate(PKCS8EncodedKeySpec(encoded))

    fun agreementPublicKey(encoded: ByteArray): PublicKey = signingPublicKey(encoded)

    fun sign(privateKey: PrivateKey, message: ByteArray): ByteArray =
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey, random)
            update(message)
            sign()
        }

    fun verify(
        publicKey: PublicKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean = runCatching {
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    }.getOrDefault(false)

    fun transcript(first: NearbyHelloPacket, second: NearbyHelloPacket): ByteArray {
        val ordered = if (
            java.lang.Long.compareUnsigned(
                first.hello.invitationNonce,
                second.hello.invitationNonce,
            ) <= 0
        ) {
            first to second
        } else {
            second to first
        }
        return ByteArrayOutputStream().use { output ->
            output.write(TRANSCRIPT_DOMAIN)
            output.write(ordered.first.bytes)
            output.write(ordered.second.bytes)
            output.toByteArray()
        }
    }

    fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA_256).digest(value)

    fun deriveSessionKey(
        ownPrivateKey: PrivateKey,
        peerPublicKey: PublicKey,
        transcriptHash: ByteArray,
    ): ByteArray {
        val sharedSecret = KeyAgreement.getInstance(ECDH_ALGORITHM).run {
            init(ownPrivateKey)
            doPhase(peerPublicKey, true)
            generateSecret()
        }
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
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return NearbyEncryptedPacket(iv, cipher.doFinal(plaintext))
    }

    fun decrypt(
        key: ByteArray,
        packet: NearbyEncryptedPacket,
        aad: ByteArray,
    ): ByteArray {
        require(key.size == AES_KEY_BYTES)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, packet.iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(packet.ciphertext)
    }

    private fun generateP256KeyPair(): KeyPair =
        KeyPairGenerator.getInstance(EC_ALGORITHM).run {
            initialize(ECGenParameterSpec(P_256_CURVE), random)
            generateKeyPair()
        }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        val extract = Mac.getInstance(HMAC_SHA_256).run {
            init(SecretKeySpec(salt, HMAC_SHA_256))
            doFinal(inputKeyMaterial)
        }
        return try {
            val output = ByteArrayOutputStream()
            var previous = ByteArray(0)
            var counter = 1
            while (output.size() < outputLength) {
                previous = Mac.getInstance(HMAC_SHA_256).run {
                    init(SecretKeySpec(extract, HMAC_SHA_256))
                    update(previous)
                    update(info)
                    update(counter.toByte())
                    doFinal()
                }
                output.write(previous)
                counter += 1
            }
            output.toByteArray().copyOf(outputLength)
        } finally {
            extract.fill(0)
        }
    }

    fun confirmationAad(transcriptHash: ByteArray, senderNonce: Long): ByteArray =
        ByteBuffer.allocate(transcriptHash.size + Long.SIZE_BYTES)
            .put(transcriptHash)
            .putLong(senderNonce)
            .array()

    private const val EC_ALGORITHM = "EC"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val SHA_256 = "SHA-256"
    private const val HMAC_SHA_256 = "HmacSHA256"
    private const val P_256_CURVE = "secp256r1"
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val TRANSCRIPT_DOMAIN = "PocketPassEncounterV2".toByteArray(Charsets.UTF_8)
    private val SESSION_KEY_INFO = "PocketPass BLE session key".toByteArray(Charsets.UTF_8)
}
