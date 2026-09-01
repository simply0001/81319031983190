@file:OptIn(ExperimentalResourceApi::class)

package com.pocketpass.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
import com.pocketpass.app.ui.theme.PocketPalette
import com.pocketpass.app.widget.WidgetSnapshot
import com.pocketpass.ui.resources.Res
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Draws the home-screen widget cards with plain android.graphics, outside
 * Compose: RemoteViews cannot use Rubik or the pocket-frame styling, so the
 * app pre-renders the whole card and the widget shows the bitmap. Colors come
 * straight from LightPalette/DarkPalette; type from the bundled Rubik fonts.
 */
object WidgetCardRenderer {
    class Inputs(
        val snapshot: WidgetSnapshot?,
        val portraitFile: File?,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val dark: Boolean,
        val nowEpochMillis: Long,
    )

    suspend fun renderSummary(context: Context, inputs: Inputs): Bitmap = withContext(Dispatchers.Default) {
        preloadFonts(context, setOf(500, 600))
        val scene = Scene(context, inputs)
        scene.drawBackdrop()
        val snapshot = inputs.snapshot
        if (snapshot == null || !snapshot.signedIn) {
            scene.drawPlaceholder()
            return@withContext scene.bitmap
        }
        val wide = inputs.widthPx >= inputs.dp(230f)
        val pad = inputs.dp(14f)
        val heroSize = if (wide) inputs.dp(44f) else inputs.dp(40f)
        val iconSize = if (wide) inputs.dp(30f) else inputs.dp(26f)
        val wave = scene.asset(Assets.FriendWave, iconSize.roundToInt(), iconSize.roundToInt())
        // Hero: wave icon + encounters today.
        var x = pad
        var y = pad + heroSize
        wave?.let { scene.canvas.drawBitmap(it, x, y - heroSize * 0.85f, null) ; x += iconSize + inputs.dp(10f) }
        scene.text(snapshot.encountersToday.toString(), x, y, heroSize, 600, scene.palette.teal)
        scene.text(
            if (snapshot.encountersToday == 1) "encounter today" else "encounters today",
            pad,
            y + inputs.dp(16f),
            inputs.dp(12f),
            500,
            scene.palette.textMuted,
        )
        // Nearby pill on the top-right.
        scene.nearbyPill(snapshot.nearbyStatus, right = inputs.widthPx - pad, top = pad)
        // Bottom row: last pass, unread, friends online.
        val rowY = inputs.heightPx - pad - inputs.dp(4f)
        scene.text(
            lastPassLabel(snapshot.lastEncounterEpochMillis, inputs.nowEpochMillis),
            pad,
            rowY,
            inputs.dp(12f),
            600,
            scene.palette.tealSoft,
            maxWidth = if (wide) inputs.widthPx * 0.5f else inputs.widthPx - 2 * pad,
        )
        if (wide) {
            val statSize = inputs.dp(13f)
            val unread = "${snapshot.unreadNotifications} unread"
            val online = "${snapshot.friendsOnline} online"
            val unreadWidth = scene.measure(unread, statSize, 600)
            val onlineWidth = scene.measure(online, statSize, 600)
            val gap = inputs.dp(12f)
            var statX = inputs.widthPx - pad - onlineWidth
            scene.dot(statX - inputs.dp(10f), rowY - statSize * 0.35f, inputs.dp(3.5f), scene.palette.teal)
            scene.text(online, statX, rowY, statSize, 600, scene.palette.textPrimary)
            statX -= gap + inputs.dp(14f) + unreadWidth
            scene.dot(statX - inputs.dp(10f), rowY - statSize * 0.35f, inputs.dp(3.5f), ACCENT_RED)
            scene.text(unread, statX, rowY, statSize, 600, scene.palette.textPrimary)
        }
        scene.bitmap
    }

    suspend fun renderProfile(context: Context, inputs: Inputs): Bitmap = withContext(Dispatchers.Default) {
        preloadFonts(context, setOf(600, 800))
        val scene = Scene(context, inputs)
        scene.drawBackdrop()
        val snapshot = inputs.snapshot
        if (snapshot == null || !snapshot.signedIn) {
            scene.drawPlaceholder()
            return@withContext scene.bitmap
        }
        val pad = inputs.dp(14f)
        val avatarSize = min(inputs.heightPx - 2 * pad, inputs.dp(96f)).toFloat()
        scene.avatar(
            left = pad,
            top = (inputs.heightPx - avatarSize) / 2f,
            size = avatarSize,
            portrait = inputs.portraitFile?.takeIf { it.isFile }?.let { file ->
                BitmapFactory.decodeFile(file.path)
            },
            fallback = scene.asset(
                Assets.HomeAvatarPetah,
                avatarSize.roundToInt(),
                avatarSize.roundToInt(),
            ),
        )
        val textX = pad + avatarSize + inputs.dp(14f)
        val textWidth = inputs.widthPx - textX - pad
        val name = snapshot.displayName.ifBlank { "PocketPass" }
        val nameSize = inputs.dp(20f)
        var y = (inputs.heightPx / 2f) - inputs.dp(10f)
        scene.text(name, textX, y, nameSize, 800, scene.palette.teal, maxWidth = textWidth)
        y += inputs.dp(6f)
        val bioHeight = scene.paragraph(
            snapshot.bio,
            textX,
            y,
            textWidth,
            inputs.dp(13f),
            600,
            scene.palette.tealSoft,
            maxLines = 2,
        )
        y += bioHeight + inputs.dp(10f)
        val counterSize = inputs.dp(12f)
        val counter = when (snapshot.encountersToday) {
            0 -> "No encounters yet today"
            1 -> "1 encounter today"
            else -> "${snapshot.encountersToday} encounters today"
        }
        if (y + counterSize <= inputs.heightPx - pad) {
            scene.dot(textX + inputs.dp(4f), y - counterSize * 0.35f, inputs.dp(3.5f), scene.palette.teal)
            scene.text(counter, textX + inputs.dp(14f), y, counterSize, 600, scene.palette.textMuted, maxWidth = textWidth)
        }
        scene.bitmap
    }

    fun lastPassLabel(lastEpochMillis: Long?, nowEpochMillis: Long): String {
        if (lastEpochMillis == null) return "No passes yet"
        val elapsed = (nowEpochMillis - lastEpochMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = elapsed / 3_600_000L
        val days = elapsed / 86_400_000L
        return when {
            minutes < 1 -> "Last pass just now"
            minutes < 60 -> "Last pass ${minutes}m ago"
            hours < 24 -> "Last pass ${hours}h ago"
            days == 1L -> "Last pass yesterday"
            else -> "Last pass $days days ago"
        }
    }

    private class Scene(private val context: Context, private val inputs: Inputs) {
        val palette: PocketPalette = if (inputs.dark) DarkPalette else LightPalette
        val bitmap: Bitmap = Bitmap.createBitmap(inputs.widthPx, inputs.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        private val cornerRadius = inputs.dp(18f)
        private val frameWidth = inputs.dp(2.5f)

        fun drawBackdrop() {
            val outer = RectF(0f, 0f, inputs.widthPx.toFloat(), inputs.heightPx.toFloat())
            val body = RectF(outer).apply { inset(inputs.dp(2f), inputs.dp(2f)); bottom -= inputs.dp(1f) }
            // Soft downward shadow, like pocketShadow.
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * palette.shadowAlpha * 0.6f).roundToInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(inputs.dp(6f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(RectF(body).apply { offset(0f, inputs.dp(2f)) }, cornerRadius, cornerRadius, shadow)
            // Home backdrop gradient with the hold fraction, then the pocket frame.
            val backdrop = palette.background(com.pocketpass.app.model.PocketPassDestination.Home, top = false)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, body.top, 0f, body.bottom,
                    intArrayOf(backdrop.top.toArgb(), backdrop.top.toArgb(), backdrop.bottom.toArgb()),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(body, cornerRadius, cornerRadius, fill)
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = frameWidth
                color = palette.borderSoft.toArgb()
            }
            val frameRect = RectF(body).apply { inset(frameWidth / 2f, frameWidth / 2f) }
            canvas.drawRoundRect(frameRect, cornerRadius - frameWidth / 2f, cornerRadius - frameWidth / 2f, frame)
        }

        fun drawPlaceholder() {
            val size = inputs.dp(15f)
            val label = "Open PocketPass"
            val width = measure(label, size, 600)
            text(label, (inputs.widthPx - width) / 2f, inputs.heightPx / 2f + size * 0.35f, size, 600, palette.teal)
        }

        fun nearbyPill(status: String, right: Float, top: Float) {
            val running = status == "Running"
            val label = when (status) {
                "Running" -> "Nearby on"
                "BluetoothOff" -> "Bluetooth off"
                "NeedsPermissions", "NeedsOnboarding" -> "Nearby setup"
                else -> "Nearby off"
            }
            val size = inputs.dp(11f)
            val textWidth = measure(label, size, 600)
            val padX = inputs.dp(9f)
            val height = inputs.dp(22f)
            val dotRadius = inputs.dp(3.5f)
            val pillWidth = textWidth + padX * 2 + dotRadius * 2 + inputs.dp(6f)
            val rect = RectF(right - pillWidth, top, right, top + height)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface.toArgb() }
            canvas.drawRoundRect(rect, height / 2f, height / 2f, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = inputs.dp(1.5f)
                color = palette.borderSoft.toArgb()
            }
            canvas.drawRoundRect(rect, height / 2f, height / 2f, stroke)
            dot(rect.left + padX + dotRadius, rect.centerY(), dotRadius, if (running) ONLINE_GREEN else palette.textMuted.toArgb())
            text(label, rect.left + padX + dotRadius * 2 + inputs.dp(6f), rect.centerY() + size * 0.35f, size, 600, palette.textPrimary)
        }

        fun avatar(left: Float, top: Float, size: Float, portrait: Bitmap?, fallback: Bitmap?) {
            val cx = left + size / 2f
            val cy = top + size / 2f
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * palette.shadowAlpha).roundToInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(inputs.dp(5f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(cx, cy + size * 0.03f, size / 2f, shadow)
            val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface.toArgb() }
            canvas.drawCircle(cx, cy, size / 2f, surface)
            val border = size * (22f / 449f)
            val image = portrait ?: fallback
            if (image != null) {
                val inner = size / 2f - border - inputs.dp(1f)
                canvas.save()
                canvas.clipPath(Path().apply { addCircle(cx, cy, inner, Path.Direction.CW) })
                val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                canvas.drawCircle(cx, cy, inner, white)
                val target = RectF(cx - inner, cy - inner, cx + inner, cy + inner)
                canvas.drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = border
                color = palette.tealBorder.toArgb()
            }
            canvas.drawCircle(cx, cy, size / 2f - border / 2f, ring)
        }

        fun dot(cx: Float, cy: Float, radius: Float, color: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawCircle(cx, cy, radius, paint)
        }

        fun dot(cx: Float, cy: Float, radius: Float, color: ComposeColor) = dot(cx, cy, radius, color.toArgb())

        fun measure(text: String, sizePx: Float, weight: Int): Float =
            textPaint(sizePx, weight, Color.BLACK).measureText(text)

        fun text(
            text: String,
            x: Float,
            baseline: Float,
            sizePx: Float,
            weight: Int,
            color: ComposeColor,
            maxWidth: Float = Float.MAX_VALUE,
        ) {
            val paint = textPaint(sizePx, weight, color.toArgb())
            val shown = if (maxWidth < Float.MAX_VALUE) {
                TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
            } else {
                text
            }
            canvas.drawText(shown, x, baseline, paint)
        }

        fun paragraph(
            text: String,
            x: Float,
            top: Float,
            width: Float,
            sizePx: Float,
            weight: Int,
            color: ComposeColor,
            maxLines: Int,
        ): Float {
            val paint = textPaint(sizePx, weight, color.toArgb())
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.roundToInt().coerceAtLeast(1))
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(x, top)
            layout.draw(canvas)
            canvas.restore()
            return layout.height.toFloat()
        }

        private fun textPaint(sizePx: Float, weight: Int, color: Int): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = rubik(context, weight)
                textSize = sizePx
                this.color = color
            }

        suspend fun asset(asset: PocketAsset, widthPx: Int, heightPx: Int): Bitmap? =
            decodeAsset(context, asset, widthPx, heightPx)
    }

    private val fontMutex = Mutex()
    private val typefaces = mutableMapOf<Int, Typeface>()

    // Fonts are loaded up front so drawing stays synchronous on the canvas.
    private suspend fun preloadFonts(context: Context, weights: Set<Int>) {
        weights.filterNot(typefaces::containsKey).forEach { weight ->
            typefaces[weight] = loadRubik(context, weight)
        }
    }

    private fun rubik(context: Context, weight: Int): Typeface =
        typefaces[weight] ?: typefaces.values.firstOrNull() ?: Typeface.DEFAULT_BOLD

    private suspend fun loadRubik(context: Context, weight: Int): Typeface = fontMutex.withLock {
        val file = File(context.cacheDir, "widget_fonts/rubik_variable.ttf")
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            val bytes = Res.readBytes("font/rubik_variable.ttf")
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeBytes(bytes)
            temporary.renameTo(file)
        }
        Typeface.Builder(file)
            .setFontVariationSettings("'wght' $weight")
            .build()
            ?: Typeface.DEFAULT_BOLD
    }

    private suspend fun decodeAsset(context: Context, asset: PocketAsset, widthPx: Int, heightPx: Int): Bitmap? {
        val bytes = runCatching { Res.readBytes(asset.path) }.getOrNull() ?: return null
        val loader: ImageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .size(Size(widthPx, heightPx))
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap(widthPx, heightPx)
    }

    private const val ONLINE_GREEN = 0xFF51FF85.toInt()
    private val ACCENT_RED = ComposeColor(0xFFE25757)

    private fun Inputs.dp(value: Float): Float = value * density
}
