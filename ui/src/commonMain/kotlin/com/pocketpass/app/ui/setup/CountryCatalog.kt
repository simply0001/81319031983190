package com.pocketpass.app.ui.setup

import com.pocketpass.app.ui.displayCountryName
import com.pocketpass.app.ui.isoCountryCodes

data class CountryOption(
    val code: String,
    val name: String,
    val flag: String,
)

object CountryCatalog {
    val countries: List<CountryOption> by lazy {
        isoCountryCodes()
            .map { code ->
                CountryOption(
                    code = code,
                    name = displayCountryName(code),
                    flag = flagEmoji(code),
                )
            }
            .sortedBy(CountryOption::name)
    }

    fun flagEmoji(code: String): String = buildString {
        code.uppercase().forEach { letter ->
            // Regional indicator symbols live above the BMP, so append the surrogate pair.
            val offset = 0x1F1E6 + (letter - 'A') - 0x10000
            append(((offset shr 10) + 0xD800).toChar())
            append(((offset and 0x3FF) + 0xDC00).toChar())
        }
    }
}
