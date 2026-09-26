package com.pocketpass.app.data.repository

import com.pocketpass.app.data.local.dao.AchievementDao
import com.pocketpass.app.data.local.dao.BingoDao
import com.pocketpass.app.data.local.dao.LeaderboardDao
import com.pocketpass.app.data.local.dao.ShopDao
import com.pocketpass.app.data.local.dao.WorldTourDao
import com.pocketpass.app.data.local.dao.PassingStatsDao
import com.pocketpass.app.data.local.entity.AchievementStateEntity
import com.pocketpass.app.data.local.entity.BingoCellEntity
import com.pocketpass.app.data.local.entity.LeaderboardEntryEntity
import com.pocketpass.app.data.local.entity.OwnedShopItemEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.SupporterStatusEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.data.local.entity.WorldTourRegionEntity
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.data.repository.remote.AchievementsRemoteDataSource
import com.pocketpass.app.data.repository.remote.BingoRemoteDataSource
import com.pocketpass.app.data.repository.remote.LeaderboardRemoteDataSource
import com.pocketpass.app.data.repository.remote.ShopRemoteDataSource
import com.pocketpass.app.data.repository.remote.WorldTourRemoteDataSource
import com.pocketpass.app.data.repository.remote.PassingStatsRemoteDataSource
import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.PassingStats
import com.pocketpass.app.domain.repository.AchievementsRepository
import com.pocketpass.app.domain.repository.BingoRepository
import com.pocketpass.app.domain.repository.LeaderboardRepository
import com.pocketpass.app.domain.repository.ShopRepository
import com.pocketpass.app.domain.repository.WorldTourRepository
import com.pocketpass.app.steps.localUtcOffsetMinutes
import com.pocketpass.app.domain.repository.PassingStatsRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import com.pocketpass.app.data.local.dao.PuzzleDao
import com.pocketpass.app.data.local.entity.PuzzlePieceEntity
import com.pocketpass.app.data.repository.remote.PuzzleRemoteDataSource
import com.pocketpass.app.domain.model.BuyPuzzlePieceCommand
import com.pocketpass.app.domain.model.PuzzleArtwork
import com.pocketpass.app.domain.model.PuzzleCollection
import com.pocketpass.app.domain.model.PuzzlePiecePurchaseOutcome
import com.pocketpass.app.domain.model.PuzzleProgress
import com.pocketpass.app.domain.repository.NoPuzzleArtworkStore
import com.pocketpass.app.domain.repository.PuzzleArtworkStore
import com.pocketpass.app.domain.repository.PuzzleRepository

class RoomShopRepository(
    private val shopDao: ShopDao,
    private val remote: ShopRemoteDataSource,
    private val mutationStore: ProductionMutationStore,
    private val pendingOperationScheduler: PendingOperationScheduler =
        PendingOperationScheduler.None,
    private val now: () -> Instant = Clock.System::now,
) : ShopRepository {
    override fun observeCatalog(): Flow<List<ShopCategory>> = combine(
        shopDao.observeCategories(),
        shopDao.observeItems(),
    ) { categories, items ->
        val itemsByCategory = items.groupBy(ShopItemEntity::categoryId)
        categories.map { category ->
            category.toDomain(
                items = itemsByCategory[category.categoryId]
                    .orEmpty()
                    .map(ShopItemEntity::toDomain),
            )
        }
    }.distinctUntilChanged()

    override fun observeTokenBalance(accountId: UserId): Flow<Int?> =
        shopDao.observeAvailableBalance(accountId.value).distinctUntilChanged()

    override fun observeOwnedItems(accountId: UserId): Flow<List<OwnedShopItem>> =
        shopDao.observeOwnedItems(accountId.value)
            .map { items -> items.map(OwnedShopItemEntity::toDomain) }
            .distinctUntilChanged()

    override fun observeOwnedHatTypes(accountId: UserId): Flow<Set<Int>> = combine(
        shopDao.observeConfirmedHatTypes(accountId.value),
        shopDao.observeItems(),
        observeSupporterActive(accountId),
    ) { confirmed, items, supporter ->
        val unlocked = items.filter { supporter || it.priceTokens == 0 }.mapNotNull(ShopItemEntity::miiHatType)
        (confirmed + unlocked).toSet()
    }.distinctUntilChanged()

    override fun observeSupporterUntil(accountId: UserId): Flow<Instant?> =
        shopDao.observeSupporterUntil(accountId.value)
            .map { millis -> millis?.let(Instant::fromEpochMilliseconds) }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSupporterActive(accountId: UserId): Flow<Boolean> =
        observeSupporterUntil(accountId).transformLatest { until ->
            val remaining = until?.let { it - now() }
            if (remaining == null || remaining <= Duration.ZERO) {
                emit(false)
            } else {
                emit(true)
                delay(remaining)
                emit(false)
            }
        }.distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> {
        when (val catalog = remote.fetchCatalog()) {
            is RepositoryResult.Failure -> return catalog
            is RepositoryResult.Success -> shopDao.replaceCatalog(
                categories = catalog.value.mapIndexed { index, category ->
                    ShopCategoryEntity(
                        categoryId = category.id,
                        slug = category.slug,
                        title = category.title,
                        subtitle = category.subtitle,
                        iconKey = category.iconKey,
                        sortOrder = index,
                    )
                },
                items = catalog.value.flatMap { category ->
                    category.items.mapIndexed { index, item ->
                        ShopItemEntity(
                            itemId = item.id,
                            categoryId = category.id,
                            slug = item.slug,
                            name = item.name,
                            priceTokens = item.priceTokens,
                            imageKey = item.imageKey,
                            sortOrder = index,
                            miiHatType = item.miiHatType,
                        )
                    }
                },
            )
        }

        when (val balance = refreshTokenBalance(accountId)) {
            is RepositoryResult.Failure -> return balance
            is RepositoryResult.Success -> Unit
        }
        when (val owned = refreshOwnedItems(accountId)) {
            is RepositoryResult.Failure -> return owned
            is RepositoryResult.Success -> Unit
        }
        return refreshSupporterStatus(accountId)
    }

    override suspend fun refreshSupporterStatus(accountId: UserId): RepositoryResult<Unit> =
        when (val status = remote.fetchSupporterStatus(accountId)) {
            is RepositoryResult.Failure -> status
            is RepositoryResult.Success -> {
                val until = status.value
                if (until == null) {
                    shopDao.deleteSupporterStatus(accountId.value)
                } else {
                    shopDao.upsertSupporterStatus(
                        SupporterStatusEntity(
                            accountId = accountId.value,
                            activeUntilEpochMillis = until.toEpochMilliseconds(),
                        ),
                    )
                }
                RepositoryResult.Success(Unit)
            }
        }

    override suspend fun refreshTokenBalance(accountId: UserId): RepositoryResult<Unit> =
        when (val balance = remote.fetchTokenBalance(accountId)) {
            is RepositoryResult.Failure -> balance
            is RepositoryResult.Success -> {
                shopDao.upsertBalance(
                    TokenBalanceEntity(
                        accountId = accountId.value,
                        balance = balance.value,
                    ),
                )
                RepositoryResult.Success(Unit)
            }
        }

    override suspend fun refreshOwnedItems(accountId: UserId): RepositoryResult<Unit> =
        when (val owned = remote.fetchOwnedItems(accountId)) {
            is RepositoryResult.Failure -> owned
            is RepositoryResult.Success -> {
                shopDao.replaceOwnedItemsFromRemote(
                    accountId = accountId.value,
                    items = owned.value.map { item ->
                        OwnedShopItemEntity(
                            accountId = accountId.value,
                            itemId = item.itemId,
                            pricePaid = item.pricePaid,
                            purchasedAtEpochMillis = item.purchasedAt.toEpochMilliseconds(),
                            pendingOperationId = null,
                        )
                    },
                )
                RepositoryResult.Success(Unit)
            }
        }

    override suspend fun purchase(
        command: PurchaseShopItemCommand,
    ): RepositoryResult<Unit> = repositoryCall {
        val result = mutationStore.enqueuePurchase(command).toShopRepositoryResult()
        if (result is RepositoryResult.Success) {
            pendingOperationScheduler.schedule(command.accountId)
        }
        result
    }
}

private fun OptimisticMutationResult<Unit>.toShopRepositoryResult(): RepositoryResult<Unit> =
    when (this) {
        is OptimisticMutationResult.Enqueued -> RepositoryResult.Success(Unit)
        is OptimisticMutationResult.AlreadyEnqueued -> RepositoryResult.Success(Unit)
        is OptimisticMutationResult.Conflict -> RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Conflict,
                message = reason,
                retryable = false,
            ),
        )
    }

class RoomLeaderboardRepository(
    private val leaderboardDao: LeaderboardDao,
    private val remote: LeaderboardRemoteDataSource,
) : LeaderboardRepository {
    override fun observeLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): Flow<List<LeaderboardEntry>> =
        leaderboardDao.observeEntries(accountId.value, scope.key)
            .map { entries -> entries.map(LeaderboardEntryEntity::toDomain) }
            .distinctUntilChanged()

    override suspend fun refresh(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<Unit> =
        when (val entries = remote.fetchLeaderboard(accountId, scope)) {
            is RepositoryResult.Failure -> entries
            is RepositoryResult.Success -> {
                leaderboardDao.replaceEntries(
                    accountId = accountId.value,
                    scope = scope.key,
                    entries = entries.value.mapIndexed { index, entry ->
                        entry.toEntity(
                            accountId = accountId,
                            scope = scope,
                            position = index,
                        )
                    },
                )
                RepositoryResult.Success(Unit)
            }
        }
}

class RoomAchievementsRepository(
    private val achievementDao: AchievementDao,
    private val remote: AchievementsRemoteDataSource,
) : AchievementsRepository {
    override fun observeAchievements(accountId: UserId): Flow<List<AchievementState>> =
        achievementDao.observeStates(accountId.value)
            .map { states -> states.map(AchievementStateEntity::toDomain) }
            .distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        when (val states = remote.fetchAchievements(accountId)) {
            is RepositoryResult.Failure -> states
            is RepositoryResult.Success -> {
                val orderedKeys = AchievementCatalog.orderedKeys
                achievementDao.replaceStates(
                    accountId = accountId.value,
                    states = states.value
                        .sortedBy { state ->
                            orderedKeys.indexOf(state.key)
                                .let { index -> if (index < 0) orderedKeys.size else index }
                        }
                        .mapIndexed { index, state ->
                            state.toEntity(accountId = accountId, position = index)
                        },
                )
                RepositoryResult.Success(Unit)
            }
        }
}

class RoomWorldTourRepository(
    private val worldTourDao: WorldTourDao,
    private val remote: WorldTourRemoteDataSource,
) : WorldTourRepository {
    override fun observeRegions(accountId: UserId): Flow<List<WorldTourRegion>> =
        worldTourDao.observeRegions(accountId.value)
            .map { regions -> regions.map(WorldTourRegionEntity::toDomain) }
            .distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        when (val regions = remote.fetchRegions(accountId)) {
            is RepositoryResult.Failure -> regions
            is RepositoryResult.Success -> {
                worldTourDao.replaceRegions(
                    accountId = accountId.value,
                    regions = regions.value
                        .sortedByDescending(WorldTourRegion::firstMetAt)
                        .mapIndexed { index, region ->
                            region.toEntity(accountId = accountId, position = index)
                        },
                )
                RepositoryResult.Success(Unit)
            }
        }
}

class RoomPassingStatsRepository(
    private val passingStatsDao: PassingStatsDao,
    private val remote: PassingStatsRemoteDataSource,
    private val now: () -> Instant = Clock.System::now,
    private val utcOffsetMinutes: (Instant) -> Int = { at ->
        localUtcOffsetMinutes(at.toEpochMilliseconds())
    },
) : PassingStatsRepository {
    override fun observeStats(accountId: UserId): Flow<PassingStats?> =
        passingStatsDao.observeStats(accountId.value)
            .map { stats -> stats?.toDomain() }
            .distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> {
        val at = now()
        return when (val stats = remote.fetchStats(accountId, utcOffsetMinutes(at))) {
            is RepositoryResult.Failure -> stats
            is RepositoryResult.Success -> {
                passingStatsDao.upsertStats(stats.value.toEntity(accountId, at))
                RepositoryResult.Success(Unit)
            }
        }
    }
}

class RoomPuzzleRepository(
    private val puzzleDao: PuzzleDao,
    private val remote: PuzzleRemoteDataSource,
    private val artworkStore: PuzzleArtworkStore = NoPuzzleArtworkStore,
    private val now: () -> Instant = Clock.System::now,
) : PuzzleRepository {
    override fun observeCollection(accountId: UserId): Flow<PuzzleCollection> =
        combine(
            puzzleDao.observeCollection(accountId.value),
            puzzleDao.observePuzzles(accountId.value),
            puzzleDao.observePieces(accountId.value),
        ) { collection, puzzles, pieces ->
            collection?.toDomain(puzzles, pieces) ?: PuzzleCollection.seed()
        }.distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        when (val fetched = remote.fetchCollection(accountId)) {
            is RepositoryResult.Failure -> fetched
            is RepositoryResult.Success -> {
                val collection = fetched.value.let { remoteCollection ->
                    remoteCollection.copy(
                        puzzles = remoteCollection.puzzles.map { puzzle -> puzzle.withStoredArtwork() },
                    )
                }
                puzzleDao.replaceCollection(
                    accountId = accountId.value,
                    collection = collection.toEntity(accountId, now()),
                    puzzles = collection.puzzles.mapIndexed { index, puzzle ->
                        puzzle.toEntity(accountId = accountId, position = index)
                    },
                    pieces = collection.puzzles.flatMap { puzzle ->
                        puzzle.ownedPieces.map { index ->
                            PuzzlePieceEntity(
                                accountId = accountId.value,
                                puzzleId = puzzle.id,
                                pieceIndex = index,
                            )
                        }
                    },
                )
                RepositoryResult.Success(Unit)
            }
        }

    override suspend fun buyPiece(
        command: BuyPuzzlePieceCommand,
    ): RepositoryResult<PuzzlePiecePurchaseOutcome> {
        val outcome = remote.buyPiece(command)
        val completed = (outcome as? RepositoryResult.Success)?.value as? PuzzlePiecePurchaseOutcome.Completed
        if (completed != null) {
            puzzleDao.insertPiece(
                PuzzlePieceEntity(
                    accountId = command.accountId.value,
                    puzzleId = completed.puzzleId,
                    pieceIndex = completed.pieceIndex,
                ),
            )
            refresh(command.accountId)
        }
        return outcome
    }

    private suspend fun PuzzleProgress.withStoredArtwork(): PuzzleProgress {
        val path = imagePath ?: return this
        val key = puzzleArtworkKey(slug ?: id, path)
        val stored = artworkStore.pathFor(key)
            ?: when (val download = remote.downloadPanel(path)) {
                is RepositoryResult.Success -> artworkStore.write(key, download.value)
                is RepositoryResult.Failure -> null
            }
        return if (stored == null) this else copy(artwork = PuzzleArtwork.File(stored))
    }
}

internal fun puzzleArtworkKey(name: String, imagePath: String): String {
    val safeName = name.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "panel" }
    val extension = imagePath.substringAfterLast('.', "png")
        .filter(Char::isLetterOrDigit)
        .take(4)
        .ifEmpty { "png" }
    val fingerprint = imagePath.hashCode().toUInt().toString(16)
    return "$safeName-$fingerprint.$extension"
}

class RoomBingoRepository(
    private val bingoDao: BingoDao,
    private val remote: BingoRemoteDataSource,
) : BingoRepository {
    override fun observeBoard(accountId: UserId): Flow<List<BingoCell>> =
        bingoDao.observeBoard(accountId.value)
            .map { cells -> cells.map(BingoCellEntity::toDomain) }
            .distinctUntilChanged()

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        when (val cells = remote.fetchBoard(accountId)) {
            is RepositoryResult.Failure -> cells
            is RepositoryResult.Success -> {
                bingoDao.replaceBoard(
                    accountId = accountId.value,
                    cells = cells.value
                        .sortedBy(BingoCell::position)
                        .map { cell -> cell.toEntity(accountId = accountId) },
                )
                RepositoryResult.Success(Unit)
            }
        }
}

