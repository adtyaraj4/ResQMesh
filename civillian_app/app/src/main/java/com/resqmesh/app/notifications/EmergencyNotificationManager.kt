package com.resqmesh.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.resqmesh.app.MainActivity
import com.resqmesh.app.mesh.MeshPacket

/**
 * Owns the "Emergency Alerts" notification channel (HIGH importance,
 * per spec — this carries genuine emergency alerts, not routine network
 * status) and posts a notification for each new incoming emergency
 * packet. Tapping a notification opens [MainActivity] with the
 * messageId attached so it can navigate straight to that message's
 * details screen.
 */
class EmergencyNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent danger alerts from nearby ResQMesh nodes"
            }
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    /** Whether the one-time rationale explanation has already been shown. */
    fun hasShownRationale(): Boolean = prefs.getBoolean(KEY_RATIONALE_SHOWN, false)

    fun markRationaleShown() {
        prefs.edit().putBoolean(KEY_RATIONALE_SHOWN, true).apply()
    }

    /**
     * Posts a high-priority notification for an incoming emergency packet.
     * Never called for self-originated packets — [com.resqmesh.app.mesh.MeshManager]
     * only tracks those as OUTGOING, and the caller is expected to filter
     * on direction before calling this.
     */
    fun notifyIncomingEmergency(packet: MeshPacket) {
        val title = "🚨 RESQMESH EMERGENCY"
        val body = "${packet.sourceNodeId} — ${describeType(packet)}  •  Priority: ${packet.priority}"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_MESSAGE_ID, packet.messageId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packet.messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(packet.messageId.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — degrade silently, per spec:
            // "If permission is denied, the application must still function."
        }
    }

    private fun describeType(packet: MeshPacket): String =
        when (packet.type.name) {
            "TRAPPED" -> "IS IN DANGER"
            "MEDICAL" -> "MEDICAL EMERGENCY"
            "EVACUATION" -> "NEEDS EVACUATION"
            "SUPPLIES" -> "NEEDS SUPPLIES"
            "SAFE" -> "IS SAFE"
            else -> packet.type.name
        }

    companion object {
        private const val CHANNEL_ID = "resqmesh_emergency_alerts"
        private const val PREFS_NAME = "resqmesh_notification_prefs"
        private const val KEY_RATIONALE_SHOWN = "rationale_shown"
    }
}
