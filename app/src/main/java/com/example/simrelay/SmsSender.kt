package com.example.simrelay

import android.os.Build
import android.telephony.SmsManager

object SmsSender {

    fun send(to: String, message: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // S+ doesn't have a direct Context-free getDefault() easily anymore, 
            // but for simple cases this is generally how it's done or using Context.
            // However, to keep it simple and consistent:
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        
        smsManager.sendTextMessage(to, null, message, null, null)
    }
}