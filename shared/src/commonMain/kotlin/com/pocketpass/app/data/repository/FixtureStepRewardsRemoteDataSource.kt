package com.pocketpass.app.data.repository

import com.pocketpass.app.data.repository.remote.StepRewardsRemoteDataSource
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.steps.DailyStepReward
import com.pocketpass.app.steps.tokensForSteps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server's step ledger in memory, so fixture mode pays like production:
 * the highest count per account and day wins, and each day pays at most the
 * daily cap.
 */
class FixtureStepRewardsRemoteDataSource(
    initialBalance: Int = 0,
) : StepRewardsRemoteDataSource {
    private data class Day(val steps: Int, val awarded: Int)

    private val mutex = Mutex()
    private val days = mutableMapOf<Pair<String, String>, Day>()
    private var balance = initialBalance

    override suspend fun reportDailySteps(
        accountId: UserId,
        localDay: String,
        steps: Int,
        utcOffsetMinutes: Int,
    ): RepositoryResult<DailyStepReward> = mutex.withLock {
        val key = accountId.value to localDay
        val previous = days[key] ?: Day(steps = 0, awarded = 0)
        val best = maxOf(previous.steps, steps.coerceAtLeast(0))
        val target = tokensForSteps(best)
        val credited = (target - previous.awarded).coerceAtLeast(0)
        balance += credited
        days[key] = Day(steps = best, awarded = target)
        RepositoryResult.Success(
            DailyStepReward(
                localDay = localDay,
                steps = best,
                tokensAwarded = target,
                tokensCredited = credited,
                balance = balance,
            ),
        )
    }
}
