package com.pocketpass.app.boards

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object BoardBrandingPicker {
    class Request {
        val result = CompletableDeferred<ByteArray?>()
        fun complete(bytes: ByteArray?) { result.complete(bytes) }
        fun fail(message: String) { result.completeExceptionally(BoardFailure(message, false)) }
    }
    private val queue = Channel<Request>(Channel.RENDEZVOUS)
    private val mutex = Mutex()
    val requests = queue.receiveAsFlow()
    suspend fun pick(): ByteArray? = mutex.withLock {
        val request = Request()
        try { withTimeoutOrNull(300_000) { queue.send(request); request.result.await() } }
        finally { request.result.cancel() }
    }
}
