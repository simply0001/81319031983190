package com.pocketpass.app.ui

import com.pocketpass.app.domain.state.SessionState

fun SessionState.showsPocketPassApp(): Boolean =
    this is SessionState.Authenticated ||
        this is SessionState.OfflineWithCachedSession
