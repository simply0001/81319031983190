package com.pocketpass.app.domain.model

const val PROFILE_NAME_MIN_LENGTH = 3
const val PROFILE_NAME_MAX_LENGTH = 12
const val PROFILE_NAME_RULE_MESSAGE =
    "Names need $PROFILE_NAME_MIN_LENGTH-$PROFILE_NAME_MAX_LENGTH letters, numbers, or dots."
const val PROFILE_NAME_TAKEN_MESSAGE = "That name is already taken."

private val ProfileNameRule = Regex("^[a-z0-9][a-z0-9.]{2,11}$")

fun filterProfileNameInput(value: String): String = value
    .lowercase()
    .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' }
    .take(PROFILE_NAME_MAX_LENGTH)

fun isValidProfileName(value: String): Boolean = ProfileNameRule.matches(value)
