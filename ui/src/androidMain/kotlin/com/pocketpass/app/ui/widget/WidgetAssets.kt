@file:OptIn(ExperimentalResourceApi::class)

package com.pocketpass.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.theme.DarkPalette
import com.pocketpass.app.ui.theme.LightPalette
import com.pocketpass.ui.resources.Res
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

object WidgetAssets {
    private val typefaces = ConcurrentHashMap<String, Typeface>()

    @Volatile
    private var patternBitmap: Bitmap? = null

    suspend fun icon(context: Context, asset: PocketAsset, widthPx: Int, heightPx: Int): Bitmap? =
        decodeAsset(context, asset, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))

    suspend fun typeface(context: Context, resourcePath: String): Typeface {
        typefaces[resourcePath]?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "fonts/" + resourcePath.substringAfterLast('/'))
                if (!file.isFile) {
                    file.parentFile?.mkdirs()
                    file.writeBytes(Res.readBytes(resourcePath))
                }
                Typeface.createFromFile(file)
            }.getOrNull()
        } ?: Typeface.DEFAULT_BOLD
        typefaces[resourcePath] = loaded
        return loaded
    }

    suspend fun pattern(context: Context): Bitmap? {
        patternBitmap?.let { return it }
        val bytes = runCatching { Res.readBytes(Assets.WidgetPattern.path) }.getOrNull() ?: return null
        val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        patternBitmap = decoded
        return decoded
    }

    suspend fun avatar(
        context: Context,
        portraitFile: File?,
        sizePx: Int,
        dark: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val image = portraitFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            ?: decodeAsset(context, Assets.HomeAvatarPetah, size, size)
        return framed(image, size, dark)
    }

    private suspend fun framed(image: Bitmap?, size: Int, dark: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            val palette = if (dark) DarkPalette else LightPalette
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f
            val border = size * (22f / 449f)
            canvas.drawCircle(
                center,
                center,
                center,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface.toArgb() },
            )
            val inner = center - border - 1f
            if (image != null) {
                canvas.save()
                canvas.clipPath(Path().apply { addCircle(center, center, inner, Path.Direction.CW) })
                canvas.drawCircle(
                    center,
                    center,
                    inner,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
                )
                canvas.drawBitmap(
                    image,
                    null,
                    RectF(center - inner, center - inner, center + inner, center + inner),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
                )
                canvas.restore()
            }
            canvas.drawCircle(
                center,
                center,
                center - border / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = border
                    color = palette.tealBorder.toArgb()
                },
            )
            bitmap
        }

    private suspend fun decodeAsset(
        context: Context,
        asset: PocketAsset,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? {
        val bytes = runCatching { Res.readBytes(asset.path) }.getOrNull() ?: return null
        val loader: ImageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .size(Size(widthPx, heightPx))
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap(widthPx, heightPx)
    }
}
