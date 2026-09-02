package com.pocketpass.app.data.repository.remote

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.steps.DailyStepReward

interface StepRewardsRemoteDataSource {
    /**
     * Reports the steps counted so far in [localDay]; the server keeps the
     * highest count it has seen for that day and pays the difference.
     */
    suspend fun reportDailySteps(
        accountId: UserId,
        localDay: String,
        steps: Int,
        utcOffsetMinutes: Int,
    ): RepositoryResult<DailyStepReward>
}

object EmptyStepRewardsRemoteDataSource : StepRewardsRemoteDataSource {
    override suspend fun reportDailySteps(
        accountId: UserId,
        localDay: String,
        steps: Int,
        utcOffsetMinutes: Int,
    ): RepositoryResult<DailyStepReward> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Unavailable,
            message = "Step rewards are unavailable",
            retryable = false,
        ),
    )
}
