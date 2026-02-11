package com.example.mysms.ui.theme

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * مدیریت مرکزی تمام اکشن‌های نوتیفیکیشن
 */
object NotificationManager {

    private const val TAG = "NotificationManager"
    const val KEY_REPLY = "key_reply"

    /**
     * هندل کردن کپی کد تأیید
     */
    fun handleCopyCode(context: Context, code: String, address: String, notificationId: Int) {
        try {
            // کپی به کلیپ‌بورد
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("کد تأیید", code)
            clipboard.setPrimaryClip(clip)

            // نمایش Toast
            Toast.makeText(context, "✅ کد کپی شد: $code", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "📋 کد کپی شد: $code برای $address")

            // حذف نوتیفیکیشن
            cancelNotification(context, notificationId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در کپی کردن کد", e)
            Toast.makeText(context, "❌ خطا در کپی کردن کد", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * هندل کردن پاسخ سریع
     */
    fun handleQuickReply(
        context: Context,
        address: String,
        replyText: String?,
        notificationId: Int
    ) {
        try {
            // محاسبه notificationId اگر 0 بود
            val actualNotificationId = if (notificationId == 0) {
                Log.w(TAG, "⚠️ notification_id is 0, calculating from address")
                address.hashCode() and 0x7FFFFFFF
            } else {
                notificationId
            }

            if (!replyText.isNullOrEmpty()) {
                // ارسال پاسخ
                sendQuickReply(context, address, replyText)

                // نمایش تأیید
                Toast.makeText(context, "✅ پاسخ ارسال شد", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "💬 پاسخ ارسال شد به $address: $replyText")

                // حذف نوتیفیکیشن
                cancelNotification(context, actualNotificationId)

                // همچنین notification با hashCode آدرس را هم حذف کن
                val alternativeNotificationId = address.hashCode() and 0x7FFFFFFF
                if (alternativeNotificationId != actualNotificationId) {
                    cancelNotification(context, alternativeNotificationId)
                }
            } else {
                // اگر متن پاسخ خالی است، دیالوگ پاسخ را نشان بده
                Log.d(TAG, "📝 نمایش دیالوگ پاسخ برای $address (notificationId: $actualNotificationId)")
                showReplyDialog(context, address, actualNotificationId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در پاسخ سریع", e)
            Toast.makeText(context, "❌ خطا در ارسال پاسخ", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * هندل کردن علامت‌گذاری به عنوان خوانده شده
     */
    fun handleMarkAsRead(context: Context, address: String, notificationId: Int) {
        try {
            // محاسبه notificationId اگر 0 بود
            val actualNotificationId = if (notificationId == 0) {
                Log.w(TAG, "⚠️ notification_id is 0 in mark read, calculating from address")
                address.hashCode() and 0x7FFFFFFF
            } else {
                notificationId
            }

            // علامت‌گذاری در دیتابیس
            markMessageAsReadInDatabase(context, address)

            // نمایش تأیید
            Toast.makeText(context, "✅ پیام خوانده شد", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ پیام از $address به عنوان خوانده شده علامت‌گذاری شد")

            // حذف نوتیفیکیشن
            cancelNotification(context, actualNotificationId)

            // حذف notification جایگزین هم
            val alternativeNotificationId = address.hashCode() and 0x7FFFFFFF
            if (alternativeNotificationId != actualNotificationId) {
                cancelNotification(context, alternativeNotificationId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در علامت‌گذاری خوانده شده", e)
            Toast.makeText(context, "❌ خطا در علامت‌گذاری", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ارسال پاسخ سریع
     */
    private fun sendQuickReply(context: Context, address: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // استفاده از ViewModel برای ارسال پیام
                val viewModel = getViewModel(context)
                viewModel.sendSms(address, message, -1) // استفاده از سیم‌کارت پیش‌فرض

                Log.d(TAG, "📤 پاسخ سریع ارسال شد به $address: $message")

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در ارسال پاسخ سریع", e)
            }
        }
    }

    /**
     * نمایش دیالوگ برای پاسخ
     */
    private fun showReplyDialog(context: Context, address: String, notificationId: Int) {
        try {
            Log.d(TAG, "📝 نمایش دیالوگ پاسخ برای $address")

            // استفاده از Intent برای شروع Activity اصلی با دیالوگ پاسخ
            val replyIntent = android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("show_quick_reply_dialog", true)
                putExtra("address", address)
                putExtra("notification_id", notificationId)
                putExtra("from_notification", true)
            }
            context.startActivity(replyIntent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در نمایش دیالوگ پاسخ", e)
            Toast.makeText(context, "❌ خطا در نمایش کادر پاسخ", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * علامت‌گذاری پیام در دیتابیس
     */
    private fun markMessageAsReadInDatabase(context: Context, address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // استفاده از ViewModel برای علامت‌گذاری
                val viewModel = getViewModel(context)
                viewModel.markConversationAsRead(address)

                Log.d(TAG, "📖 پیام‌های $address در دیتابیس علامت‌گذاری شدند")

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در علامت‌گذاری پیام در دیتابیس", e)
            }
        }
    }

    /**
     * دریافت ViewModel از context
     */
    private fun getViewModel(context: Context): com.example.mysms.viewmodel.HomeViewModel {
        // اگر context یک Activity باشد
        if (context is androidx.activity.ComponentActivity) {
            return androidx.lifecycle.ViewModelProvider(context).get(com.example.mysms.viewmodel.HomeViewModel::class.java)
        }

        // اگر ApplicationContext باشد
        val application = context.applicationContext as android.app.Application
        return com.example.mysms.viewmodel.HomeViewModel(application)
    }

    /**
     * اضافه کردن دکمه "خوانده شده" به نوتیفیکیشن کد تأیید
     */
    fun createMarkAsReadPendingIntent(
        context: Context,
        address: String,
        notificationId: Int
    ): android.app.PendingIntent {
        val markReadIntent = android.content.Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARK_READ_ACTION"
            putExtra("address", address)
            putExtra("notification_id", notificationId)
        }

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // برای اندروید 12+ و RemoteInput باید از FLAG_MUTABLE استفاده شود
                android.app.PendingIntent.getBroadcast(
                    context,
                    address.hashCode() + 3,
                    markReadIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )
            } else {
                android.app.PendingIntent.getBroadcast(
                    context,
                    address.hashCode() + 3,
                    markReadIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }
        } else {
            android.app.PendingIntent.getBroadcast(
                context,
                address.hashCode() + 3,
                markReadIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }


    /**
     * حذف نوتیفیکیشن با آدرس مخاطب
     * این تابع عمومی است و از بیرون قابل فراخوانی
     */
    fun cancelNotificationByAddress(context: Context, address: String) {
        try {
            val notificationId = address.hashCode() and 0x7FFFFFFF
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "🗑️ نوتیفیکیشن با آدرس $address حذف شد (ID: $notificationId)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در حذف نوتیفیکیشن با آدرس", e)
        }
    }

    /**
     * حذف نوتیفیکیشن با شناسه مستقیم
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "🗑️ نوتیفیکیشن $notificationId حذف شد")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در حذف نوتیفیکیشن", e)
        }
    }


    /**
     * دریافت متن پاسخ از RemoteInput
     */
    fun getReplyTextFromIntent(intent: android.content.Intent): String? {
        return try {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            remoteInput?.getCharSequence(KEY_REPLY)?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در دریافت متن از RemoteInput", e)
            null
        }
    }
}