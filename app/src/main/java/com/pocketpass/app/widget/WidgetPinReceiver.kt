package com.pocketpass.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val designId = intent.getStringExtra(EXTRA_DESIGN_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || designId == null) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetBindingStore(WidgetSnapshotStore.directory(appContext)).bind(appWidgetId, designId)
                WidgetRefresh.bump()
                val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                CustomWidget().update(appContext, glanceId)
            } catch (error: Exception) {
                Log.w(TAG, "Pinned widget could not be bound", error)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_PINNED = "com.pocketpass.app.widget.PINNED"
        const val EXTRA_DESIGN_ID = "com.pocketpass.app.widget.DESIGN_ID"
        private const val TAG = "PocketPassWidgets"
    }
}
