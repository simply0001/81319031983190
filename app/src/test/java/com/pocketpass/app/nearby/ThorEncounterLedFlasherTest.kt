package com.pocketpass.app.nearby

import java.util.Collections
import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ThorEncounterLedFlasherTest {
    @Test
    fun encounterPulseSetsEveryRingSegmentGreenAndEnablesBothSides() {
        val command = buildThorEncounterPulseCommand(intensity = 1f, enable = true)

        assertTrue(command.contains("echo 1-0:255:0 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 2-0:255:0 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 1-0:255:0 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 2-0:255:0 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 1 > /sys/class/sn3112l/led/enable"))
        assertTrue(command.contains("echo 1 > /sys/class/sn3112r/led/enable"))
    }

    @Test
    fun encounterPulseScalesGreenWithoutRepeatedlyWritingEnableNodes() {
        val command = buildThorEncounterPulseCommand(intensity = 0.5f, enable = false)

        assertTrue(command.contains("echo 1-0:127:0 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 2-0:127:0 > /sys/class/sn3112r/led/brightness"))
        assertTrue(!command.contains("/enable"))
    }

    @Test
    fun standardRingSettingsRestoreTheirColorsBrightnessAndEnabledState() {
        val state = ThorLedState.fromSettings(
            standardEnabledValue = "1,0",
            standardColorsValue = "#804020,#204080",
            segmentEnabledValue = "0,0,0,0",
            segmentColorsValue = null,
            brightnessValue = 0.5f,
        )

        val command = state.restoreCommand()

        assertTrue(command.contains("echo 1-64:32:16 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 2-64:32:16 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 1-16:32:64 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 2-16:32:64 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 1 > /sys/class/sn3112l/led/enable"))
        assertTrue(command.contains("echo 0 > /sys/class/sn3112r/led/enable"))
    }

    @Test
    fun segmentedSettingsRestoreOnlyTheirEnabledSections() {
        val state = ThorLedState.fromSettings(
            standardEnabledValue = "1,1",
            standardColorsValue = "#FFFFFF,#FFFFFF",
            segmentEnabledValue = "1,0,0,1",
            segmentColorsValue = "#FF0000,#00FF00,#0000FF,#FFFF00",
            brightnessValue = 1f,
        )

        val command = state.restoreCommand()

        assertTrue(command.contains("echo 1-0:0:0 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 2-255:0:0 > /sys/class/sn3112l/led/brightness"))
        assertTrue(command.contains("echo 1-255:255:0 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 2-0:0:0 > /sys/class/sn3112r/led/brightness"))
        assertTrue(command.contains("echo 1 > /sys/class/sn3112l/led/enable"))
        assertTrue(command.contains("echo 1 > /sys/class/sn3112r/led/enable"))
    }

    @Test
    fun disabledRingsAreTemporarilyActivatedAndRestored() = runTest {
        val environment = FakeThorEnvironment("0,0")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        val result = flasher.flash()

        assertEquals(ThorEncounterLedResult.Completed, result)
        assertEquals("0,0", environment.setting)
        assertNull(recoveryStore.load())
        assertTrue(
            transport.commands.contains(
                "settings put system joystick_light_enabled '1,1'",
            ),
        )
        assertTrue(
            transport.commands.contains(
                "settings put system joystick_light_enabled '0,0'",
            ),
        )
    }

    @Test
    fun asymmetricRingStateIsRestoredExactly() = runTest {
        val environment = FakeThorEnvironment("1,0")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        assertEquals(ThorEncounterLedResult.Completed, flasher.flash())
        assertEquals("1,0", environment.setting)
        assertNull(recoveryStore.load())
    }

    @Test
    fun alreadyEnabledRingsDoNotChangeTheAuthoritativeSetting() = runTest {
        val environment = FakeThorEnvironment("1,1")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        assertEquals(ThorEncounterLedResult.Completed, flasher.flash())

        assertFalse(
            transport.commands.any {
                it.startsWith("settings put system joystick_light_enabled")
            },
        )
    }

    @Test
    fun unconfirmedActivationReturnsActivationFailedWithoutAFalseSuccess() = runTest {
        val environment = FakeThorEnvironment("0,0")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(
            environment = environment,
            applyActivationSetting = false,
        )
        val flasher = createFlasher(environment, recoveryStore, transport)

        val result = flasher.flash()

        assertEquals(ThorEncounterLedResult.ActivationFailed, result)
        assertEquals("0,0", environment.setting)
        assertFalse(
            transport.commands.any {
                it.contains("0:127:0") || it.contains("0:255:0")
            },
        )
    }

    @Test
    fun cancellationStillRestoresTheHardwareBaseline() = runTest {
        val environment = FakeThorEnvironment("1,1")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(environment)
        val flasher = createFlasher(
            environment = environment,
            recoveryStore = recoveryStore,
            transport = transport,
            sleeper = { throw CancellationException("cancelled") },
        )

        try {
            flasher.flash()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            Unit
        }

        assertTrue(
            transport.commands.last().contains(
                "echo 1-255:0:0 > /sys/class/sn3112l/led/brightness",
            ),
        )
    }

    @Test
    fun pendingOverrideIsRecoveredBeforeAnotherPulse() = runTest {
        val environment = FakeThorEnvironment("1,1")
        val recoveryStore = FakeRecoveryStore().apply {
            save(ThorLedRecoveryRecord("0,0"))
        }
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        assertTrue(flasher.recoverIfNeeded())
        assertEquals("0,0", environment.setting)
        assertNull(recoveryStore.load())
    }

    @Test
    fun restorationFailureIsReportedAndRetainedForRecovery() = runTest {
        val environment = FakeThorEnvironment("0,0")
        val recoveryStore = FakeRecoveryStore(clearSucceeds = false)
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        val result = flasher.flash()

        assertEquals(ThorEncounterLedResult.RestorationFailed, result)
        assertTrue(recoveryStore.load() != null)
    }

    @Test
    fun overlappingPulsesRemainSerialized() = runTest {
        val environment = FakeThorEnvironment("1,1")
        val recoveryStore = FakeRecoveryStore()
        val transport = FakeTransport(environment)
        val flasher = createFlasher(environment, recoveryStore, transport)

        listOf(
            async { flasher.flash() },
            async { flasher.flash() },
        ).awaitAll()

        val pulsePositions = transport.commands.indices.filter { index ->
            transport.commands[index].contains("0:127:0") ||
                transport.commands[index].contains("0:255:0")
        }
        val restorePositions = transport.commands.indices.filter { index ->
            transport.commands[index].contains(
                "echo 1-255:0:0 > /sys/class/sn3112l/led/brightness",
            )
        }

        assertEquals(2, restorePositions.size)
        assertTrue(pulsePositions.any { it < restorePositions[0] })
        assertTrue(
            pulsePositions.any {
                it > restorePositions[0] && it < restorePositions[1]
            },
        )
    }

    @Test
    fun absentAuthoritativeSettingUsesDeleteDuringRestoration() {
        assertEquals(
            "settings delete system joystick_light_enabled",
            buildThorLedSettingCommand(null),
        )
    }

    private fun createFlasher(
        environment: FakeThorEnvironment,
        recoveryStore: FakeRecoveryStore,
        transport: FakeTransport,
        sleeper: suspend (Long) -> Unit = { duration ->
            environment.now += duration
            yield()
        },
    ): ThorEncounterLedFlasher = ThorEncounterLedFlasher(
        baselineProvider = environment::state,
        enabledSettingReader = { environment.setting },
        recoveryStore = recoveryStore,
        transport = transport,
        supported = true,
        durationMillis = 10L,
        pulseFrameCount = 2,
        activationTimeoutMillis = 100L,
        observerSettleMillis = 1L,
        monotonicMillis = { environment.now },
        sleeper = sleeper,
    )

    private class FakeThorEnvironment(
        @Volatile var setting: String?,
    ) {
        @Volatile
        var now: Long = 0L

        fun state(): ThorLedState = ThorLedState.fromSettings(
            standardEnabledValue = setting,
            standardColorsValue = "#FF0000,#0000FF",
            segmentEnabledValue = "0,0,0,0",
            segmentColorsValue = null,
            brightnessValue = 1f,
        )
    }

    private class FakeRecoveryStore(
        private val clearSucceeds: Boolean = true,
    ) : ThorLedRecoveryStore {
        private var record: ThorLedRecoveryRecord? = null

        override fun load(): ThorLedRecoveryRecord? = record

        override fun save(record: ThorLedRecoveryRecord): Boolean {
            this.record = record
            return true
        }

        override fun clear(): Boolean {
            if (clearSucceeds) record = null
            return clearSucceeds
        }
    }

    private class FakeTransport(
        private val environment: FakeThorEnvironment,
        private val applyActivationSetting: Boolean = true,
    ) : ThorLedCommandTransport {
        val commands: MutableList<String> =
            Collections.synchronizedList(mutableListOf())

        override fun execute(command: String): Boolean {
            commands += command
            when (command) {
                "settings put system joystick_light_enabled '1,1'" -> {
                    if (applyActivationSetting) environment.setting = "1,1"
                }

                "settings put system joystick_light_enabled '1,0'" -> {
                    environment.setting = "1,0"
                }

                "settings put system joystick_light_enabled '0,0'" -> {
                    environment.setting = "0,0"
                }

                "settings delete system joystick_light_enabled" -> {
                    environment.setting = null
                }
            }
            return true
        }
    }
}
