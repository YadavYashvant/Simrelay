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
import com.example.simrelay.NetworkMonitor
import android.util.Log
import com.example.simrelay.SmsSender

class SmsService : Service() {

    private var networkMonitor: NetworkMonitor? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("SmsService", "Service created")
        SmsSender.registerReceivers(this)
        networkMonitor = NetworkMonitor(this) { ip ->
            Log.d("SmsService", "Network changed: $ip")
            updateNotification(ip)
            if (ip != null) {
                ServerManager.startServer(this)
            } else {
                Log.w("SmsService", "No IP available, server will wait.")
            }
        }
        networkMonitor?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val ip = ServerManager.getLocalIpAddress()
        Log.i("SmsService", "onStartCommand: Received intent, current ip=$ip")
        startForeground(NOTIFICATION_ID, createNotification(ip))

        // Ensure server is starting if we have an IP and it's not already running
        if (ip != null) {
            if (!ServerManager.isRunning) {
                Log.d("SmsService", "Server not running, attempting to start from onStartCommand")
                ServerManager.startServer(this)
            } else {
                Log.d("SmsService", "Server already running, skipping start in onStartCommand")
            }
        }

        return START_STICKY
    }

    private fun updateNotification(ip: String?) {
        Log.d("SmsService", "Updating notification for IP: $ip")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(ip))
    }

    private fun createNotification(ip: String?) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("SimRelay SMS Gateway")
        .setContentText(if (ip != null) "Listening on http://$ip:3000" else "Waiting for network...")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        Log.d("SmsService", "Service destroying")
        networkMonitor?.stop()
        ServerManager.stopServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
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