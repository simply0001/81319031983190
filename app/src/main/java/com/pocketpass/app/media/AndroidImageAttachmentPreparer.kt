package com.pocketpass.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImageAttachmentPreparer(
    private val context: Context,
    private val directory: File = File(context.filesDir, ATTACHMENT_DIRECTORY),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun prepare(uri: Uri): ImageAttachmentPreparation =
        prepare({ context.contentResolver.openInputStream(uri) }) { ImageDecoder.createSource(context.contentResolver, uri) }

    suspend fun prepare(file: File): ImageAttachmentPreparation =
        prepare({ file.inputStream() }) { ImageDecoder.createSource(file) }

    private suspend fun prepare(
        open: () -> InputStream?,
        source: () -> ImageDecoder.Source,
    ): ImageAttachmentPreparation = withContext(dispatcher) {
        directory.mkdirs()
        try {
            open()?.buffered()?.use { input ->
                val header = ByteArray(6) { input.read().toByte() }
                if (header.decodeToString() in setOf("GIF87a", "GIF89a")) {
                    return@withContext preserveGif(header, input)
                }
            } ?: return@withContext ImageAttachmentPreparation.Unreadable
        } catch (_: Exception) {
            return@withContext ImageAttachmentPreparation.Unreadable
        }
        for (maxLongEdge in ImageAttachmentPolicy.LONG_EDGE_STEPS) {
            val bitmap = decode(source, maxLongEdge)
                ?: return@withContext ImageAttachmentPreparation.Unreadable
            val candidate = try {
                encode(bitmap)
            } finally {
                bitmap.recycle()
            }
            candidate ?: return@withContext ImageAttachmentPreparation.Unreadable
            if (ImageAttachmentPolicy.fitsUploadLimit(candidate.byteCount)) {
                return@withContext ImageAttachmentPreparation.Ready(candidate)
            }
            File(candidate.path).delete()
        }
        ImageAttachmentPreparation.TooLarge
    }

    private fun preserveGif(header: ByteArray, input: InputStream): ImageAttachmentPreparation {
        val target = File(directory, "${UUID.randomUUID()}.gif")
        var keep = false
        try {
            target.outputStream().buffered().use { output ->
                output.write(header)
                var count = header.size.toLong()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (!ImageAttachmentPolicy.fitsUploadLimit(count)) return ImageAttachmentPreparation.TooLarge
                    output.write(buffer, 0, read)
                }
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(target.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ImageAttachmentPreparation.Unreadable
            val dimensions = ImageDimensions(bounds.outWidth, bounds.outHeight)
            if (dimensions.longEdge > ImageAttachmentPolicy.MAX_LONG_EDGE_PIXELS) return ImageAttachmentPreparation.TooLarge
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(target))
            when (drawable) {
                is AnimatedImageDrawable -> drawable.stop()
                is BitmapDrawable -> drawable.bitmap.recycle()
            }
            keep = true
            return ImageAttachmentPreparation.Ready(PreparedImageAttachment(target.absolutePath, ImageAttachmentFormat.Gif, dimensions, target.length()))
        } catch (_: Exception) {
            return ImageAttachmentPreparation.Unreadable
        } finally {
            if (!keep) target.delete()
        }
    }

    private fun decode(source: () -> ImageDecoder.Source, maxLongEdge: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(source()) { decoder, info, _ ->
            val target = ImageAttachmentPolicy.fit(
                ImageDimensions(info.size.width, info.size.height),
                maxLongEdge,
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(target.width, target.height)
        }
    } catch (_: Exception) {
        null
    }

    private fun encode(bitmap: Bitmap): PreparedImageAttachment? {
        val format = ImageAttachmentPolicy.formatFor(bitmap.hasAlpha())
        val target = File(directory, "${UUID.randomUUID()}.${format.extension}")
        val staging = File(directory, "${target.name}.part")
        val written = try {
            staging.outputStream().buffered().use { output ->
                when (format) {
                    ImageAttachmentFormat.Jpeg -> bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        ImageAttachmentPolicy.JPEG_QUALITY,
                        output,
                    )

                    ImageAttachmentFormat.Png -> bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output,
                    )
                    ImageAttachmentFormat.Gif -> false
                }
            }
        } catch (_: Exception) {
            false
        }
        if (!written || !staging.renameTo(target)) {
            staging.delete()
            return null
        }
        return PreparedImageAttachment(
            path = target.absolutePath,
            format = format,
            dimensions = ImageDimensions(bitmap.width, bitmap.height),
            byteCount = target.length(),
        )
    }

    companion object {
        const val ATTACHMENT_DIRECTORY = "message-attachments"
    }
}
