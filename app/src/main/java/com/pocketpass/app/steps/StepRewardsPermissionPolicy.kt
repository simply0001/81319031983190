package com.pocketpass.app.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

object StepRewardsPermissionPolicy {
    fun requiredPermission(): String = Manifest.permission.ACTIVITY_RECOGNITION

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) ==
            PackageManager.PERMISSION_GRANTED

    fun supportsStepCounter(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}
