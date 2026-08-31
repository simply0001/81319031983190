package com.pocketpass.app.mii

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileMiiProfilePublishQueue(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = MiiPublishQueueJson,
) : MiiProfilePublishQueue {
    private val queueFile = AtomicFile(
        File(context.applicationContext.filesDir, QUEUE_FILE_PATH),
    )
    private val mutex = Mutex()

    override suspend fun enqueue(
        request: MiiEditorSaveRequest,
    ): MiiProfilePublishQueueEntry =
        withContext(dispatcher) {
            mutex.withLock {
                val entry = request.toQueueEntry(
                    queueId = UUID.randomUUID().toString(),
                )
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
        if (!queueFile.baseFile.isFile) return emptyList()
        return runCatching {
            queueFile.openRead().bufferedReader().use { reader ->
                json.decodeFromString<MiiProfilePublishQueueDocument>(
                    reader.readText(),
                ).entries
            }
        }.getOrDefault(emptyList())
    }

    private fun writeUnsafe(entries: List<MiiProfilePublishQueueEntry>) {
        queueFile.baseFile.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) {
                "Unable to create private Mii publication storage."
            }
        }
        val output = queueFile.startWrite()
        try {
            output.write(
                json.encodeToString(
                    MiiProfilePublishQueueDocument(entries = entries),
                ).toByteArray(Charsets.UTF_8),
            )
            queueFile.finishWrite(output)
        } catch (error: Throwable) {
            queueFile.failWrite(output)
            throw error
        }
    }

    private companion object {
        const val QUEUE_FILE_PATH = "mii/profile-publish-queue.json"
    }
}
