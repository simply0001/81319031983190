package com.pocketpass.app.domain.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AccountBanSignal {
    private val reports = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val detected: SharedFlow<Unit> = reports.asSharedFlow()

    fun report() {
        reports.tryEmit(Unit)
    }
}
