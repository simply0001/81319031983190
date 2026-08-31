package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.UserId

fun interface PendingOperationScheduler {
    fun schedule(accountId: UserId)

    companion object {
        val None = PendingOperationScheduler { }
    }
}
