package com.resqteam.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val messageId: String,
    val sourceNodeId: String,
    val type: String,
    val priority: Int,
    val latitude: Double?,
    val longitude: Double?,
    /** Timestamp as reported by the originating ResQMesh node, if it sent one. */
    val nodeTimestamp: Long?,
    val battery: Int?,
    val ttl: Int?,
    val hopCount: Int?,
    val peopleCount: Int?,
    val injuredCount: Int?,

    val status: String = IncidentStatus.NEW.name,

    /** Wall-clock time (device millis) the gateway first delivered this messageId. */
    val receivedAt: Long,
    /** Updated every time a duplicate of this messageId arrives (spec section 24). */
    val lastReceivedAt: Long,
    val duplicateCount: Int = 0,

    val acknowledgedAt: Long? = null,
    val respondingAt: Long? = null,
    val resolvedAt: Long? = null,
    val operatorId: String? = null
)
