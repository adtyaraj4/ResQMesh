package com.resqmesh.app.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class MeshPacketType {
    TRAPPED,
    MEDICAL,
    EVACUATION,
    SUPPLIES,
    SAFE,
    ACK
}

/**
 * Priority per emergency type, exactly as specified:
 * TRAPPED=5, MEDICAL=5 (CRITICAL), EVACUATION=4 (HIGH), SUPPLIES=3 (MEDIUM), SAFE=1 (STATUS).
 */
fun MeshPacketType.defaultPriority(): MeshPriority = when (this) {
    MeshPacketType.TRAPPED,
    MeshPacketType.MEDICAL -> MeshPriority.CRITICAL
    MeshPacketType.EVACUATION -> MeshPriority.HIGH
    MeshPacketType.SUPPLIES -> MeshPriority.MEDIUM
    MeshPacketType.SAFE -> MeshPriority.STATUS
    MeshPacketType.ACK -> MeshPriority.HIGH
}

@Serializable
enum class MeshPriority {
    CRITICAL,   // 5 — trapped / life-threatening medical
    HIGH,       // 4 — evacuation
    MEDIUM,     // 3 — supplies
    LOW,        // 2 — general assistance
    STATUS      // 1 — safe / status update
}

@Serializable
enum class PacketOrigin {
    PHONE,
    ESP32_LORA
}

/**
 * The single shared packet format used across every transport
 * (Nearby Connections, BLE gateway, LoRa). MeshManager and every
 * MeshTransport implementation speak this type — no transport-specific
 * message formats exist anywhere else in the app.
 *
 * Kept as compact JSON for now, per spec Section "PACKET FORMAT":
 * "Structure the code so that it can later be replaced with a binary
 * protocol." All fields are primitives/enums specifically so a later
 * CBOR/binary encoder can be swapped in without touching MeshManager
 * or callers — only [MeshPacketCodec] would change.
 */
@Serializable
data class MeshPacket(
    val version: Int = PROTOCOL_VERSION,
    val messageId: String,
    val sourceNodeId: String,
    val destinationNodeId: String? = null,
    val type: MeshPacketType,
    val payload: String? = null,
    val timestamp: Long,
    val ttl: Int,
    val hopCount: Int = 0,
    val priority: MeshPriority,
    val origin: PacketOrigin = PacketOrigin.PHONE,
    // Optional emergency fields (Section 7 of the original spec).
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationTimestamp: Long? = null,
    val batteryLevel: Int? = null,
    val peopleCount: Int? = null,
    val medicalCondition: String? = null,
    // Message this packet acknowledges, only set when type == ACK.
    // Named ackFor (not acknowledgesMessageId) to match the exact wire
    // field name the ESP32 firmware parses with ArduinoJson.
    val ackFor: String? = null
) {
    /** Returns a copy ready to forward one more hop: ttl--, hopCount++. */
    fun forwarded(): MeshPacket = copy(ttl = ttl - 1, hopCount = hopCount + 1)

    val isExpired: Boolean get() = ttl <= 0

    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_TTL = 8
        const val CRITICAL_TTL = 8

        fun newMessageId(): String = UUID.randomUUID().toString()

        fun emergency(
            type: MeshPacketType,
            sourceNodeId: String,
            latitude: Double?,
            longitude: Double?,
            locationAccuracy: Float?,
            locationTimestamp: Long?,
            batteryLevel: Int?,
            peopleCount: Int? = null,
            payload: String? = null
        ): MeshPacket = MeshPacket(
            messageId = newMessageId(),
            sourceNodeId = sourceNodeId,
            type = type,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            ttl = CRITICAL_TTL,
            priority = type.defaultPriority(),
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = locationAccuracy,
            locationTimestamp = locationTimestamp,
            batteryLevel = batteryLevel,
            peopleCount = peopleCount
        )

        fun ack(originalPacket: MeshPacket, ackingNodeId: String): MeshPacket = MeshPacket(
            messageId = newMessageId(),
            sourceNodeId = ackingNodeId,
            destinationNodeId = originalPacket.sourceNodeId,
            type = MeshPacketType.ACK,
            timestamp = System.currentTimeMillis(),
            ttl = DEFAULT_TTL,
            priority = MeshPriority.HIGH,
            ackFor = originalPacket.messageId
        )
    }
}

/** JSON encode/decode isolated here so a future binary codec is a one-file swap. */
object MeshPacketCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(packet: MeshPacket): String = json.encodeToString(packet)

    /** Returns null (never throws) on malformed input — callers must validate. */
    fun decodeOrNull(raw: String): MeshPacket? = try {
        json.decodeFromString(MeshPacket.serializer(), raw)
    } catch (e: Exception) {
        null
    }
}
