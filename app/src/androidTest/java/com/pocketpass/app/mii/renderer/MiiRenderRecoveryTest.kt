package com.pocketpass.app.mii.renderer

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MiiRenderRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val controller by lazy { MiiRenderController.get(compose.activity) }

    @Test
    fun crashedRendererIsReplacedAndBootsAgain() {
        showSurface()
        compose.waitUntil(BOOT_TIMEOUT_MS) { controller.status.value is MiiRenderStatus.Ready }
        val generation = controller.surfaceGeneration.value
        val crashed = currentWebView()

        crash(crashed)

        compose.waitUntil(SWAP_TIMEOUT_MS) { controller.surfaceGeneration.value == generation + 1 }
        compose.waitUntil(SWAP_TIMEOUT_MS) { currentWebView().let { it != null && it !== crashed } }
        compose.waitUntil(BOOT_TIMEOUT_MS) { controller.status.value is MiiRenderStatus.Ready }
    }

    @Test
    fun rendererThatKeepsCrashingStopsRestarting() {
        showSurface()
        val start = controller.surfaceGeneration.value
        var crashed: WebView? = null
        repeat(3) { restart ->
            compose.waitUntil(BOOT_TIMEOUT_MS) {
                controller.surfaceGeneration.value == start + restart &&
                    currentWebView().let { it != null && it !== crashed }
            }
            assertTrue(controller.status.value !is MiiRenderStatus.Ready)
            crashed = currentWebView()
            crash(crashed)
        }

        compose.waitUntil(SWAP_TIMEOUT_MS) { controller.status.value is MiiRenderStatus.Error }
        compose.waitUntil(SWAP_TIMEOUT_MS) { currentWebView().let { it != null && it !== crashed } }
        assertEquals(start + 3, controller.surfaceGeneration.value)
        assertEquals(
            MiiRenderStatus.Error("The Piip preview stopped working."),
            controller.status.value,
        )
    }

    private fun showSurface() {
        compose.setContent {
            MiiRenderSurface(controller, Modifier.size(240.dp))
        }
    }

    private fun crash(webView: WebView?) {
        checkNotNull(webView)
        compose.runOnUiThread { webView.loadUrl("chrome://crash") }
    }

    private fun currentWebView(): WebView? =
        compose.runOnUiThread { compose.activity.window.decorView.findWebView() }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
        else -> null
    }

    private companion object {
        const val BOOT_TIMEOUT_MS = 240_000L
        const val SWAP_TIMEOUT_MS = 20_000L
    }
}
