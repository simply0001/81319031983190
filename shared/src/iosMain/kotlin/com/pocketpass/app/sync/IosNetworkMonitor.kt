@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.DISPATCH_QUEUE_PRIORITY_LOW
import platform.darwin.dispatch_get_global_queue

/**
 * NWPathMonitor feeding the realtime gate: availability plus a coarse
 * interface fingerprint standing in for Android's network handle, so a
 * wifi-to-cellular hop bumps the generation and rebuilds the channels.
 */
class IosNetworkMonitor {
    private val mutableState = MutableStateFlow(
        RealtimeNetworkState(available = true, networkHandle = null, generation = 0L),
    )
    val state: StateFlow<RealtimeNetworkState> = mutableState

    private val monitor = nw_path_monitor_create()
    private var started = false

    fun start() {
        if (started) return
        started = true
        nw_path_monitor_set_update_handler(monitor) { path ->
            val available = nw_path_get_status(path) == nw_path_status_satisfied
            var fingerprint = 0L
            if (nw_path_uses_interface_type(path, nw_interface_type_wifi)) fingerprint += 1L
            if (nw_path_uses_interface_type(path, nw_interface_type_cellular)) fingerprint += 2L
            if (nw_path_uses_interface_type(path, nw_interface_type_wired)) fingerprint += 4L
            mutableState.update { current ->
                if (current.available == available && current.networkHandle == fingerprint) {
                    current
                } else {
                    RealtimeNetworkState(
                        available = available,
                        networkHandle = fingerprint,
                        generation = current.generation + 1L,
                    )
                }
            }
        }
        nw_path_monitor_set_queue(
            monitor,
            dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW.toLong(), 0u),
        )
        nw_path_monitor_start(monitor)
    }
}
