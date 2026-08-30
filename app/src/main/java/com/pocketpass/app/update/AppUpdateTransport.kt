package com.pocketpass.app.update

import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface UpdateManifestFetcher {
    suspend fun fetch(url: String): String
}

fun interface UpdateApkDownloader {
    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (Float) -> Unit,
    )
}

class OkHttpUpdateTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : UpdateManifestFetcher, UpdateApkDownloader {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
    }

    override suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body
            val total = expectedBytes.takeIf { it > 0 } ?: body.contentLength()
            var received = 0L
            var lastPercent = -1
            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            val percent = ((received * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress((percent / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
