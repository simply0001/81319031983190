package com.pocketpass.app.widget

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class WidgetDesignsStateHolder(
    private val repository: WidgetDesignRepository,
    @Suppress("unused") private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { randomDesignId() },
) {
    val state: StateFlow<List<WidgetDesign>> = repository.designs

    fun now(): Long = nowEpochMillis()

    fun newDesign(size: WidgetSize = WidgetSize.Wide): WidgetDesign {
        val now = nowEpochMillis()
        return WidgetDesign(
            id = newId(),
            name = nextName(state.value),
            size = size,
            hero = WidgetBlock.Tokens,
            tiles = emptyList(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        ).normalized()
    }

    suspend fun save(design: WidgetDesign) {
        repository.upsert(design)
    }

    suspend fun delete(id: String) {
        repository.delete(id)
    }

    companion object {
        internal fun nextName(existing: List<WidgetDesign>): String {
            val taken = existing.mapTo(hashSetOf()) { it.name }
            var number = existing.size + 1
            while ("Widget $number" in taken) number++
            return "Widget $number"
        }

        @OptIn(ExperimentalUuidApi::class)
        private fun randomDesignId(): String = Uuid.random().toString()
    }
}
