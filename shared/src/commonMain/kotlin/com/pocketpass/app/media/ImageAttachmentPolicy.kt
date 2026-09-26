package com.pocketpass.app.media

import kotlin.math.max
import kotlin.math.roundToInt

enum class ImageAttachmentFormat(val mimeType: String, val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    Gif("image/gif", "gif"),
}

data class ImageDimensions(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
    }

    val longEdge: Int
        get() = max(width, height)
}

data class PreparedImageAttachment(
    val path: String,
    val format: ImageAttachmentFormat,
    val dimensions: ImageDimensions,
    val byteCount: Long,
) {
    val mimeType: String
        get() = format.mimeType
}

sealed interface ImageAttachmentPreparation {
    data class Ready(val attachment: PreparedImageAttachment) : ImageAttachmentPreparation

    sealed interface Failed : ImageAttachmentPreparation {
        val message: String
    }

    data object Unreadable : Failed {
        override val message: String = ImageAttachmentPolicy.UNREADABLE_MESSAGE
    }

    data object TooLarge : Failed {
        override val message: String = ImageAttachmentPolicy.TOO_LARGE_MESSAGE
    }
}

object ImageAttachmentPolicy {
    const val MAX_LONG_EDGE_PIXELS: Int = 2048
    const val JPEG_QUALITY: Int = 85
    const val MAX_UPLOAD_BYTES: Long = 10L * 1024 * 1024
    const val UNREADABLE_MESSAGE: String = "That image could not be read."
    const val TOO_LARGE_MESSAGE: String = "That image is too large to send."

    val LONG_EDGE_STEPS: List<Int> = listOf(MAX_LONG_EDGE_PIXELS, 1600, 1280, 1024, 800)

    fun fit(source: ImageDimensions, maxLongEdge: Int = MAX_LONG_EDGE_PIXELS): ImageDimensions {
        require(maxLongEdge > 0) { "maxLongEdge must be positive" }
        if (source.longEdge <= maxLongEdge) return source
        val scale = maxLongEdge.toDouble() / source.longEdge
        return if (source.width >= source.height) {
            ImageDimensions(maxLongEdge, max(1, (source.height * scale).roundToInt()))
        } else {
            ImageDimensions(max(1, (source.width * scale).roundToInt()), maxLongEdge)
        }
    }

    fun formatFor(hasAlpha: Boolean): ImageAttachmentFormat =
        if (hasAlpha) ImageAttachmentFormat.Png else ImageAttachmentFormat.Jpeg

    fun fitsUploadLimit(byteCount: Long): Boolean = byteCount in 1..MAX_UPLOAD_BYTES
}
