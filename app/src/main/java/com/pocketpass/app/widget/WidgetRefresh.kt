package com.pocketpass.app.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object WidgetRefresh {
    private val counter = MutableStateFlow(0L)

    val generation: StateFlow<Long> = counter

    fun bump() {
        counter.update { it + 1 }
    }
}
