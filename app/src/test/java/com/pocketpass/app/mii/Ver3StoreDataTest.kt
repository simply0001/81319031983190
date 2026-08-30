package com.pocketpass.app.mii

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ver3StoreDataTest {
    @Test
    fun decodesPretendoDefaultMiiToTheEditorDefaults() {
        val mii = requireNotNull(Ver3StoreData.decode(pretendoDefaultMii()))

        assertEquals("Default", mii.name)
        assertEquals("", mii.creator)
        assertEquals(MiiAppearance(), mii.appearance)
    }

    @Test
    fun readsEveryFieldFromItsBitPosition() {
        val bytes = pretendoDefaultMii()
        bytes.setBits(0x18, 0, 1, 1)
        bytes.setBits(0x18, 10, 4, 9)
        bytes[0x2E] = 100.toByte()
        bytes[0x2F] = 20.toByte()
        bytes.setBits(0x30, 1, 4, 11)
        bytes.setBits(0x30, 5, 3, 5)
        bytes.setBits(0x30, 8, 4, 7)
        bytes.setBits(0x30, 12, 4, 3)
        bytes[0x32] = 57.toByte()
        bytes.setBits(0x33, 0, 3, 6)
        bytes.setBits(0x33, 3, 1, 1)
        bytes.setBits(0x34, 0, 6, 45)
        bytes.setBits(0x34, 6, 3, 5)
        bytes.setBits(0x34, 9, 4, 7)
        bytes.setBits(0x34, 13, 3, 6)
        bytes.setBits(0x34, 16, 5, 7)
        bytes.setBits(0x34, 21, 4, 12)
        bytes.setBits(0x34, 25, 5, 18)
        bytes.setBits(0x38, 0, 5, 23)
        bytes.setBits(0x38, 5, 3, 7)
        bytes.setBits(0x38, 8, 4, 8)
        bytes.setBits(0x38, 12, 3, 6)
        bytes.setBits(0x38, 16, 5, 11)
        bytes.setBits(0x38, 21, 4, 12)
        bytes.setBits(0x38, 25, 5, 3)
        bytes.setBits(0x3C, 0, 5, 17)
        bytes.setBits(0x3C, 5, 4, 8)
        bytes.setBits(0x3C, 9, 5, 18)
        bytes.setBits(0x3E, 0, 6, 35)
        bytes.setBits(0x3E, 6, 3, 4)
        bytes.setBits(0x3E, 9, 4, 8)
        bytes.setBits(0x3E, 13, 3, 6)
        bytes.setBits(0x3E, 16, 5, 18)
        bytes.setBits(0x3E, 21, 3, 5)
        bytes.setBits(0x42, 0, 3, 5)
        bytes.setBits(0x42, 3, 3, 7)
        bytes.setBits(0x42, 6, 4, 8)
        bytes.setBits(0x42, 10, 5, 16)
        bytes.setBits(0x44, 0, 4, 8)
        bytes.setBits(0x44, 4, 3, 5)
        bytes.setBits(0x44, 7, 4, 7)
        bytes.setBits(0x44, 11, 5, 20)
        bytes.setBits(0x44, 16, 1, 1)
        bytes.setBits(0x44, 17, 4, 8)
        bytes.setBits(0x44, 21, 5, 16)
        bytes.setBits(0x44, 26, 5, 30)
        bytes.sign()

        val expected = MiiAppearance(
            gender = 1,
            favoriteColor = 9,
            height = 100,
            build = 20,
            faceType = 11,
            skinColor = 5,
            wrinklesType = 7,
            makeupType = 3,
            hairType = 57,
            hairColor = 6,
            flipHair = true,
            eyeType = 45,
            eyeColor = 5,
            eyeScale = 7,
            eyeVerticalStretch = 6,
            eyeRotation = 7,
            eyeSpacing = 12,
            eyeYPosition = 18,
            eyebrowType = 23,
            eyebrowColor = 7,
            eyebrowScale = 8,
            eyebrowVerticalStretch = 6,
            eyebrowRotation = 11,
            eyebrowSpacing = 12,
            eyebrowYPosition = 3,
            noseType = 17,
            noseScale = 8,
            noseYPosition = 18,
            mouthType = 35,
            mouthColor = 4,
            mouthScale = 8,
            mouthHorizontalStretch = 6,
            mouthYPosition = 18,
            mustacheType = 5,
            beardType = 5,
            facialHairColor = 7,
            mustacheScale = 8,
            mustacheYPosition = 16,
            glassesType = 8,
            glassesColor = 5,
            glassesScale = 7,
            glassesYPosition = 20,
            moleEnabled = true,
            moleScale = 8,
            moleXPosition = 16,
            moleYPosition = 30,
        )
        assertEquals(expected, requireNotNull(Ver3StoreData.decode(bytes)).appearance)
    }

    @Test
    fun namesStopAtTheFirstNul() {
        val bytes = pretendoDefaultMii()
        "Piip".toByteArray(Charsets.UTF_16LE).copyInto(bytes, 0x1A)
        bytes[0x1A + 8] = 0
        bytes[0x1A + 9] = 0
        "PocketPass".toByteArray(Charsets.UTF_16LE).copyInto(bytes, 0x48)
        bytes.sign()

        val mii = requireNotNull(Ver3StoreData.decode(bytes))

        assertEquals("Piip", mii.name)
        assertEquals("PocketPass", mii.creator)
    }

    @Test
    fun importedMiisNeverCarryHatsOrCommonColourOverrides() {
        val appearance = requireNotNull(Ver3StoreData.decode(pretendoDefaultMii())).appearance

        assertEquals(-1, appearance.extHatType)
        assertEquals(-1, appearance.extHatColor)
        assertEquals(-1, appearance.extFacePaintColor)
        assertEquals(-1, appearance.extGlassesColor)
        assertEquals(-1, appearance.extHairColor)
        assertEquals(-1, appearance.extEyebrowColor)
        assertEquals(-1, appearance.extMouthColor)
        assertEquals(-1, appearance.extFacialHairColor)
        assertEquals(appearance, appearance.normalized())
    }

    @Test
    fun rejectsACorruptedChecksum() {
        val bytes = pretendoDefaultMii()
        bytes[0x32] = 5

        assertNull(Ver3StoreData.decode(bytes))
    }

    @Test
    fun rejectsTheWrongLength() {
        assertNull(Ver3StoreData.decode(pretendoDefaultMii().copyOf(95)))
        assertNull(Ver3StoreData.decode(pretendoDefaultMii().copyOf(97)))
        assertNull(Ver3StoreData.decode(ByteArray(0)))
    }

    private fun ByteArray.setBits(byteOffset: Int, bitOffset: Int, width: Int, value: Int) {
        for (bit in 0 until width) {
            val absolute = byteOffset * 8 + bitOffset + bit
            val index = absolute / 8
            val mask = 1 shl (absolute % 8)
            val current = this[index].toInt() and 0xFF
            this[index] = if ((value shr bit) and 1 == 1) {
                (current or mask).toByte()
            } else {
                (current and mask.inv()).toByte()
            }
        }
    }

    private fun ByteArray.sign() {
        val crc = Ver3StoreData.checksum(this)
        this[94] = (crc shr 8).toByte()
        this[95] = (crc and 0xFF).toByte()
    }

    companion object {
        const val PRETENDO_DEFAULT_MII_BASE64 =
            "AwAAQOlVognnx0GC2/uogAOzuI0n2QAAAEBEAGUAZgBhAHUAbAB0AAAAAAAAAEBAAAAhAQJoRBgmNEYUgRIXaA0AACkAUkhQ" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAGm9"

        fun pretendoDefaultMii(): ByteArray = Base64.getDecoder().decode(PRETENDO_DEFAULT_MII_BASE64)
    }
}
