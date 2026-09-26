package com.pocketpass.app.puzzle

import android.content.Context
import com.pocketpass.app.domain.repository.PuzzleArtworkStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilePuzzleArtworkStore(context: Context) : PuzzleArtworkStore {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    override fun pathFor(key: String): String? =
        File(directory, key).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    override suspend fun write(key: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            val target = File(directory, key)
            val temporary = File(directory, "$key.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                target.delete()
                check(temporary.renameTo(target)) { "Could not store puzzle artwork" }
            }
            target.absolutePath
        }.getOrNull()
    }

    private companion object {
        const val DIRECTORY_NAME = "puzzles"
    }
}
