package com.pocketpass.app.domain.model

/**
 * Collapses repeated encounters with the same person into one entry that
 * carries the most recent meeting, newest first. Recent interactions are a
 * list of people, not of passes.
 */
fun List<NearbyEncounter>.latestPerPerson(): List<NearbyEncounter> =
    groupBy { it.profile.userId }
        .values
        .map { encounters -> encounters.maxBy { it.occurredAt } }
        .sortedByDescending { it.occurredAt }
