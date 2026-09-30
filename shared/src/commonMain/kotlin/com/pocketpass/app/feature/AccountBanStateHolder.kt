package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.AccountBanNotice
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.AccountBanSource
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.accountIdOrNull
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AccountBanStateHolder(
    private val source: AccountBanSource,
    sessionState: StateFlow<SessionState>,
    appForeground: StateFlow<Boolean>,
    banReports: Flow<Unit>,
    scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow<AccountBanNotice?>(null)
    val state: StateFlow<AccountBanNotice?> = mutableState.asStateFlow()
    private val checkRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            appForeground.drop(1).filter { it }.collect { requestCheck() }
        }
        scope.launch {
            banReports.collect { if (mutableState.value == null) requestCheck() }
        }
        scope.launch {
            mutableState.collectLatest { ban ->
                val endsAt = ban?.endsAtEpochMillis ?: return@collectLatest
                delay((endsAt - nowEpochMillis()).coerceAtLeast(0L) + BAN_END_RECHECK_DELAY_MILLIS)
                requestCheck()
            }
        }
        scope.launch {
            var checkedAccount: UserId? = null
            sessionState
                .map { it.accountIdOrNull() to (it is SessionState.Authenticated) }
                .distinctUntilChanged()
                .collectLatest { (accountId, online) ->
                    if (accountId != checkedAccount) {
                        checkedAccount = accountId
                        mutableState.value = null
                    }
                    if (accountId == null || !online) return@collectLatest
                    requestCheck()
                    while (true) {
                        checkRequests.receive()
                        check(accountId)
                    }
                }
        }
    }

    fun requestCheck() {
        checkRequests.trySend(Unit)
    }

    private suspend fun check(accountId: UserId) {
        val result = source.fetchAccountBan(accountId)
        if (result is RepositoryResult.Success) mutableState.value = result.value
    }

    private companion object {
        const val BAN_END_RECHECK_DELAY_MILLIS = 5_000L
    }
}
