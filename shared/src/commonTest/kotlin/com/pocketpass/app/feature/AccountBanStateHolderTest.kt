package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.AccountBanNotice
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.AccountBanSource
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountBanStateHolderTest {
    private val account = UserId("90000000-0000-4000-8000-000000000001")
    private val otherAccount = UserId("90000000-0000-4000-8000-000000000002")
    private val permanentBan = AccountBanNotice(reason = "Spam in Boards.", endsAtEpochMillis = null)

    @Test
    fun authenticatedSessionChecksAndShowsTheBan() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan))
        runCurrent()
        assertEquals(0, harness.source.calls)

        harness.session.value = SessionState.Authenticated(account)
        runCurrent()

        assertEquals(listOf(account), harness.source.accounts)
        assertEquals(permanentBan, harness.holder.state.value)
    }

    @Test
    fun returningToTheForegroundClearsAnEndedBan() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan), foreground = false)
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()
        assertEquals(permanentBan, harness.holder.state.value)

        harness.source.result = RepositoryResult.Success(null)
        harness.foreground.value = true
        runCurrent()

        assertEquals(2, harness.source.calls)
        assertNull(harness.holder.state.value)
    }

    @Test
    fun bannedFailuresTriggerChecksOnlyUntilTheBanIsKnown() = runTest {
        val harness = harness(RepositoryResult.Success(null))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()
        assertEquals(1, harness.source.calls)

        harness.source.result = RepositoryResult.Success(permanentBan)
        harness.reports.emit(Unit)
        runCurrent()
        assertEquals(2, harness.source.calls)
        assertEquals(permanentBan, harness.holder.state.value)

        harness.reports.emit(Unit)
        runCurrent()
        assertEquals(2, harness.source.calls)
    }

    @Test
    fun realtimeBanEventsAlwaysCheck() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()

        val updated = permanentBan.copy(reason = "Harassment.")
        harness.source.result = RepositoryResult.Success(updated)
        harness.holder.requestCheck()
        runCurrent()

        assertEquals(2, harness.source.calls)
        assertEquals(updated, harness.holder.state.value)
    }

    @Test
    fun failedChecksKeepTheCurrentState() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()

        harness.source.result = RepositoryResult.Failure(
            RepositoryFailure(RepositoryFailureKind.Offline, retryable = true),
        )
        harness.holder.requestCheck()
        runCurrent()

        assertEquals(2, harness.source.calls)
        assertEquals(permanentBan, harness.holder.state.value)
    }

    @Test
    fun offlineSessionsKeepTheBanWithoutChecking() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()

        harness.session.value = SessionState.OfflineWithCachedSession(
            account,
            RepositoryFailure(RepositoryFailureKind.Offline, retryable = true),
        )
        harness.holder.requestCheck()
        runCurrent()

        assertEquals(1, harness.source.calls)
        assertEquals(permanentBan, harness.holder.state.value)
    }

    @Test
    fun signingOutOrSwitchingAccountsResetsTheBan() = runTest {
        val harness = harness(RepositoryResult.Success(permanentBan))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()
        assertEquals(permanentBan, harness.holder.state.value)

        harness.session.value = SessionState.SignedOut
        runCurrent()
        assertNull(harness.holder.state.value)
        assertEquals(1, harness.source.calls)

        harness.source.result = RepositoryResult.Success(null)
        harness.session.value = SessionState.Authenticated(otherAccount)
        runCurrent()
        assertEquals(listOf(account, otherAccount), harness.source.accounts)
        assertNull(harness.holder.state.value)
    }

    @Test
    fun timedBansAreCheckedAgainWhenTheyEnd() = runTest {
        val timedBan = AccountBanNotice(reason = "Cool down.", endsAtEpochMillis = 60_000L)
        val harness = harness(RepositoryResult.Success(timedBan))
        harness.session.value = SessionState.Authenticated(account)
        runCurrent()
        assertEquals(timedBan, harness.holder.state.value)

        harness.source.result = RepositoryResult.Success(null)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(timedBan, harness.holder.state.value)

        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(2, harness.source.calls)
        assertNull(harness.holder.state.value)
    }

    private fun TestScope.harness(
        result: RepositoryResult<AccountBanNotice?>,
        foreground: Boolean = true,
    ): Harness {
        val source = FakeAccountBanSource(result)
        val session = MutableStateFlow<SessionState>(SessionState.Initializing)
        val foregroundState = MutableStateFlow(foreground)
        val reports = MutableSharedFlow<Unit>()
        val holder = AccountBanStateHolder(
            source = source,
            sessionState = session,
            appForeground = foregroundState,
            banReports = reports,
            scope = backgroundScope,
            nowEpochMillis = { testScheduler.currentTime },
        )
        return Harness(source, session, foregroundState, reports, holder)
    }

    private class Harness(
        val source: FakeAccountBanSource,
        val session: MutableStateFlow<SessionState>,
        val foreground: MutableStateFlow<Boolean>,
        val reports: MutableSharedFlow<Unit>,
        val holder: AccountBanStateHolder,
    )

    private class FakeAccountBanSource(
        var result: RepositoryResult<AccountBanNotice?>,
    ) : AccountBanSource {
        val accounts = mutableListOf<UserId>()
        val calls: Int
            get() = accounts.size

        override suspend fun fetchAccountBan(accountId: UserId): RepositoryResult<AccountBanNotice?> {
            accounts += accountId
            return result
        }
    }
}
