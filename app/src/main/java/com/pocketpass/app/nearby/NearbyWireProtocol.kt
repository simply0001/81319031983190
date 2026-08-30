@file:OptIn(ExperimentalSerializationApi::class)

package com.pocketpass.app.nearby

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf

internal object NearbyWireProtocol {
    const val VERSION: Int = 2
    const val MAX_PACKET_BYTES: Int = 4_096

    fun packetType(packet: ByteArray): Int {
        require(packet.size >= HEADER_BYTES)
        val header = ByteBuffer.wrap(packet)
        require(header.int == MAGIC)
        require(header.get().toInt() and 0xFF == VERSION)
        return header.get().toInt() and 0xFF
    }

    fun helloPacket(hello: NearbyHello): NearbyHelloPacket =
        NearbyHelloPacket(hello, encodeHello(hello))

    fun decodeHelloPacket(packet: ByteArray): NearbyHelloPacket =
        NearbyHelloPacket(decodeHello(packet), packet)

    fun encodeHello(hello: NearbyHello): ByteArray =
        encodePacket(PacketType.Hello, NearbyHello.serializer(), hello)

    fun decodeHello(packet: ByteArray): NearbyHello =
        decodePacket(packet, PacketType.Hello, NearbyHello.serializer())

    fun encodeSignature(signature: ByteArray): ByteArray =
        encodePacket(PacketType.Signature, NearbySignature.serializer(), NearbySignature(signature))

    fun decodeSignature(packet: ByteArray): ByteArray =
        decodePacket(packet, PacketType.Signature, NearbySignature.serializer()).signature

    fun encodeConfirmation(
        ownToken: ByteArray,
        peerToken: ByteArray,
        occurredAt: Instant,
        transcriptHash: ByteArray,
    ): ByteArray = encodePacket(
        PacketType.Confirmation,
        NearbyConfirmation.serializer(),
        NearbyConfirmation(
            ownToken = ownToken,
            peerToken = peerToken,
            occurredAtEpochMillis = occurredAt.toEpochMilliseconds(),
            transcriptHash = transcriptHash,
        ),
    )

    fun decodeConfirmation(packet: ByteArray): NearbyConfirmation =
        decodePacket(packet, PacketType.Confirmation, NearbyConfirmation.serializer())

    fun encodeEncrypted(packet: NearbyEncryptedPacket): ByteArray =
        encodePacket(PacketType.Encrypted, NearbyEncryptedPacket.serializer(), packet)

    fun decodeEncrypted(packet: ByteArray): NearbyEncryptedPacket =
        decodePacket(packet, PacketType.Encrypted, NearbyEncryptedPacket.serializer())

    private fun <T> encodePacket(
        type: PacketType,
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        val body = ProtoBuf.encodeToByteArray(serializer, value)
        val packet = ByteBuffer.allocate(HEADER_BYTES + body.size)
            .putInt(MAGIC)
            .put(VERSION.toByte())
            .put(type.value.toByte())
            .put(body)
            .array()
        require(packet.size <= MAX_PACKET_BYTES)
        return packet
    }

    private fun <T> decodePacket(
        packet: ByteArray,
        expectedType: PacketType,
        serializer: KSerializer<T>,
    ): T {
        require(packet.size in HEADER_BYTES..MAX_PACKET_BYTES)
        val header = ByteBuffer.wrap(packet)
        require(header.int == MAGIC) { "Invalid PocketPass packet" }
        require(header.get().toInt() and 0xFF == VERSION) { "Unsupported protocol version" }
        require(header.get().toInt() and 0xFF == expectedType.value) {
            "Unexpected PocketPass packet type"
        }
        return ProtoBuf.decodeFromByteArray(
            serializer,
            packet.copyOfRange(HEADER_BYTES, packet.size),
        )
    }

    private enum class PacketType(val value: Int) {
        Hello(1),
        Signature(2),
        Confirmation(3),
        Encrypted(4),
    }

    private const val MAGIC = 0x50504E31
    private const val HEADER_BYTES = 6
}

internal object NearbyBleFraming {
    const val HEADER_BYTES = 8
    const val DEFAULT_ATT_PAYLOAD_BYTES = 20

    fun fragment(
        messageId: Int,
        packet: ByteArray,
        attPayloadBytes: Int,
    ): List<ByteArray> {
        require(messageId in 0..0xFFFF)
        require(packet.size <= NearbyWireProtocol.MAX_PACKET_BYTES)
        require(attPayloadBytes > HEADER_BYTES)
        val bodyBytes = attPayloadBytes - HEADER_BYTES
        val count = (packet.size + bodyBytes - 1) / bodyBytes
        require(count in 1..0xFFFF)
        return List(count) { index ->
            val start = index * bodyBytes
            val end = minOf(packet.size, start + bodyBytes)
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeShort(messageId)
                    output.writeShort(index)
                    output.writeShort(count)
                    output.writeShort(packet.size)
                    output.write(packet, start, end - start)
                }
                buffer.toByteArray()
            }
        }
    }

    class Reassembler {
        private var messageId: Int? = null
        private var fragmentCount: Int = 0
        private var packetSize: Int = 0
        private val fragments = mutableMapOf<Int, ByteArray>()

        fun accept(fragment: ByteArray): ByteArray? {
            require(fragment.size > HEADER_BYTES)
            val input = DataInputStream(ByteArrayInputStream(fragment))
            val incomingMessageId = input.readUnsignedShort()
            val index = input.readUnsignedShort()
            val incomingCount = input.readUnsignedShort()
            val incomingSize = input.readUnsignedShort()
            require(incomingCount > 0 && index < incomingCount)
            require(incomingSize in 1..NearbyWireProtocol.MAX_PACKET_BYTES)

            if (messageId != incomingMessageId) {
                reset()
                messageId = incomingMessageId
                fragmentCount = incomingCount
                packetSize = incomingSize
            }
            require(fragmentCount == incomingCount && packetSize == incomingSize)
            fragments.putIfAbsent(index, input.readBytes())
            if (fragments.size != fragmentCount) return null

            val result = ByteArrayOutputStream(packetSize).use { output ->
                repeat(fragmentCount) { fragmentIndex ->
                    output.write(requireNotNull(fragments[fragmentIndex]))
                }
                output.toByteArray()
            }
            require(result.size == packetSize)
            reset()
            return result
        }

        fun reset() {
            messageId = null
            fragmentCount = 0
            packetSize = 0
            fragments.clear()
        }
    }
}
