package com.pocketpass.app.state

import com.pocketpass.app.widget.WidgetDesign

interface WidgetPlatformActions {
    val pinSupported: Boolean

    suspend fun pin(design: WidgetDesign): Boolean

    suspend fun assign(appWidgetId: Int, designId: String)

    suspend fun refreshAll()
}

object InactiveWidgetPlatform : WidgetPlatformActions {
    override val pinSupported: Boolean = false
    override suspend fun pin(design: WidgetDesign): Boolean = false
    override suspend fun assign(appWidgetId: Int, designId: String) = Unit
    override suspend fun refreshAll() = Unit
}
