package com.pocketpass.app

import androidx.test.core.app.ApplicationProvider
import com.pocketpass.app.data.DataStoreSettingsRepository
import com.pocketpass.app.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun preferencesPersistAndResetToFigmaDefaults() = runBlocking {
        val repository = DataStoreSettingsRepository(
            ApplicationProvider.getApplicationContext(),
        )

        repository.setGlobalLeaderboardLimit(75)
        repository.setNearby(false)
        repository.setSoundLevel(0.8f)
        repository.setThemeMode(ThemeMode.Dark)
        repository.setMessageAlertsEnabled(false)
        repository.setBoardsVisible(false)
        val saved = repository.settings.first()

        assertEquals(75, saved.globalLeaderboardLimit)
        assertEquals(75, DataStoreSettingsRepository(ApplicationProvider.getApplicationContext()).settings.first().globalLeaderboardLimit)
        assertFalse(saved.nearbyEnabled)
        assertEquals(0.8f, saved.soundLevel)
        assertEquals(ThemeMode.Dark, saved.themeMode)
        assertFalse(saved.messageAlertsEnabled)
        assertFalse(saved.boardsVisible)
        assertFalse(DataStoreSettingsRepository(ApplicationProvider.getApplicationContext()).settings.first().boardsVisible)

        repository.resetSettings()
        val reset = repository.settings.first()
        assertEquals(20, reset.globalLeaderboardLimit)
        assertTrue(reset.nearbyEnabled)
        assertEquals(0.45f, reset.soundLevel)
        assertEquals(ThemeMode.System, reset.themeMode)
        assertTrue(reset.messageAlertsEnabled)
        assertTrue(reset.boardsVisible)
    }
}
