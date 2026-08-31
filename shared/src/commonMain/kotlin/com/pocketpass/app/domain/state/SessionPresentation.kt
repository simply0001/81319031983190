package com.pocketpass.app.domain.state

fun SessionState.showsPocketPassApp(): Boolean =
    this is SessionState.Authenticated ||
        this is SessionState.OfflineWithCachedSession
