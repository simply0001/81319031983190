package com.pocketpass.app.puzzle

import com.pocketpass.app.domain.repository.PuzzleArtworkStore
import com.pocketpass.app.mii.iosDocumentsPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class IosFilePuzzleArtworkStore(
    baseDirectory: String = iosDocumentsPath(),
) : PuzzleArtworkStore {
    private val fileSystem = FileSystem.SYSTEM
    private val directory: Path = baseDirectory.toPath() / "puzzles"

    override fun pathFor(key: String): String? {
        val file = directory / key
        return if (fileSystem.exists(file)) file.toString() else null
    }

    override suspend fun write(key: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            fileSystem.createDirectories(directory)
            val target = directory / key
            val temporary = directory / "$key.tmp"
            fileSystem.write(temporary) { write(bytes) }
            fileSystem.atomicMove(temporary, target)
            target.toString()
        }.getOrNull()
    }
}
