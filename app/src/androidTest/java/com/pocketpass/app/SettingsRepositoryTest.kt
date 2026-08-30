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

        repository.setNearby(false)
        repository.setSoundLevel(0.8f)
        repository.setThemeMode(ThemeMode.Dark)
        val saved = repository.settings.first()

        assertFalse(saved.nearbyEnabled)
        assertEquals(0.8f, saved.soundLevel)
        assertEquals(ThemeMode.Dark, saved.themeMode)

        repository.resetSettings()
        val reset = repository.settings.first()
        assertTrue(reset.nearbyEnabled)
        assertEquals(0.45f, reset.soundLevel)
        assertEquals(ThemeMode.System, reset.themeMode)
    }
}
