package com.example.mysms.ui.theme

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val SAMSUNG_MESSAGES_PACKAGE = "com.samsung.android.messaging"
        private const val ANDROID_MESSAGES_PACKAGE = "com.android.mms"
        private const val ANDROID_MESSAGING_PACKAGE = "com.android.messaging"

        // بررسی آیا سرویس فعال است
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(packageName) == true
        }

        // باز کردن تنظیمات برای فعال‌سازی دسترسی
         fun openNotificationSettings(context: Context) {
            try {
                // روش استاندارد
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // چک کن اگر این intent پشتیبانی می‌شود
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // روش جایگزین برای برخی دستگاه‌ها
                    val intent2 = Intent()
                    intent2.action = "android.settings.NOTIFICATION_LISTENER_SETTINGS"
                    intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening notification settings", e)

                // روش fallback: باز کردن تنظیمات اصلی
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                // Toast راهنما
                Toast.makeText(
                    context,
                    "لطفاً در تنظیمات: بخش 'دسترسی ویژه' > 'دسترسی به اعلان‌ها' را پیدا کنید",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ NotificationListener service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "🔗 NotificationListener connected")

        // بررسی نوتیفیکیشن‌های فعلی
        try {
            val activeNotifications = activeNotifications
            Log.d(TAG, "📊 Active notifications: ${activeNotifications.size}")

            activeNotifications.forEach { sbn ->
                checkAndCancelDuplicateSmsNotification(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val notificationId = sbn.id
            val tag = sbn.tag
            val key = sbn.key

            Log.d(TAG, "📨 Notification from: $packageName")
            Log.d(TAG, "  ID: $notificationId, Tag: $tag, Key: $key")

            // اگر از اپ ماست، کاری نکن
            if (packageName == this.packageName) {
                Log.d(TAG, "✅ This is our own notification, ignoring")
                return
            }

            // لیست اپ‌های پیامکی که باید نوتیفیکیشن آن‌ها را حذف کنیم
            val smsAppsToBlock = listOf(
                "com.google.android.apps.messaging",  // Google Messages
                "com.samsung.android.messaging",      // Samsung Messages
                "com.android.mms",                    // Android MMS
                "com.android.messaging",              // Android Messaging
                "org.thoughtcrime.securesms",         // Signal
                "com.whatsapp",                       // WhatsApp
                "org.telegram.messenger",             // Telegram
                "com.viber.voip",                     // Viber
                "com.skype.raider"                    // Skype
            )

            // بررسی محتوای نوتیفیکیشن
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getString("android.text") ?: ""

            Log.d(TAG, "  Title: $title")
            Log.d(TAG, "  Text: $text")

            // بررسی کلمات کلیدی SMS
            val smsKeywords = listOf(
                "SMS", "پیام", "پیامک", "Message", "رسید", "Received",
                "New message", "پیام جدید", "متن", "Text", "MMS",
                "کد تأیید", "کد احراز", "کد ورود", "Verification", "Code"
            )

            val isSmsNotification = smsKeywords.any { keyword ->
                title.contains(keyword, ignoreCase = true) ||
                        text.contains(keyword, ignoreCase = true)
            }

            // اگر از اپ پیامکی است و محتوای SMS دارد
            if (smsAppsToBlock.contains(packageName) && isSmsNotification) {
                Log.d(TAG, "⚠️ Detected SMS notification from: $packageName")
                Log.d(TAG, "🔄 Attempting to cancel...")

                // حذف نوتیفیکیشن
                safeCancelNotification(packageName, tag, notificationId)

                // لاگ در فایل برای دیباگ
                Log.i(TAG, "✅ CANCELLED: SMS notification from $packageName")
                Log.i(TAG, "   Title: $title")
                Log.i(TAG, "   Text: ${text.take(50)}...")
            } else {
                Log.d(TAG, "📱 Not an SMS notification, ignoring")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onNotificationPosted", e)
        }
    }

    /**
     * بررسی آیا نوتیفیکیشن از اپ پیامکی دیگر است
     */
    private fun isOtherSmsAppNotification(packageName: String, sbn: StatusBarNotification): Boolean {
        // لیست اپ‌های پیامکی که نوتیفیکیشن آن‌ها را حذف می‌کنیم
        val otherSmsApps = listOf(
            GOOGLE_MESSAGES_PACKAGE,
            SAMSUNG_MESSAGES_PACKAGE,
            ANDROID_MESSAGES_PACKAGE,
            ANDROID_MESSAGING_PACKAGE
        )

        // اگر از اپ خودمان است، کاری نکن
        if (packageName == this.packageName) {
            return false
        }

        // بررسی محتوای نوتیفیکیشن
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")

        // بررسی کلمات کلیدی در نوتیفیکیشن
        val hasSmsKeywords = containsSmsKeywords(title, text)

        // اگر از اپ پیامکی دیگر است و محتوای SMS دارد
        return otherSmsApps.contains(packageName) && hasSmsKeywords
    }

    /**
     * بررسی وجود کلمات کلیدی SMS در متن نوتیفیکیشن
     */
    private fun containsSmsKeywords(title: String?, text: String?): Boolean {
        val combinedText = (title ?: "") + (text ?: "")

        // کلمات کلیدی فارسی و انگلیسی
        val keywords = listOf(
            "SMS", "پیام", "پیامک", "Message", "رسید", "Received",
            "New message", "پیام جدید", "متن", "Text", "MMS"
        )

        return keywords.any { keyword ->
            combinedText.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * حذف یک نوتیفیکیشن خاص
     */
    private fun safeCancelNotification(packageName: String, tag: String?, id: Int) {
        try {
            if (isNotificationServiceEnabled(this)) {
                // استفاده از متد اصلی با استفاده از super
                super.cancelNotification(packageName, tag, id)
                Log.d(TAG, "🗑️ Notification cancelled: $packageName - $id")
            } else {
                Log.w(TAG, "⚠️ Notification service not enabled, cannot cancel")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "🔒 Security exception when cancelling notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling notification", e)
        }
    }

    /**
     * بررسی و حذف نوتیفیکیشن‌های تکراری SMS
     */
    private fun checkAndCancelDuplicateSmsNotification(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName

            // اگر از اپ‌های پیامکی دیگر است
            if (isOtherSmsAppNotification(packageName, sbn)) {
                Log.d(TAG, "🔄 Found existing duplicate SMS notification from: $packageName")
                safeCancelNotification(packageName, sbn.tag, sbn.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking duplicate notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // لاگ برای دیباگ
        Log.d(TAG, "➖ Notification removed: ${sbn.packageName}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 NotificationListener service destroyed")
    }
}