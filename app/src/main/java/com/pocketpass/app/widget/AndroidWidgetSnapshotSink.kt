package com.pocketpass.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

class AndroidWidgetSnapshotSink(context: Context) : WidgetSnapshotSink {
    private val appContext = context.applicationContext

    override suspend fun publish(snapshot: WidgetSnapshot, portraitSourcePath: String?) {
        try {
            WidgetSnapshotStore.write(appContext, snapshot, portraitSourcePath)
            StreetPassSummaryWidget().updateAll(appContext)
            ProfileCardWidget().updateAll(appContext)
        } catch (error: Exception) {
            Log.w(TAG, "Widget snapshot could not be published", error)
        }
    }

    private companion object {
        const val TAG = "PocketPassWidgets"
    }
}
