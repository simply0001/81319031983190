package com.pocketpass.app.mii

object PretendoId {
    const val MAX_LENGTH = 16
    private val pattern = Regex("^[a-z0-9](?:[a-z0-9]|[-_.](?=[a-z0-9])){5,15}$")

    fun normalize(raw: String): String? =
        raw.trim().lowercase().takeIf(pattern::matches)

    fun isAllowedCharacter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
            character == '-' || character == '_' || character == '.'
}

sealed interface PretendoLookupResult {
    data class Found(
        val pnid: String,
        val pid: Long,
        val miiName: String,
        val portraitUrl: String,
        val appearance: MiiAppearance,
    ) : PretendoLookupResult

    data object NotFound : PretendoLookupResult

    data class Unavailable(val message: String) : PretendoLookupResult
}

fun interface PretendoMiiSource {
    suspend fun lookup(pnid: String): PretendoLookupResult
}

data class PretendoMiiRecord(
    val pid: Long,
    val name: String,
    val miiDataBase64: String,
    val portraitUrl: String?,
)

fun pretendoPortraitUrl(pid: Long): String = "https://r2-cdn.pretendo.cc/mii/$pid/normal_face.png"

class FixturePretendoMiiSource(
    private val miis: Map<String, PretendoLookupResult.Found> = DEFAULT_FIXTURES,
) : PretendoMiiSource {
    override suspend fun lookup(pnid: String): PretendoLookupResult =
        PretendoId.normalize(pnid)?.let(miis::get) ?: PretendoLookupResult.NotFound

    private companion object {
        val DEFAULT_FIXTURES: Map<String, PretendoLookupResult.Found> = mapOf(
            "spob" to PretendoLookupResult.Found(
                pnid = "spob",
                pid = 1_000_001L,
                miiName = "Spob",
                portraitUrl = pretendoPortraitUrl(1_000_001L),
                appearance = MiiAppearance(hairType = 12, hairColor = 3, favoriteColor = 5),
            ),
            "sans" to PretendoLookupResult.Found(
                pnid = "sans",
                pid = 1_000_002L,
                miiName = "Sans",
                portraitUrl = pretendoPortraitUrl(1_000_002L),
                appearance = MiiAppearance(hairType = 25, eyeType = 7, favoriteColor = 8),
            ),
        )
    }
}
