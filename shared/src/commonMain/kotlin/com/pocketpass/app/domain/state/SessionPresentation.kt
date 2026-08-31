package com.pocketpass.app.domain.state

import com.pocketpass.app.domain.model.UserId

fun SessionState.showsPocketPassApp(): Boolean =
    this is SessionState.Authenticated ||
        this is SessionState.OfflineWithCachedSession

fun SessionState.accountIdOrNull(): UserId? = when (this) {
    is SessionState.Authenticated -> userId
    is SessionState.OfflineWithCachedSession -> userId
    else -> null
}
