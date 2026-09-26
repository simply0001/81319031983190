package com.pocketpass.app.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageAttachmentPolicyTest {
    @Test
    fun fitShrinksTheLongEdgeAndKeepsTheAspectRatio() {
        assertEquals(ImageDimensions(2048, 1536), ImageAttachmentPolicy.fit(ImageDimensions(4000, 3000)))
        assertEquals(ImageDimensions(1536, 2048), ImageAttachmentPolicy.fit(ImageDimensions(3000, 4000)))
        assertEquals(ImageDimensions(2048, 2048), ImageAttachmentPolicy.fit(ImageDimensions(5000, 5000)))
        assertEquals(ImageDimensions(1600, 900), ImageAttachmentPolicy.fit(ImageDimensions(3840, 2160), 1600))
    }

    @Test
    fun fitNeverUpscalesOrCollapsesAnEdge() {
        assertEquals(ImageDimensions(800, 600), ImageAttachmentPolicy.fit(ImageDimensions(800, 600)))
        assertEquals(ImageDimensions(2048, 100), ImageAttachmentPolicy.fit(ImageDimensions(2048, 100)))
        assertEquals(ImageDimensions(2048, 1), ImageAttachmentPolicy.fit(ImageDimensions(20000, 3)))
        assertFailsWith<IllegalArgumentException> { ImageAttachmentPolicy.fit(ImageDimensions(10, 10), 0) }
        assertFailsWith<IllegalArgumentException> { ImageDimensions(0, 10) }
    }

    @Test
    fun stepsStartAtTheCapAndOnlyShrink() {
        val steps = ImageAttachmentPolicy.LONG_EDGE_STEPS
        assertEquals(ImageAttachmentPolicy.MAX_LONG_EDGE_PIXELS, steps.first())
        assertEquals(steps.sortedDescending(), steps)
        assertEquals(steps.distinct(), steps)
        assertTrue(steps.all { it > 0 })
    }

    @Test
    fun transparencyPicksPngAndEverythingElseJpeg() {
        assertEquals(ImageAttachmentFormat.Png, ImageAttachmentPolicy.formatFor(hasAlpha = true))
        assertEquals(ImageAttachmentFormat.Jpeg, ImageAttachmentPolicy.formatFor(hasAlpha = false))
        assertEquals("image/png", ImageAttachmentFormat.Png.mimeType)
        assertEquals("jpg", ImageAttachmentFormat.Jpeg.extension)
    }

    @Test
    fun uploadLimitMatchesTheBucket() {
        assertEquals(10L * 1024 * 1024, ImageAttachmentPolicy.MAX_UPLOAD_BYTES)
        assertTrue(ImageAttachmentPolicy.fitsUploadLimit(1))
        assertTrue(ImageAttachmentPolicy.fitsUploadLimit(ImageAttachmentPolicy.MAX_UPLOAD_BYTES))
        assertFalse(ImageAttachmentPolicy.fitsUploadLimit(ImageAttachmentPolicy.MAX_UPLOAD_BYTES + 1))
        assertFalse(ImageAttachmentPolicy.fitsUploadLimit(0))
    }

    @Test
    fun failuresCarryTheirUserMessage() {
        assertEquals(ImageAttachmentPolicy.UNREADABLE_MESSAGE, ImageAttachmentPreparation.Unreadable.message)
        assertEquals(ImageAttachmentPolicy.TOO_LARGE_MESSAGE, ImageAttachmentPreparation.TooLarge.message)
        val prepared = PreparedImageAttachment(
            path = "/tmp/a.jpg",
            format = ImageAttachmentFormat.Jpeg,
            dimensions = ImageDimensions(2048, 1536),
            byteCount = 512_000,
        )
        assertEquals("image/jpeg", ImageAttachmentPreparation.Ready(prepared).attachment.mimeType)
    }
}
