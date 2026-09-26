package com.pocketpass.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.pocketpass.app.PocketPassLauncherActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class CustomWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    class Loaded(
        val bitmaps: Map<DpSize, Bitmap>,
        val onClick: Action,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val startGeneration = WidgetRefresh.generation.value
        val initial = load(context, id)
        provideContent {
            val generation by WidgetRefresh.generation.collectAsState()
            val loaded by produceState(initial, generation) {
                if (generation != startGeneration) value = load(context, id)
            }
            val size = LocalSize.current
            val bitmap = loaded.bitmaps[size] ?: loaded.bitmaps.nearest(size)
            Box(modifier = GlanceModifier.fillMaxSize().clickable(loaded.onClick)) {
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "PocketPass widget",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    private suspend fun load(context: Context, id: GlanceId): Loaded {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = runCatching { manager.getAppWidgetId(id) }.getOrNull()
        val now = System.currentTimeMillis()
        val receiverSize = appWidgetId?.let { providerSize(context, it) }
        val designId = appWidgetId?.let { widgetId ->
            WidgetBindingStore(WidgetSnapshotStore.directory(context)).resolve(widgetId, receiverSize, now)
        }
        val design = designId
            ?.let { wanted -> WidgetDesignStore.read(context).firstOrNull { it.id == wanted } }
            ?.let { found ->
                if (receiverSize != null && found.size != receiverSize) {
                    found.withSize(receiverSize, found.updatedAtEpochMillis)
                } else {
                    found
                }
            }
        val snapshot = WidgetSnapshotStore.read(context)
        val sizes = runCatching { manager.getAppWidgetSizes(id) }
            .getOrDefault(emptyList())
            .ifEmpty { listOf(fallbackSize(receiverSize)) }
        val painter = WidgetPainter(context)
        val density = context.resources.displayMetrics.density
        val bitmaps = sizes.associateWith { size ->
            val w = (size.width.value * density).roundToInt()
            val h = (size.height.value * density).roundToInt()
            when {
                design == null -> painter.paintNotice(w, h, "Tap to choose a design", "Widget Maker in Settings")
                snapshot == null || !snapshot.signedIn -> painter.paintNotice(w, h, "Open PocketPass", "Sign in to fill this widget")
                else -> painter.paint(design, snapshot, w, h, now)
            }
        }
        val onClick = if (design == null) {
            actionStartActivity(
                Intent(context, PocketPassLauncherActivity::class.java)
                    .putExtra(EXTRA_ASSIGN_APPWIDGET_ID, appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID),
            )
        } else {
            actionStartActivity<PocketPassLauncherActivity>()
        }
        return Loaded(bitmaps = bitmaps, onClick = onClick)
    }

    private fun providerSize(context: Context, appWidgetId: Int): WidgetSize? =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className }
            .getOrNull()
            ?.let(WidgetReceivers::sizeFor)

    private fun fallbackSize(size: WidgetSize?): DpSize = when (size) {
        WidgetSize.Mini -> DpSize(72.dp, 72.dp)
        WidgetSize.Tall -> DpSize(150.dp, 320.dp)
        WidgetSize.Wide -> DpSize(310.dp, 150.dp)
        WidgetSize.Small, null -> DpSize(150.dp, 150.dp)
    }

    private fun Map<DpSize, Bitmap>.nearest(size: DpSize): Bitmap? =
        entries.minByOrNull { (candidate, _) ->
            abs(candidate.width.value - size.width.value) + abs(candidate.height.value - size.height.value)
        }?.value

    companion object {
        const val EXTRA_ASSIGN_APPWIDGET_ID = "com.pocketpass.app.widget.ASSIGN_APPWIDGET_ID"
    }
}
