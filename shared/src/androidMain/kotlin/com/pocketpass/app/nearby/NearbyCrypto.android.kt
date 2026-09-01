package com.pocketpass.app.nearby

import java.security.KeyFactory
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

internal actual object NearbyCryptoPrimitives {
    private val random = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(random::nextBytes)

    actual fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA_256).digest(value)

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC_SHA_256).run {
            init(SecretKeySpec(key, HMAC_SHA_256))
            doFinal(data)
        }

    actual fun generateP256KeyPair(): NearbyKeyPair {
        val pair = KeyPairGenerator.getInstance(EC_ALGORITHM).run {
            initialize(ECGenParameterSpec(P_256_CURVE), random)
            generateKeyPair()
        }
        return NearbyKeyPair(
            publicKeyDer = pair.public.encoded,
            privateKeyDer = pair.private.encoded,
        )
    }

    actual fun signP256(privateKeyDer: ByteArray, message: ByteArray): ByteArray =
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey(privateKeyDer), random)
            update(message)
            sign()
        }

    actual fun verifyP256(
        publicKeyDer: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean = Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initVerify(publicKey(publicKeyDer))
        update(message)
        verify(signature)
    }

    actual fun ecdhSharedSecret(
        privateKeyDer: ByteArray,
        peerPublicKeyDer: ByteArray,
    ): ByteArray = KeyAgreement.getInstance(ECDH_ALGORITHM).run {
        init(privateKey(privateKeyDer))
        doPhase(publicKey(peerPublicKeyDer), true)
        generateSecret()
    }

    actual fun aesGcmEncrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): NearbyEncryptedPacket {
        val iv = randomBytes(NearbyCrypto.GCM_IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return NearbyEncryptedPacket(iv, cipher.doFinal(plaintext))
    }

    actual fun aesGcmDecrypt(
        key: ByteArray,
        packet: NearbyEncryptedPacket,
        aad: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, packet.iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(packet.ciphertext)
    }

    private fun publicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance(EC_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(encoded))

    private fun privateKey(encoded: ByteArray): PrivateKey =
        KeyFactory.getInstance(EC_ALGORITHM)
            .generatePrivate(PKCS8EncodedKeySpec(encoded))

    private const val EC_ALGORITHM = "EC"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val SHA_256 = "SHA-256"
    private const val HMAC_SHA_256 = "HmacSHA256"
    private const val P_256_CURVE = "secp256r1"
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
}
