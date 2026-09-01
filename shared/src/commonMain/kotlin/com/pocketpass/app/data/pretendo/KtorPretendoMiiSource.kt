package com.pocketpass.app.data.pretendo

import com.pocketpass.app.mii.PretendoId
import com.pocketpass.app.mii.PretendoLookupResult
import com.pocketpass.app.mii.PretendoMiiSource
import com.pocketpass.app.mii.PretendoXml
import com.pocketpass.app.mii.Ver3StoreData
import com.pocketpass.app.mii.pretendoPortraitUrl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * Multiplatform port of OkHttpPretendoMiiSource; the engine comes from
 * whatever ktor client artifact the consuming source set ships.
 */
class KtorPretendoMiiSource(
    versionName: String,
    platform: String,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
        }
    },
    private val baseUrl: String = PRETENDO_ACCOUNT_BASE_URL,
) : PretendoMiiSource {
    private val userAgent = "PocketPass/$versionName ($platform; +https://pocketpass.xyz)"

    override suspend fun lookup(pnid: String): PretendoLookupResult {
        val id = PretendoId.normalize(pnid) ?: return PretendoLookupResult.NotFound
        return try {
            val pid = PretendoXml.parseMappedPid(
                fetch("admin/mapped_ids?input_type=user_id&output_type=pid&input=$id"),
            ) ?: return PretendoLookupResult.NotFound
            val record = PretendoXml.parseMii(fetch("miis?pids=$pid"))
                ?: return PretendoLookupResult.Unavailable(UNREADABLE)
            val bytes = runCatching { Base64.Default.decode(record.miiDataBase64) }.getOrNull()
                ?: return PretendoLookupResult.Unavailable(UNREADABLE)
            val mii = Ver3StoreData.decode(bytes)
                ?: return PretendoLookupResult.Unavailable(UNREADABLE)
            PretendoLookupResult.Found(
                pnid = id,
                pid = pid,
                miiName = mii.name.ifBlank { record.name },
                portraitUrl = record.portraitUrl ?: pretendoPortraitUrl(pid),
                appearance = mii.appearance,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: PretendoRefusedException) {
            PretendoLookupResult.Unavailable(REFUSED)
        } catch (error: Throwable) {
            PretendoLookupResult.Unavailable(UNREACHABLE)
        }
    }

    private suspend fun fetch(path: String): String {
        val response = client.get(baseUrl + path) {
            header("User-Agent", userAgent)
            header("X-Nintendo-Client-ID", PRETENDO_WII_U_CLIENT_ID)
            header("X-Nintendo-Client-Secret", PRETENDO_WII_U_CLIENT_SECRET)
        }
        val body = response.bodyAsText()
        if (response.status.value == 401 || response.status.value == 403) {
            throw PretendoRefusedException()
        }
        if (!response.status.isSuccess()) {
            if (PretendoXml.errorMessage(body) != null) throw PretendoRefusedException()
            throw PretendoUnreachableException("HTTP ${response.status.value}")
        }
        return body
    }

    private class PretendoRefusedException : RuntimeException()
    private class PretendoUnreachableException(message: String) : RuntimeException(message)

    companion object {
        const val PRETENDO_ACCOUNT_BASE_URL = "https://account.pretendo.cc/v1/api/"
        const val PRETENDO_WII_U_CLIENT_ID = "a2efa818a34fa16b8afbc8a74eba3eda"
        const val PRETENDO_WII_U_CLIENT_SECRET = "c91cdb5658bd4954ade78533a339cf9a"
        const val UNREADABLE = "Pretendo returned Mii data PocketPass can't read"
        const val UNREACHABLE = "Pretendo Network isn't reachable right now"
        const val REFUSED = "Pretendo Network refused the request"
    }
}
