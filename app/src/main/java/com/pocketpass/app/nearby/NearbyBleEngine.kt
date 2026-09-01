package com.pocketpass.app.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import java.nio.ByteBuffer
import kotlin.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
internal class NearbyBleEngine(
    context: Context,
    private val credentialPool: NearbyCredentialPool,
    private val accountId: UserId,
    private val onProof: (NearbyEncounterProof) -> Unit = {},
    private val onState: (
        NearbyRuntimeStatus,
        String?,
        Int,
        Instant?,
    ) -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager.adapter
    private val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter.bluetoothLeAdvertiser
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val invitationNonce = NearbyCrypto.randomNonce()
    private val sessions = ConcurrentHashMap<String, GattSession>()
    private val recentlyAttempted = ConcurrentHashMap<String, Long>()
    private val messageIds = AtomicInteger(1)
    private var gattServer: BluetoothGattServer? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null
    private var advertising = false
    private var scanning = false
    private var unfilteredScan = false
    private var stopped = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            Log.i(TAG, "BLE advertising started")
            publishState()
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            Log.w(TAG, "BLE advertising failed with code $errorCode")
            reportError("BLE advertising failed ($errorCode).")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val serviceData = record.getServiceData(SERVICE_UUID)
            val remoteNonce: Long?
            if (serviceData != null) {
                if (
                    serviceData.size != SERVICE_DATA_BYTES ||
                    serviceData[0].toInt() != NearbyWireProtocol.VERSION
                ) {
                    return
                }
                val nonce = ByteBuffer.wrap(serviceData, 1, Long.SIZE_BYTES).long
                if (
                    nonce == invitationNonce ||
                    java.lang.Long.compareUnsigned(invitationNonce, nonce) <= 0
                ) {
                    return
                }
                remoteNonce = nonce
            } else {
                // iOS cannot put service data in its advertisements, so a bare
                // PocketPass service UUID is an iPhone; this side initiates.
                if (record.serviceUuids?.contains(SERVICE_UUID) != true) return
                remoteNonce = null
            }
            val address = result.device.address
            val now = System.currentTimeMillis()
            val previous = recentlyAttempted.put(address, now)
            if (previous != null && now - previous < CONNECTION_RETRY_WINDOW_MILLIS) return
            connectAsCentral(result.device, remoteNonce)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            if (!unfilteredScan && !stopped) {
                unfilteredScan = true
                Log.w(TAG, "Filtered BLE scan failed; retrying with process filtering")
                startScanning()
                return
            }
            reportError("BLE scanning failed ($errorCode).")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS && service?.uuid == GATT_SERVICE_UUID) {
                startAdvertising()
            } else {
                reportError("PocketPass BLE service could not be published.")
            }
        }

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val session = sessions.computeIfAbsent(device.address) {
                        GattSession(
                            device = device,
                            role = Role.Peripheral,
                            machine = NearbyHandshakeSession(
                                role = NearbyLinkRole.Peripheral,
                                invitationNonce = invitationNonce,
                                advertisedPeerNonce = null,
                            ),
                        )
                    }
                    scope.launch {
                        prepareCredential(session)
                    }
                    publishState()
                }

                BluetoothProfile.STATE_DISCONNECTED ->
                    closeSession(device.address)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            sessions[device.address]?.mtu = mtu.coerceAtLeast(MINIMUM_MTU)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid != CLIENT_CONFIGURATION_UUID || offset != 0) {
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_INVALID_OFFSET,
                        offset,
                        null,
                    )
                }
                return
            }
            @Suppress("DEPRECATION")
            descriptor.value = value
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value,
                )
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val session = sessions[device.address]
            val accepted = session != null &&
                characteristic.uuid == TRANSFER_CHARACTERISTIC_UUID &&
                !preparedWrite &&
                offset == 0
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    if (accepted) {
                        BluetoothGatt.GATT_SUCCESS
                    } else {
                        BluetoothGatt.GATT_FAILURE
                    },
                    offset,
                    null,
                )
            }
            if (accepted) acceptFragment(requireNotNull(session), value)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            val session = sessions[device.address] ?: return
            synchronized(session) {
                session.sending = false
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendNextFragment(session)
            } else {
                failSession(session, "Encrypted response could not be delivered.")
            }
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            val session = sessions[gatt.device.address] ?: return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                session.gatt = gatt
                if (!gatt.requestMtu(PREFERRED_MTU)) gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeSession(gatt.device.address)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            sessions[gatt.device.address]?.mtu = if (status == BluetoothGatt.GATT_SUCCESS) {
                mtu.coerceAtLeast(MINIMUM_MTU)
            } else {
                MINIMUM_MTU
            }
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val session = sessions[gatt.device.address] ?: return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failSession(session, "PocketPass BLE service discovery failed.")
                return
            }
            val characteristic = gatt
                .getService(GATT_SERVICE_UUID)
                ?.getCharacteristic(TRANSFER_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                failSession(
                    session,
                    "The nearby device is not running PocketPass protocol " +
                        "v${NearbyWireProtocol.VERSION}.",
                )
                return
            }
            session.characteristic = characteristic
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                failSession(session, "Encrypted response notifications are unavailable.")
                return
            }
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION_UUID)
            if (descriptor == null || !writeDescriptor(gatt, descriptor)) {
                failSession(session, "Encrypted response notifications could not be enabled.")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val session = sessions[gatt.device.address] ?: return
            if (
                descriptor.uuid != CLIENT_CONFIGURATION_UUID ||
                status != BluetoothGatt.GATT_SUCCESS
            ) {
                failSession(session, "Encrypted response notifications could not be enabled.")
                return
            }
            dispatchEvents(session, session.machine.onTransportReady())
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicChanged(gatt, characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val session = sessions[gatt.device.address] ?: return
            synchronized(session) {
                session.sending = false
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendNextFragment(session)
            } else {
                failSession(session, "Encrypted request could not be delivered.")
            }
        }
    }

    fun start() {
        if (scanner == null || advertiser == null) {
            Log.w(TAG, "BLE scanner or advertiser is unavailable")
            onState(
                NearbyRuntimeStatus.Unsupported,
                "This device cannot both scan and advertise over BLE.",
                0,
                null,
            )
            return
        }
        val server = manager.openGattServer(appContext, serverCallback)
        if (server == null) {
            Log.w(TAG, "GATT server could not be opened")
            reportError("PocketPass could not open its BLE exchange service.")
            return
        }
        gattServer = server
        val characteristic = BluetoothGattCharacteristic(
            TRANSFER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(
            BluetoothGattDescriptor(
                CLIENT_CONFIGURATION_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        transferCharacteristic = characteristic
        val service = BluetoothGattService(
            GATT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        ).apply {
            addCharacteristic(characteristic)
        }
        if (!server.addService(service)) {
            reportError("PocketPass could not register its BLE exchange service.")
            return
        }
        startScanning()
    }

    fun stop() {
        stopped = true
        if (advertising) advertiser?.stopAdvertising(advertiseCallback)
        if (scanning) scanner?.stopScan(scanCallback)
        sessions.keys.toList().forEach(::closeSession)
        gattServer?.clearServices()
        gattServer?.close()
        gattServer = null
        transferCharacteristic = null
        advertising = false
        scanning = false
        recentlyAttempted.clear()
        scope.cancel()
    }

    private fun connectAsCentral(device: BluetoothDevice, remoteNonce: Long?) {
        if (stopped || sessions.containsKey(device.address)) return
        val session = GattSession(
            device = device,
            role = Role.Central,
            machine = NearbyHandshakeSession(
                role = NearbyLinkRole.Central,
                invitationNonce = invitationNonce,
                advertisedPeerNonce = remoteNonce,
            ),
        )
        sessions[device.address] = session
        publishState()
        scope.launch {
            if (!prepareCredential(session)) return@launch
            if (stopped || sessions[device.address] !== session) return@launch
            session.gatt = device.connectGatt(
                appContext,
                false,
                clientCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        }
    }

    private suspend fun prepareCredential(session: GattSession): Boolean {
        if (session.credentialAttached) return true
        return when (val result = credentialPool.acquire(accountId)) {
            is RepositoryResult.Failure -> {
                failSession(
                    session,
                    result.error.message ?: "No anonymous encounter pass is available.",
                )
                false
            }

            is RepositoryResult.Success -> {
                session.credentialAttached = true
                val events = session.machine.attachCredential(result.value)
                dispatchEvents(session, events)
                events.none { it is NearbyHandshakeSession.Event.Failed }
            }
        }
    }

    private fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (characteristic.uuid != TRANSFER_CHARACTERISTIC_UUID) return
        sessions[gatt.device.address]?.let { acceptFragment(it, value) }
    }

    private fun acceptFragment(session: GattSession, fragment: ByteArray) {
        val packet = runCatching { session.reassembler.accept(fragment) }
            .getOrElse {
                failSession(session, "The nearby device sent an invalid encrypted frame.")
                return
            }
            ?: return
        scope.launch {
            dispatchEvents(session, session.machine.onPacket(packet))
        }
    }

    private fun dispatchEvents(
        session: GattSession,
        events: List<NearbyHandshakeSession.Event>,
    ) {
        events.forEach { event ->
            when (event) {
                is NearbyHandshakeSession.Event.SendPacket ->
                    enqueuePacket(session, event.packet)

                is NearbyHandshakeSession.Event.Failed ->
                    failSession(session, event.message)

                is NearbyHandshakeSession.Event.ProofReady -> {
                    onProof(event.proof)
                    onState(
                        NearbyRuntimeStatus.Running,
                        null,
                        sessions.size,
                        event.proof.occurredAt,
                    )
                    closeSession(session.device.address)
                }
            }
        }
    }

    private fun enqueuePacket(session: GattSession, packet: ByteArray) {
        val fragments = NearbyBleFraming.fragment(
            messageId = messageIds.getAndUpdate { current ->
                if (current >= 0xFFFF) 1 else current + 1
            },
            packet = packet,
            attPayloadBytes = (session.mtu - ATT_PROTOCOL_OVERHEAD)
                .coerceAtLeast(NearbyBleFraming.DEFAULT_ATT_PAYLOAD_BYTES),
        )
        synchronized(session) {
            session.outbound.addAll(fragments)
        }
        sendNextFragment(session)
    }

    private fun sendNextFragment(session: GattSession) {
        val fragment = synchronized(session) {
            if (session.sending) return
            val next = session.outbound.pollFirst() ?: return
            session.sending = true
            next
        }
        val started = when (session.role) {
            Role.Central -> {
                val gatt = session.gatt
                val characteristic = session.characteristic
                if (gatt == null || characteristic == null) {
                    false
                } else {
                    writeCharacteristic(gatt, characteristic, fragment)
                }
            }

            Role.Peripheral -> {
                val server = gattServer
                val characteristic = transferCharacteristic
                if (server == null || characteristic == null) {
                    false
                } else {
                    notifyCharacteristic(server, session.device, characteristic, fragment)
                }
            }
        }
        if (!started) {
            synchronized(session) {
                session.sending = false
            }
            failSession(session, "The encrypted BLE transfer could not start.")
        }
    }

    private fun startAdvertising() {
        val bleAdvertiser = advertiser ?: return
        val serviceData = ByteBuffer.allocate(SERVICE_DATA_BYTES)
            .put(NearbyWireProtocol.VERSION.toByte())
            .putLong(invitationNonce)
            .array()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceData(SERVICE_UUID, serviceData)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startScanning() {
        val bleScanner = scanner ?: return
        val filters = if (unfilteredScan) {
            emptyList()
        } else {
            listOf(
                ScanFilter.Builder()
                    .setServiceData(
                        SERVICE_UUID,
                        byteArrayOf(NearbyWireProtocol.VERSION.toByte()),
                        byteArrayOf(0xFF.toByte()),
                    )
                    .build(),
                // iOS peers advertise the service UUID with no service data.
                ScanFilter.Builder()
                    .setServiceUuid(SERVICE_UUID)
                    .build(),
            )
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        bleScanner.startScan(filters, settings, scanCallback)
        scanning = true
        Log.i(
            TAG,
            if (unfilteredScan) {
                "BLE scan started with process filtering"
            } else {
                "BLE scan started with service-data filtering"
            },
        )
        publishState()
    }

    private fun closeSession(address: String) {
        val session = sessions.remove(address) ?: return
        session.machine.close()
        session.gatt?.runCatching {
            disconnect()
            close()
        }
        if (session.role == Role.Peripheral) {
            runCatching { gattServer?.cancelConnection(session.device) }
        }
        publishState()
    }

    private fun failSession(session: GattSession, message: String) {
        closeSession(session.device.address)
        onState(
            if (advertising && scanning) {
                NearbyRuntimeStatus.Running
            } else {
                NearbyRuntimeStatus.Error
            },
            message,
            sessions.size,
            null,
        )
    }

    private fun publishState() {
        if (advertising && scanning) {
            onState(NearbyRuntimeStatus.Running, null, sessions.size, null)
        }
    }

    private fun reportError(message: String) {
        onState(NearbyRuntimeStatus.Error, message, sessions.size, null)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(
            descriptor,
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(
            characteristic,
            value,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun notifyCharacteristic(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        server.notifyCharacteristicChanged(
            device,
            characteristic,
            true,
            value,
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.value = value
        server.notifyCharacteristicChanged(device, characteristic, true)
    }

    private data class GattSession(
        val device: BluetoothDevice,
        val role: Role,
        val machine: NearbyHandshakeSession,
        val reassembler: NearbyBleFraming.Reassembler = NearbyBleFraming.Reassembler(),
        val outbound: ArrayDeque<ByteArray> = ArrayDeque(),
        var gatt: BluetoothGatt? = null,
        var characteristic: BluetoothGattCharacteristic? = null,
        var mtu: Int = MINIMUM_MTU,
        var sending: Boolean = false,
        var credentialAttached: Boolean = false,
    )

    private enum class Role {
        Central,
        Peripheral,
    }

    companion object {
        private const val TAG = "PocketPassNearby"
        val SERVICE_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("9b40e2f8-7543-4e62-9b4d-8672d18514c7"),
        )
        private val GATT_SERVICE_UUID: UUID = SERVICE_UUID.uuid
        private val TRANSFER_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("9b40e2f9-7543-4e62-9b4d-8672d18514c7")
        private val CLIENT_CONFIGURATION_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SERVICE_DATA_BYTES = 1 + Long.SIZE_BYTES
        private const val MINIMUM_MTU = 23
        private const val PREFERRED_MTU = 247
        private const val ATT_PROTOCOL_OVERHEAD = 3
        private const val CONNECTION_RETRY_WINDOW_MILLIS = 5 * 60 * 1_000L
    }
}
