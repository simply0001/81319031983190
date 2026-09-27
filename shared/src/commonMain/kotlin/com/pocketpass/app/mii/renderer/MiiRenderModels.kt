package com.pocketpass.app.mii.renderer

sealed interface MiiRenderStatus {
    data object Detached : MiiRenderStatus
    data object Loading : MiiRenderStatus

    data class Ready(
        val canonicalBase64: String,
    ) : MiiRenderStatus

    data class Error(
        val message: String,
    ) : MiiRenderStatus
}

enum class MiiRenderCamera(
    val wireValue: String,
) {
    FullBody("fullBody"),
    WholeHead("head"),
}

class MiiRendererException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
