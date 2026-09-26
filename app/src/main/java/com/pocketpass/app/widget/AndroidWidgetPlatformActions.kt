package com.pocketpass.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.pocketpass.app.state.WidgetPlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidWidgetPlatformActions(context: Context) : WidgetPlatformActions {
    private val appContext = context.applicationContext

    private val bindings: WidgetBindingStore
        get() = WidgetBindingStore(WidgetSnapshotStore.directory(appContext))

    override val pinSupported: Boolean
        get() = runCatching { AppWidgetManager.getInstance(appContext).isRequestPinAppWidgetSupported }
            .getOrDefault(false)

    override suspend fun pin(design: WidgetDesign): Boolean = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(appContext)
        if (!manager.isRequestPinAppWidgetSupported) return@withContext false
        bindings.markPending(design.id, design.size, System.currentTimeMillis())
        val callback = PendingIntent.getBroadcast(
            appContext,
            design.id.hashCode(),
            Intent(appContext, WidgetPinReceiver::class.java)
                .setAction(WidgetPinReceiver.ACTION_PINNED)
                .putExtra(WidgetPinReceiver.EXTRA_DESIGN_ID, design.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching {
            manager.requestPinAppWidget(
                ComponentName(appContext, WidgetReceivers.receiverFor(design.size)),
                null,
                callback,
            )
        }.onFailure { Log.w(TAG, "Pin request failed", it) }.getOrDefault(false)
    }

    override suspend fun assign(appWidgetId: Int, designId: String) = withContext(Dispatchers.IO) {
        bindings.bind(appWidgetId, designId)
        WidgetRefresh.bump()
        runCatching {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
            CustomWidget().update(appContext, glanceId)
        }.onFailure { Log.w(TAG, "Assigned widget could not be redrawn", it) }
        Unit
    }

    override suspend fun refreshAll() {
        WidgetRefresh.bump()
        runCatching { CustomWidget().updateAll(appContext) }
            .onFailure { Log.w(TAG, "Widgets could not be refreshed", it) }
    }

    private companion object {
        const val TAG = "PocketPassWidgets"
    }
}
