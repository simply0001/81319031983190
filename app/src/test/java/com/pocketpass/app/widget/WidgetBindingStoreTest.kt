package com.pocketpass.app.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetBindingStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store: WidgetBindingStore
        get() = WidgetBindingStore(File(folder.root, "widgets"))

    @Test
    fun bindingsSurviveAReopenAndUnbindDropsThem() {
        store.bind(3, "design-a")
        store.bind(4, "design-b")

        assertEquals("design-a", store.designIdFor(3))
        assertEquals("design-b", store.designIdFor(4))

        store.unbind(listOf(3))

        assertNull(store.designIdFor(3))
        assertEquals("design-b", store.designIdFor(4))
    }

    @Test
    fun anUnboundWidgetClaimsThePendingDesignOfItsSizeOnce() {
        store.markPending("design-a", WidgetSize.Wide, nowEpochMillis = 10_000L)

        assertNull(store.resolve(8, WidgetSize.Small, 11_000L))
        assertEquals("design-a", store.resolve(9, WidgetSize.Wide, 11_000L))
        assertEquals("design-a", store.designIdFor(9))
        assertNull(store.read().pendingDesignId)
        assertNull(store.resolve(10, WidgetSize.Wide, 12_000L))
    }

    @Test
    fun aCorruptFileReadsAsEmpty() {
        val directory = File(folder.root, "widgets").apply { mkdirs() }
        File(directory, WidgetBindings.FILE_NAME).writeText("{ not json")

        assertEquals(WidgetBindings(), store.read())
        store.bind(1, "design-a")
        assertEquals("design-a", store.designIdFor(1))
    }
}
