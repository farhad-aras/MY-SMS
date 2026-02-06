package com.example.mysms.ui.theme

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val KEY_REPLY = "key_reply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "COPY_CODE_ACTION" -> handleCopyCode(context, intent)
            "QUICK_REPLY_ACTION" -> handleQuickReply(context, intent)
            "MARK_READ_ACTION" -> handleMarkAsRead(context, intent)
            else -> Log.w(TAG, "⚠️ Unknown action: ${intent.action}")
        }
    }

    /**
     * کپی کردن کد تأیید
     */
    private fun handleCopyCode(context: Context, intent: Intent) {
        try {
            val code = intent.getStringExtra("code") ?: ""
            val address = intent.getStringExtra("address") ?: ""

            if (code.isNotEmpty()) {
                // کپی به کلیپ‌بورد
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("کد تأیید", code)
                clipboard.setPrimaryClip(clip)

                // نمایش Toast
                Toast.makeText(context, "✅ کد کپی شد: $code", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "📋 کد کپی شد: $code برای $address")

                // حذف نوتیفیکیشن
                cancelNotification(context, intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در کپی کردن کد", e)
            Toast.makeText(context, "❌ خطا در کپی کردن کد", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * پاسخ سریع به پیام
     */
    private fun handleQuickReply(context: Context, intent: Intent) {
        try {
            val address = intent.getStringExtra("address") ?: ""
            val notificationId = intent.getIntExtra("notification_id", 0)

            // دریافت متن از RemoteInput
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(KEY_REPLY)?.toString()

            if (replyText != null && replyText.isNotEmpty()) {
                // ارسال پاسخ
                sendQuickReply(context, address, replyText)

                // نمایش تأیید
                Toast.makeText(context, "✅ پاسخ ارسال شد", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "💬 پاسخ ارسال شد به $address: $replyText")

                // حذف نوتیفیکیشن
                cancelNotification(context, notificationId)
            } else {
                // اگر متن پاسخ خالی است، کادر پاسخ را نشان بده
                showReplyInput(context, address, notificationId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در پاسخ سریع", e)
            Toast.makeText(context, "❌ خطا در ارسال پاسخ", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * علامت‌گذاری پیام به عنوان خوانده شده
     */
    private fun handleMarkAsRead(context: Context, intent: Intent) {
        try {
            val address = intent.getStringExtra("address") ?: ""
            val notificationId = intent.getIntExtra("notification_id", 0)

            // علامت‌گذاری در دیتابیس
            markMessageAsReadInDatabase(context, address)

            // نمایش تأیید
            Toast.makeText(context, "✅ پیام خوانده شد", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ پیام از $address به عنوان خوانده شده علامت‌گذاری شد")

            // حذف نوتیفیکیشن
            cancelNotification(context, notificationId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در علامت‌گذاری خوانده شده", e)
            Toast.makeText(context, "❌ خطا در علامت‌گذاری", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ارسال پاسخ سریع
     */
    private fun sendQuickReply(context: Context, address: String, message: String) {
        // اینجا باید منطق ارسال پیام را پیاده‌سازی کنی
        // فعلاً فقط لاگ می‌کنیم
        Log.d(TAG, "📤 ارسال پاسخ سریع به $address: $message")

        // TODO: اینجا باید با استفاده از SmsManager پیام را ارسال کنی
        // یا از ViewModel استفاده کنی
    }

    /**
     * نمایش کادر ورود برای پاسخ
     */
    private fun showReplyInput(context: Context, address: String, notificationId: Int) {
        // اینجا باید یک Activity یا Dialog برای دریافت پاسخ نمایش دهی
        // فعلاً فقط لاگ می‌کنیم
        Log.d(TAG, "📝 نمایش کادر پاسخ برای $address")

        // می‌توانی یک Intent به Activity اصلی بفرستی
        val replyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("quick_reply", true)
            putExtra("address", address)
            putExtra("notification_id", notificationId)
        }
        context.startActivity(replyIntent)
    }

    /**
     * علامت‌گذاری پیام در دیتابیس
     */
    private fun markMessageAsReadInDatabase(context: Context, address: String) {
        // اینجا باید پیام‌ها را در دیتابیس به عنوان خوانده شده علامت‌گذاری کنی
        Log.d(TAG, "📖 علامت‌گذاری پیام‌های $address به عنوان خوانده شده در دیتابیس")

        // TODO: با استفاده از ViewModel یا مستقیم با DAO این کار را انجام بده
    }

    /**
     * حذف نوتیفیکیشن
     */
    private fun cancelNotification(context: Context, intent: Intent) {
        try {
            val notificationId = intent.getIntExtra("notification_id", 0)
            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در حذف نوتیفیکیشن", e)
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "🗑️ نوتیفیکیشن $notificationId حذف شد")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در حذف نوتیفیکیشن", e)
        }
    }
}