package com.pocketpass.spike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
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

const val RENDERER_SCHEME = "pp-assets"
const val RENDERER_HOST = "renderer"
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
    "png" to "image/png",
    "jpg" to "image/jpeg",
)

@OptIn(ExperimentalForeignApi::class)
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
        uRL = url ?: NSURL(string = "$RENDERER_SCHEME://$RENDERER_HOST/"),
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

@OptIn(ExperimentalForeignApi::class)
fun createRendererWebView(
    canonicalBase64: String,
    onMessage: (String) -> Unit,
): WKWebView {
    val resourceRoot = "${NSBundle.mainBundle.resourcePath}/mii_renderer"
    val configuration = WKWebViewConfiguration()
    configuration.setURLSchemeHandler(
        RendererSchemeHandler(resourceRoot),
        forURLScheme = RENDERER_SCHEME,
    )
    configuration.userContentController.addUserScript(
        WKUserScript(
            source = BRIDGE_SHIM,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true,
        ),
    )
    configuration.userContentController.addScriptMessageHandler(
        RendererMessageHandler(onMessage),
        name = NATIVE_BRIDGE_NAME,
    )

    val webView = WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = configuration)
    webView.setOpaque(false)
    webView.backgroundColor = UIColor.clearColor
    webView.scrollView.scrollEnabled = false
    webView.scrollView.bounces = false

    val boot = NSURL(
        string = "$RENDERER_SCHEME://$RENDERER_HOST/index.html?mii=" + urlEncode(canonicalBase64),
    )
    webView.loadRequest(platform.Foundation.NSURLRequest(uRL = boot))
    return webView
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

@Composable
fun MiiRendererSurface(
    canonicalBase64: String,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
) {
    val webView = remember(canonicalBase64) { createRendererWebView(canonicalBase64, onMessage) }
    UIKitView(factory = { webView }, modifier = modifier)
}
