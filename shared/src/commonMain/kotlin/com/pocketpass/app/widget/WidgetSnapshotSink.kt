package com.pocketpass.app.widget

/**
 * Platform side of widget publishing: persist the snapshot (and a copy of the
 * portrait file, when [portraitSourcePath] is given) where the widget process
 * can read it, then ask the OS to refresh the widgets.
 */
fun interface WidgetSnapshotSink {
    suspend fun publish(snapshot: WidgetSnapshot, portraitSourcePath: String?)
}
