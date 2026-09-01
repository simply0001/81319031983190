package com.pocketpass.app.widget

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the widgets read their data from: a JSON snapshot plus a copy of the
 * active Mii portrait under the app's private files directory. The widget
 * receivers only ever read this, so a periodic system update can re-render
 * without touching the app container.
 */
object WidgetSnapshotStore {
    private fun directory(context: Context): File = File(context.filesDir, "widgets")

    fun snapshotFile(context: Context): File = File(directory(context), WidgetSnapshot.SNAPSHOT_FILE_NAME)

    fun portraitFile(context: Context): File = File(directory(context), WidgetSnapshot.PORTRAIT_FILE_NAME)

    suspend fun read(context: Context): WidgetSnapshot? = withContext(Dispatchers.IO) {
        val file = snapshotFile(context)
        if (!file.isFile) return@withContext null
        runCatching { WidgetSnapshot.decode(file.readText()) }.getOrNull()
    }

    suspend fun write(context: Context, snapshot: WidgetSnapshot, portraitSourcePath: String?) =
        withContext(Dispatchers.IO) {
            val directory = directory(context)
            directory.mkdirs()
            val portrait = portraitFile(context)
            val source = portraitSourcePath?.let(::File)?.takeIf { it.isFile }
            if (source != null) {
                if (!portrait.isFile || portrait.lastModified() < source.lastModified()) {
                    val temporary = File(directory, "${portrait.name}.tmp")
                    source.copyTo(temporary, overwrite = true)
                    temporary.renameTo(portrait)
                }
            } else {
                portrait.delete()
            }
            val temporary = File(directory, "${WidgetSnapshot.SNAPSHOT_FILE_NAME}.tmp")
            temporary.writeText(snapshot.encode())
            temporary.renameTo(snapshotFile(context))
        }
}
