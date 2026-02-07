package com.example.mysms.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                "COPY_CODE_ACTION" -> {
                    val code = intent.getStringExtra("code") ?: ""
                    val address = intent.getStringExtra("address") ?: ""
                    val notificationId = intent.getIntExtra("notification_id", 0)
                    NotificationManager.handleCopyCode(context, code, address, notificationId)
                }

                "QUICK_REPLY_ACTION" -> {
                    val address = intent.getStringExtra("address") ?: ""
                    val notificationId = intent.getIntExtra("notification_id", 0)
                    val replyText = NotificationManager.getReplyTextFromIntent(intent)
                    NotificationManager.handleQuickReply(context, address, replyText, notificationId)
                }

                "MARK_READ_ACTION" -> {
                    val address = intent.getStringExtra("address") ?: ""
                    val notificationId = intent.getIntExtra("notification_id", 0)
                    NotificationManager.handleMarkAsRead(context, address, notificationId)
                }

                else -> Log.w(TAG, "⚠️ Unknown action: ${intent.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در پردازش اکشن نوتیفیکیشن", e)
        }
    }
}