package com.praymute.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Ye Foreground Service app ko background mein "zinda" rakhti hai jab Auto-Silent
 * feature ON ho — taaki 10-min-before wala check chalte rehe chahe user screen
 * band kar de ya doosri app khol le.
 *
 * IMPORTANT (honest limitation): Android policy ke hisaab se foreground service
 * ke liye ek permanent, dikhne wali notification zaroori hai — ise chupaya nahi
 * ja sakta. Isiliye jab Auto-Silent ON hoga, notification bar mein hamesha ek
 * chhota "Pray Mute is active" wala notification dikhega. Ye Android ka rule hai,
 * hamari app ki limitation nahi — WhatsApp jaisi apps bhi isi tarah kaam karti hain.
 */
class PrayerMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "praymute_monitor_channel"
        const val NOTIF_ID = 501
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pray Mute")
            .setContentText("Namaz timings monitor ho rahi hain — auto-silent active hai")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // START_STICKY: agar Android system ne kabhi resources kam hone par service
        // ko khatam kiya, to ye khud dobara start karne ki koshish karega
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Monitoring",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = "Background monitoring for automatic prayer-time silent mode"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
