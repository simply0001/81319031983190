package com.pocketpass.app.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationExemption {
    fun isGranted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )

    fun wasRequested(context: Context): Boolean =
        preferences(context).getBoolean(KEY_REQUESTED, false)

    fun markRequested(context: Context) {
        preferences(context).edit().putBoolean(KEY_REQUESTED, true).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "pocketpass_power"
    private const val KEY_REQUESTED = "battery_exemption_requested"
}
