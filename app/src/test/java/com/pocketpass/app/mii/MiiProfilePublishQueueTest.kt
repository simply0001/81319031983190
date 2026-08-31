package com.pocketpass.app.mii

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import java.nio.file.Files
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiiProfilePublishQueueTest {
    @Test
    fun successfulPublicationIsNotRejectedWhenRefreshCallbackFails() = runTest {
        val queue = FakeMiiProfilePublishQueue()
        var publishCount = 0
        val callback = QueuedMiiEditorSaveCallback(
            queue = queue,
            readPortrait = ::readPortraitFile,
            publisher = MiiProfilePublisher { command ->
                publishCount += 1
                RepositoryResult.Success(publication(command))
            },
            onPublished = { error("simulated profile refresh failure") },
        )

        val result = callback.onMiiSaved(saveRequest(revision = 4))

        assertEquals(MiiEditorSaveResult.Completed, result)
        assertEquals(1, publishCount)
        assertTrue(queue.pending(ACCOUNT).isEmpty())
    }

    @Test
    fun retryableFailureLeavesQueueRowForNextDrain() = runTest {
        val queue = FakeMiiProfilePublishQueue()
        queue.enqueue(saveRequest(revision = 5))
        var publishCount = 0
        val callback = QueuedMiiEditorSaveCallback(
            queue = queue,
            readPortrait = ::readPortraitFile,
            publisher = MiiProfilePublisher {
                publishCount += 1
                RepositoryResult.Failure(
                    RepositoryFailure(RepositoryFailureKind.Offline),
                )
            },
        )

        assertEquals(0, callback.drain(ACCOUNT))
        assertEquals(1, publishCount)
        assertEquals(1, queue.pending(ACCOUNT).size)
    }

    @Test
    fun backendValidationFailureIsRetainedForServerRepair() = runTest {
        val queue = FakeMiiProfilePublishQueue()
        queue.enqueue(saveRequest(revision = 6))
        val callback = QueuedMiiEditorSaveCallback(
            queue = queue,
            readPortrait = ::readPortraitFile,
            publisher = MiiProfilePublisher {
                RepositoryResult.Failure(
                    RepositoryFailure(
                        kind = RepositoryFailureKind.Validation,
                        message = "Server rejected a valid publication",
                        retryable = false,
                    ),
                )
            },
        )

        assertEquals(0, callback.drain(ACCOUNT))
        assertEquals(1, queue.pending(ACCOUNT).size)
    }

    @Test
    fun forbiddenPublicationIsDroppedAndRejected() = runTest {
        val queue = FakeMiiProfilePublishQueue(listOf(queueEntry(revision = 7)))
        var publishCount = 0
        val callback = QueuedMiiEditorSaveCallback(
            queue = queue,
            readPortrait = ::readPortraitFile,
            publisher = MiiProfilePublisher {
                publishCount += 1
                RepositoryResult.Failure(
                    RepositoryFailure(
                        kind = RepositoryFailureKind.Forbidden,
                        message = MII_HAT_NOT_OWNED_MESSAGE,
                        retryable = false,
                    ),
                )
            },
        )

        assertEquals(0, callback.drain(ACCOUNT))
        assertTrue(queue.pending(ACCOUNT).isEmpty())

        val result = callback.onMiiSaved(saveRequest(revision = 8))

        assertEquals(MiiEditorSaveResult.Rejected(MII_HAT_NOT_OWNED_MESSAGE), result)
        assertEquals(2, publishCount)
        assertTrue(queue.pending(ACCOUNT).isEmpty())
    }

    @Test
    fun missingPortraitIsDroppedAndDoesNotBlockNewerValidRow() = runTest {
        val missing = MiiProfilePublishQueueEntry(
            queueId = "missing",
            accountKey = ACCOUNT,
            appearance = MiiAppearance(),
            portraitFilePath = "definitely-not-a-real-mii-portrait.png",
            revision = 1,
            clientOperationId = "operation-missing",
        )
        val valid = queueEntry(revision = 2)
        val queue = FakeMiiProfilePublishQueue(listOf(missing, valid))
        val publishedRevisions = mutableListOf<Long>()
        val callback = QueuedMiiEditorSaveCallback(
            queue = queue,
            readPortrait = ::readPortraitFile,
            publisher = MiiProfilePublisher { command ->
                publishedRevisions += command.revision
                RepositoryResult.Success(publication(command))
            },
        )

        assertEquals(1, callback.drain(ACCOUNT))
        assertEquals(listOf(2L), publishedRevisions)
        assertTrue(queue.pending(ACCOUNT).isEmpty())
    }

    @Test
    fun queuedEntryRestoresCanonicalBytesAndNormalizesAppearance() {
        val entry = queueEntry(
            revision = 3,
            appearance = MiiAppearance(glassesType = 500),
            canonical = byteArrayOf(1, 2, 3, 4),
        )

        val command = entry.toCommand(PNG)

        assertEquals(19, command.appearance.glassesType)
        assertTrue(command.canonicalMiic!!.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(3, command.revision)
        assertEquals("operation-3", command.clientOperationId.value)
    }

    @Test
    fun storedProfileCanRestoreADeletedPublicationQueueEntry() {
        val portrait = portraitFile().toString()
        val stored = MiiStoredProfile(
            appearance = MiiAppearance(hairType = 42),
            encodedMiiBase64 = java.util.Base64.getEncoder()
                .encodeToString(byteArrayOf(5, 6, 7)),
            portraitFilePath = portrait,
            rendererVersion = "renderer-test",
            revision = 8,
            savedAtEpochMillis = 1L,
        )

        val request = stored.toSaveRequest(ACCOUNT)

        assertEquals(ACCOUNT, request.accountKey)
        assertEquals(42, request.appearance.hairType)
        assertEquals(portrait, request.artifact.portraitFilePath)
        assertTrue(request.artifact.encodedMii!!.contentEquals(byteArrayOf(5, 6, 7)))
        assertEquals(8, request.revision)
    }

    private fun saveRequest(revision: Long): MiiEditorSaveRequest =
        MiiEditorSaveRequest(
            accountKey = ACCOUNT,
            appearance = MiiAppearance(hairType = revision.toInt()),
            artifact = MiiRendererSaveArtifact(
                encodedMii = byteArrayOf(1, 2, 3),
                portraitFilePath = portraitFile().toString(),
                rendererVersion = "renderer-test",
            ),
            revision = revision,
        )

    private fun queueEntry(
        revision: Long,
        appearance: MiiAppearance = MiiAppearance(),
        canonical: ByteArray? = null,
    ): MiiProfilePublishQueueEntry =
        MiiProfilePublishQueueEntry(
            queueId = "queue-$revision",
            accountKey = ACCOUNT,
            appearance = appearance,
            portraitFilePath = portraitFile().toString(),
            canonicalMiicBase64 = canonical?.let {
                java.util.Base64.getEncoder().encodeToString(it)
            },
            rendererVersion = "renderer-test",
            revision = revision,
            clientOperationId = "operation-$revision",
        )

    private fun readPortraitFile(path: String): ByteArray? =
        java.io.File(path).takeIf { it.isFile }?.readBytes()

    private fun portraitFile() = Files.createTempFile("pocketpass-mii-", ".png").also {
        Files.write(it, PNG)
        it.toFile().deleteOnExit()
    }

    private fun publication(command: PublishMiiProfileCommand) =
        MiiProfilePublication(
            accountId = command.accountId,
            appearanceSchemaVersion = command.appearance.schemaVersion,
            revision = command.revision,
            avatar = AvatarReference.Remote("https://example.invalid/mii.png"),
            publishedAt = Instant.fromEpochSeconds(0),
        )

    private class FakeMiiProfilePublishQueue(
        initial: List<MiiProfilePublishQueueEntry> = emptyList(),
    ) : MiiProfilePublishQueue {
        private val entries = initial.toMutableList()

        override suspend fun enqueue(
            request: MiiEditorSaveRequest,
        ): MiiProfilePublishQueueEntry {
            val entry = MiiProfilePublishQueueEntry(
                queueId = "queue-${request.revision}",
                accountKey = request.accountKey,
                appearance = request.appearance.normalized(),
                portraitFilePath = requireNotNull(request.artifact.portraitFilePath),
                canonicalMiicBase64 = request.artifact.encodedMii?.let {
                    java.util.Base64.getEncoder().encodeToString(it)
                },
                rendererVersion = request.artifact.rendererVersion,
                revision = request.revision.coerceAtLeast(1),
                clientOperationId = "operation-${request.revision}",
            )
            entries.removeAll {
                it.accountKey == entry.accountKey && it.revision <= entry.revision
            }
            entries += entry
            return entry
        }

        override suspend fun pending(
            accountKey: String,
        ): List<MiiProfilePublishQueueEntry> =
            entries.filter { it.accountKey == accountKey }.sortedBy { it.revision }

        override suspend fun remove(queueId: String) {
            entries.removeAll { it.queueId == queueId }
        }

        override suspend fun clearAccount(accountKey: String) {
            entries.removeAll { it.accountKey == accountKey }
        }
    }

    private companion object {
        const val ACCOUNT = "90000000-0000-4000-8000-000000000001"
        val PNG = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
        )
    }
}
