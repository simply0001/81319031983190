package com.pocketpass.app.widget

fun interface WidgetSnapshotSink {
    suspend fun publish(snapshot: WidgetSnapshot, portraitSourcePath: String?)
}
