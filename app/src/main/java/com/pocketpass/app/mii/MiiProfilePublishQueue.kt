package com.pocketpass.app.mii

import android.content.Context
import android.util.AtomicFile
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface MiiProfilePublishQueue {
    suspend fun enqueue(request: MiiEditorSaveRequest): MiiProfilePublishQueueEntry
    suspend fun pending(accountKey: String): List<MiiProfilePublishQueueEntry>
    suspend fun remove(queueId: String)
    suspend fun clearAccount(accountKey: String)
}

class FileMiiProfilePublishQueue(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = QueueJson,
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
                val current = readUnsafe().toMutableList()
                val entry = MiiProfilePublishQueueEntry(
                    queueId = UUID.randomUUID().toString(),
                    accountKey = request.accountKey,
                    appearance = request.appearance.normalized(),
                    portraitFilePath = requireNotNull(
                        request.artifact.portraitFilePath,
                    ) { "A Mii publication requires its rendered portrait." },
                    canonicalMiicBase64 = request.artifact.encodedMii?.let {
                        Base64.getEncoder().encodeToString(it)
                    },
                    rendererVersion = request.artifact.rendererVersion,
                    revision = request.revision.coerceAtLeast(1L),
                    clientOperationId = ClientOperationId.new().value,
                    slot = request.slot.coerceToMiiSlot(),
                )
                current.removeAll { queued ->
                    queued.accountKey == entry.accountKey &&
                        queued.slot == entry.slot &&
                        queued.revision <= entry.revision
                }
                current += entry
                writeUnsafe(current)
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

@Serializable
data class MiiProfilePublishQueueEntry(
    val queueId: String,
    val accountKey: String,
    val appearance: MiiAppearance,
    val portraitFilePath: String,
    val canonicalMiicBase64: String? = null,
    val rendererVersion: String? = null,
    val revision: Long,
    val clientOperationId: String,
    val slot: Int = MII_FIRST_SLOT,
) {
    fun toCommand(): PublishMiiProfileCommand {
        val portrait = File(portraitFilePath)
        require(portrait.isFile) { "The queued Mii portrait is unavailable." }
        return PublishMiiProfileCommand(
            accountId = UserId(accountKey),
            appearance = appearance.normalized(),
            portraitPng = portrait.readBytes(),
            canonicalMiic = canonicalMiicBase64?.let {
                Base64.getDecoder().decode(it)
            },
            revision = revision,
            clientOperationId = ClientOperationId(clientOperationId),
            slot = slot.coerceToMiiSlot(),
        )
    }
}

class QueuedMiiEditorSaveCallback(
    private val queue: MiiProfilePublishQueue,
    private val publisher: MiiProfilePublisher,
    private val onPublished: suspend (MiiProfilePublication) -> Unit = {},
) : MiiEditorSaveCallback {
    override suspend fun onMiiSaved(
        request: MiiEditorSaveRequest,
    ): MiiEditorSaveResult {
        val entry = queue.enqueue(request)
        return when (val result = publish(entry)) {
            is RepositoryResult.Success -> {
                queue.remove(entry.queueId)
                notifyPublished(result.value)
                MiiEditorSaveResult.Completed
            }

            is RepositoryResult.Failure -> if (result.error.isTerminal()) {
                queue.remove(entry.queueId)
                MiiEditorSaveResult.Rejected(result.error.rejectionMessage())
            } else {
                MiiEditorSaveResult.QueuedForSync
            }
        }
    }

    suspend fun drain(accountKey: String): Int {
        var completed = 0
        for (entry in queue.pending(accountKey)) {
            when (val result = publish(entry)) {
                is RepositoryResult.Success -> {
                    queue.remove(entry.queueId)
                    notifyPublished(result.value)
                    completed += 1
                }

                is RepositoryResult.Failure -> {
                    if (result.error.isMissingLocalPortrait() || result.error.isTerminal()) {
                        queue.remove(entry.queueId)
                        continue
                    }
                    break
                }
            }
        }
        return completed
    }

    private fun com.pocketpass.app.domain.state.RepositoryFailure.isTerminal(): Boolean =
        !retryable && kind == com.pocketpass.app.domain.state.RepositoryFailureKind.Forbidden

    private fun com.pocketpass.app.domain.state.RepositoryFailure.rejectionMessage(): String =
        if (isMiiHatNotOwned()) MII_HAT_NOT_OWNED_MESSAGE else SYNC_FORBIDDEN_MESSAGE

    private suspend fun notifyPublished(publication: MiiProfilePublication) {
        runCatching { onPublished(publication) }
            .onFailure { error ->
                if (error is CancellationException) throw error
            }
    }

    private suspend fun publish(
        entry: MiiProfilePublishQueueEntry,
    ): RepositoryResult<MiiProfilePublication> = runCatching {
        entry.toCommand()
    }.fold(
        onSuccess = { publisher.publishMiiProfile(it) },
        onFailure = {
            RepositoryResult.Failure(
                com.pocketpass.app.domain.state.RepositoryFailure(
                    kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Validation,
                    message = MISSING_LOCAL_PORTRAIT_MESSAGE,
                    retryable = false,
                ),
            )
        },
    )

    private fun com.pocketpass.app.domain.state.RepositoryFailure
        .isMissingLocalPortrait(): Boolean =
        kind == com.pocketpass.app.domain.state.RepositoryFailureKind.Validation &&
            message == MISSING_LOCAL_PORTRAIT_MESSAGE

    private companion object {
        const val MISSING_LOCAL_PORTRAIT_MESSAGE =
            "The queued Mii portrait is no longer available."
        const val SYNC_FORBIDDEN_MESSAGE =
            "Your Mii was saved locally but this account cannot sync it."
    }
}

internal fun MiiStoredProfile.toSaveRequest(
    accountKey: String,
    slot: Int = MII_FIRST_SLOT,
): MiiEditorSaveRequest =
    MiiEditorSaveRequest(
        accountKey = accountKey,
        appearance = appearance.normalized(),
        artifact = MiiRendererSaveArtifact(
            encodedMii = encodedMiiBase64?.let {
                Base64.getDecoder().decode(it)
            },
            portraitFilePath = portraitFilePath,
            rendererVersion = rendererVersion,
        ),
        revision = revision,
        slot = slot,
    )

@Serializable
private data class MiiProfilePublishQueueDocument(
    val version: Int = 1,
    val entries: List<MiiProfilePublishQueueEntry> = emptyList(),
)

private val QueueJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
