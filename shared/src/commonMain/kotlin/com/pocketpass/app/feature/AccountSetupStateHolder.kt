package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.PROFILE_NAME_RULE_MESSAGE
import com.pocketpass.app.domain.model.PROFILE_NAME_TAKEN_MESSAGE
import com.pocketpass.app.domain.model.filterProfileNameInput
import com.pocketpass.app.domain.model.isValidProfileName
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.repository.ProfileRepository
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.model.BIO_MAX_LENGTH
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ACCOUNT_SETUP_AGE_MIN = 13
const val ACCOUNT_SETUP_AGE_MAX = 120

enum class AccountSetupStep {
    Name,
    Bio,
    Age,
    Country,
}

data class AccountSetupUiState(
    val required: Boolean = false,
    val resolved: Boolean = false,
    val step: AccountSetupStep = AccountSetupStep.Name,
    val nameDraft: String = "",
    val bioDraft: String = "",
    val ageDraft: String = "",
    val countryCode: String? = null,
    val countryListExpanded: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val errorShakeNonce: Int = 0,
) {
    val nameValid: Boolean
        get() = isValidProfileName(nameDraft)

    val bioValid: Boolean
        get() = bioDraft.isNotBlank()

    val ageValid: Boolean
        get() = ageDraft.isEmpty() ||
            ageDraft.toIntOrNull() in ACCOUNT_SETUP_AGE_MIN..ACCOUNT_SETUP_AGE_MAX

    val canContinue: Boolean
        get() = !submitting && when (step) {
            AccountSetupStep.Name -> nameValid
            AccountSetupStep.Bio -> bioValid
            AccountSetupStep.Age -> ageValid
            AccountSetupStep.Country -> countryCode != null
        }
}

sealed interface AccountSetupEvent {
    data class NameChanged(val value: String) : AccountSetupEvent
    data class BioChanged(val value: String) : AccountSetupEvent
    data class AgeChanged(val value: String) : AccountSetupEvent
    data class CountrySelected(val code: String) : AccountSetupEvent
    data object ExpandCountryList : AccountSetupEvent
    data object Continue : AccountSetupEvent
    data object BackStep : AccountSetupEvent
    data object SkipAge : AccountSetupEvent
    data object Submit : AccountSetupEvent
}

class AccountSetupStateHolder(
    private val accountId: StateFlow<UserId?>,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Clock.System::now,
) {
    private val mutableState = MutableStateFlow(AccountSetupUiState())
    val state: StateFlow<AccountSetupUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            accountId.collectLatest { account ->
                mutableState.value = AccountSetupUiState()
                if (account == null) return@collectLatest
                launch { profileRepository.refreshProfile(account) }
                val placeholder = placeholderUsername(account)
                profileRepository.observeProfile(account).collectLatest { profile ->
                    mutableState.update { current ->
                        when {
                            profile == null -> current.copy(resolved = false)
                            current.submitting -> current
                            profile.username.isEmpty() ->
                                current.copy(resolved = true, required = false)
                            profile.username == placeholder -> {
                                if (current.required) {
                                    current.copy(resolved = true)
                                } else {
                                    current.copy(
                                        resolved = true,
                                        required = true,
                                        bioDraft = profile.bio.take(BIO_MAX_LENGTH),
                                        ageDraft = profile.age?.toString().orEmpty(),
                                        countryCode = profile.countryCode,
                                    )
                                }
                            }
                            else -> current.copy(resolved = true, required = false)
                        }
                    }
                }
            }
        }
    }

    fun dispatch(event: AccountSetupEvent) {
        when (event) {
            is AccountSetupEvent.NameChanged -> setName(event.value)
            is AccountSetupEvent.BioChanged -> setBio(event.value)
            is AccountSetupEvent.AgeChanged -> setAge(event.value)
            is AccountSetupEvent.CountrySelected -> selectCountry(event.code)
            AccountSetupEvent.ExpandCountryList -> expandCountryList()
            AccountSetupEvent.Continue -> continueStep()
            AccountSetupEvent.BackStep -> backStep()
            AccountSetupEvent.SkipAge -> skipAge()
            AccountSetupEvent.Submit -> submit()
        }
    }

    fun backStep(): Boolean {
        val current = mutableState.value
        if (!current.required || current.submitting) return false
        if (current.step == AccountSetupStep.Country && !current.countryListExpanded) {
            expandCountryList()
            return true
        }
        val previous = when (current.step) {
            AccountSetupStep.Name -> return true
            AccountSetupStep.Bio -> AccountSetupStep.Name
            AccountSetupStep.Age -> AccountSetupStep.Bio
            AccountSetupStep.Country -> AccountSetupStep.Age
        }
        mutableState.update { it.copy(step = previous, error = null) }
        return true
    }

    private fun setName(value: String) {
        mutableState.update { current ->
            if (current.submitting) return@update current
            val filtered = filterProfileNameInput(value)
            current.copy(nameDraft = filtered, error = null)
        }
    }

    private fun setBio(value: String) {
        mutableState.update { current ->
            if (current.submitting) return@update current
            current.copy(bioDraft = value.take(BIO_MAX_LENGTH), error = null)
        }
    }

    private fun setAge(value: String) {
        mutableState.update { current ->
            if (current.submitting) return@update current
            val filtered = value.filter(Char::isDigit).take(3)
            current.copy(ageDraft = filtered, error = null)
        }
    }

    private fun selectCountry(code: String) {
        mutableState.update { current ->
            if (current.submitting) return@update current
            current.copy(
                countryCode = code.uppercase(),
                countryListExpanded = false,
                error = null,
            )
        }
    }

    private fun expandCountryList() {
        mutableState.update { current ->
            if (current.submitting) return@update current
            current.copy(countryListExpanded = true, error = null)
        }
    }

    private fun continueStep() {
        val current = mutableState.value
        if (current.submitting) return
        if (!current.canContinue) {
            mutableState.update {
                it.copy(
                    error = stepError(it.step),
                    errorShakeNonce = it.errorShakeNonce + 1,
                )
            }
            return
        }
        when (current.step) {
            AccountSetupStep.Name ->
                mutableState.update { it.copy(step = AccountSetupStep.Bio, error = null) }
            AccountSetupStep.Bio ->
                mutableState.update { it.copy(step = AccountSetupStep.Age, error = null) }
            AccountSetupStep.Age ->
                mutableState.update { it.copy(step = AccountSetupStep.Country, error = null) }
            AccountSetupStep.Country -> submit()
        }
    }

    private fun skipAge() {
        mutableState.update { current ->
            if (current.submitting || current.step != AccountSetupStep.Age) return@update current
            current.copy(step = AccountSetupStep.Country, ageDraft = "", error = null)
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (!current.required || current.submitting) return
        val account = accountId.value ?: return
        val repository = profileRepository as? MutableProfileRepository ?: return
        val country = current.countryCode
        if (!current.nameValid || !current.bioValid || !current.ageValid || country == null) {
            mutableState.update {
                it.copy(
                    error = stepError(it.step),
                    errorShakeNonce = it.errorShakeNonce + 1,
                )
            }
            return
        }
        mutableState.update { it.copy(submitting = true, error = null) }
        scope.launch {
            val result = repository.completeAccountSetup(
                AccountSetupCommand(
                    accountId = account,
                    username = current.nameDraft,
                    displayName = current.nameDraft,
                    bio = current.bioDraft.trim().take(BIO_MAX_LENGTH),
                    age = current.ageDraft.toIntOrNull(),
                    countryCode = country,
                    changedAt = now(),
                ),
            )
            when (result) {
                is RepositoryResult.Success -> mutableState.update {
                    it.copy(submitting = false, required = false)
                }

                is RepositoryResult.Failure -> mutableState.update {
                    if (result.error.kind == RepositoryFailureKind.Conflict) {
                        it.copy(
                            submitting = false,
                            step = AccountSetupStep.Name,
                            error = PROFILE_NAME_TAKEN_MESSAGE,
                            errorShakeNonce = it.errorShakeNonce + 1,
                        )
                    } else {
                        it.copy(
                            submitting = false,
                            error = "Your profile could not be saved. Try again.",
                            errorShakeNonce = it.errorShakeNonce + 1,
                        )
                    }
                }
            }
        }
    }

    private fun stepError(step: AccountSetupStep): String = when (step) {
        AccountSetupStep.Name -> PROFILE_NAME_RULE_MESSAGE
        AccountSetupStep.Bio -> "Write a short bio to introduce yourself."
        AccountSetupStep.Age ->
            "Ages must be between $ACCOUNT_SETUP_AGE_MIN and $ACCOUNT_SETUP_AGE_MAX."
        AccountSetupStep.Country -> "Pick the country you play from."
    }

    companion object {
        fun placeholderUsername(accountId: UserId): String =
            accountId.value.replace("-", "")
    }
}
