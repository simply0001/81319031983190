package com.pocketpass.app.mii

data class Ver3Mii(
    val name: String,
    val creator: String,
    val appearance: MiiAppearance,
)

object Ver3StoreData {
    const val SIZE = 96

    fun decode(bytes: ByteArray): Ver3Mii? {
        if (bytes.size != SIZE) return null
        val stored = (bytes.unsigned(CHECKSUM_OFFSET) shl 8) or bytes.unsigned(CHECKSUM_OFFSET + 1)
        if (checksum(bytes) != stored) return null
        val appearance = MiiAppearance(
            gender = bytes.bits(0x18, 0, 1),
            favoriteColor = bytes.bits(0x18, 10, 4),
            height = bytes.unsigned(0x2E),
            build = bytes.unsigned(0x2F),
            faceType = bytes.bits(0x30, 1, 4),
            skinColor = bytes.bits(0x30, 5, 3),
            wrinklesType = bytes.bits(0x30, 8, 4),
            makeupType = bytes.bits(0x30, 12, 4),
            hairType = bytes.unsigned(0x32),
            hairColor = bytes.bits(0x33, 0, 3),
            flipHair = bytes.bits(0x33, 3, 1) == 1,
            eyeType = bytes.bits(0x34, 0, 6),
            eyeColor = bytes.bits(0x34, 6, 3),
            eyeScale = bytes.bits(0x34, 9, 4),
            eyeVerticalStretch = bytes.bits(0x34, 13, 3),
            eyeRotation = bytes.bits(0x34, 16, 5),
            eyeSpacing = bytes.bits(0x34, 21, 4),
            eyeYPosition = bytes.bits(0x34, 25, 5),
            eyebrowType = bytes.bits(0x38, 0, 5),
            eyebrowColor = bytes.bits(0x38, 5, 3),
            eyebrowScale = bytes.bits(0x38, 8, 4),
            eyebrowVerticalStretch = bytes.bits(0x38, 12, 3),
            eyebrowRotation = bytes.bits(0x38, 16, 5),
            eyebrowSpacing = bytes.bits(0x38, 21, 4),
            eyebrowYPosition = bytes.bits(0x38, 25, 5),
            noseType = bytes.bits(0x3C, 0, 5),
            noseScale = bytes.bits(0x3C, 5, 4),
            noseYPosition = bytes.bits(0x3C, 9, 5),
            mouthType = bytes.bits(0x3E, 0, 6),
            mouthColor = bytes.bits(0x3E, 6, 3),
            mouthScale = bytes.bits(0x3E, 9, 4),
            mouthHorizontalStretch = bytes.bits(0x3E, 13, 3),
            mouthYPosition = bytes.bits(0x3E, 16, 5),
            mustacheType = bytes.bits(0x3E, 21, 3),
            beardType = bytes.bits(0x42, 0, 3),
            facialHairColor = bytes.bits(0x42, 3, 3),
            mustacheScale = bytes.bits(0x42, 6, 4),
            mustacheYPosition = bytes.bits(0x42, 10, 5),
            glassesType = bytes.bits(0x44, 0, 4),
            glassesColor = bytes.bits(0x44, 4, 3),
            glassesScale = bytes.bits(0x44, 7, 4),
            glassesYPosition = bytes.bits(0x44, 11, 5),
            moleEnabled = bytes.bits(0x44, 16, 1) == 1,
            moleScale = bytes.bits(0x44, 17, 4),
            moleXPosition = bytes.bits(0x44, 21, 5),
            moleYPosition = bytes.bits(0x44, 26, 5),
        ).normalized()
        return Ver3Mii(
            name = bytes.utf16(NAME_OFFSET),
            creator = bytes.utf16(CREATOR_OFFSET),
            appearance = appearance,
        )
    }

    fun checksum(bytes: ByteArray): Int {
        var crc = 0
        for (index in 0 until CHECKSUM_OFFSET) {
            val byte = bytes.unsigned(index)
            for (bit in 7 downTo 0) {
                val carry = crc and 0x8000 != 0
                crc = (((crc shl 1) or ((byte shr bit) and 1)) xor (if (carry) POLYNOMIAL else 0)) and 0xFFFF
            }
        }
        repeat(16) {
            val carry = crc and 0x8000 != 0
            crc = ((crc shl 1) xor (if (carry) POLYNOMIAL else 0)) and 0xFFFF
        }
        return crc
    }

    private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.bits(byteOffset: Int, bitOffset: Int, width: Int): Int {
        var value = 0
        for (bit in 0 until width) {
            val absolute = byteOffset * 8 + bitOffset + bit
            val source = (unsigned(absolute / 8) shr (absolute % 8)) and 1
            value = value or (source shl bit)
        }
        return value
    }

    private fun ByteArray.utf16(offset: Int): String = buildString {
        for (index in 0 until NAME_BYTES step 2) {
            val unit = unsigned(offset + index) or (unsigned(offset + index + 1) shl 8)
            if (unit == 0) break
            append(unit.toChar())
        }
    }

    private const val CHECKSUM_OFFSET = 94
    private const val POLYNOMIAL = 0x1021
    private const val NAME_OFFSET = 0x1A
    private const val CREATOR_OFFSET = 0x48
    private const val NAME_BYTES = 20
}
