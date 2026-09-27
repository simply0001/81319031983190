package com.pocketpass.app.domain.model

fun List<NearbyEncounter>.latestPerPerson(): List<NearbyEncounter> =
    groupBy { it.profile.userId }
        .values
        .map { encounters -> encounters.maxBy { it.occurredAt } }
        .sortedByDescending { it.occurredAt }
