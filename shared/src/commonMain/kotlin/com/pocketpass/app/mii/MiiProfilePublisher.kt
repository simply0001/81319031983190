package com.pocketpass.app.mii

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant

const val MII_HAT_NOT_OWNED_HINT = "HAT_NOT_OWNED"
const val MII_HAT_NOT_OWNED_MESSAGE = "That hat is no longer unlocked. Save your Mii again."

class MiiHatNotOwnedException : RuntimeException(MII_HAT_NOT_OWNED_MESSAGE)

fun RepositoryFailure.isMiiHatNotOwned(): Boolean =
    kind == RepositoryFailureKind.Forbidden && message == MII_HAT_NOT_OWNED_MESSAGE

data class PublishMiiProfileCommand(
    val accountId: UserId,
    val appearance: MiiAppearance,
    val portraitPng: ByteArray,
    val canonicalMiic: ByteArray? = null,
    val revision: Long,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val slot: Int = MII_FIRST_SLOT,
) {
    init {
        require(appearance == appearance.normalized()) {
            "Mii appearance must use the current normalized schema"
        }
        require(revision > 0L) { "Mii revision must be positive" }
        require(slot in MII_FIRST_SLOT..MII_SLOT_COUNT) {
            "Mii slot must be between $MII_FIRST_SLOT and $MII_SLOT_COUNT"
        }
        require(portraitPng.size in PNG_SIGNATURE.size..MAX_PORTRAIT_BYTES) {
            "Mii portrait must be a non-empty PNG no larger than 5 MiB"
        }
        require(portraitPng.startsWith(PNG_SIGNATURE)) {
            "Mii portrait does not have a PNG signature"
        }
        require(canonicalMiic == null || canonicalMiic.size in 1..MAX_MIIC_BYTES) {
            "Canonical Mii data must be no larger than 4 KiB"
        }
    }

    private companion object {
        const val MAX_PORTRAIT_BYTES = 5 * 1024 * 1024
        const val MAX_MIIC_BYTES = 4 * 1024
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}

data class MiiProfilePublication(
    val accountId: UserId,
    val appearanceSchemaVersion: Int,
    val revision: Long,
    val avatar: AvatarReference,
    val publishedAt: Instant,
)

fun interface MiiProfilePublisher {
    suspend fun publishMiiProfile(
        command: PublishMiiProfileCommand,
    ): RepositoryResult<MiiProfilePublication>
}

data class MiiProfileSnapshot(
    val slot: Int,
    val appearance: MiiAppearance,
    val revision: Long,
    val isActive: Boolean,
    val portraitPng: ByteArray?,
    val savedAt: Instant,
)

fun interface MiiProfileFetcher {
    suspend fun fetchMiiProfiles(
        accountId: UserId,
    ): RepositoryResult<List<MiiProfileSnapshot>>
}

interface MiiActiveSlotPublisher {
    suspend fun setActiveMiiSlot(
        accountId: UserId,
        slot: Int,
        clientOperationId: ClientOperationId = ClientOperationId.new(),
    ): RepositoryResult<Unit>
}

fun interface MiiSlotDeleter {
    suspend fun deleteMiiSlot(
        accountId: UserId,
        slot: Int,
    ): RepositoryResult<Unit>
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }
