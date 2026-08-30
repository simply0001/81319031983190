package com.pocketpass.app.data.pretendo

import com.pocketpass.app.mii.PretendoId
import com.pocketpass.app.mii.PretendoLookupResult
import com.pocketpass.app.mii.PretendoMiiSource
import com.pocketpass.app.mii.PretendoXml
import com.pocketpass.app.mii.Ver3StoreData
import com.pocketpass.app.mii.pretendoPortraitUrl
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

class OkHttpPretendoMiiSource(
    versionName: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = PRETENDO_ACCOUNT_BASE_URL,
) : PretendoMiiSource {
    private val userAgent = "PocketPass/$versionName (Android; +https://pocketpass.xyz)"

    override suspend fun lookup(pnid: String): PretendoLookupResult = withContext(Dispatchers.IO) {
        val id = PretendoId.normalize(pnid) ?: return@withContext PretendoLookupResult.NotFound
        try {
            val pid = PretendoXml.parseMappedPid(
                fetch("admin/mapped_ids?input_type=user_id&output_type=pid&input=$id"),
            ) ?: return@withContext PretendoLookupResult.NotFound
            val record = PretendoXml.parseMii(fetch("miis?pids=$pid"))
                ?: return@withContext PretendoLookupResult.Unavailable(UNREADABLE)
            val bytes = runCatching { Base64.getDecoder().decode(record.miiDataBase64) }.getOrNull()
                ?: return@withContext PretendoLookupResult.Unavailable(UNREADABLE)
            val mii = Ver3StoreData.decode(bytes)
                ?: return@withContext PretendoLookupResult.Unavailable(UNREADABLE)
            PretendoLookupResult.Found(
                pnid = id,
                pid = pid,
                miiName = mii.name.ifBlank { record.name },
                portraitUrl = record.portraitUrl ?: pretendoPortraitUrl(pid),
                appearance = mii.appearance,
            )
        } catch (error: PretendoRefusedException) {
            PretendoLookupResult.Unavailable(REFUSED)
        } catch (error: IOException) {
            PretendoLookupResult.Unavailable(UNREACHABLE)
        }
    }

    private fun fetch(path: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("User-Agent", userAgent)
            .header("X-Nintendo-Client-ID", PRETENDO_WII_U_CLIENT_ID)
            .header("X-Nintendo-Client-Secret", PRETENDO_WII_U_CLIENT_SECRET)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 403 || response.code == 401) throw PretendoRefusedException()
            if (!response.isSuccessful) {
                if (PretendoXml.errorMessage(body) != null) throw PretendoRefusedException()
                throw IOException("HTTP ${response.code}")
            }
            return body
        }
    }

    private class PretendoRefusedException : RuntimeException()

    companion object {
        const val PRETENDO_ACCOUNT_BASE_URL = "https://account.pretendo.cc/v1/api/"
        const val PRETENDO_WII_U_CLIENT_ID = "a2efa818a34fa16b8afbc8a74eba3eda"
        const val PRETENDO_WII_U_CLIENT_SECRET = "c91cdb5658bd4954ade78533a339cf9a"
        const val UNREADABLE = "Pretendo returned Mii data PocketPass can't read"
        const val UNREACHABLE = "Pretendo Network isn't reachable right now"
        const val REFUSED = "Pretendo Network refused the request"
    }
}
