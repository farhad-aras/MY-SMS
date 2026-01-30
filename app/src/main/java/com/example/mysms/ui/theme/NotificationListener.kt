package com.example.mysms.ui.theme

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationListener", "✅ Notification service started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val title = sbn.notification.extras.getString("android.title")
            val text = sbn.notification.extras.getString("android.text")

            Log.d("NotificationListener", "📨 Notification from: $packageName")
            Log.d("NotificationListener", "Title: $title")
            Log.d("NotificationListener", "Text: $text")

            // Filter SMS notifications
            if (isSmsNotification(packageName, title, text)) {
                processSmsNotification(title, text)
            }

        } catch (e: Exception) {
            Log.e("NotificationListener", "Error processing notification", e)
        }
    }

    private fun isSmsNotification(packageName: String, title: String?, text: String?): Boolean {
        return packageName.contains("sms") ||
                packageName.contains("mms") ||
                packageName.contains("messaging") ||
                packageName.contains("telephony") ||
                (title?.contains("SMS") == true) ||
                (title?.contains("پیامک") == true) ||
                (text?.contains("+98") == true) ||
                (text?.matches(Regex(".*09\\d{9}.*")) == true)
    }

    private fun processSmsNotification(title: String?, text: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extract phone number from text
                val phoneNumber = extractPhoneNumber(text ?: "")

                if (phoneNumber.isNotEmpty()) {
                    Log.d("NotificationListener", "📱 New message from: $phoneNumber")

                    // Save to database
                    val smsEntity = com.example.mysms.data.SmsEntity(
                        id = "notif_${System.currentTimeMillis()}",
                        address = phoneNumber,
                        body = text ?: "",
                        date = System.currentTimeMillis(),
                        type = 1,
                        subId = -1,
                        read = false
                    )

                    val database = com.example.mysms.data.AppDatabase.getDatabase(this@NotificationListener)
                    database.smsDao().insertAll(listOf(smsEntity))

                    Log.d("NotificationListener", "✅ Message saved from: $phoneNumber")
                }

            } catch (e: Exception) {
                Log.e("NotificationListener", "Error saving message", e)
            }
        }
    }

    private fun extractPhoneNumber(text: String): String {
        // Extract phone number from text
        val regex = Regex("""(09\d{9}|\+98\d{10})""")
        val match = regex.find(text)
        return match?.value ?: "Unknown"
    }
}