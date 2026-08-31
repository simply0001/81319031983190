package com.pocketpass.app.data.repository

import com.pocketpass.app.data.local.dao.NearbyEncounterDao
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.domain.state.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomEncounterRepository(
    private val dao: NearbyEncounterDao,
    private val remote: EncounterRemoteDataSource,
) : EncounterRepository {
    override fun observeRecent(accountId: UserId): Flow<List<NearbyEncounter>> =
        dao.observeRecent(accountId.value)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        when (val result = remote.fetchEncounters(accountId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> {
                result.value.forEach { encounter ->
                    dao.upsertEncounter(encounter.toEntity())
                }
                RepositoryResult.Success(Unit)
            }
        }

    suspend fun reconcile(encounter: NearbyEncounter) {
        dao.upsertEncounter(encounter.toEntity())
    }
}
