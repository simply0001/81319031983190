package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.repository.remote.PuzzleRemoteDataSource
import com.pocketpass.app.domain.model.BuyPuzzlePieceCommand
import com.pocketpass.app.domain.model.PuzzleArtwork
import com.pocketpass.app.domain.model.PuzzleCollection
import com.pocketpass.app.domain.model.PuzzleKind
import com.pocketpass.app.domain.model.PuzzlePiecePurchaseOutcome
import com.pocketpass.app.domain.model.PuzzleProgress
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.PuzzleArtworkStore
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPuzzleRepositoryTest {
    private lateinit var database: PocketPassDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketPassDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anEmptyCacheShowsTheSeedAndRefreshStoresThePanelArtwork() = runBlocking {
        val store = FakeArtworkStore()
        val remote = FakePuzzleRemote(collection = serverCollection())
        val repository = RoomPuzzleRepository(database.puzzleDao(), remote, store)

        assertEquals(PuzzleCollection.seed(), repository.observeCollection(ACCOUNT).first())

        assertTrue(repository.refresh(ACCOUNT) is RepositoryResult.Success)
        val cached = repository.observeCollection(ACCOUNT).first()
        assertEquals(2, cached.puzzles.size)
        assertEquals(1, cached.currentIndex)
        assertEquals(setOf(5, 9), cached.puzzles[0].ownedPieces)
        assertEquals(PuzzleArtwork.File("/fake/pocki_happy-" + "panels/pocki_happy.png".hashCode().toUInt().toString(16) + ".png"), cached.puzzles[1].artwork)
        assertEquals(1, remote.downloads)

        assertTrue(repository.refresh(ACCOUNT) is RepositoryResult.Success)
        assertEquals(1, remote.downloads)
    }

    @Test
    fun aFailedRefreshKeepsTheCachedCollection() = runBlocking {
        val remote = FakePuzzleRemote(collection = serverCollection())
        val repository = RoomPuzzleRepository(database.puzzleDao(), remote, FakeArtworkStore())
        repository.refresh(ACCOUNT)

        remote.fail = true
        assertTrue(repository.refresh(ACCOUNT) is RepositoryResult.Failure)
        assertEquals(2, repository.observeCollection(ACCOUNT).first().puzzles.size)
    }

    @Test
    fun buyingAPieceRecordsItBeforeTheRefreshLands() = runBlocking {
        val remote = FakePuzzleRemote(collection = serverCollection())
        val repository = RoomPuzzleRepository(database.puzzleDao(), remote, FakeArtworkStore())
        repository.refresh(ACCOUNT)
        remote.fail = true
        remote.purchase = PuzzlePiecePurchaseOutcome.Completed(
            puzzleId = "panel:abc",
            pieceIndex = 4,
            balance = 60,
            puzzleCompleted = false,
            nextPuzzleId = null,
        )

        val outcome = repository.buyPiece(
            BuyPuzzlePieceCommand(accountId = ACCOUNT, priceTokens = 15, requestedAt = NOW),
        )

        assertTrue(outcome is RepositoryResult.Success)
        assertEquals(setOf(2, 4), repository.observeCollection(ACCOUNT).first().puzzles[1].ownedPieces)
    }

    private fun serverCollection() = PuzzleCollection(
        puzzles = listOf(
            PuzzleProgress(
                id = PuzzleCollection.OWN_PIIP_ID,
                kind = PuzzleKind.OwnPiip,
                title = PuzzleCollection.OWN_PIIP_TITLE,
                slug = null,
                artwork = PuzzleArtwork.OwnPortrait,
                columns = 4,
                rows = 4,
                ownedPieces = setOf(5, 9),
                startedAt = NOW,
                completedAt = null,
            ),
            PuzzleProgress(
                id = "panel:abc",
                kind = PuzzleKind.Panel,
                title = "Pocki Happy",
                slug = "pocki_happy",
                artwork = PuzzleArtwork.Remote("https://cdn.test/panels/pocki_happy.png"),
                columns = 3,
                rows = 5,
                ownedPieces = setOf(2),
                startedAt = NOW,
                completedAt = null,
                imagePath = "panels/pocki_happy.png",
            ),
        ),
        currentIndex = 1,
        piecePriceTokens = 15,
    )

    private class FakeArtworkStore : PuzzleArtworkStore {
        val stored = mutableMapOf<String, String>()

        override fun pathFor(key: String): String? = stored[key]

        override suspend fun write(key: String, bytes: ByteArray): String? =
            "/fake/$key".also { stored[key] = it }
    }

    private class FakePuzzleRemote(
        private val collection: PuzzleCollection,
    ) : PuzzleRemoteDataSource {
        var fail = false
        var downloads = 0
        var purchase: PuzzlePiecePurchaseOutcome? = null

        override suspend fun fetchCollection(accountId: UserId): RepositoryResult<PuzzleCollection> =
            if (fail) failure() else RepositoryResult.Success(collection)

        override suspend fun buyPiece(command: BuyPuzzlePieceCommand): RepositoryResult<PuzzlePiecePurchaseOutcome> =
            purchase?.let { RepositoryResult.Success(it) } ?: failure()

        override suspend fun downloadPanel(imagePath: String): RepositoryResult<ByteArray> {
            downloads += 1
            return RepositoryResult.Success(byteArrayOf(1, 2, 3))
        }

        private fun <T> failure(): RepositoryResult<T> = RepositoryResult.Failure(
            RepositoryFailure(kind = RepositoryFailureKind.Unavailable, message = "offline"),
        )
    }

    private companion object {
        val ACCOUNT = UserId("account-one")
        val NOW: Instant = Instant.fromEpochSeconds(1_785_100_000)
    }
}
