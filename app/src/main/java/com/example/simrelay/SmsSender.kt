package com.example.simrelay

import android.telephony.SmsManager

object SmsSender {

    fun send(to: String, message: String) {
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(to, null, message, null, null)
    }
}