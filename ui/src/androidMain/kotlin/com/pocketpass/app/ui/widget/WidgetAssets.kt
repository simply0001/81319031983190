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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The few pictures the home-screen widgets cannot express as RemoteViews:
 * the figma SVG icons, which live in this module's compose resources, and the
 * framed circular avatar from the profile hero. Each is rasterised once at the
 * exact pixel size it is shown at, so nothing is scaled by the launcher.
 */
object WidgetAssets {
    suspend fun icon(context: Context, asset: PocketAsset, sizePx: Int): Bitmap? =
        decodeAsset(context, asset, sizePx, sizePx)

    /** The profile-hero avatar: surface disc, clipped portrait, teal frame ring. */
    suspend fun avatar(
        context: Context,
        portraitFile: File?,
        sizePx: Int,
        dark: Boolean,
    ): Bitmap = withContext(Dispatchers.Default) {
        val palette = if (dark) DarkPalette else LightPalette
        val size = sizePx.coerceAtLeast(1)
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
        val image = portraitFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            ?: decodeAsset(context, Assets.HomeAvatarPetah, size, size)
        if (image != null) {
            val inner = center - border - 1f
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
