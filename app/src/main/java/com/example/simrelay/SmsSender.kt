package com.example.simrelay

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsSender {

    private const val ACTION_SMS_SENT = "com.example.simrelay.SMS_SENT"
    private const val ACTION_SMS_DELIVERED = "com.example.simrelay.SMS_DELIVERED"

    fun send(context: Context, to: String, message: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val sentIntent = PendingIntent.getBroadcast(
            context, 
            to.hashCode(), 
            Intent(ACTION_SMS_SENT).apply { setPackage(context.packageName) }, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val deliveredIntent = PendingIntent.getBroadcast(
            context, 
            to.hashCode(), 
            Intent(ACTION_SMS_DELIVERED).apply { setPackage(context.packageName) }, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        smsManager.sendTextMessage(to, null, message, sentIntent, deliveredIntent)
    }

    fun registerReceivers(context: Context) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }

            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val detail = when (resultCode) {
                        Activity.RESULT_OK -> "SMS Sent Successfully"
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
                        else -> "Unknown error"
                    }
                    LogRepository.addLog("SYSTEM", "SENT", resultCode, detail)
                }
            }, IntentFilter(ACTION_SMS_SENT), flags)

            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    LogRepository.addLog("SYSTEM", "DELIVERED", 1, "SMS Delivered")
                }
            }, IntentFilter(ACTION_SMS_DELIVERED), flags)
        } catch (e: Exception) {
            Log.e("SmsSender", "Error registering receivers", e)
        }
    }
}