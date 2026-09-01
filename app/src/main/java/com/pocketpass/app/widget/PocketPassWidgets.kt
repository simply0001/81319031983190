package com.pocketpass.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.pocketpass.app.PocketPassLauncherActivity
import com.pocketpass.app.ui.widget.WidgetCardRenderer
import kotlin.math.roundToInt

/**
 * The two home-screen widgets. Each is a pre-rendered card: the app draws the
 * design (Rubik, pocket frames, gradients) into a bitmap per responsive size
 * and Glance only displays it and forwards the tap.
 */
abstract class PocketPassCardWidget(
    private val sizes: Set<DpSize>,
    private val description: String,
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(sizes)

    protected abstract suspend fun render(
        context: Context,
        inputs: WidgetCardRenderer.Inputs,
    ): Bitmap

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        val density = context.resources.displayMetrics.density
        val dark = resolveDark(context, snapshot?.themeMode)
        val portrait = snapshot?.portraitFileName?.let { WidgetSnapshotStore.portraitFile(context) }
        val now = System.currentTimeMillis()
        val bitmaps = sizes.associateWith { size ->
            render(
                context,
                WidgetCardRenderer.Inputs(
                    snapshot = snapshot,
                    portraitFile = portrait,
                    widthPx = (size.width.value * density).roundToInt().coerceIn(1, MAX_EDGE_PX),
                    heightPx = (size.height.value * density).roundToInt().coerceIn(1, MAX_EDGE_PX),
                    density = density,
                    dark = dark,
                    nowEpochMillis = now,
                ),
            )
        }
        provideContent {
            val size = LocalSize.current
            val bitmap = bitmaps[size] ?: bitmaps.values.first()
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<PocketPassLauncherActivity>()),
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = description,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }

    private fun resolveDark(context: Context, themeMode: String?): Boolean = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private companion object {
        // Keeps the RemoteViews bitmap budget comfortable even on 3.5x screens.
        const val MAX_EDGE_PX = 1600
    }
}

class StreetPassSummaryWidget : PocketPassCardWidget(
    sizes = setOf(SMALL, MEDIUM),
    description = "PocketPass street-pass summary",
) {
    override suspend fun render(context: Context, inputs: WidgetCardRenderer.Inputs): Bitmap =
        WidgetCardRenderer.renderSummary(context, inputs)

    companion object {
        val SMALL = DpSize(150.dp, 110.dp)
        val MEDIUM = DpSize(310.dp, 110.dp)
    }
}

class ProfileCardWidget : PocketPassCardWidget(
    sizes = setOf(MEDIUM, LARGE),
    description = "PocketPass profile card",
) {
    override suspend fun render(context: Context, inputs: WidgetCardRenderer.Inputs): Bitmap =
        WidgetCardRenderer.renderProfile(context, inputs)

    companion object {
        val MEDIUM = DpSize(310.dp, 120.dp)
        val LARGE = DpSize(310.dp, 220.dp)
    }
}

class StreetPassSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreetPassSummaryWidget()
}

class ProfileCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProfileCardWidget()
}
