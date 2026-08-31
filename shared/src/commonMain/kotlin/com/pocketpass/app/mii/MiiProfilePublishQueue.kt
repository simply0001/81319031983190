package com.pocketpass.app.mii

import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface MiiProfilePublishQueue {
    suspend fun enqueue(request: MiiEditorSaveRequest): MiiProfilePublishQueueEntry
    suspend fun pending(accountKey: String): List<MiiProfilePublishQueueEntry>
    suspend fun remove(queueId: String)
    suspend fun clearAccount(accountKey: String)
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
    fun toCommand(portraitPng: ByteArray): PublishMiiProfileCommand =
        PublishMiiProfileCommand(
            accountId = UserId(accountKey),
            appearance = appearance.normalized(),
            portraitPng = portraitPng,
            canonicalMiic = canonicalMiicBase64?.let(Base64.Default::decode),
            revision = revision,
            clientOperationId = ClientOperationId(clientOperationId),
            slot = slot.coerceToMiiSlot(),
        )
}

fun MiiEditorSaveRequest.toQueueEntry(queueId: String): MiiProfilePublishQueueEntry =
    MiiProfilePublishQueueEntry(
        queueId = queueId,
        accountKey = accountKey,
        appearance = appearance.normalized(),
        portraitFilePath = requireNotNull(artifact.portraitFilePath) {
            "A Mii publication requires its rendered portrait."
        },
        canonicalMiicBase64 = artifact.encodedMii?.let(Base64.Default::encode),
        rendererVersion = artifact.rendererVersion,
        revision = revision.coerceAtLeast(1L),
        clientOperationId = ClientOperationId.new().value,
        slot = slot.coerceToMiiSlot(),
    )

// Later revisions of the same slot supersede queued ones.
fun List<MiiProfilePublishQueueEntry>.withoutSuperseded(
    entry: MiiProfilePublishQueueEntry,
): List<MiiProfilePublishQueueEntry> = filterNot { queued ->
    queued.accountKey == entry.accountKey &&
        queued.slot == entry.slot &&
        queued.revision <= entry.revision
}

class QueuedMiiEditorSaveCallback(
    private val queue: MiiProfilePublishQueue,
    private val publisher: MiiProfilePublisher,
    private val readPortrait: suspend (String) -> ByteArray?,
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

    private fun RepositoryFailure.isTerminal(): Boolean =
        !retryable && kind == RepositoryFailureKind.Forbidden

    private fun RepositoryFailure.rejectionMessage(): String =
        if (isMiiHatNotOwned()) MII_HAT_NOT_OWNED_MESSAGE else SYNC_FORBIDDEN_MESSAGE

    private suspend fun notifyPublished(publication: MiiProfilePublication) {
        runCatching { onPublished(publication) }
            .onFailure { error ->
                if (error is CancellationException) throw error
            }
    }

    private suspend fun publish(
        entry: MiiProfilePublishQueueEntry,
    ): RepositoryResult<MiiProfilePublication> {
        val command = runCatching {
            val portrait = readPortrait(entry.portraitFilePath)
                ?: error("The queued Mii portrait file is gone.")
            entry.toCommand(portrait)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Validation,
                    message = MISSING_LOCAL_PORTRAIT_MESSAGE,
                    retryable = false,
                ),
            )
        }
        return publisher.publishMiiProfile(command)
    }

    private fun RepositoryFailure.isMissingLocalPortrait(): Boolean =
        kind == RepositoryFailureKind.Validation &&
            message == MISSING_LOCAL_PORTRAIT_MESSAGE

    private companion object {
        const val MISSING_LOCAL_PORTRAIT_MESSAGE =
            "The queued Mii portrait is no longer available."
        const val SYNC_FORBIDDEN_MESSAGE =
            "Your Mii was saved locally but this account cannot sync it."
    }
}

fun MiiStoredProfile.toSaveRequest(
    accountKey: String,
    slot: Int = MII_FIRST_SLOT,
): MiiEditorSaveRequest =
    MiiEditorSaveRequest(
        accountKey = accountKey,
        appearance = appearance.normalized(),
        artifact = MiiRendererSaveArtifact(
            encodedMii = encodedMiiBase64?.let(Base64.Default::decode),
            portraitFilePath = portraitFilePath,
            rendererVersion = rendererVersion,
        ),
        revision = revision,
        slot = slot,
    )

@Serializable
internal data class MiiProfilePublishQueueDocument(
    val version: Int = 1,
    val entries: List<MiiProfilePublishQueueEntry> = emptyList(),
)

internal val MiiPublishQueueJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
