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
import com.pocketpass.app.ui.screens.avatarResourceForKey
import com.pocketpass.app.ui.theme.DarkPalette
import com.pocketpass.app.ui.theme.LightPalette
import com.pocketpass.app.widget.WidgetPerson
import com.pocketpass.ui.resources.Res
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The few pictures the home-screen widgets cannot express as RemoteViews:
 * the figma SVG icons, which live in this module's compose resources, and the
 * framed circular Piip faces from the profile hero. Each is rasterised once at
 * the exact pixel size it is shown at, so nothing is scaled by the launcher.
 */
object WidgetAssets {
    private val typefaces = mutableMapOf<String, Typeface>()
    private var patternBitmap: Bitmap? = null

    suspend fun icon(context: Context, asset: PocketAsset, sizePx: Int): Bitmap? =
        decodeAsset(context, asset, sizePx, sizePx)

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

    /** The profile-hero avatar: surface disc, clipped portrait, teal frame ring. */
    suspend fun avatar(
        context: Context,
        portraitFile: File?,
        sizePx: Int,
        dark: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val image = portraitFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            ?: decodeAsset(context, Assets.HomeAvatarPetah, size, size)
        return framed(image, size, dark, initial = null)
    }

    suspend fun personAvatar(
        context: Context,
        person: WidgetPerson,
        sizePx: Int,
        dark: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val remote = person.avatarUrl?.let { url ->
            withTimeoutOrNull(REMOTE_AVATAR_TIMEOUT_MILLIS) { decodeUrl(context, url, size) }
        }
        val image = remote
            ?: person.avatarBundledKey?.let(::avatarResourceForKey)?.let { decodeAsset(context, it, size, size) }
        return framed(image, size, dark, initial = person.displayName.trim().take(1).uppercase())
    }

    private suspend fun framed(image: Bitmap?, size: Int, dark: Boolean, initial: String?): Bitmap =
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
            } else if (!initial.isNullOrBlank()) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.teal.toArgb()
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = inner * 1.1f
                }
                val baseline = center - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(initial, center, baseline, paint)
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

    private suspend fun decodeUrl(context: Context, url: String, sizePx: Int): Bitmap? {
        val loader: ImageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(sizePx, sizePx))
            .build()
        val result = runCatching { loader.execute(request) }.getOrNull() as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap(sizePx, sizePx) }.getOrNull()
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

    private const val REMOTE_AVATAR_TIMEOUT_MILLIS = 3_000L
}
