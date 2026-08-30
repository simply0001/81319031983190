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

enum class MiiRenderPart(
    val wireValue: Int,
) {
    Head(0),
    Face(1),
    Body(2),
}

enum class MiiBodyUpdate(
    val wireValue: Int,
) {
    None(0),
    Clothing(1),
    RepositionCamera(2),
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
