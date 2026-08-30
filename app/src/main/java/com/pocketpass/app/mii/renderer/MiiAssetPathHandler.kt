package com.pocketpass.app.mii.renderer

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileNotFoundException

internal class MiiAssetPathHandler(
    private val assets: AssetManager,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val normalizedPath = path
            .substringBefore('?')
            .trimStart('/')
            .replace('\\', '/')
        if (
            normalizedPath.isBlank() ||
            normalizedPath.startsWith('.') ||
            normalizedPath.split('/').any { it == ".." }
        ) {
            return null
        }

        val assetPath = "$ASSET_ROOT/$normalizedPath"
        val stream = try {
            assets.open(assetPath, AssetManager.ACCESS_STREAMING)
        } catch (_: FileNotFoundException) {
            return null
        }
        val mimeType = mimeTypeFor(normalizedPath)
        val encoding = if (
            mimeType.startsWith("text/") ||
            mimeType == "application/javascript" ||
            mimeType == "application/json"
        ) {
            Charsets.UTF_8.name()
        } else {
            null
        }
        return WebResourceResponse(
            mimeType,
            encoding,
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Cross-Origin-Resource-Policy" to "same-origin",
                "X-Content-Type-Options" to "nosniff",
            ),
            stream,
        )
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "css" -> "text/css"
        "wasm" -> "application/wasm"
        "glb" -> "model/gltf-binary"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "dat" -> "application/octet-stream"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }

    private companion object {
        const val ASSET_ROOT = "mii_renderer"
    }
}
