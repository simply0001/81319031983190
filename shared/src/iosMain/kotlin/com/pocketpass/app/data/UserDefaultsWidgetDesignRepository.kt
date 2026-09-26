package com.pocketpass.app.data

import com.pocketpass.app.widget.WidgetDesign
import com.pocketpass.app.widget.WidgetDesignDocument
import com.pocketpass.app.widget.WidgetDesignRepository
import com.pocketpass.app.widget.upserted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSUserDefaults

class UserDefaultsWidgetDesignRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : WidgetDesignRepository {
    private val state = MutableStateFlow(load())

    override val designs: StateFlow<List<WidgetDesign>> = state

    override suspend fun upsert(design: WidgetDesign) {
        mutate { current -> current.upserted(design) }
    }

    override suspend fun delete(id: String) {
        mutate { current -> current.filterNot { it.id == id } }
    }

    private fun load(): List<WidgetDesign> =
        defaults.stringForKey(KEY)?.let(WidgetDesignDocument::decode)?.designs.orEmpty()

    private fun mutate(write: (List<WidgetDesign>) -> List<WidgetDesign>) {
        state.update(write)
        defaults.setObject(WidgetDesignDocument(designs = state.value).encode(), KEY)
    }

    private companion object {
        const val KEY = "pocketpass.widgets.designs"
    }
}
