package com.pocketpass.app.widget

import java.io.File

class WidgetBindingStore(private val directory: File) {
    private val file: File
        get() = File(directory, WidgetBindings.FILE_NAME)

    fun read(): WidgetBindings = synchronized(Lock) { load() }

    fun designIdFor(appWidgetId: Int): String? = read().designIdFor(appWidgetId)

    fun bind(appWidgetId: Int, designId: String) = synchronized(Lock) {
        save(load().bind(appWidgetId, designId).clearPending())
    }

    fun unbind(appWidgetIds: Collection<Int>) = synchronized(Lock) {
        if (appWidgetIds.isEmpty()) return
        save(load().unbind(appWidgetIds))
    }

    fun markPending(designId: String, size: WidgetSize, nowEpochMillis: Long) = synchronized(Lock) {
        save(load().withPending(designId, size, nowEpochMillis))
    }

    fun resolve(appWidgetId: Int, size: WidgetSize?, nowEpochMillis: Long): String? = synchronized(Lock) {
        val bindings = load()
        bindings.designIdFor(appWidgetId)?.let { return it }
        val pending = bindings.pendingFor(size, nowEpochMillis) ?: return null
        save(bindings.bind(appWidgetId, pending).clearPending())
        pending
    }

    private fun load(): WidgetBindings {
        val current = file
        if (!current.isFile) return WidgetBindings()
        return runCatching { WidgetBindings.decode(current.readText()) }.getOrNull() ?: WidgetBindings()
    }

    private fun save(bindings: WidgetBindings) {
        directory.mkdirs()
        val temporary = File(directory, "${WidgetBindings.FILE_NAME}.tmp")
        temporary.writeText(bindings.encode())
        temporary.renameTo(file)
    }

    private companion object {
        val Lock = Any()
    }
}
