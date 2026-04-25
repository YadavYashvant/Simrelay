package com.example.simrelay.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.example.simrelay.ServerManager

class SmsService : Service() {

    override fun onCreate() {
        super.onCreate()
        // Ktor server will be started in onStartCommand to ensure foreground is active
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SimRelay SMS Gateway")
            .setContentText("Local server is listening on port 3000")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start server if not running
        if (!ServerManager.isRunning) {
            ServerManager.startServer()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        ServerManager.stopServer()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SimRelay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keep SimRelay SMS Gateway running in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL_ID = "simrelay_channel"
        private const val NOTIFICATION_ID = 1
    }
}