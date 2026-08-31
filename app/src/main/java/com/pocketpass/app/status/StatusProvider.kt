package com.pocketpass.app.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.pocketpass.app.model.StatusInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(time: LocalTime): String = time.format(clockFormat)

private fun batteryIsCharging(status: Int): Boolean =
    status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

fun interface StatusProvider {
    fun status(context: Context): Flow<StatusInfo>
}

class AndroidStatusProvider : StatusProvider {
    override fun status(context: Context): Flow<StatusInfo> = callbackFlow {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        var batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        var networkCapabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)

        fun emitStatus() {
            val fallback = batteryManager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
                ?: 0
            val level = batteryIntent
                ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                ?: -1
            val scale = batteryIntent
                ?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                ?: -1
            val batteryStatus = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN,
            ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val wifiConnected = networkCapabilities
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                ?: false
            val wifiSignalLevel = StatusFormatter.wifiSignalLevel(
                connected = wifiConnected,
                signalStrength = networkCapabilities?.signalStrength ?: Int.MIN_VALUE,
            )
            trySend(
                StatusInfo(
                    time = formatTime(LocalTime.now()),
                    batteryPercent = StatusFormatter.batteryPercent(level, scale, fallback),
                    batteryCharging = batteryIsCharging(batteryStatus),
                    wifiConnected = wifiConnected,
                    wifiSignalLevel = wifiSignalLevel,
                ),
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    batteryIntent = intent
                }
                emitStatus()
            }
        }
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                emitStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                networkCapabilities = capabilities
                emitStatus()
            }

            override fun onLost(network: Network) {
                networkCapabilities = connectivityManager.activeNetwork
                    ?.let(connectivityManager::getNetworkCapabilities)
                emitStatus()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        emitStatus()
        awaitClose {
            context.unregisterReceiver(receiver)
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
}

class FixedStatusProvider(
    private val fixed: StatusInfo = StatusInfo(),
) : StatusProvider {
    override fun status(context: Context): Flow<StatusInfo> = flow { emit(fixed) }
}
