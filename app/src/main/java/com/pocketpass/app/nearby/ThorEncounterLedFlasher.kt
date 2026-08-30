package com.pocketpass.app.nearby

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

internal data class ThorLedState(
    val leftEnabled: Boolean,
    val rightEnabled: Boolean,
    val leftTopColor: Int,
    val leftBottomColor: Int,
    val rightTopColor: Int,
    val rightBottomColor: Int,
    val brightness: Float,
    val standardEnabledSettingValue: String?,
) {
    fun restoreCommand(): String = buildThorLedCommand(
        leftEnabled = leftEnabled,
        rightEnabled = rightEnabled,
        leftTopColor = scaleColor(leftTopColor, brightness),
        leftBottomColor = scaleColor(leftBottomColor, brightness),
        rightTopColor = scaleColor(rightTopColor, brightness),
        rightBottomColor = scaleColor(rightBottomColor, brightness),
    )

    companion object {
        fun fromSettings(
            standardEnabledValue: String?,
            standardColorsValue: String?,
            segmentEnabledValue: String?,
            segmentColorsValue: String?,
            brightnessValue: Float,
        ): ThorLedState {
            val standardEnabled = parseEnabledValues(standardEnabledValue, 2)
            val standardColors = parseColorValues(standardColorsValue, 2)
            val segmentEnabled = parseEnabledValues(segmentEnabledValue, 4)
            val segmentColors = parseColorValues(segmentColorsValue, 4)
            val usesSegments = segmentEnabled.any { it }
            return if (usesSegments) {
                ThorLedState(
                    leftEnabled = segmentEnabled[0] || segmentEnabled[2],
                    rightEnabled = segmentEnabled[1] || segmentEnabled[3],
                    leftTopColor = segmentColors[2].takeIf { segmentEnabled[2] } ?: BLACK,
                    leftBottomColor = segmentColors[0].takeIf { segmentEnabled[0] } ?: BLACK,
                    rightTopColor = segmentColors[3].takeIf { segmentEnabled[3] } ?: BLACK,
                    rightBottomColor = segmentColors[1].takeIf { segmentEnabled[1] } ?: BLACK,
                    brightness = brightnessValue.coerceIn(0f, 1f),
                    standardEnabledSettingValue = standardEnabledValue,
                )
            } else {
                ThorLedState(
                    leftEnabled = standardEnabled[0],
                    rightEnabled = standardEnabled[1],
                    leftTopColor = standardColors[0],
                    leftBottomColor = standardColors[0],
                    rightTopColor = standardColors[1],
                    rightBottomColor = standardColors[1],
                    brightness = brightnessValue.coerceIn(0f, 1f),
                    standardEnabledSettingValue = standardEnabledValue,
                )
            }
        }
    }
}

internal enum class ThorEncounterLedResult {
    Completed,
    Unsupported,
    ActivationFailed,
    RestorationFailed,
}

internal data class ThorLedRecoveryRecord(
    val standardEnabledSettingValue: String?,
)

internal interface ThorLedRecoveryStore {
    fun load(): ThorLedRecoveryRecord?
    fun save(record: ThorLedRecoveryRecord): Boolean
    fun clear(): Boolean
}

private class SharedPreferencesThorLedRecoveryStore(
    context: Context,
) : ThorLedRecoveryStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        RECOVERY_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): ThorLedRecoveryRecord? {
        if (!preferences.getBoolean(RECOVERY_PENDING, false)) return null
        val hasValue = preferences.getBoolean(RECOVERY_VALUE_PRESENT, false)
        return ThorLedRecoveryRecord(
            standardEnabledSettingValue = if (hasValue) {
                preferences.getString(RECOVERY_VALUE, null)
            } else {
                null
            },
        )
    }

    override fun save(record: ThorLedRecoveryRecord): Boolean {
        val editor = preferences.edit()
            .putBoolean(RECOVERY_PENDING, true)
            .putBoolean(
                RECOVERY_VALUE_PRESENT,
                record.standardEnabledSettingValue != null,
            )
        if (record.standardEnabledSettingValue == null) {
            editor.remove(RECOVERY_VALUE)
        } else {
            editor.putString(RECOVERY_VALUE, record.standardEnabledSettingValue)
        }
        return editor.commit()
    }

    override fun clear(): Boolean = preferences.edit()
        .remove(RECOVERY_PENDING)
        .remove(RECOVERY_VALUE_PRESENT)
        .remove(RECOVERY_VALUE)
        .commit()

    companion object {
        private const val RECOVERY_PREFERENCES = "thor_led_recovery"
        private const val RECOVERY_PENDING = "pending"
        private const val RECOVERY_VALUE_PRESENT = "value_present"
        private const val RECOVERY_VALUE = "value"
    }
}

internal fun buildThorEncounterPulseCommand(
    intensity: Float,
    enable: Boolean,
): String {
    val color = scaleColor(ENCOUNTER_GREEN, intensity)
    return buildThorLedColorCommand(
        leftTopColor = color,
        leftBottomColor = color,
        rightTopColor = color,
        rightBottomColor = color,
    ) + if (enable) {
        " && echo 1 > $LEFT_LED_PATH/enable && echo 1 > $RIGHT_LED_PATH/enable"
    } else {
        ""
    }
}

internal fun buildThorLedSettingCommand(value: String?): String =
    if (value == null) {
        "settings delete system $JOYSTICK_LIGHT_ENABLED"
    } else {
        "settings put system $JOYSTICK_LIGHT_ENABLED ${shellQuote(value)}"
    }

private fun shellQuote(value: String): String =
    "'${value.replace("'", "'\"'\"'")}'"

private fun buildThorLedCommand(
    leftEnabled: Boolean,
    rightEnabled: Boolean,
    leftTopColor: Int,
    leftBottomColor: Int,
    rightTopColor: Int,
    rightBottomColor: Int,
): String = buildThorLedColorCommand(
    leftTopColor = leftTopColor,
    leftBottomColor = leftBottomColor,
    rightTopColor = rightTopColor,
    rightBottomColor = rightBottomColor,
) + " && echo ${leftEnabled.toEnableValue()} > $LEFT_LED_PATH/enable" +
    " && echo ${rightEnabled.toEnableValue()} > $RIGHT_LED_PATH/enable"

private fun buildThorLedColorCommand(
    leftTopColor: Int,
    leftBottomColor: Int,
    rightTopColor: Int,
    rightBottomColor: Int,
): String = listOf(
    ledColorCommand(LEFT_LED_PATH, 1, leftTopColor),
    ledColorCommand(LEFT_LED_PATH, 2, leftBottomColor),
    ledColorCommand(RIGHT_LED_PATH, 1, rightTopColor),
    ledColorCommand(RIGHT_LED_PATH, 2, rightBottomColor),
).joinToString(" && ")

private fun ledColorCommand(path: String, segment: Int, color: Int): String =
    "echo $segment-${color.red()}:${color.green()}:${color.blue()} > $path/brightness"

private fun scaleColor(color: Int, brightness: Float): Int {
    val scale = brightness.coerceIn(0f, 1f)
    return ((color.red() * scale).toInt() shl 16) or
        ((color.green() * scale).toInt() shl 8) or
        (color.blue() * scale).toInt()
}

private fun parseEnabledValues(value: String?, count: Int): List<Boolean> {
    val parsed = value
        ?.split(',')
        ?.map { it.trim() == "1" }
        .orEmpty()
    return List(count) { index -> parsed.getOrElse(index) { false } }
}

private fun parseColorValues(value: String?, count: Int): List<Int> {
    val parsed = value
        ?.split(',')
        ?.map(::parseColor)
        .orEmpty()
    return List(count) { index -> parsed.getOrElse(index) { DEFAULT_THOR_COLOR } }
}

private fun parseColor(value: String): Int {
    val hex = value.trim().removePrefix("#")
    return hex.takeLast(6).toIntOrNull(16) ?: DEFAULT_THOR_COLOR
}

private fun Int.red(): Int = (this shr 16) and 0xff

private fun Int.green(): Int = (this shr 8) and 0xff

private fun Int.blue(): Int = this and 0xff

private fun Boolean.toEnableValue(): Int = if (this) 1 else 0

internal fun interface ThorLedCommandTransport {
    fun execute(command: String): Boolean
}

internal class PServerBinderThorLedTransport : ThorLedCommandTransport {
    @Volatile
    private var binder: IBinder? = null

    override fun execute(command: String): Boolean {
        val service = binder
            ?.takeIf(IBinder::isBinderAlive)
            ?: findBinder()?.also { binder = it }
            ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            service.transact(TRANSACTION_EXECUTE, data, reply, 0)
        } catch (error: Throwable) {
            binder = null
            Log.w(TAG, "AYN LED command failed", error)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @SuppressLint("PrivateApi")
    private fun findBinder(): IBinder? = try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        getService.invoke(null, PSERVER_BINDER_SERVICE) as? IBinder
    } catch (error: Throwable) {
        Log.w(TAG, "AYN LED service is unavailable", error)
        null
    }

    companion object {
        private const val TAG = "PocketPassThorLed"
        private const val TRANSACTION_EXECUTE = 0
        private const val PSERVER_BINDER_SERVICE = "PServerBinder"
    }
}

internal fun isAynThorDevice(): Boolean =
    Build.MANUFACTURER.equals("AYN", ignoreCase = true) &&
        Build.MODEL.equals("AYN Thor", ignoreCase = true)

internal class ThorEncounterLedFlasher internal constructor(
    private val baselineProvider: () -> ThorLedState,
    private val enabledSettingReader: () -> String?,
    private val recoveryStore: ThorLedRecoveryStore,
    private val transport: ThorLedCommandTransport,
    private val supported: Boolean,
    private val durationMillis: Long,
    private val pulseFrameCount: Int,
    private val activationTimeoutMillis: Long,
    private val observerSettleMillis: Long,
    private val monotonicMillis: () -> Long,
    private val sleeper: suspend (Long) -> Unit,
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        baselineProvider = { readThorLedState(context.contentResolver) },
        enabledSettingReader = {
            Settings.System.getString(
                context.contentResolver,
                JOYSTICK_LIGHT_ENABLED,
            )
        },
        recoveryStore = SharedPreferencesThorLedRecoveryStore(context),
        transport = PServerBinderThorLedTransport(),
        supported = isAynThorDevice(),
        durationMillis = PULSE_DURATION_MILLIS,
        pulseFrameCount = PULSE_FRAME_COUNT,
        activationTimeoutMillis = ACTIVATION_TIMEOUT_MILLIS,
        observerSettleMillis = OBSERVER_SETTLE_MILLIS,
        monotonicMillis = SystemClock::elapsedRealtime,
        sleeper = { delay(it) },
    )

    suspend fun recoverIfNeeded(): Boolean {
        if (!supported) return true
        return mutex.withLock {
            withContext(NonCancellable) {
                restorePendingOverride()
            }
        }
    }

    suspend fun flash(): ThorEncounterLedResult {
        if (!supported) return ThorEncounterLedResult.Unsupported
        return mutex.withLock {
            if (!restorePendingOverride()) {
                return@withLock ThorEncounterLedResult.RestorationFailed
            }
            val baseline = baselineProvider()
            var operationSucceeded = false
            var restorationSucceeded = false
            try {
                val activated = activateIfNeeded(baseline)
                operationSucceeded = activated && runPulse()
            } finally {
                restorationSucceeded = withContext(NonCancellable) {
                    restoreAfterPulse(baseline)
                }
            }
            when {
                !restorationSucceeded -> ThorEncounterLedResult.RestorationFailed
                operationSucceeded -> ThorEncounterLedResult.Completed
                else -> ThorEncounterLedResult.ActivationFailed
            }
        }
    }

    private suspend fun activateIfNeeded(baseline: ThorLedState): Boolean {
        if (baseline.leftEnabled && baseline.rightEnabled) return true
        val recoveryRecord = ThorLedRecoveryRecord(
            standardEnabledSettingValue = baseline.standardEnabledSettingValue,
        )
        if (!recoveryStore.save(recoveryRecord)) return false
        if (!execute(buildThorLedSettingCommand(ENABLED_BOTH_VALUE))) return false
        if (!awaitEnabledSetting(ENABLED_BOTH_VALUE)) return false
        if (observerSettleMillis > 0L) sleeper(observerSettleMillis)
        return true
    }

    private suspend fun runPulse(): Boolean {
        val started = execute(
            buildThorEncounterPulseCommand(
                intensity = encounterPulseIntensity(0),
                enable = true,
            ),
        )
        if (!started) return false
        logInfo("Encounter LED pulse started")
        val startedAt = monotonicMillis()
        for (frame in 1..pulseFrameCount) {
            val frameDeadline = startedAt +
                (durationMillis * frame / pulseFrameCount)
            val waitMillis = frameDeadline - monotonicMillis()
            if (waitMillis > 0L) sleeper(waitMillis)
            val updated = execute(
                buildThorEncounterPulseCommand(
                    intensity = encounterPulseIntensity(frame),
                    enable = false,
                ),
            )
            if (!updated) return false
        }
        return true
    }

    private suspend fun restoreAfterPulse(baseline: ThorLedState): Boolean {
        val restored = if (recoveryStore.load() != null) {
            restorePendingOverride()
        } else {
            execute(baseline.restoreCommand())
        }
        if (restored) {
            logInfo("Encounter LED state restored")
        } else {
            logWarning("Encounter LED state restoration failed")
        }
        return restored
    }

    private suspend fun restorePendingOverride(): Boolean {
        val recoveryRecord = recoveryStore.load() ?: return true
        val expectedValue = recoveryRecord.standardEnabledSettingValue
        if (!execute(buildThorLedSettingCommand(expectedValue))) return false
        if (!awaitEnabledSetting(expectedValue)) return false
        if (observerSettleMillis > 0L) sleeper(observerSettleMillis)
        val restoredState = baselineProvider()
        if (!execute(restoredState.restoreCommand())) return false
        return recoveryStore.clear()
    }

    private suspend fun awaitEnabledSetting(expectedValue: String?): Boolean {
        val deadline = monotonicMillis() + activationTimeoutMillis
        while (true) {
            if (enabledSettingReader() == expectedValue) return true
            val remaining = deadline - monotonicMillis()
            if (remaining <= 0L) return false
            sleeper(minOf(SETTING_POLL_MILLIS, remaining))
        }
    }

    private suspend fun execute(command: String): Boolean =
        withContext(Dispatchers.IO) {
            transport.execute(command)
        }

    private fun encounterPulseIntensity(frame: Int): Float {
        val progress = frame.coerceIn(0, pulseFrameCount).toDouble() /
            pulseFrameCount
        val wave = sin(PI * PULSE_COUNT * progress)
        return (0.5 + 0.5 * wave * wave).toFloat()
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarning(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    companion object {
        private const val TAG = "PocketPassThorLed"
        private const val PULSE_DURATION_MILLIS = 2_500L
        private const val PULSE_COUNT = 3
        private const val PULSE_FRAME_COUNT = 50
        private const val ACTIVATION_TIMEOUT_MILLIS = 750L
        private const val OBSERVER_SETTLE_MILLIS = 120L
        private const val SETTING_POLL_MILLIS = 25L
        private const val ENABLED_BOTH_VALUE = "1,1"
    }
}

private fun readThorLedState(contentResolver: ContentResolver): ThorLedState =
    ThorLedState.fromSettings(
        standardEnabledValue = Settings.System.getString(
            contentResolver,
            JOYSTICK_LIGHT_ENABLED,
        ),
        standardColorsValue = Settings.System.getString(
            contentResolver,
            JOYSTICK_LIGHT_COLORS,
        ),
        segmentEnabledValue = Settings.System.getString(
            contentResolver,
            JOYSTICK_SEGMENT_ENABLED,
        ),
        segmentColorsValue = Settings.System.getString(
            contentResolver,
            JOYSTICK_SEGMENT_COLORS,
        ),
        brightnessValue = Settings.System.getFloat(
            contentResolver,
            JOYSTICK_LIGHT_BRIGHTNESS,
            DEFAULT_THOR_BRIGHTNESS,
        ),
    )

private const val LEFT_LED_PATH = "/sys/class/sn3112l/led"
private const val RIGHT_LED_PATH = "/sys/class/sn3112r/led"
private const val JOYSTICK_LIGHT_ENABLED = "joystick_light_enabled"
private const val JOYSTICK_LIGHT_COLORS = "joystick_led_light_picker_color"
private const val JOYSTICK_SEGMENT_ENABLED = "joystick_handle_light_enabled"
private const val JOYSTICK_SEGMENT_COLORS = "joystick_handle_light_picker_color"
private const val JOYSTICK_LIGHT_BRIGHTNESS = "led_light_brightness_percent"
private const val DEFAULT_THOR_BRIGHTNESS = 0.5f
private const val DEFAULT_THOR_COLOR = 0x2BE0D8
private const val ENCOUNTER_GREEN = 0x00FF00
private const val BLACK = 0x000000
