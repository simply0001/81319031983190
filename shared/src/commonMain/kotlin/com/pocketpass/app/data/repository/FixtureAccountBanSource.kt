package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.AccountBanNotice
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.AccountBanSource
import com.pocketpass.app.domain.state.RepositoryResult

object FixtureAccountBanSource : AccountBanSource {
    override suspend fun fetchAccountBan(
        accountId: UserId,
    ): RepositoryResult<AccountBanNotice?> = RepositoryResult.Success(null)
}
