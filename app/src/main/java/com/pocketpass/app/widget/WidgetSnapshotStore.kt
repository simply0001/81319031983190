package com.pocketpass.app.widget

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetSnapshotStore {
    internal fun directory(context: Context): File = File(context.filesDir, "widgets")

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
                val temporary = File(directory, "${portrait.name}.tmp")
                source.copyTo(temporary, overwrite = true)
                temporary.renameTo(portrait)
            } else {
                portrait.delete()
            }
            val temporary = File(directory, "${WidgetSnapshot.SNAPSHOT_FILE_NAME}.tmp")
            temporary.writeText(snapshot.encode())
            temporary.renameTo(snapshotFile(context))
        }
}
