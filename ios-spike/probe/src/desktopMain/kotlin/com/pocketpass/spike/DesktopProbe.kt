package com.pocketpass.spike

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

private data class Viewport(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
)

private val Viewports = listOf(
    Viewport("iphone-15-pro-portrait", 1179, 2556, 3f),
    Viewport("iphone-se-portrait", 750, 1334, 2f),
    Viewport("iphone-15-pro-landscape", 2556, 1179, 3f),
    Viewport("ipad-pro-11-portrait", 1668, 2388, 2f),
)

fun main(args: Array<String>) {
    if (args.contains("--probe-backend")) {
        probeBackend()
        return
    }
    val renderIndex = args.indexOf("--render")
    if (renderIndex >= 0) {
        renderAll(File(args.getOrNull(renderIndex + 1) ?: "build/probe-renders"))
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PocketPass CMP probe",
            state = androidx.compose.ui.window.rememberWindowState(size = DpSize(393.dp, 852.dp)),
        ) {
            ProbeApp()
        }
    }
}

private fun probeBackend() = runBlocking {
    val key = System.getenv("POCKETPASS_SUPABASE_PUBLISHABLE_KEY").orEmpty()
    val client = buildClient(key.ifEmpty { "probe-no-key" })
    println("PROBE-BACKEND client built for ${client.supabaseUrl}")
    val status = runCatching { probeAuthSettings() }
    status.onSuccess { println("PROBE-BACKEND GET /auth/v1/settings -> HTTP $it") }
    status.onFailure { println("PROBE-BACKEND request failed: $it") }
    client.close()
}

private fun renderAll(outputDir: File) {
    outputDir.mkdirs()
    Viewports.forEach { viewport ->
        ImageComposeScene(
            width = viewport.widthPx,
            height = viewport.heightPx,
            density = Density(viewport.density),
            coroutineContext = Dispatchers.Unconfined,
        ) {
            ProbeApp()
        }.use { scene ->
            var image = scene.render(0L)
            repeat(FRAMES) { frame ->
                Thread.sleep(FRAME_SETTLE_MILLIS)
                image = scene.render((frame + 1) * FRAME_STEP_NANOS)
            }
            val encoded = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG encode failed for ${viewport.name}")
            val file = File(outputDir, "${viewport.name}.png")
            file.writeBytes(encoded.bytes)
            println(
                "PROBE-RENDER ${viewport.name} ${viewport.widthPx}x${viewport.heightPx} " +
                    "@${viewport.density}x -> ${file.absolutePath} (${file.length()} bytes)",
            )
        }
    }
}

private const val FRAMES = 24
private const val FRAME_SETTLE_MILLIS = 40L
private const val FRAME_STEP_NANOS = 30_000_000L
