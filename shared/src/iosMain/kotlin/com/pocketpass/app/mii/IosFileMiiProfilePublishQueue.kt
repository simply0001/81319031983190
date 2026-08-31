package com.pocketpass.app.mii

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * iOS counterpart of the Android FileMiiProfilePublishQueue: one JSON document
 * under Documents/mii, written through a temp file and an atomic move.
 */
class IosFileMiiProfilePublishQueue(
    baseDirectory: String = iosDocumentsPath(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = MiiPublishQueueJson,
) : MiiProfilePublishQueue {
    private val fileSystem = FileSystem.SYSTEM
    private val queueFile: Path = baseDirectory.toPath() / "mii" / "profile-publish-queue.json"
    private val mutex = Mutex()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enqueue(
        request: MiiEditorSaveRequest,
    ): MiiProfilePublishQueueEntry =
        withContext(dispatcher) {
            mutex.withLock {
                val entry = request.toQueueEntry(queueId = Uuid.random().toString())
                writeUnsafe(readUnsafe().withoutSuperseded(entry) + entry)
                entry
            }
        }

    override suspend fun pending(
        accountKey: String,
    ): List<MiiProfilePublishQueueEntry> =
        withContext(dispatcher) {
            mutex.withLock {
                readUnsafe()
                    .filter { it.accountKey == accountKey }
                    .sortedBy(MiiProfilePublishQueueEntry::revision)
            }
        }

    override suspend fun remove(queueId: String) = withContext(dispatcher) {
        mutex.withLock {
            writeUnsafe(readUnsafe().filterNot { it.queueId == queueId })
        }
    }

    override suspend fun clearAccount(accountKey: String) = withContext(dispatcher) {
        mutex.withLock {
            writeUnsafe(readUnsafe().filterNot { it.accountKey == accountKey })
        }
    }

    private fun readUnsafe(): List<MiiProfilePublishQueueEntry> {
        if (!fileSystem.exists(queueFile)) return emptyList()
        return runCatching {
            val text = fileSystem.read(queueFile) { readUtf8() }
            json.decodeFromString<MiiProfilePublishQueueDocument>(text).entries
        }.getOrDefault(emptyList())
    }

    private fun writeUnsafe(entries: List<MiiProfilePublishQueueEntry>) {
        queueFile.parent?.let(fileSystem::createDirectories)
        val temporary = queueFile.parent?.div(".${queueFile.name}.tmp") ?: return
        try {
            fileSystem.write(temporary) {
                writeUtf8(json.encodeToString(MiiProfilePublishQueueDocument(entries = entries)))
            }
            fileSystem.atomicMove(temporary, queueFile)
        } catch (error: Throwable) {
            runCatching { fileSystem.delete(temporary) }
            throw error
        }
    }
}

/** Reads a rendered portrait back for publication; null when it is gone. */
suspend fun iosReadPortraitFile(path: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
        }.getOrNull()
    }

/** Writes a portrait restored from the server; returns its path, or null on failure. */
@OptIn(ExperimentalUuidApi::class)
suspend fun iosWriteRestoredPortrait(bytes: ByteArray): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = iosPortraitsDirectory().toPath()
            FileSystem.SYSTEM.createDirectories(directory)
            val file = directory / "portrait-${Uuid.random()}.png"
            FileSystem.SYSTEM.write(file) { write(bytes) }
            file.toString()
        }.getOrNull()
    }
