package com.pocketpass.app.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/** What Android needs before the step counter can be read. */
object StepRewardsPermissionPolicy {
    /** The runtime permission the counter needs, or null on versions that grant it implicitly. */
    fun requiredPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACTIVITY_RECOGNITION
        } else {
            null
        }

    fun isGranted(context: Context): Boolean {
        val permission = requiredPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun supportsStepCounter(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}
