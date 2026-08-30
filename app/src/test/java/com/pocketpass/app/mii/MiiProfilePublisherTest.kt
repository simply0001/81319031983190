package com.pocketpass.app.mii

import com.pocketpass.app.data.supabase.miiAvatarPath
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MiiProfilePublisherTest {
    @Test
    fun publicationPathIsOwnerPrefixedRevisionedAndIdempotent() {
        val path = miiAvatarPath(
            accountId = UserId(ACCOUNT_ID),
            revision = 42,
            clientOperationId = OPERATION_ID,
        )

        assertEquals(
            "$ACCOUNT_ID/mii-r42-$OPERATION_ID.png",
            path,
        )
    }

    @Test
    fun commandRejectsNonPngAndUnnormalizedAppearance() {
        assertThrows(IllegalArgumentException::class.java) {
            PublishMiiProfileCommand(
                accountId = UserId(ACCOUNT_ID),
                appearance = MiiAppearance(),
                portraitPng = byteArrayOf(1, 2, 3),
                revision = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PublishMiiProfileCommand(
                accountId = UserId(ACCOUNT_ID),
                appearance = MiiAppearance(glassesType = 99),
                portraitPng = validPng(),
                revision = 1,
            )
        }
    }

    @Test
    fun commandAcceptsSanitizedAppearanceAndOptionalMiic() {
        val command = PublishMiiProfileCommand(
            accountId = UserId(ACCOUNT_ID),
            appearance = MiiAppearance(glassesType = 19),
            portraitPng = validPng(),
            canonicalMiic = byteArrayOf(1, 2, 3),
            revision = 7,
            clientOperationId = ClientOperationId(OPERATION_ID),
        )

        assertEquals(7, command.revision)
        assertEquals(19, command.appearance.glassesType)
        assertTrue(command.canonicalMiic!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun serializedAppearanceHasNoPersonalIdentityFields() {
        val payload = Json { encodeDefaults = true }.encodeToString(MiiAppearance())

        assertTrue("\"schemaVersion\"" in payload)
        assertFalse("\"name\"" in payload)
        assertFalse("creator" in payload.lowercase())
        assertFalse("birth" in payload.lowercase())
        assertFalse("account" in payload.lowercase())
    }

    private fun validPng(): ByteArray = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
    )

    private companion object {
        const val ACCOUNT_ID = "90000000-0000-4000-8000-000000000001"
        const val OPERATION_ID = "91000000-0000-4000-8000-000000000001"
    }
}
