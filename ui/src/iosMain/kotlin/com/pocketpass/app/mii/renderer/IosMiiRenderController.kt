@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.mii.renderer

import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.toNativeRendererFields
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIColor
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

const val MII_RENDERER_SCHEME = "pp-assets"
const val MII_RENDERER_HOST = "renderer"
private const val NATIVE_BRIDGE_NAME = "PocketPassNative"

private const val BRIDGE_SHIM = """
globalThis.$NATIVE_BRIDGE_NAME = {
  postMessage: function (payload) {
    window.webkit.messageHandlers.$NATIVE_BRIDGE_NAME.postMessage(String(payload));
  }
};
"""

private val MIME_TYPES = mapOf(
    "html" to "text/html",
    "js" to "application/javascript",
    "mjs" to "application/javascript",
    "json" to "application/json",
    "css" to "text/css",
    "wasm" to "application/wasm",
    "dat" to "application/octet-stream",
    "glb" to "model/gltf-binary",
    "zip" to "application/zip",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "webp" to "image/webp",
    "svg" to "image/svg+xml",
)

private class RendererSchemeHandler(
    private val resourceRoot: String,
) : NSObject(), WKURLSchemeHandlerProtocol {

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val url = startURLSchemeTask.request.URL
        val path = url?.path?.trimStart('/').orEmpty()
        val data = resolve(path)
        if (url == null || data == null) {
            startURLSchemeTask.didReceiveResponse(notFound(url))
            startURLSchemeTask.didFinish()
            return
        }
        val response = NSHTTPURLResponse(
            uRL = url,
            statusCode = 200,
            HTTPVersion = "HTTP/1.1",
            headerFields = mapOf(
                "Content-Type" to mimeFor(path),
                "Cache-Control" to "no-store",
                "Cross-Origin-Resource-Policy" to "same-origin",
                "X-Content-Type-Options" to "nosniff",
            ),
        )
        startURLSchemeTask.didReceiveResponse(response)
        startURLSchemeTask.didReceiveData(data)
        startURLSchemeTask.didFinish()
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) = Unit

    private fun resolve(path: String): NSData? {
        val normalized = path.ifEmpty { "index.html" }
        if (normalized.split('/').any { it == ".." || it.startsWith('.') }) return null
        return NSData.dataWithContentsOfFile("$resourceRoot/$normalized")
    }

    private fun notFound(url: NSURL?): NSHTTPURLResponse = NSHTTPURLResponse(
        uRL = url ?: NSURL(string = "$MII_RENDERER_SCHEME://$MII_RENDERER_HOST/"),
        statusCode = 404,
        HTTPVersion = "HTTP/1.1",
        headerFields = emptyMap<Any?, Any?>(),
    )

    private fun mimeFor(path: String): String =
        MIME_TYPES[path.substringAfterLast('.', "")] ?: "application/octet-stream"
}

private class RendererMessageHandler(
    private val onMessage: (String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        onMessage(didReceiveScriptMessage.body.toString())
    }
}

/**
 * WKWebView counterpart of the Android MiiRenderController: same page, same base64
 * command protocol, same capture streaming. Main-thread confined like the original.
 */
class IosMiiRenderController private constructor() {
    private val mutableStatus = MutableStateFlow<MiiRenderStatus>(MiiRenderStatus.Detached)
    val status: StateFlow<MiiRenderStatus> = mutableStatus.asStateFlow()

    var canonicalBase64: String = DEFAULT_MII_BASE64
        private set

    private var activeWebView: WKWebView? = null
    private var readySignal = CompletableDeferred<Unit>()
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<String?>>()
    private val pendingCaptures = mutableMapOf<String, CompletableDeferred<ByteArray>>()
    private val captureChunks = mutableMapOf<String, Array<String?>>()

    fun createWebView(): WKWebView {
        val resourceRoot = "${NSBundle.mainBundle.resourcePath}/mii_renderer"
        val configuration = WKWebViewConfiguration()
        configuration.setURLSchemeHandler(
            RendererSchemeHandler(resourceRoot),
            forURLScheme = MII_RENDERER_SCHEME,
        )
        configuration.userContentController.addUserScript(
            WKUserScript(
                source = BRIDGE_SHIM,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
        configuration.userContentController.addScriptMessageHandler(
            RendererMessageHandler(::onRendererMessage),
            name = NATIVE_BRIDGE_NAME,
        )
        val webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
        webView.setOpaque(false)
        webView.backgroundColor = UIColor.clearColor
        webView.scrollView.scrollEnabled = false
        webView.scrollView.bounces = false
        return webView
    }

    fun attach(webView: WKWebView, initialCanonicalBase64: String) {
        val bootCanonical = initialCanonicalBase64
            .takeIf { runCatching { validateCanonical(it) }.isSuccess }
            ?: DEFAULT_MII_BASE64
        activeWebView?.let(::silence)
        failPending(MiiRendererException("Mii render surface was replaced"))
        readySignal = CompletableDeferred()
        canonicalBase64 = bootCanonical
        activeWebView = webView
        mutableStatus.value = MiiRenderStatus.Loading
        val boot = NSURL(
            string = "$MII_RENDERER_SCHEME://$MII_RENDERER_HOST/index.html?mii=" +
                urlEncode(bootCanonical),
        )
        webView.loadRequest(NSURLRequest(uRL = boot))
    }

    fun detach(webView: WKWebView) {
        if (activeWebView !== webView) return
        silence(webView)
        activeWebView = null
        failPending(MiiRendererException("Mii render surface was detached"))
        mutableStatus.value = MiiRenderStatus.Detached
    }

    private fun silence(webView: WKWebView) {
        webView.stopLoading()
        webView.configuration.userContentController
            .removeScriptMessageHandlerForName(NATIVE_BRIDGE_NAME)
    }

    suspend fun applyAppearance(appearance: MiiAppearance): String {
        val exported = request(
            buildJsonObject {
                put("type", "applyAppearance")
                put(
                    "fields",
                    buildJsonObject {
                        appearance.toNativeRendererFields().forEach { (name, value) ->
                            put(name, value)
                        }
                    },
                )
            },
            RENDER_TIMEOUT_MS,
        ) ?: throw MiiRendererException("Mii renderer returned no canonical appearance data")
        validateCanonical(exported)
        canonicalBase64 = exported
        mutableStatus.value = MiiRenderStatus.Ready(exported)
        return exported
    }

    suspend fun setCamera(camera: MiiRenderCamera, transitionMillis: Int = 0) {
        require(transitionMillis in 0..2_000) {
            "Camera transition must be between 0 and 2000 milliseconds"
        }
        request(
            buildJsonObject {
                put("type", "setCamera")
                put("camera", camera.wireValue)
                put("transitionMillis", transitionMillis)
            },
            DEFAULT_TIMEOUT_MS,
        )
    }

    suspend fun capturePortraitPng(size: Int = DEFAULT_PORTRAIT_SIZE): ByteArray {
        require(size in MIN_PORTRAIT_SIZE..MAX_PORTRAIT_SIZE) {
            "Portrait size must be between $MIN_PORTRAIT_SIZE and $MAX_PORTRAIT_SIZE"
        }
        return withTimeout(CAPTURE_TIMEOUT_MS) {
            readySignal.await()
            val id = NSUUID().UUIDString
            val deferred = CompletableDeferred<ByteArray>()
            pendingCaptures[id] = deferred
            try {
                sendCommand(
                    buildJsonObject {
                        put("id", id)
                        put("type", "capturePortrait")
                        put("size", size)
                    },
                )
                deferred.await()
            } finally {
                pendingCaptures.remove(id)
                captureChunks.remove(id)
            }
        }
    }

    suspend fun exportPortraitToFile(destinationPath: String, size: Int = DEFAULT_PORTRAIT_SIZE): String {
        val bytes = capturePortraitPng(size)
        return withContext(Dispatchers.IO) {
            val destination = destinationPath.toPath()
            val directory = destination.parent
                ?: throw MiiRendererException("Portrait destination has no parent directory")
            FileSystem.SYSTEM.createDirectories(directory)
            val temporary = directory / ".${destination.name}.tmp"
            try {
                FileSystem.SYSTEM.write(temporary) { write(bytes) }
                FileSystem.SYSTEM.atomicMove(temporary, destination)
            } catch (error: Throwable) {
                runCatching { FileSystem.SYSTEM.delete(temporary) }
                throw error
            }
            destinationPath
        }
    }

    private suspend fun request(command: JsonObject, timeoutMillis: Long): String? =
        withTimeout(timeoutMillis) {
            readySignal.await()
            val id = NSUUID().UUIDString
            val deferred = CompletableDeferred<String?>()
            pendingRequests[id] = deferred
            try {
                sendCommand(
                    buildJsonObject {
                        command.forEach { (key, value) -> put(key, value) }
                        put("id", id)
                    },
                )
                deferred.await()
            } finally {
                pendingRequests.remove(id)
            }
        }

    private fun sendCommand(command: JsonObject) {
        val webView = activeWebView
            ?: throw MiiRendererException("Mii render surface is not attached")
        val encoded = Base64.encode(command.toString().encodeToByteArray())
        webView.evaluateJavaScript(
            "globalThis.PocketPassMiiRenderer?.receiveBase64(\"$encoded\")",
            null,
        )
    }

    private fun onRendererMessage(payload: String) {
        val json = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        when (json.text("type")) {
            "state" -> handleState(json)
            "result" -> handleResult(json)
            "capture-start" -> handleCaptureStart(json)
            "capture-chunk" -> handleCaptureChunk(json)
            "capture-complete" -> handleCaptureComplete(json)
            "protocol-error" -> failRuntime("The Mii renderer protocol failed.")
        }
    }

    private fun handleState(json: JsonObject) {
        when (json.text("state")) {
            "loading" -> mutableStatus.value = MiiRenderStatus.Loading
            "ready" -> {
                val canonical = json.text("canonicalBase64") ?: canonicalBase64
                runCatching { validateCanonical(canonical) }.onSuccess {
                    canonicalBase64 = canonical
                }
                mutableStatus.value = MiiRenderStatus.Ready(canonicalBase64)
                if (!readySignal.isCompleted) readySignal.complete(Unit)
            }
            "error" -> failRuntime("The Mii renderer could not be initialized.")
        }
    }

    private fun handleResult(json: JsonObject) {
        val id = json.text("id") ?: return
        if (json.boolean("ok") != true) {
            val error = MiiRendererException(
                json.text("error") ?: "The Mii renderer operation failed.",
            )
            pendingRequests.remove(id)?.completeExceptionally(error)
            pendingCaptures.remove(id)?.completeExceptionally(error)
            return
        }
        val value = json["value"]
            ?.let { it as? JsonPrimitive }
            ?.takeUnless { it.toString() == "null" }
            ?.content
        pendingRequests.remove(id)?.complete(value)
    }

    private fun handleCaptureStart(json: JsonObject) {
        val id = json.text("id") ?: return
        val total = json.int("total") ?: return
        if (pendingCaptures[id] == null || total !in 1..MAX_CAPTURE_CHUNKS) return
        captureChunks[id] = arrayOfNulls(total)
    }

    private fun handleCaptureChunk(json: JsonObject) {
        val id = json.text("id") ?: return
        val parts = captureChunks[id] ?: return
        val index = json.int("index") ?: -1
        val data = json.text("data").orEmpty()
        if (index !in parts.indices || data.length > MAX_CAPTURE_CHUNK_LENGTH) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait stream is invalid"),
            )
            captureChunks.remove(id)
            return
        }
        parts[index] = data
    }

    private fun handleCaptureComplete(json: JsonObject) {
        val id = json.text("id") ?: return
        val parts = captureChunks.remove(id) ?: return
        if (parts.any { it == null }) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait stream is incomplete"),
            )
            return
        }
        val encoded = parts.joinToString(separator = "")
        if (encoded.length > MAX_CAPTURE_BASE64_LENGTH) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait is unexpectedly large"),
            )
            return
        }
        val bytes = runCatching { Base64.decode(encoded) }.getOrNull()
        if (bytes == null || !bytes.hasPngSignature()) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait is invalid"),
            )
            return
        }
        pendingCaptures.remove(id)?.complete(bytes)
    }

    private fun failRuntime(message: String) {
        val error = MiiRendererException(message)
        if (!readySignal.isCompleted) readySignal.completeExceptionally(error)
        failPending(error)
        mutableStatus.value = MiiRenderStatus.Error(message)
    }

    private fun failPending(error: Throwable) {
        pendingRequests.values.forEach { it.completeExceptionally(error) }
        pendingCaptures.values.forEach { it.completeExceptionally(error) }
        pendingRequests.clear()
        pendingCaptures.clear()
        captureChunks.clear()
    }

    private fun validateCanonical(value: String) {
        val decoded = runCatching { Base64.decode(value) }.getOrNull()
        require(decoded != null && decoded.size in MIN_CANONICAL_BYTES..MAX_CANONICAL_BYTES) {
            "Invalid canonical Mii data"
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.toString() == "null" }?.content

    private fun JsonObject.int(key: String): Int? =
        runCatching { (this[key] as? JsonPrimitive)?.int }.getOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        runCatching { (this[key] as? JsonPrimitive)?.boolean }.getOrNull()

    companion object {
        const val RENDERER_VERSION =
            "ariankordi/mii-creator@1cd6b7d1d09e75fffd5c116a10e3e162647ecb78+pocketpass.20260809.5"

        const val DEFAULT_MII_BASE64 =
            "BAXGigDvV8wSNID/cJl869TJwxYAAAAAAAAAAAAAAAAAAAAAAAAAAE0AaQBpAAAAAAAAAAAAAAAAAAAACAAAAAAAQAMDAQYEBgIKCAQEAgIMAAAAAP8AAAAACAQACgEAIf///0AABAACFAMTBBcNBAAKBAEJ//8A/wAAAP//"

        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val RENDER_TIMEOUT_MS = 30_000L
        private const val CAPTURE_TIMEOUT_MS = 45_000L
        private const val DEFAULT_PORTRAIT_SIZE = 512
        private const val MIN_PORTRAIT_SIZE = 128
        private const val MAX_PORTRAIT_SIZE = 1024
        private const val MIN_CANONICAL_BYTES = 80
        private const val MAX_CANONICAL_BYTES = 256
        private const val MAX_CAPTURE_CHUNKS = 512
        private const val MAX_CAPTURE_CHUNK_LENGTH = 40 * 1024
        private const val MAX_CAPTURE_BASE64_LENGTH = 16 * 1024 * 1024

        val shared: IosMiiRenderController by lazy { IosMiiRenderController() }
    }
}

private fun urlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val char = byte.toInt().toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            append(char)
        } else {
            append('%')
            append(byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun ByteArray.hasPngSignature(): Boolean =
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte() &&
        this[4] == 0x0D.toByte() &&
        this[5] == 0x0A.toByte() &&
        this[6] == 0x1A.toByte() &&
        this[7] == 0x0A.toByte()
