package com.pocketpass.app.media

import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

fun iosApplicationSupportPath(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
        ?: ""

class IosImageAttachmentPreparer(
    baseDirectory: String = iosApplicationSupportPath(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val fileSystem = FileSystem.SYSTEM
    private val directory: Path = baseDirectory.toPath() / "pocketpass" / ATTACHMENT_DIRECTORY

    suspend fun prepare(sourcePath: String): ImageAttachmentPreparation = withContext(dispatcher) {
        val image = UIImage.imageWithContentsOfFile(sourcePath)
            ?: return@withContext ImageAttachmentPreparation.Unreadable
        val source = image.pixelDimensions()
            ?: return@withContext ImageAttachmentPreparation.Unreadable
        val format = ImageAttachmentPolicy.formatFor(image.hasAlphaChannel())
        try {
            fileSystem.createDirectories(directory)
        } catch (_: okio.IOException) {
            return@withContext ImageAttachmentPreparation.Unreadable
        }
        for (maxLongEdge in ImageAttachmentPolicy.LONG_EDGE_STEPS) {
            val target = ImageAttachmentPolicy.fit(source, maxLongEdge)
            val scaled = image.renderedAt(target)
                ?: return@withContext ImageAttachmentPreparation.Unreadable
            val data = when (format) {
                ImageAttachmentFormat.Jpeg ->
                    UIImageJPEGRepresentation(scaled, ImageAttachmentPolicy.JPEG_QUALITY / 100.0)

                ImageAttachmentFormat.Png -> UIImagePNGRepresentation(scaled)
                ImageAttachmentFormat.Gif -> null
            } ?: return@withContext ImageAttachmentPreparation.Unreadable
            val byteCount = data.length.toLong()
            if (!ImageAttachmentPolicy.fitsUploadLimit(byteCount)) continue
            val file = directory / "${NSUUID().UUIDString}.${format.extension}"
            if (!data.writeToFile(file.toString(), atomically = true)) {
                return@withContext ImageAttachmentPreparation.Unreadable
            }
            return@withContext ImageAttachmentPreparation.Ready(
                PreparedImageAttachment(
                    path = file.toString(),
                    format = format,
                    dimensions = scaled.pixelDimensions() ?: target,
                    byteCount = byteCount,
                ),
            )
        }
        ImageAttachmentPreparation.TooLarge
    }

    suspend fun discard(path: String) = withContext(dispatcher) {
        try {
            fileSystem.delete(path.toPath(), mustExist = false)
        } catch (_: okio.IOException) {
        }
    }

    companion object {
        const val ATTACHMENT_DIRECTORY = "message-attachments"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.pixelDimensions(): ImageDimensions? {
    val imageScale = scale
    val (width, height) = size.useContents { (width * imageScale) to (height * imageScale) }
    val pixelWidth = width.roundToInt()
    val pixelHeight = height.roundToInt()
    if (pixelWidth <= 0 || pixelHeight <= 0) return null
    return ImageDimensions(pixelWidth, pixelHeight)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.hasAlphaChannel(): Boolean =
    when (CGImageGetAlphaInfo(CGImage)) {
        CGImageAlphaInfo.kCGImageAlphaNone,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipLast,
        -> false

        else -> true
    }

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.renderedAt(target: ImageDimensions): UIImage? {
    val width = target.width.toDouble()
    val height = target.height.toDouble()
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        scale = 1.0
        opaque = false
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format)
    return renderer.imageWithActions { _ ->
        drawInRect(CGRectMake(0.0, 0.0, width, height))
    }
}
