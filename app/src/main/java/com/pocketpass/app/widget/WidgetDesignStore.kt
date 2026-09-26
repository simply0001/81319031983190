package com.pocketpass.app.widget

import android.content.Context
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetDesignStore {
    fun file(context: Context): File =
        File(WidgetSnapshotStore.directory(context), WidgetDesignDocument.FILE_NAME)

    suspend fun read(context: Context): List<WidgetDesign> = withContext(Dispatchers.IO) {
        val file = file(context)
        if (!file.isFile) return@withContext emptyList()
        runCatching { WidgetDesignDocument.decode(file.readText())?.designs }.getOrNull().orEmpty()
    }

    suspend fun write(context: Context, designs: List<WidgetDesign>) = withContext(Dispatchers.IO) {
        val file = file(context)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(WidgetDesignDocument(designs = designs).encode())
        temporary.renameTo(file)
    }
}

class FileWidgetDesignRepository(
    context: Context,
    scope: CoroutineScope,
) : WidgetDesignRepository {
    private val appContext = context.applicationContext
    private val state = MutableStateFlow<List<WidgetDesign>>(emptyList())
    private val loaded = CompletableDeferred<Unit>()
    private val mutex = Mutex()

    override val designs: StateFlow<List<WidgetDesign>> = state

    init {
        scope.launch {
            try {
                state.value = runCatching { WidgetDesignStore.read(appContext) }.getOrDefault(emptyList())
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    override suspend fun upsert(design: WidgetDesign) = mutate { current -> current.upserted(design) }

    override suspend fun delete(id: String) = mutate { current -> current.filterNot { it.id == id } }

    private suspend fun mutate(write: (List<WidgetDesign>) -> List<WidgetDesign>) {
        loaded.await()
        mutex.withLock {
            val next = write(state.value)
            state.value = next
            WidgetDesignStore.write(appContext, next)
        }
    }
}
