package com.pocketpass.app.mii

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// How many trait options fit on one editor page; mirrored by MiiEditorStateHolder.
const val TRAITS_PER_PAGE = 12

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

fun MiiPersistedEditorSession.normalized(): MiiPersistedEditorSession {
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
