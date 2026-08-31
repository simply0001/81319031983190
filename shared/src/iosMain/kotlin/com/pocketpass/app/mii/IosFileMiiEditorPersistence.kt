package com.pocketpass.app.mii

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val DefaultMiiJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun iosDocumentsPath(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
        ?: ""

/**
 * iOS counterpart of the Android FileMiiEditorPersistence: one JSON file per account
 * (named by the SHA-256 of the account key) under Documents/mii_editor.
 */
class IosFileMiiEditorPersistence(
    baseDirectory: String = iosDocumentsPath(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = DefaultMiiJson,
) : MiiEditorPersistence {
    private val fileSystem = FileSystem.SYSTEM
    private val directory: Path = baseDirectory.toPath() / "mii_editor"
    private val mutex = Mutex()

    override suspend fun load(accountKey: String): MiiPersistedEditorSession? =
        withContext(dispatcher) {
            mutex.withLock {
                val file = fileFor(accountKey)
                if (!fileSystem.exists(file)) return@withLock null
                try {
                    val text = fileSystem.read(file) { readUtf8() }
                    json.decodeFromString<MiiPersistedEditorSession>(text).normalized()
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: okio.IOException) {
                    null
                }
            }
        }

    override suspend fun save(
        accountKey: String,
        session: MiiPersistedEditorSession,
    ) = withContext(dispatcher) {
        mutex.withLock {
            fileSystem.createDirectories(directory)
            val file = fileFor(accountKey)
            val temporary = directory / ".${file.name}.tmp"
            try {
                fileSystem.write(temporary) {
                    writeUtf8(json.encodeToString(session.normalized()))
                }
                fileSystem.atomicMove(temporary, file)
            } catch (error: Throwable) {
                runCatching { fileSystem.delete(temporary) }
                throw error
            }
        }
    }

    override suspend fun clear(accountKey: String) = withContext(dispatcher) {
        mutex.withLock {
            fileSystem.delete(fileFor(accountKey), mustExist = false)
        }
    }

    private fun fileFor(accountKey: String): Path =
        directory / "${accountKey.encodeUtf8().sha256().hex()}.json"
}

/** Where freshly rendered Mii portraits are written on iOS. */
fun iosPortraitsDirectory(): String = "${iosDocumentsPath()}/mii/portraits"

fun iosDeletePortraitFile(path: String) {
    runCatching { FileSystem.SYSTEM.delete(path.toPath(), mustExist = false) }
}
