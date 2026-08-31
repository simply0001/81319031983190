package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class AccountSetupStateHolderTest {
    private val account = UserId("90000000-0000-4000-8000-000000000001")
    private val placeholder = AccountSetupStateHolder.placeholderUsername(account)
    private val fixtureNow = Instant.parse("2026-08-04T12:00:00Z")

    @Test
    fun placeholderUsernameRequiresSetupAndPrefillsDrafts() = runTest {
        val repository = FakeProfileRepository()
        val holder = holder(repository)
        runCurrent()

        assertFalse(holder.state.value.resolved)

        repository.profiles.value = profile(
            username = placeholder,
            bio = "Existing bio",
            age = 21,
            countryCode = "SE",
        )
        runCurrent()

        val state = holder.state.value
        assertTrue(state.resolved)
        assertTrue(state.required)
        assertEquals("Existing bio", state.bioDraft)
        assertEquals("21", state.ageDraft)
        assertEquals("SE", state.countryCode)
    }

    @Test
    fun claimedUsernameDoesNotRequireSetup() = runTest {
        val repository = FakeProfileRepository()
        val holder = holder(repository)
        repository.profiles.value = profile(username = "petah.g")
        runCurrent()

        assertTrue(holder.state.value.resolved)
        assertFalse(holder.state.value.required)
    }

    @Test
    fun preUpdateLocalRowsWithoutUsernameDoNotGate() = runTest {
        val repository = FakeProfileRepository()
        val holder = holder(repository)
        repository.profiles.value = profile(username = "")
        runCurrent()

        assertTrue(holder.state.value.resolved)
        assertFalse(holder.state.value.required)
    }

    @Test
    fun nameInputIsLowercasedFilteredAndCapped() = runTest {
        val repository = FakeProfileRepository()
        val holder = requiredHolder(repository)

        holder.dispatch(AccountSetupEvent.NameChanged("Ab!C.d-ff123456"))
        runCurrent()

        assertEquals("abc.dff12345", holder.state.value.nameDraft)
    }

    @Test
    fun stepsAdvanceOnlyWhenValid() = runTest {
        val repository = FakeProfileRepository()
        val holder = requiredHolder(repository)

        holder.dispatch(AccountSetupEvent.NameChanged("ab"))
        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertEquals(AccountSetupStep.Name, holder.state.value.step)
        assertEquals(1, holder.state.value.errorShakeNonce)

        holder.dispatch(AccountSetupEvent.NameChanged("petah.g"))
        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertEquals(AccountSetupStep.Bio, holder.state.value.step)

        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertEquals(AccountSetupStep.Bio, holder.state.value.step)

        holder.dispatch(AccountSetupEvent.BioChanged("Hello there!"))
        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertEquals(AccountSetupStep.Age, holder.state.value.step)

        holder.dispatch(AccountSetupEvent.AgeChanged("999"))
        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertEquals(AccountSetupStep.Age, holder.state.value.step)

        holder.dispatch(AccountSetupEvent.SkipAge)
        runCurrent()
        assertEquals(AccountSetupStep.Country, holder.state.value.step)
        assertEquals("", holder.state.value.ageDraft)
    }

    @Test
    fun submitSendsTheChosenNameAsUsernameAndDisplayName() = runTest {
        val repository = FakeProfileRepository()
        val holder = requiredHolder(repository)

        holder.dispatch(AccountSetupEvent.NameChanged("petah.g"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.BioChanged("Hello there!"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.SkipAge)
        holder.dispatch(AccountSetupEvent.CountrySelected("us"))
        holder.dispatch(AccountSetupEvent.Submit)
        runCurrent()

        val command = repository.setupCommands.single()
        assertEquals(account, command.accountId)
        assertEquals("petah.g", command.username)
        assertEquals("petah.g", command.displayName)
        assertEquals("Hello there!", command.bio)
        assertEquals(null, command.age)
        assertEquals("US", command.countryCode)
        assertFalse(holder.state.value.required)
        assertFalse(holder.state.value.submitting)
    }

    @Test
    fun usernameConflictReturnsToTheNameStep() = runTest {
        val repository = FakeProfileRepository()
        repository.setupResult = RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Conflict,
                message = "conflict",
                retryable = false,
            ),
        )
        val holder = requiredHolder(repository)

        holder.dispatch(AccountSetupEvent.NameChanged("petah.g"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.BioChanged("Hello there!"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.SkipAge)
        holder.dispatch(AccountSetupEvent.CountrySelected("US"))
        holder.dispatch(AccountSetupEvent.Submit)
        runCurrent()

        val state = holder.state.value
        assertTrue(state.required)
        assertEquals(AccountSetupStep.Name, state.step)
        assertEquals("That name is already taken.", state.error)
        assertTrue(state.errorShakeNonce > 0)
    }

    @Test
    fun backStepIsConsumedOnTheFirstStepWithoutMoving() = runTest {
        val repository = FakeProfileRepository()
        val holder = requiredHolder(repository)

        assertTrue(holder.backStep())
        assertEquals(AccountSetupStep.Name, holder.state.value.step)

        holder.dispatch(AccountSetupEvent.NameChanged("petah.g"))
        holder.dispatch(AccountSetupEvent.Continue)
        runCurrent()
        assertTrue(holder.backStep())
        runCurrent()
        assertEquals(AccountSetupStep.Name, holder.state.value.step)
    }

    @Test
    fun pickingACountryCollapsesTheListAndBackExpandsItBeforeLeavingTheStep() = runTest {
        val repository = FakeProfileRepository()
        val holder = requiredHolder(repository)
        holder.dispatch(AccountSetupEvent.NameChanged("petah.g"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.BioChanged("Hello there!"))
        holder.dispatch(AccountSetupEvent.Continue)
        holder.dispatch(AccountSetupEvent.SkipAge)
        runCurrent()
        assertEquals(AccountSetupStep.Country, holder.state.value.step)
        assertTrue(holder.state.value.countryListExpanded)

        holder.dispatch(AccountSetupEvent.CountrySelected("se"))
        runCurrent()
        assertEquals("SE", holder.state.value.countryCode)
        assertFalse(holder.state.value.countryListExpanded)

        assertTrue(holder.backStep())
        runCurrent()
        assertEquals(AccountSetupStep.Country, holder.state.value.step)
        assertTrue(holder.state.value.countryListExpanded)
        assertEquals("SE", holder.state.value.countryCode)

        holder.dispatch(AccountSetupEvent.CountrySelected("no"))
        holder.dispatch(AccountSetupEvent.ExpandCountryList)
        runCurrent()
        assertTrue(holder.state.value.countryListExpanded)

        assertTrue(holder.backStep())
        runCurrent()
        assertEquals(AccountSetupStep.Age, holder.state.value.step)
    }

    private fun kotlinx.coroutines.test.TestScope.holder(
        repository: FakeProfileRepository,
    ): AccountSetupStateHolder = AccountSetupStateHolder(
        accountId = MutableStateFlow(account),
        profileRepository = repository,
        scope = backgroundScope,
        now = { fixtureNow },
    )

    private fun kotlinx.coroutines.test.TestScope.requiredHolder(
        repository: FakeProfileRepository,
    ): AccountSetupStateHolder {
        val holder = holder(repository)
        repository.profiles.value = profile(username = placeholder)
        runCurrent()
        assertTrue(holder.state.value.required)
        return holder
    }

    private fun profile(
        username: String,
        bio: String = "",
        age: Int? = null,
        countryCode: String? = null,
    ): UserProfile = UserProfile(
        userId = account,
        displayName = "PocketPass User",
        avatar = null,
        username = username,
        bio = bio,
        age = age,
        countryCode = countryCode,
        updatedAt = fixtureNow,
    )

    private inner class FakeProfileRepository : MutableProfileRepository {
        val profiles = MutableStateFlow<UserProfile?>(null)
        var setupResult: RepositoryResult<UserProfile>? = null
        val setupCommands = mutableListOf<AccountSetupCommand>()

        override fun observeProfile(userId: UserId): Flow<UserProfile?> = profiles

        override suspend fun refreshProfile(userId: UserId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun updateProfile(
            command: UpdateProfileCommand,
        ): RepositoryResult<UserProfile> = error("Not used by account setup")

        override suspend fun renameProfile(
            command: RenameProfileCommand,
        ): RepositoryResult<UserProfile> = error("Not used by account setup")

        override suspend fun completeAccountSetup(
            command: AccountSetupCommand,
        ): RepositoryResult<UserProfile> {
            setupCommands += command
            setupResult?.let { return it }
            val updated = profile(
                username = command.username,
                bio = command.bio,
                age = command.age,
                countryCode = command.countryCode,
            )
            profiles.value = updated
            return RepositoryResult.Success(updated)
        }
    }
}
