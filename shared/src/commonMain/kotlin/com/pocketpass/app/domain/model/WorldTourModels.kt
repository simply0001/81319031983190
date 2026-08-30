package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class WorldTourRegion(
    val countryCode: String,
    val firstMetAt: Instant,
) {
    init {
        require(countryCode.matches(Regex("^[A-Z]{2}$"))) {
            "Country code must be two uppercase letters"
        }
    }
}
