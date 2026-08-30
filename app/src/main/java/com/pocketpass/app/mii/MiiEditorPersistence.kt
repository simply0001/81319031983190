package com.pocketpass.app.mii

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileMiiEditorPersistence(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = DefaultMiiJson,
) : MiiEditorPersistence {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
    private val mutex = Mutex()

    override suspend fun load(accountKey: String): MiiPersistedEditorSession? =
        withContext(dispatcher) {
            mutex.withLock {
                val file = fileFor(accountKey)
                if (!file.isFile) return@withLock null
                try {
                    json.decodeFromString<MiiPersistedEditorSession>(file.readText())
                        .normalized()
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }

    override suspend fun save(
        accountKey: String,
        session: MiiPersistedEditorSession,
    ) = withContext(dispatcher) {
        mutex.withLock {
            check(directory.exists() || directory.mkdirs()) {
                "Unable to create private Mii storage."
            }
            val atomicFile = AtomicFile(fileFor(accountKey))
            val output = atomicFile.startWrite()
            try {
                output.write(
                    json.encodeToString(session.normalized()).toByteArray(Charsets.UTF_8),
                )
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }

    override suspend fun clear(accountKey: String) = withContext(dispatcher) {
        mutex.withLock {
            val file = fileFor(accountKey)
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("Unable to clear private Mii storage.")
            }
            File(file.path + ".bak").delete()
            Unit
        }
    }

    private fun fileFor(accountKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(directory, "$digest.json")
    }

    private companion object {
        const val DIRECTORY_NAME = "mii_editor"
    }
}

class InMemoryMiiEditorPersistence : MiiEditorPersistence {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, MiiPersistedEditorSession>()

    override suspend fun load(accountKey: String): MiiPersistedEditorSession? =
        mutex.withLock { sessions[accountKey] }

    override suspend fun save(
        accountKey: String,
        session: MiiPersistedEditorSession,
    ) {
        mutex.withLock {
            sessions[accountKey] = session.normalized()
        }
    }

    override suspend fun clear(accountKey: String) {
        mutex.withLock {
            sessions.remove(accountKey)
            Unit
        }
    }
}

private val DefaultMiiJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun MiiPersistedEditorSession.normalized(): MiiPersistedEditorSession {
    val legacy = savedProfile?.let { profile ->
        profile.copy(appearance = profile.appearance.normalized())
    }
    val slots = savedProfiles
        .ifEmpty { legacy?.let { mapOf(MII_FIRST_SLOT to it) }.orEmpty() }
        .mapNotNull { (slot, profile) ->
            slot.takeIf { it in MII_FIRST_SLOT..MII_SLOT_COUNT }
                ?.let { it to profile.copy(appearance = profile.appearance.normalized()) }
        }
        .toMap()
    return copy(
        schemaVersion = MiiPersistedEditorSession.CURRENT_SCHEMA_VERSION,
        savedProfile = null,
        savedProfiles = slots,
        activeSlot = activeSlot
            .takeIf { slots.containsKey(it) }
            ?: slots.keys.minOrNull()
            ?: MII_FIRST_SLOT,
        draft = draft?.normalized(),
        traitPageByCategory = traitPageByCategory.mapValues { (category, page) ->
            val largestTrait = MiiEditorCatalog.descriptor(category)
                .traits
                .maxOf(MiiTraitDescriptor::optionCount)
            val lastPage = (largestTrait - 1) / TRAITS_PER_PAGE
            page.coerceIn(0, lastPage)
        },
    )
}

private const val TRAITS_PER_PAGE = 12
