package com.resqteam.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.resqteam.app.data.IncidentEntity

private const val CHANNEL_ID_CRITICAL = "resqteam_critical_alerts"

class EmergencyNotificationManager(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical ResQMesh Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New critical/high-priority incidents from the ResQMesh gateway"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Spec section 17: a distinct Android notification for every new critical/high incident. */
    fun notifyNewIncident(incident: IncidentEntity) {
        if (!hasPostPermission()) return

        val locationLine = if (incident.latitude != null && incident.longitude != null) {
            "Location: ${incident.latitude}, ${incident.longitude}"
        } else {
            "Location: not reported"
        }
        val peopleLine = incident.peopleCount?.let { "$it people reported" } ?: "People count not reported"

        val title = if (incident.priority == 5) {
            "\uD83D\uDEA8 CRITICAL RESQMESH ALERT"
        } else {
            "\uD83D\uDFE0 HIGH PRIORITY RESQMESH ALERT"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("${incident.type} — $peopleLine")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${incident.type}\n$peopleLine\n$locationLine")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        manager.notify(incident.messageId.hashCode(), notification)
    }
}
