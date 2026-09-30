package com.pocketpass.app.mii.renderer

import com.pocketpass.app.mii.MiiHatColourLayout

sealed interface MiiRenderStatus {
    data object Detached : MiiRenderStatus
    data object Loading : MiiRenderStatus

    data class Ready(
        val canonicalBase64: String,
        val hatColours: List<MiiHatColourLayout> = emptyList(),
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
