package com.resqteam.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raw wire format forwarded by the ESP32 bridge, unchanged from what the
 * ResQMesh civilian packet contains (spec section 21/22).
 *
 * Every field except messageId/type/priority is optional on the wire —
 * some ResQMesh nodes may not have a GPS fix, battery telemetry, etc.
 * We must never invent values for missing optional fields (spec section 8).
 */
@Serializable
data class ResQMessage(
    val messageId: String,
    val sourceNodeId: String,
    val type: String,
    val priority: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long? = null,
    val battery: Int? = null,
    val ttl: Int? = null,
    val hopCount: Int? = null,
    val peopleCount: Int? = null,
    val injuredCount: Int? = null
)

enum class EmergencyPriority(val level: Int, val label: String) {
    CRITICAL(5, "CRITICAL"),
    HIGH(4, "HIGH"),
    MEDIUM(3, "MEDIUM"),
    LOW(2, "LOW"),
    STATUS(1, "STATUS");

    companion object {
        /** Spec section 6: numeric priority from the wire maps to a named tier. */
        fun fromLevel(level: Int): EmergencyPriority =
            entries.firstOrNull { it.level == level } ?: LOW
    }
}

enum class IncidentStatus {
    NEW, ACKNOWLEDGED, RESPONDING, RESCUED, RESOLVED
}

sealed class PacketParseResult {
    data class Success(val message: ResQMessage) : PacketParseResult()
    data class Invalid(val reason: String, val raw: String) : PacketParseResult()
}

/**
 * Parses one newline-delimited JSON line from the ESP32 bridge (spec section 22).
 * Never throws — malformed lines must be discarded, not crash the app (spec section 33/47).
 */
object ResQPacketParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawLine: String): PacketParseResult {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            return PacketParseResult.Invalid("empty line", rawLine)
        }

        // Try the normal ResQMesh JSON format first.
        val message = try {
            json.decodeFromString(ResQMessage.serializer(), trimmed)
        } catch (_: Exception) {
            // IMPORTANT: Anything received from the ESP32 must still be shown.
            // Wrap non-JSON/raw packets as a valid local incident instead of
            // dropping them. The original packet is preserved verbatim in type.
            return PacketParseResult.Success(
                ResQMessage(
                    messageId = "RAW-${System.currentTimeMillis()}-${trimmed.hashCode().toUInt()}",
                    sourceNodeId = "ESP32-RAW",
                    type = "RAW PACKET: $trimmed",
                    priority = 5,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // If JSON is missing required fields or has bad ranges, do NOT discard it.
        // Preserve the entire received line as a visible raw incident.
        if (message.messageId.isBlank() ||
            message.sourceNodeId.isBlank() ||
            message.priority !in 1..5 ||
            message.latitude?.let { it < -90.0 || it > 90.0 } == true ||
            message.longitude?.let { it < -180.0 || it > 180.0 } == true ||
            message.timestamp?.let { it < 0 } == true
        ) {
            return PacketParseResult.Success(
                ResQMessage(
                    messageId = "RAW-${System.currentTimeMillis()}-${trimmed.hashCode().toUInt()}",
                    sourceNodeId = "ESP32-RAW",
                    type = "RAW PACKET: $trimmed",
                    priority = 5,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        return PacketParseResult.Success(message)
    }
}
