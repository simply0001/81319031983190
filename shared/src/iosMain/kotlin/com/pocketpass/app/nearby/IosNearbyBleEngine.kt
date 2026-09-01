@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.nearby

import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.logPlatformInfo
import com.pocketpass.app.logPlatformWarning
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataOverflowServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

/**
 * CoreBluetooth transport for the shared street-pass handshake. The exchange
 * itself is identical to Android's; the differences are all in discovery:
 * iOS may only advertise the bare service UUID (no nonce), so this side reads
 * Android's nonce-carrying service data when scanning, treats a bare UUID as
 * another iPhone, and Android always initiates toward bare advertisements.
 * One serial dispatch queue carries every CoreBluetooth callback, which is
 * what makes the per-link machine calls safe.
 */
class IosNearbyBleEngine(
    private val credentialPool: NearbyCredentialPool,
    private val accountId: UserId,
    private val scope: CoroutineScope,
    private val onProof: (NearbyEncounterProof) -> Unit,
    private val onState: (NearbyRuntimeStatus, String?, Int, Instant?) -> Unit,
) {
    private val invitationNonce = NearbyCrypto.randomNonce()
    private val queue = dispatch_queue_create("xyz.pocketpass.nearby", null)
    private val links = mutableMapOf<String, Link>()
    private val heldPeripherals = mutableMapOf<String, CBPeripheral>()
    private val recentlyAttempted = mutableMapOf<String, Long>()
    private var messageId = 1
    private var centralManager: CBCentralManager? = null
    private var peripheralManager: CBPeripheralManager? = null
    private var transferCharacteristic: CBMutableCharacteristic? = null
    private var scanning = false
    private var advertising = false
    private var stopped = false

    private val serviceUuid = CBUUID.UUIDWithString(SERVICE_UUID_STRING)
    private val characteristicUuid = CBUUID.UUIDWithString(TRANSFER_CHARACTERISTIC_UUID_STRING)

    fun start() {
        centralManager = CBCentralManager(centralDelegate, queue)
        peripheralManager = CBPeripheralManager(peripheralManagerDelegate, queue)
    }

    fun stop() {
        stopped = true
        centralManager?.let { manager ->
            if (scanning) manager.stopScan()
            heldPeripherals.values.forEach { manager.cancelPeripheralConnection(it) }
        }
        peripheralManager?.let { manager ->
            if (advertising) manager.stopAdvertising()
            manager.removeAllServices()
        }
        links.values.forEach { it.machine.close() }
        links.clear()
        heldPeripherals.clear()
        recentlyAttempted.clear()
        scanning = false
        advertising = false
        centralManager = null
        peripheralManager = null
        transferCharacteristic = null
    }

    private class Link(
        val key: String,
        val role: NearbyLinkRole,
        val machine: NearbyHandshakeSession,
        val reassembler: NearbyBleFraming.Reassembler = NearbyBleFraming.Reassembler(),
        val outbound: ArrayDeque<ByteArray> = ArrayDeque(),
        var sending: Boolean = false,
        var credentialAttached: Boolean = false,
        var peripheral: CBPeripheral? = null,
        var characteristic: CBCharacteristic? = null,
        var subscribedCentral: CBCentral? = null,
        var attPayloadBytes: Int = NearbyBleFraming.DEFAULT_ATT_PAYLOAD_BYTES,
    )

    // ---- Central: find Android peers by nonce, iPhone peers by bare UUID ----

    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            when (central.state) {
                CBManagerStatePoweredOn -> startScanning(central)
                CBManagerStateUnauthorized ->
                    onState(NearbyRuntimeStatus.Error, "Bluetooth access is not allowed.", 0, null)

                CBManagerStateUnsupported ->
                    onState(NearbyRuntimeStatus.Unsupported, "This device has no BLE radio.", 0, null)

                else -> {
                    scanning = false
                    publishState()
                }
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            if (stopped) return
            val remoteNonce = advertisedNonce(advertisementData) ?: run {
                if (!advertisesBareService(advertisementData)) return
                null
            }
            if (remoteNonce != null) {
                if (
                    remoteNonce == invitationNonce ||
                    invitationNonce.toULong() <= remoteNonce.toULong()
                ) {
                    return
                }
            }
            val key = didDiscoverPeripheral.identifier.UUIDString
            if (links.containsKey(key)) return
            val now = tickMillis()
            val previous = recentlyAttempted.put(key, now)
            if (previous != null && now - previous < CONNECTION_RETRY_WINDOW_MILLIS) return
            val link = Link(
                key = key,
                role = NearbyLinkRole.Central,
                machine = NearbyHandshakeSession(
                    role = NearbyLinkRole.Central,
                    invitationNonce = invitationNonce,
                    advertisedPeerNonce = remoteNonce,
                ),
            )
            link.peripheral = didDiscoverPeripheral
            links[key] = link
            heldPeripherals[key] = didDiscoverPeripheral
            didDiscoverPeripheral.delegate = peripheralDelegate
            central.connectPeripheral(didDiscoverPeripheral, null)
            publishState()
        }

        override fun centralManager(
            central: CBCentralManager,
            didConnectPeripheral: CBPeripheral,
        ) {
            val link = links[didConnectPeripheral.identifier.UUIDString] ?: return
            attachCredentialAsync(link)
            didConnectPeripheral.discoverServices(listOf(serviceUuid))
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            closeLink(didFailToConnectPeripheral.identifier.UUIDString)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            closeLink(didDisconnectPeripheral.identifier.UUIDString)
        }
    }

    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverServices: NSError?,
        ) {
            val link = links[peripheral.identifier.UUIDString] ?: return
            if (didDiscoverServices != null) {
                failLink(link, "PocketPass BLE service discovery failed.")
                return
            }
            val service = peripheral.services
                ?.filterIsInstance<CBService>()
                ?.firstOrNull { it.UUID == serviceUuid }
            if (service == null) {
                failLink(
                    link,
                    "The nearby device is not running PocketPass protocol " +
                        "v${NearbyWireProtocol.VERSION}.",
                )
                return
            }
            peripheral.discoverCharacteristics(listOf(characteristicUuid), service)
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            val link = links[peripheral.identifier.UUIDString] ?: return
            val characteristic = didDiscoverCharacteristicsForService.characteristics
                ?.filterIsInstance<CBCharacteristic>()
                ?.firstOrNull { it.UUID == characteristicUuid }
            if (error != null || characteristic == null) {
                failLink(
                    link,
                    "The nearby device is not running PocketPass protocol " +
                        "v${NearbyWireProtocol.VERSION}.",
                )
                return
            }
            link.characteristic = characteristic
            peripheral.setNotifyValue(true, characteristic)
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateNotificationStateForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            val link = links[peripheral.identifier.UUIDString] ?: return
            if (error != null || !didUpdateNotificationStateForCharacteristic.isNotifying()) {
                failLink(link, "Encrypted response notifications could not be enabled.")
                return
            }
            link.attPayloadBytes = peripheral
                .maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
                .toInt()
                .coerceAtLeast(NearbyBleFraming.DEFAULT_ATT_PAYLOAD_BYTES)
            dispatchEvents(link, link.machine.onTransportReady())
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            val link = links[peripheral.identifier.UUIDString] ?: return
            if (error != null) {
                failLink(link, "The encrypted BLE transfer broke down.")
                return
            }
            val value = didUpdateValueForCharacteristic.value ?: return
            acceptFragment(link, value.toByteArray())
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didWriteValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            val link = links[peripheral.identifier.UUIDString] ?: return
            link.sending = false
            if (error != null) {
                failLink(link, "Encrypted request could not be delivered.")
                return
            }
            sendNextFragment(link)
        }
    }

    // ---- Peripheral: advertise the bare UUID, serve the transfer characteristic ----

    private val peripheralManagerDelegate = object :
        NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            if (peripheral.state != CBManagerStatePoweredOn) {
                advertising = false
                return
            }
            val characteristic = CBMutableCharacteristic(
                type = characteristicUuid,
                properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyIndicate,
                value = null,
                permissions = CBAttributePermissionsWriteable,
            )
            transferCharacteristic = characteristic
            val service = CBMutableService(type = serviceUuid, primary = true)
            service.setCharacteristics(listOf(characteristic))
            peripheral.addService(service)
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didAddService: CBService,
            error: NSError?,
        ) {
            if (error != null) {
                onState(
                    NearbyRuntimeStatus.Error,
                    "PocketPass could not register its BLE exchange service.",
                    links.size,
                    null,
                )
                return
            }
            peripheral.startAdvertising(
                mapOf<Any?, Any>(
                    CBAdvertisementDataServiceUUIDsKey to listOf(serviceUuid),
                ),
            )
        }

        override fun peripheralManagerDidStartAdvertising(
            peripheral: CBPeripheralManager,
            error: NSError?,
        ) {
            advertising = error == null
            if (error != null) {
                logPlatformWarning(TAG, "BLE advertising failed: ${error.localizedDescription}")
                onState(NearbyRuntimeStatus.Error, "BLE advertising failed.", links.size, null)
            } else {
                logPlatformInfo(TAG, "BLE advertising started")
                publishState()
            }
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: CBCentral,
            didSubscribeToCharacteristic: CBCharacteristic,
        ) {
            if (didSubscribeToCharacteristic.UUID != characteristicUuid) return
            val link = peripheralLink(central)
            link.attPayloadBytes = central.maximumUpdateValueLength
                .toInt()
                .coerceAtLeast(NearbyBleFraming.DEFAULT_ATT_PAYLOAD_BYTES)
            publishState()
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didReceiveWriteRequests: List<*>,
        ) {
            didReceiveWriteRequests.filterIsInstance<CBATTRequest>().forEach { request ->
                if (request.characteristic.UUID == characteristicUuid) {
                    val link = peripheralLink(request.central)
                    request.value?.let { acceptFragment(link, it.toByteArray()) }
                }
                peripheral.respondToRequest(request, CBATTErrorSuccess)
            }
        }

        override fun peripheralManagerIsReadyToUpdateSubscribers(
            peripheral: CBPeripheralManager,
        ) {
            links.values
                .filter { it.role == NearbyLinkRole.Peripheral && it.outbound.isNotEmpty() }
                .forEach { link ->
                    link.sending = false
                    sendNextFragment(link)
                }
        }
    }

    private fun peripheralLink(central: CBCentral): Link {
        val key = central.identifier.UUIDString
        return links.getOrPut(key) {
            Link(
                key = key,
                role = NearbyLinkRole.Peripheral,
                machine = NearbyHandshakeSession(
                    role = NearbyLinkRole.Peripheral,
                    invitationNonce = invitationNonce,
                    advertisedPeerNonce = null,
                ),
            ).also { link ->
                link.subscribedCentral = central
                attachCredentialAsync(link)
            }
        }.also { it.subscribedCentral = central }
    }

    private fun startScanning(central: CBCentralManager) {
        if (stopped) return
        central.scanForPeripheralsWithServices(listOf(serviceUuid), null)
        scanning = true
        logPlatformInfo(TAG, "BLE scan started for the PocketPass service")
        publishState()
    }

    private fun attachCredentialAsync(link: Link) {
        if (link.credentialAttached) return
        link.credentialAttached = true
        scope.launch {
            when (val result = credentialPool.acquire(accountId)) {
                is RepositoryResult.Failure -> onQueue {
                    failLink(
                        link,
                        result.error.message ?: "No anonymous encounter pass is available.",
                    )
                }

                is RepositoryResult.Success -> onQueue {
                    dispatchEvents(link, link.machine.attachCredential(result.value))
                }
            }
        }
    }

    private fun acceptFragment(link: Link, fragment: ByteArray) {
        val packet = runCatching { link.reassembler.accept(fragment) }
            .getOrElse {
                failLink(link, "The nearby device sent an invalid encrypted frame.")
                return
            }
            ?: return
        dispatchEvents(link, link.machine.onPacket(packet))
    }

    private fun dispatchEvents(link: Link, events: List<NearbyHandshakeSession.Event>) {
        events.forEach { event ->
            when (event) {
                is NearbyHandshakeSession.Event.SendPacket ->
                    enqueuePacket(link, event.packet)

                is NearbyHandshakeSession.Event.Failed ->
                    failLink(link, event.message)

                is NearbyHandshakeSession.Event.ProofReady -> {
                    onProof(event.proof)
                    onState(
                        NearbyRuntimeStatus.Running,
                        null,
                        links.size,
                        event.proof.occurredAt,
                    )
                    closeLink(link.key)
                }
            }
        }
    }

    private fun enqueuePacket(link: Link, packet: ByteArray) {
        val fragments = NearbyBleFraming.fragment(
            messageId = nextMessageId(),
            packet = packet,
            attPayloadBytes = link.attPayloadBytes,
        )
        link.outbound.addAll(fragments)
        sendNextFragment(link)
    }

    private fun nextMessageId(): Int {
        val id = messageId
        messageId = if (messageId >= 0xFFFF) 1 else messageId + 1
        return id
    }

    private fun sendNextFragment(link: Link) {
        if (link.sending) return
        when (link.role) {
            NearbyLinkRole.Central -> {
                val fragment = link.outbound.removeFirstOrNull() ?: return
                val peripheral = link.peripheral
                val characteristic = link.characteristic
                if (peripheral == null || characteristic == null) {
                    failLink(link, "The encrypted BLE transfer could not start.")
                    return
                }
                link.sending = true
                peripheral.writeValue(
                    fragment.toNSData(),
                    characteristic,
                    CBCharacteristicWriteWithResponse,
                )
            }

            NearbyLinkRole.Peripheral -> {
                val manager = peripheralManager
                val characteristic = transferCharacteristic
                val central = link.subscribedCentral
                if (manager == null || characteristic == null || central == null) {
                    failLink(link, "The encrypted BLE transfer could not start.")
                    return
                }
                while (true) {
                    val fragment = link.outbound.firstOrNull() ?: return
                    val accepted = manager.updateValue(
                        fragment.toNSData(),
                        characteristic,
                        listOf(central),
                    )
                    if (accepted) {
                        link.outbound.removeFirstOrNull()
                    } else {
                        // The shared transmit queue is full; resume from
                        // peripheralManagerIsReadyToUpdateSubscribers.
                        link.sending = true
                        return
                    }
                }
            }
        }
    }

    private fun closeLink(key: String) {
        val link = links.remove(key) ?: return
        link.machine.close()
        heldPeripherals.remove(key)?.let { peripheral ->
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        publishState()
    }

    private fun failLink(link: Link, message: String) {
        closeLink(link.key)
        onState(
            if (advertising && scanning) {
                NearbyRuntimeStatus.Running
            } else {
                NearbyRuntimeStatus.Error
            },
            message,
            links.size,
            null,
        )
    }

    private fun publishState() {
        if (advertising && scanning) {
            onState(NearbyRuntimeStatus.Running, null, links.size, null)
        }
    }

    private fun advertisedNonce(advertisementData: Map<Any?, *>): Long? {
        val dataByService = advertisementData[CBAdvertisementDataServiceDataKey]
            as? Map<Any?, *> ?: return null
        val payload = (dataByService[serviceUuid] as? NSData)?.toByteArray() ?: return null
        if (payload.size != SERVICE_DATA_BYTES) return null
        if (payload[0].toInt() != NearbyWireProtocol.VERSION) return null
        var nonce = 0L
        for (index in 1 until payload.size) {
            nonce = (nonce shl 8) or (payload[index].toLong() and 0xFF)
        }
        return nonce
    }

    private fun advertisesBareService(advertisementData: Map<Any?, *>): Boolean {
        val advertised = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*>
        val overflow = advertisementData[CBAdvertisementDataOverflowServiceUUIDsKey] as? List<*>
        return advertised.orEmpty().contains(serviceUuid) ||
            overflow.orEmpty().contains(serviceUuid)
    }

    private inline fun onQueue(crossinline block: () -> Unit) {
        platform.darwin.dispatch_async(queue) { block() }
    }

    private fun tickMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(
            bytes = if (isEmpty()) null else pinned.addressOf(0),
            length = size.convert(),
        )
    }

    private companion object {
        const val TAG = "PocketPassNearby"
        const val SERVICE_UUID_STRING = "9b40e2f8-7543-4e62-9b4d-8672d18514c7"
        const val TRANSFER_CHARACTERISTIC_UUID_STRING = "9b40e2f9-7543-4e62-9b4d-8672d18514c7"
        const val SERVICE_DATA_BYTES = 1 + Long.SIZE_BYTES
        const val CONNECTION_RETRY_WINDOW_MILLIS = 5 * 60 * 1_000L
    }
}
