package com.pocketpass.app.nearby

import kotlin.io.encoding.Base64

// Matches android.util.Base64 with URL_SAFE or NO_PADDING or NO_WRAP, and java.util.UUID's
// big-endian sixteen-byte layout with the lowercase hex-and-dash rendering: values written by
// the Android client keep decoding after the multiplatform port.
object NearbyEncoding {
    fun encode(bytes: ByteArray): String = URL_SAFE_NO_PADDING.encode(bytes)

    fun decode(value: String): ByteArray = URL_SAFE_NO_PADDING.decode(value)

    fun uuidStringToBytes(value: String): ByteArray {
        val hex = value.trim().lowercase().replace("-", "")
        require(value.trim().length == 36 && hex.length == 32) { "Malformed UUID" }
        return ByteArray(16) { index ->
            val high = hex[index * 2].digitToInt(16)
            val low = hex[index * 2 + 1].digitToInt(16)
            ((high shl 4) or low).toByte()
        }
    }

    fun bytesToUuidString(bytes: ByteArray): String {
        require(bytes.size == 16)
        val hex = bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        return buildString(36) {
            append(hex, 0, 8)
            append('-')
            append(hex, 8, 12)
            append('-')
            append(hex, 12, 16)
            append('-')
            append(hex, 16, 20)
            append('-')
            append(hex, 20, 32)
        }
    }

    private val URL_SAFE_NO_PADDING =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
}
