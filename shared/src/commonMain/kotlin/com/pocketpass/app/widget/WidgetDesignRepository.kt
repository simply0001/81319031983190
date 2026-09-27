package com.pocketpass.app.widget

import kotlinx.coroutines.flow.StateFlow

interface WidgetDesignRepository {
    val designs: StateFlow<List<WidgetDesign>>

    suspend fun upsert(design: WidgetDesign)

    suspend fun delete(id: String)
}

fun List<WidgetDesign>.upserted(design: WidgetDesign): List<WidgetDesign> {
    val normalized = design.normalized()
    val index = indexOfFirst { it.id == normalized.id }
    return if (index >= 0) {
        toMutableList().also { it[index] = normalized }
    } else {
        this + normalized
    }
}
