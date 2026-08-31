package com.pocketpass.app.sync

import kotlin.io.encoding.Base64
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal val OUTBOX_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

// Payloads written before the multiplatform port used DataOutputStream.writeUTF, so queued
// operations decode only if these helpers keep that exact wire format: a two-byte big-endian
// byte count followed by CESU-8-style "modified UTF-8", where each UTF-16 unit (surrogate
// halves included) is encoded on its own and NUL becomes the two-byte form.
internal fun Buffer.writeLengthPrefixedUtf(value: String) {
    val bytes = Buffer()
    for (character in value) {
        val code = character.code
        when {
            code in 0x0001..0x007F -> bytes.writeByte(code.toByte())
            code <= 0x07FF -> {
                bytes.writeByte((0xC0 or (code shr 6 and 0x1F)).toByte())
                bytes.writeByte((0x80 or (code and 0x3F)).toByte())
            }
            else -> {
                bytes.writeByte((0xE0 or (code shr 12 and 0x0F)).toByte())
                bytes.writeByte((0x80 or (code shr 6 and 0x3F)).toByte())
                bytes.writeByte((0x80 or (code and 0x3F)).toByte())
            }
        }
    }
    val encoded = bytes.readByteArray()
    require(encoded.size <= 0xFFFF) { "Encoded string is too long: ${encoded.size} bytes" }
    writeShort(encoded.size.toShort())
    write(encoded)
}

internal fun Buffer.readLengthPrefixedUtf(): String {
    val length = readShort().toInt() and 0xFFFF
    val bytes = readByteArray(length)
    val builder = StringBuilder(length)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xFF
        when {
            first and 0x80 == 0 -> {
                builder.append(first.toChar())
                index += 1
            }
            first shr 5 == 0b110 -> {
                require(index + 1 < bytes.size) { "Truncated modified UTF-8 sequence" }
                val second = bytes[index + 1].toInt()
                require(second and 0xC0 == 0x80) { "Malformed modified UTF-8 sequence" }
                builder.append(((first and 0x1F) shl 6 or (second and 0x3F)).toChar())
                index += 2
            }
            first shr 4 == 0b1110 -> {
                require(index + 2 < bytes.size) { "Truncated modified UTF-8 sequence" }
                val second = bytes[index + 1].toInt()
                val third = bytes[index + 2].toInt()
                require(second and 0xC0 == 0x80 && third and 0xC0 == 0x80) {
                    "Malformed modified UTF-8 sequence"
                }
                builder.append(
                    ((first and 0x0F) shl 12 or ((second and 0x3F) shl 6) or (third and 0x3F))
                        .toChar(),
                )
                index += 3
            }
            else -> throw IllegalArgumentException("Malformed modified UTF-8 sequence")
        }
    }
    return builder.toString()
}
