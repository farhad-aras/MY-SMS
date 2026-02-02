package com.example.mysms.ui.theme

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiverService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SmsReceiverService", "📨 SMS Service started")

        if (intent?.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            processSms(intent)
        }

        // Service را نگه دار
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun processSms(intent: Intent) {
        try {
            val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else {
                // روش قدیمی
                @Suppress("DEPRECATION")
                intent.extras?.get("pdus") as? Array<*> ?: emptyArray()
            }

            if (messages.isNullOrEmpty()) {
                Log.w("SmsReceiverService", "No messages found")
                return
            }

            Log.d("SmsReceiverService", "📱 Processing ${messages.size} message(s)")

            for (message in messages) {
                if (message is SmsMessage) {
                    val address = message.originatingAddress ?: "Unknown"
                    val body = message.messageBody ?: ""
                    val timestamp = message.timestampMillis

                    Log.d("SmsReceiverService", "📨 Message from $address: ${body.take(30)}...")

                    // ۱. ذخیره در دیتابیس
                    saveToDatabase(address, body, timestamp)

                    // ۲. نمایش نوتیفیکیشن - حتی وقتی اپ بسته است
                    showPersistentNotification(address, body)
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiverService", "💥 Error processing SMS: ${e.message}", e)
        }
    }

    private fun saveToDatabase(address: String, body: String, timestamp: Long) {
        // اینجا کد ذخیره در دیتابیس
        // می‌توانید از AppDatabase استفاده کنید
        Log.d("SmsReceiverService", "💾 Saving message from $address")
    }

    private fun showPersistentNotification(address: String, body: String) {
        // اینجا کد نمایش نوتیفیکیشن
        // مهم: از ForegroundSmsService استفاده کنید
        try {
            ForegroundSmsService.startService(this)
            Log.d("SmsReceiverService", "📢 Notification triggered for $address")
        } catch (e: Exception) {
            Log.e("SmsReceiverService", "Error showing notification: ${e.message}")
        }
    }
}