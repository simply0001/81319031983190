package com.pocketpass.app.ui.setup

import java.util.Locale

data class CountryOption(
    val code: String,
    val name: String,
    val flag: String,
)

object CountryCatalog {
    val countries: List<CountryOption> by lazy {
        Locale.getISOCountries()
            .map { code ->
                CountryOption(
                    code = code,
                    name = Locale.Builder()
                        .setRegion(code)
                        .build()
                        .getDisplayCountry(Locale.ENGLISH)
                        .ifBlank { code },
                    flag = flagEmoji(code),
                )
            }
            .sortedBy(CountryOption::name)
    }

    fun flagEmoji(code: String): String = buildString {
        code.uppercase().forEach { letter ->
            appendCodePoint(0x1F1E6 + (letter - 'A'))
        }
    }
}
