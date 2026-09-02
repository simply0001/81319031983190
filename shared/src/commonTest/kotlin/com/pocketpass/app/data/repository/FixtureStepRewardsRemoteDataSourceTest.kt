package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.steps.DailyStepReward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FixtureStepRewardsRemoteDataSourceTest {
    private val walker = UserId("walker")

    private suspend fun FixtureStepRewardsRemoteDataSource.report(
        steps: Int,
        account: UserId = walker,
        day: String = "2026-09-02",
    ): DailyStepReward =
        (reportDailySteps(account, day, steps, 0) as RepositoryResult.Success).value

    @Test
    fun paysTheDifferenceAndKeepsTheHighestCount() = runTest {
        val remote = FixtureStepRewardsRemoteDataSource()

        val first = remote.report(3_999)
        assertEquals(9, first.tokensAwarded)
        assertEquals(9, first.tokensCredited)

        val repeat = remote.report(3_999)
        assertEquals(0, repeat.tokensCredited)

        val lower = remote.report(1_000)
        assertEquals(3_999, lower.steps)
        assertEquals(0, lower.tokensCredited)

        val capped = remote.report(10_000)
        assertEquals(25, capped.tokensAwarded)
        assertEquals(16, capped.tokensCredited)
        assertEquals(25, capped.balance)
    }

    @Test
    fun accountsAndDaysAreIndependent() = runTest {
        val remote = FixtureStepRewardsRemoteDataSource()
        remote.report(10_000)

        val other = remote.report(400, account = UserId("other"))
        assertEquals(1, other.tokensCredited)

        val tomorrow = remote.report(800, day = "2026-09-03")
        assertEquals(2, tomorrow.tokensCredited)
    }
}
