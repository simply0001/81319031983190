package com.pocketpass.app.mii.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.pocketpass.app.mii.MiiAppearance
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class MiiRenderController private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mutableStatus = MutableStateFlow<MiiRenderStatus>(MiiRenderStatus.Detached)
    val status: StateFlow<MiiRenderStatus> = mutableStatus.asStateFlow()

    @Volatile
    var canonicalBase64: String = DEFAULT_MII_BASE64
        private set

    private var activeWebView: WebView? = null
    private var lastOrbit: Pair<Float, Float>? = null
    private var readySignal = CompletableDeferred<Unit>()
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val pendingCaptures = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val captureChunks = ConcurrentHashMap<String, CaptureAccumulator>()

    @SuppressLint("SetJavaScriptEnabled")
    internal fun createWebView(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = true
        overScrollMode = View.OVER_SCROLL_NEVER
        setOnLongClickListener { true }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE,
                -> view.parent?.requestDisallowInterceptTouchEvent(true)

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.blockNetworkLoads = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setGeolocationEnabled(false)

        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "Renderer: ${consoleMessage.message()}")
                }
                return true
            }
        }
    }

    internal fun attach(
        webView: WebView,
        initialCanonicalBase64: String,
    ) {
        checkMainThread()
        val bootCanonical = initialCanonicalBase64
            .takeIf { runCatching { validateCanonical(it) }.isSuccess }
            ?: DEFAULT_MII_BASE64
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            mutableStatus.value = MiiRenderStatus.Error(
                "This Android WebView cannot run the Mii renderer safely.",
            )
            return
        }

        activeWebView?.let(::removeMessageListener)
        failPending(MiiRendererException("Mii render surface was replaced"))
        readySignal = CompletableDeferred()
        lastOrbit = null
        canonicalBase64 = bootCanonical
        activeWebView = webView
        mutableStatus.value = MiiRenderStatus.Loading

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/", MiiAssetPathHandler(appContext.assets))
            .build()
        webView.webViewClient = localOnlyWebViewClient(assetLoader)
        WebViewCompat.addWebMessageListener(
            webView,
            NATIVE_BRIDGE_NAME,
            setOf(ASSET_ORIGIN),
            WebViewCompat.WebMessageListener(::onRendererMessage),
        )

        val page = Uri.Builder()
            .scheme("https")
            .authority(ASSET_HOST)
            .path("/index.html")
            .appendQueryParameter("mii", bootCanonical)
            .build()
        webView.loadUrl(page.toString())
    }

    internal fun detach(webView: WebView) {
        checkMainThread()
        if (activeWebView !== webView) return
        removeMessageListener(webView)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        activeWebView = null
        lastOrbit = null
        failPending(MiiRendererException("Mii render surface was detached"))
        mutableStatus.value = MiiRenderStatus.Detached
    }

    fun postOrbit(x: Float, y: Float) {
        checkMainThread()
        val webView = activeWebView ?: return
        val orbit = x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f)
        if (orbit == lastOrbit) return
        lastOrbit = orbit
        val encoded = Base64.encodeToString(
            JSONObject()
                .put("type", "setOrbit")
                .put("x", orbit.first.toDouble())
                .put("y", orbit.second.toDouble())
                .toString()
                .toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        webView.evaluateJavascript(
            "globalThis.PocketPassMiiRenderer?.receiveBase64(\"$encoded\")",
            null,
        )
    }

    suspend fun setMii(
        canonicalBase64: String,
        camera: MiiRenderCamera = MiiRenderCamera.WholeHead,
    ) {
        validateCanonical(canonicalBase64)
        val value = request(
            JSONObject()
                .put("type", "setMii")
                .put("canonicalBase64", canonicalBase64)
                .put("camera", camera.wireValue),
            RENDER_TIMEOUT_MS,
        )
        val resolvedCanonical = value?.takeIf(String::isNotBlank) ?: canonicalBase64
        validateCanonical(resolvedCanonical)
        this.canonicalBase64 = resolvedCanonical
        mutableStatus.value = MiiRenderStatus.Ready(resolvedCanonical)
    }

    suspend fun updateField(
        name: String,
        value: Any,
        renderPart: MiiRenderPart = MiiRenderPart.Head,
        bodyUpdate: MiiBodyUpdate = MiiBodyUpdate.None,
    ) {
        require(name.matches(FIELD_NAME_REGEX)) { "Invalid Mii field name" }
        require(value is Number || value is String || value is Boolean) {
            "Mii field values must be numbers, strings, or booleans"
        }
        request(
            JSONObject()
                .put("type", "updateField")
                .put("field", name)
                .put("value", value)
                .put("renderPart", renderPart.wireValue)
                .put("bodyUpdate", bodyUpdate.wireValue),
            RENDER_TIMEOUT_MS,
        )
    }

    suspend fun applyAppearance(
        appearance: MiiAppearance,
    ): String {
        val fields = JSONObject()
        appearance.toNativeRendererFields().forEach { (name, value) ->
            fields.put(name, value)
        }
        val exported = request(
            JSONObject()
                .put("type", "applyAppearance")
                .put("fields", fields),
            RENDER_TIMEOUT_MS,
        ) ?: throw MiiRendererException(
            "Mii renderer returned no canonical appearance data",
        )
        validateCanonical(exported)
        canonicalBase64 = exported
        mutableStatus.value = MiiRenderStatus.Ready(exported)
        return exported
    }

    suspend fun setCamera(
        camera: MiiRenderCamera,
        transitionMillis: Int = 0,
    ) {
        require(transitionMillis in 0..2_000) {
            "Camera transition must be between 0 and 2000 milliseconds"
        }
        request(
            JSONObject()
                .put("type", "setCamera")
                .put("camera", camera.wireValue)
                .put("transitionMillis", transitionMillis),
            DEFAULT_TIMEOUT_MS,
        )
    }

    suspend fun exportCanonical(): String {
        val exported = request(
            JSONObject().put("type", "export"),
            DEFAULT_TIMEOUT_MS,
        ) ?: throw MiiRendererException("Mii renderer returned no canonical data")
        validateCanonical(exported)
        canonicalBase64 = exported
        mutableStatus.value = MiiRenderStatus.Ready(exported)
        return exported
    }

    suspend fun capturePortraitPng(
        size: Int = DEFAULT_PORTRAIT_SIZE,
    ): ByteArray {
        require(size in MIN_PORTRAIT_SIZE..MAX_PORTRAIT_SIZE) {
            "Portrait size must be between $MIN_PORTRAIT_SIZE and $MAX_PORTRAIT_SIZE"
        }
        return withTimeout(CAPTURE_TIMEOUT_MS) {
            readySignal.await()
            val id = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<ByteArray>()
            pendingCaptures[id] = deferred
            try {
                sendCommand(
                    JSONObject()
                        .put("id", id)
                        .put("type", "capturePortrait")
                        .put("size", size),
                )
                deferred.await()
            } finally {
                pendingCaptures.remove(id)
                captureChunks.remove(id)
            }
        }
    }

    suspend fun capturePortraitBitmap(
        size: Int = DEFAULT_PORTRAIT_SIZE,
    ): Bitmap {
        val bytes = capturePortraitPng(size)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw MiiRendererException("The exported Mii portrait is invalid")
    }

    suspend fun exportPortrait(
        destination: File,
        size: Int = DEFAULT_PORTRAIT_SIZE,
    ): File {
        val bytes = capturePortraitPng(size)
        return withContext(Dispatchers.IO) {
            val parent = destination.absoluteFile.parentFile
                ?: throw IOException("Portrait destination has no parent directory")
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Unable to create the portrait directory")
            }
            val temporary = File(parent, ".${destination.name}.${UUID.randomUUID()}.tmp")
            try {
                temporary.outputStream().use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
            destination
        }
    }

    private suspend fun request(
        command: JSONObject,
        timeoutMillis: Long,
    ): String? = withTimeout(timeoutMillis) {
        readySignal.await()
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pendingRequests[id] = deferred
        try {
            command.put("id", id)
            sendCommand(command)
            deferred.await()
        } finally {
            pendingRequests.remove(id)
        }
    }

    private suspend fun sendCommand(command: JSONObject) {
        withContext(Dispatchers.Main.immediate) {
            val webView = activeWebView
                ?: throw MiiRendererException("Mii render surface is not attached")
            val encoded = Base64.encodeToString(
                command.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            webView.evaluateJavascript(
                "globalThis.PocketPassMiiRenderer?.receiveBase64(\"$encoded\")",
                null,
            )
        }
    }

    private fun onRendererMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        @Suppress("UNUSED_PARAMETER") replyProxy: androidx.webkit.JavaScriptReplyProxy,
    ) {
        if (
            view !== activeWebView ||
            !isMainFrame ||
            sourceOrigin.scheme != "https" ||
            sourceOrigin.host != ASSET_HOST
        ) {
            return
        }
        val payload = message.data ?: return
        val json = try {
            JSONObject(payload)
        } catch (_: Throwable) {
            return
        }
        when (json.optString("type")) {
            "state" -> handleState(json)
            "result" -> handleResult(json)
            "capture-start" -> handleCaptureStart(json)
            "capture-chunk" -> handleCaptureChunk(json)
            "capture-complete" -> handleCaptureComplete(json)
            "protocol-error" -> failRuntime("The Mii renderer protocol failed.")
        }
    }

    private fun handleState(json: JSONObject) {
        when (json.optString("state")) {
            "loading" -> mutableStatus.value = MiiRenderStatus.Loading
            "ready" -> {
                val canonical = json.optString("canonicalBase64", canonicalBase64)
        runCatching { validateCanonical(canonical) }.onSuccess {
            canonicalBase64 = canonical
        }
                mutableStatus.value = MiiRenderStatus.Ready(canonicalBase64)
                if (!readySignal.isCompleted) readySignal.complete(Unit)
            }
            "error" -> failRuntime("The Mii renderer could not be initialized.")
        }
    }

    private fun handleResult(json: JSONObject) {
        val id = json.optString("id")
        if (id.isBlank()) return
        if (!json.optBoolean("ok")) {
            val error = MiiRendererException(
                json.optString("error", "The Mii renderer operation failed."),
            )
            pendingRequests.remove(id)?.completeExceptionally(error)
            pendingCaptures.remove(id)?.completeExceptionally(error)
            return
        }
        val value = if (json.has("value") && !json.isNull("value")) {
            json.opt("value")?.toString()
        } else {
            null
        }
        pendingRequests.remove(id)?.complete(value)
    }

    private fun handleCaptureStart(json: JSONObject) {
        val id = json.optString("id")
        val total = json.optInt("total")
        if (
            id.isBlank() ||
            pendingCaptures[id] == null ||
            total !in 1..MAX_CAPTURE_CHUNKS
        ) {
            return
        }
        captureChunks[id] = CaptureAccumulator(total)
    }

    private fun handleCaptureChunk(json: JSONObject) {
        val id = json.optString("id")
        val accumulator = captureChunks[id] ?: return
        val index = json.optInt("index", -1)
        val data = json.optString("data")
        if (
            index !in accumulator.parts.indices ||
            data.length > MAX_CAPTURE_CHUNK_LENGTH
        ) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait stream is invalid"),
            )
            captureChunks.remove(id)
            return
        }
        accumulator.parts[index] = data
    }

    private fun handleCaptureComplete(json: JSONObject) {
        val id = json.optString("id")
        val accumulator = captureChunks.remove(id) ?: return
        if (accumulator.parts.any { it == null }) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait stream is incomplete"),
            )
            return
        }
        val encoded = buildString {
            accumulator.parts.forEach { append(it) }
        }
        if (encoded.length > MAX_CAPTURE_BASE64_LENGTH) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait is unexpectedly large"),
            )
            return
        }
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
        if (bytes == null || !bytes.hasPngSignature()) {
            pendingCaptures.remove(id)?.completeExceptionally(
                MiiRendererException("The Mii portrait is invalid"),
            )
            return
        }
        pendingCaptures.remove(id)?.complete(bytes)
    }

    private fun localOnlyWebViewClient(
        assetLoader: WebViewAssetLoader,
    ): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url
            if (url.scheme == "https" && url.host == ASSET_HOST) {
                return assetLoader.shouldInterceptRequest(url) ?: notFoundResponse()
            }
            return blockedResponse()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = request.url.host != ASSET_HOST || request.url.scheme != "https"

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                failRuntime("The local Mii renderer page could not be loaded.")
            }
        }
    }

    private fun removeMessageListener(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching {
                WebViewCompat.removeWebMessageListener(webView, NATIVE_BRIDGE_NAME)
            }
        }
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
        val decoded = runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()
        require(decoded != null && decoded.size in MIN_CANONICAL_BYTES..MAX_CANONICAL_BYTES) {
            "Invalid canonical Mii data"
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Mii render surfaces must be attached on the main thread"
        }
    }

    private fun notFoundResponse() = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-store"),
        "Not Found".byteInputStream(),
    )

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        403,
        "Blocked",
        mapOf("Cache-Control" to "no-store"),
        "Blocked".byteInputStream(),
    )

    private data class CaptureAccumulator(
        val total: Int,
        val parts: Array<String?> = arrayOfNulls(total),
    )

    companion object {
        const val RENDERER_VERSION =
            "ariankordi/mii-creator@1cd6b7d1d09e75fffd5c116a10e3e162647ecb78+pocketpass.20260809.5"

        const val DEFAULT_MII_BASE64 =
            "BAXGigDvV8wSNID/cJl869TJwxYAAAAAAAAAAAAAAAAAAAAAAAAAAE0AaQBpAAAAAAAAAAAAAAAAAAAACAAAAAAAQAMDAQYEBgIKCAQEAgIMAAAAAP8AAAAACAQACgEAIf///0AABAACFAMTBBcNBAAKBAEJ//8A/wAAAP//"

        private const val TAG = "MiiRenderController"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val ASSET_ORIGIN = "https://$ASSET_HOST"
        private const val NATIVE_BRIDGE_NAME = "PocketPassNative"
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
        private val FIELD_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9]{0,63}")

        @Volatile
        private var instance: MiiRenderController? = null

        fun get(context: Context): MiiRenderController =
            instance ?: synchronized(this) {
                instance ?: MiiRenderController(context).also { instance = it }
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
