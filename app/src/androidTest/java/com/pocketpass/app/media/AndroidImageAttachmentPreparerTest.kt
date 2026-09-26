package com.pocketpass.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidImageAttachmentPreparerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workingDirectory: File
    private lateinit var outputDirectory: File
    private lateinit var preparer: AndroidImageAttachmentPreparer

    @Before
    fun createDirectories() {
        workingDirectory = File(context.cacheDir, "preparer-${UUID.randomUUID()}").apply { mkdirs() }
        outputDirectory = File(workingDirectory, "out")
        preparer = AndroidImageAttachmentPreparer(context, outputDirectory)
    }

    @After
    fun deleteDirectories() {
        workingDirectory.deleteRecursively()
    }

    @Test
    fun largePhotosAreResizedReencodedAndStrippedOfMetadata() = runBlocking {
        val source = File(workingDirectory, "photo.jpg")
        writeJpeg(source, 4000, 3000)
        tagWithLocation(source)
        assertTrue(hasLocation(source))

        val prepared = ready(preparer.prepare(Uri.fromFile(source)))

        assertEquals(ImageAttachmentFormat.Jpeg, prepared.format)
        assertEquals(ImageDimensions(2048, 1536), prepared.dimensions)
        val output = File(prepared.path)
        assertEquals(outputDirectory, output.parentFile)
        assertTrue(output.name.endsWith(".jpg"))
        assertEquals(output.length(), prepared.byteCount)
        assertTrue(ImageAttachmentPolicy.fitsUploadLimit(prepared.byteCount))
        assertEquals(2048 to 1536, decodedBounds(output))
        assertFalse(hasLocation(output))
        assertNull(ExifInterface(output.absolutePath).getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test
    fun smallPhotosKeepTheirSizeButStillLoseMetadata() = runBlocking {
        val source = File(workingDirectory, "small.jpg")
        writeJpeg(source, 640, 480)
        tagWithLocation(source)

        val prepared = ready(preparer.prepare(source))

        assertEquals(ImageAttachmentFormat.Jpeg, prepared.format)
        assertEquals(ImageDimensions(640, 480), prepared.dimensions)
        assertEquals(640 to 480, decodedBounds(File(prepared.path)))
        assertFalse(hasLocation(File(prepared.path)))
    }

    @Test
    fun rotatedPhotosComeOutUpright() = runBlocking {
        val source = File(workingDirectory, "rotated.jpg")
        writeJpeg(source, 3000, 2000)
        ExifInterface(source.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }

        val prepared = ready(preparer.prepare(source))

        assertEquals(ImageDimensions(1365, 2048), prepared.dimensions)
        assertEquals(1365 to 2048, decodedBounds(File(prepared.path)))
        val orientation = ExifInterface(prepared.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
        assertTrue(
            orientation == ExifInterface.ORIENTATION_UNDEFINED ||
                orientation == ExifInterface.ORIENTATION_NORMAL,
        )
    }

    @Test
    fun transparentImagesStayPng() = runBlocking {
        val source = File(workingDirectory, "sticker.png")
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.TRANSPARENT)
            drawRect(300f, 0f, 600f, 400f, Paint().apply { color = Color.MAGENTA })
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val prepared = ready(preparer.prepare(source))

        assertEquals(ImageAttachmentFormat.Png, prepared.format)
        assertEquals(ImageDimensions(600, 400), prepared.dimensions)
        assertTrue(prepared.path.endsWith(".png"))
        val decoded = BitmapFactory.decodeFile(prepared.path)
        assertEquals(0, Color.alpha(decoded.getPixel(10, 10)))
        assertEquals(255, Color.alpha(decoded.getPixel(590, 390)))
        assertEquals(Color.MAGENTA, decoded.getPixel(590, 390))
        decoded.recycle()
    }

    @Test
    fun unreadableInputIsRejectedWithoutLeavingFiles() = runBlocking {
        val garbage = File(workingDirectory, "garbage.jpg")
        garbage.writeBytes(ByteArray(4096) { (it * 31).toByte() })

        assertEquals(ImageAttachmentPreparation.Unreadable, preparer.prepare(garbage))
        assertEquals(
            ImageAttachmentPreparation.Unreadable,
            preparer.prepare(Uri.fromFile(File(workingDirectory, "missing.jpg"))),
        )
        assertTrue(outputDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun ready(outcome: ImageAttachmentPreparation): PreparedImageAttachment {
        assertTrue("expected a prepared image but got $outcome", outcome is ImageAttachmentPreparation.Ready)
        return (outcome as ImageAttachmentPreparation.Ready).attachment
    }

    @Test fun gifsKeepTheirFramesAndAreDetectedByContent() = runBlocking {
        val bytes = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("animated-message.gif").use { it.readBytes() }
        val source = File(workingDirectory, "misnamed-photo.jpg").apply { writeBytes(bytes) }
        val prepared = ready(preparer.prepare(Uri.fromFile(source)))
        assertEquals(ImageAttachmentFormat.Gif, prepared.format)
        assertEquals("image/gif", prepared.mimeType)
        assertTrue(bytes.contentEquals(File(prepared.path).readBytes()))
        assertEquals(ImageDimensions(32, 24), prepared.dimensions)
        val decoded = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(File(prepared.path)))
        assertTrue(decoded is android.graphics.drawable.AnimatedImageDrawable)
        val result = coil3.SingletonImageLoader.get(context).execute(coil3.request.ImageRequest.Builder(context).data(File(prepared.path)).build())
        assertTrue(result is coil3.request.SuccessResult)
    }

    @Test fun oversizedAndBrokenGifsAreRejectedWithoutFlattening() = runBlocking {
        val source = File(workingDirectory, "large.gif")
        source.outputStream().use { output ->
            output.write("GIF89a".toByteArray())
            repeat(11) { output.write(ByteArray(1024 * 1024)) }
        }
        assertEquals(ImageAttachmentPreparation.TooLarge, preparer.prepare(source))
        source.writeText("GIF89a")
        assertEquals(ImageAttachmentPreparation.Unreadable, preparer.prepare(source))
        assertTrue(outputDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun writeJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(40, 90, 160))
            drawCircle(width / 3f, height / 2f, min(width, height) / 4f, Paint().apply { color = Color.YELLOW })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
    }

    private fun tagWithLocation(file: File) {
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,51/1,30/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "2/1,17/1,40/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            saveAttributes()
        }
    }

    private fun hasLocation(file: File): Boolean =
        ExifInterface(file.absolutePath).getLatLong(FloatArray(2))

    private fun decodedBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }
}
