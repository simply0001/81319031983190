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
import java.security.KeyPair
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
            val serviceData = result.scanRecord?.getServiceData(SERVICE_UUID) ?: return
            if (
                serviceData.size != SERVICE_DATA_BYTES ||
                serviceData[0].toInt() != NearbyWireProtocol.VERSION
            ) {
                return
            }
            val remoteNonce = ByteBuffer.wrap(serviceData, 1, Long.SIZE_BYTES).long
            if (
                remoteNonce == invitationNonce ||
                java.lang.Long.compareUnsigned(invitationNonce, remoteNonce) <= 0
            ) {
                return
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
                            advertisedPeerNonce = null,
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
            session.transportReady = true
            maybeProgress(session)
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

    private fun connectAsCentral(device: BluetoothDevice, remoteNonce: Long) {
        if (stopped || sessions.containsKey(device.address)) return
        val session = GattSession(
            device = device,
            role = Role.Central,
            advertisedPeerNonce = remoteNonce,
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
        if (session.credential != null) return true
        return when (val result = credentialPool.acquire(accountId)) {
            is RepositoryResult.Failure -> {
                failSession(
                    session,
                    result.error.message ?: "No anonymous encounter pass is available.",
                )
                false
            }

            is RepositoryResult.Success -> {
                if (!result.value.isUsableAt(Clock.System.now())) {
                    failSession(session, "The anonymous encounter pass expired.")
                    false
                } else {
                    session.credential = result.value
                    session.agreementKeyPair = NearbyCrypto.generateAgreementKeyPair()
                    session.localHello = NearbyWireProtocol.helloPacket(
                        NearbyHello(
                            invitationNonce = invitationNonce,
                            credentialToken = result.value.token,
                            signingPublicKey = result.value.signingPublicKey,
                            agreementPublicKey =
                                requireNotNull(session.agreementKeyPair).public.encoded,
                            challenge = NearbyCrypto.randomBytes(NearbyHello.CHALLENGE_BYTES),
                        ),
                    )
                    maybeProgress(session)
                    true
                }
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
            processPacket(session, packet)
        }
    }

    private fun processPacket(session: GattSession, packet: ByteArray) {
        try {
            when (NearbyWireProtocol.packetType(packet)) {
                PACKET_HELLO -> {
                    val hello = NearbyWireProtocol.decodeHelloPacket(packet)
                    if (
                        session.advertisedPeerNonce != null &&
                        hello.hello.invitationNonce != session.advertisedPeerNonce
                    ) {
                        throw IllegalArgumentException("Invitation nonce mismatch")
                    }
                    session.remoteHello = hello
                }

                PACKET_SIGNATURE -> {
                    val signature = NearbyWireProtocol.decodeSignature(packet)
                    val transcriptHash = requireNotNull(session.transcriptHash)
                    val remoteKey = NearbyCrypto.signingPublicKey(
                        requireNotNull(session.remoteHello).hello.signingPublicKey,
                    )
                    require(NearbyCrypto.verify(remoteKey, transcriptHash, signature))
                    session.remoteSignature = signature
                }

                PACKET_ENCRYPTED -> {
                    val remoteHello = requireNotNull(session.remoteHello).hello
                    val localHello = requireNotNull(session.localHello).hello
                    val transcriptHash = requireNotNull(session.transcriptHash)
                    val key = requireNotNull(session.sessionKey)
                    val plaintext = NearbyCrypto.decrypt(
                        key = key,
                        packet = NearbyWireProtocol.decodeEncrypted(packet),
                        aad = NearbyCrypto.confirmationAad(
                            transcriptHash,
                            remoteHello.invitationNonce,
                        ),
                    )
                    val confirmation = NearbyWireProtocol.decodeConfirmation(plaintext)
                    require(confirmation.ownToken.contentEquals(remoteHello.credentialToken))
                    require(confirmation.peerToken.contentEquals(localHello.credentialToken))
                    require(confirmation.transcriptHash.contentEquals(transcriptHash))
                    require(
                        (Clock.System.now() - confirmation.occurredAt).absoluteValue <=
                            MAX_CLOCK_SKEW,
                    )
                    session.remoteConfirmation = confirmation
                }

                else -> throw IllegalArgumentException("Unknown packet type")
            }
            maybeProgress(session)
        } catch (_: Throwable) {
            failSession(session, "The encrypted PocketPass handshake was rejected.")
        }
    }

    private fun maybeProgress(session: GattSession) {
        val localHelloPacket = session.localHello ?: return
        if (session.role == Role.Central && !session.transportReady) return
        val remoteHelloPacket = session.remoteHello

        if (!session.localHelloSent) {
            if (session.role == Role.Central || remoteHelloPacket != null) {
                session.localHelloSent = true
                enqueuePacket(session, localHelloPacket.bytes)
            } else {
                return
            }
        }
        if (remoteHelloPacket == null) return
        val localHello = localHelloPacket.hello
        val remoteHello = remoteHelloPacket.hello

        if (session.transcriptHash == null) {
            session.transcriptHash = NearbyCrypto.sha256(
                NearbyCrypto.transcript(localHelloPacket, remoteHelloPacket),
            )
        }
        val transcriptHash = requireNotNull(session.transcriptHash)
        if (session.localSignature == null) {
            val credential = requireNotNull(session.credential)
            session.localSignature = NearbyCrypto.sign(
                NearbyCrypto.signingPrivateKey(credential.signingPrivateKey),
                transcriptHash,
            )
            enqueuePacket(
                session,
                NearbyWireProtocol.encodeSignature(requireNotNull(session.localSignature)),
            )
        }
        if (session.remoteSignature == null) return

        if (session.sessionKey == null) {
            session.sessionKey = NearbyCrypto.deriveSessionKey(
                ownPrivateKey = requireNotNull(session.agreementKeyPair).private,
                peerPublicKey = NearbyCrypto.agreementPublicKey(
                    remoteHello.agreementPublicKey,
                ),
                transcriptHash = transcriptHash,
            )
        }
        if (!session.localConfirmationSent) {
            session.localConfirmationSent = true
            val confirmation = NearbyWireProtocol.encodeConfirmation(
                ownToken = localHello.credentialToken,
                peerToken = remoteHello.credentialToken,
                occurredAt = Clock.System.now(),
                transcriptHash = transcriptHash,
            )
            val encrypted = NearbyCrypto.encrypt(
                key = requireNotNull(session.sessionKey),
                plaintext = confirmation,
                aad = NearbyCrypto.confirmationAad(
                    transcriptHash,
                    localHello.invitationNonce,
                ),
            )
            enqueuePacket(session, NearbyWireProtocol.encodeEncrypted(encrypted))
        }
        if (session.remoteConfirmation != null && !session.proofEmitted) {
            session.proofEmitted = true
            val occurredAt = requireNotNull(session.remoteConfirmation).occurredAt
            val proof = NearbyEncounterProof(
                encounterId = UUID.randomUUID().toString(),
                ownToken = localHello.credentialToken,
                peerToken = remoteHello.credentialToken,
                ownSigningPublicKey = localHello.signingPublicKey,
                peerSigningPublicKey = remoteHello.signingPublicKey,
                ownTranscriptSignature = requireNotNull(session.localSignature),
                peerTranscriptSignature = requireNotNull(session.remoteSignature),
                transcriptHash = transcriptHash,
                occurredAt = occurredAt,
            )
            onProof(proof)
            onState(
                NearbyRuntimeStatus.Running,
                null,
                sessions.size,
                occurredAt,
            )
            session.sessionKey?.fill(0)
            closeSession(session.device.address)
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
        session.sessionKey?.fill(0)
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
        val advertisedPeerNonce: Long?,
        val reassembler: NearbyBleFraming.Reassembler = NearbyBleFraming.Reassembler(),
        val outbound: ArrayDeque<ByteArray> = ArrayDeque(),
        var gatt: BluetoothGatt? = null,
        var characteristic: BluetoothGattCharacteristic? = null,
        var mtu: Int = MINIMUM_MTU,
        var transportReady: Boolean = false,
        var sending: Boolean = false,
        var credential: NearbyCredential? = null,
        var agreementKeyPair: KeyPair? = null,
        var localHello: NearbyHelloPacket? = null,
        var remoteHello: NearbyHelloPacket? = null,
        var localHelloSent: Boolean = false,
        var transcriptHash: ByteArray? = null,
        var localSignature: ByteArray? = null,
        var remoteSignature: ByteArray? = null,
        var sessionKey: ByteArray? = null,
        var localConfirmationSent: Boolean = false,
        var remoteConfirmation: NearbyConfirmation? = null,
        var proofEmitted: Boolean = false,
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
        private const val PACKET_HELLO = 1
        private const val PACKET_SIGNATURE = 2
        private const val PACKET_ENCRYPTED = 4
        private const val CONNECTION_RETRY_WINDOW_MILLIS = 5 * 60 * 1_000L
        private val MAX_CLOCK_SKEW = 5.minutes
    }
}
