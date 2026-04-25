package com.example.simrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.simrelay.services.SmsService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SmsService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
