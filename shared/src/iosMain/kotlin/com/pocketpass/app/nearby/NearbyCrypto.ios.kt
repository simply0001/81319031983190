package com.pocketpass.app.nearby

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

// CryptoKit/Security-framework backed twins of the Android JCA primitives;
// the DER encodings and DER ECDSA signature format keep the two
// implementations wire-compatible.
internal actual object NearbyCryptoPrimitives {
    private val provider = CryptographyProvider.Default
    private val ecdsa = provider.get(ECDSA)
    private val ecdh = provider.get(ECDH)
    private val aesGcm = provider.get(AES.GCM)
    private val hmac = provider.get(HMAC)
    private val sha256Hasher = provider.get(SHA256).hasher()

    actual fun randomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    actual fun sha256(value: ByteArray): ByteArray = sha256Hasher.hashBlocking(value)

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        hmac.keyDecoder(SHA256)
            .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignatureBlocking(data)

    actual fun generateP256KeyPair(): NearbyKeyPair {
        val pair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        return NearbyKeyPair(
            publicKeyDer = pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER),
            privateKeyDer = pair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER),
        )
    }

    actual fun signP256(privateKeyDer: ByteArray, message: ByteArray): ByteArray =
        ecdsa.privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, privateKeyDer)
            .signatureGenerator(digest = SHA256, format = ECDSA.SignatureFormat.DER)
            .generateSignatureBlocking(message)

    actual fun verifyP256(
        publicKeyDer: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean =
        ecdsa.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PublicKey.Format.DER, publicKeyDer)
            .signatureVerifier(digest = SHA256, format = ECDSA.SignatureFormat.DER)
            .tryVerifySignatureBlocking(message, signature)

    actual fun ecdhSharedSecret(
        privateKeyDer: ByteArray,
        peerPublicKeyDer: ByteArray,
    ): ByteArray {
        val privateKey = ecdh.privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, privateKeyDer)
        val publicKey = ecdh.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PublicKey.Format.DER, peerPublicKeyDer)
        return privateKey.sharedSecretGenerator()
            .generateSharedSecretToByteArrayBlocking(publicKey)
    }

    actual fun aesGcmEncrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): NearbyEncryptedPacket {
        val combined = aesGcm.keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()
            .encryptBlocking(plaintext, aad)
        // The library prepends the generated IV to ciphertext||tag.
        return NearbyEncryptedPacket(
            iv = combined.copyOfRange(0, NearbyCrypto.GCM_IV_BYTES),
            ciphertext = combined.copyOfRange(NearbyCrypto.GCM_IV_BYTES, combined.size),
        )
    }

    actual fun aesGcmDecrypt(
        key: ByteArray,
        packet: NearbyEncryptedPacket,
        aad: ByteArray,
    ): ByteArray =
        aesGcm.keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()
            .decryptBlocking(packet.iv + packet.ciphertext, aad)
}
