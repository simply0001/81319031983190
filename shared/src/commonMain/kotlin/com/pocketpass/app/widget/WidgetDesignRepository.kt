package com.pocketpass.app.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

interface WidgetDesignRepository {
    val designs: StateFlow<List<WidgetDesign>>

    suspend fun upsert(design: WidgetDesign)

    suspend fun delete(id: String)
}

class InMemoryWidgetDesignRepository(
    initial: List<WidgetDesign> = emptyList(),
) : WidgetDesignRepository {
    private val state = MutableStateFlow(initial.map { it.normalized() })

    override val designs: StateFlow<List<WidgetDesign>> = state

    override suspend fun upsert(design: WidgetDesign) {
        state.update { current -> current.upserted(design) }
    }

    override suspend fun delete(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }
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
