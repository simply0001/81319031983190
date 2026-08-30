package com.pocketpass.app.nearby

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object NearbyPermissionPolicy {
    fun foregroundPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun backgroundLocationPermission(): String? =
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else {
            null
        }

    fun missingBlePermissions(context: Context): List<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
        return required.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun isNotificationPermissionMissing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

    fun supportsBle(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            context.getSystemService(BluetoothManager::class.java)?.adapter != null

    fun isBluetoothEnabled(context: Context): Boolean {
        if (missingBlePermissions(context).any {
                it == Manifest.permission.BLUETOOTH_CONNECT
            }
        ) {
            return false
        }
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.isEnabled == true
        }.getOrDefault(false)
    }

    fun isLegacyLocationEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT > Build.VERSION_CODES.R ||
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
}
