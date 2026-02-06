package com.example.mysms.ui.theme

import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.mysms.R
import kotlinx.coroutines.*

class ForegroundSmsService : Service() {

    companion object {
        private const val CHANNEL_ID = "foreground_sms_service_channel"
        private const val CHANNEL_NAME = "سرویس پیام‌رسان"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "ForegroundSmsService"

        // متد استاتیک برای شروع سرویس
        fun startService(context: Context) {
            val intent = Intent(context, ForegroundSmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // متد استاتیک برای توقف سرویس
        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundSmsService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🟢 ForegroundSmsService created")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Service started")

        // ایجاد نوتیفیکیشن foreground
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // شروع کارهای پس‌زمینه
        startBackgroundTasks()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "🛑 Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "سرویس اجرای پیوسته پیام‌رسان برای دریافت پیام‌ها"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        // Intent برای باز کردن اپ
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("پیام‌رسان در حال اجرا")
            .setContentText("آماده دریافت پیام‌های جدید")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true) // بدون صدا و ویبره
            .build()
    }

    private fun startBackgroundTasks() {
        // 1. چک دوره‌ای برای پیام‌های جدید
        serviceScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "🔄 Checking for new messages...")

                    // 2. به صورت دوره‌یی SMS Provider را چک کن
                    checkSmsProvider()

                    delay(5 * 60 * 1000) // هر 5 دقیقه

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in background task: ${e.message}")
                    delay(10 * 60 * 1000) // اگر خطا داشت، 10 دقیقه صبر کن
                }
            }
        }
    }

    private suspend fun checkSmsProvider() {
        withContext(Dispatchers.IO) {
            try {
                // چک کردن SMS Provider برای پیام‌های جدید
                val cursor = contentResolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    "${android.provider.Telephony.Sms.DATE} DESC LIMIT 10"
                )

                cursor?.use {
                    // اگر پیام جدیدی پیدا شد، نوتیفیکیشن بده
                    if (it.moveToFirst()) {
                        val addressIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                        val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                        val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)

                        if (addressIdx != -1 && bodyIdx != -1) {
                            val address = it.getString(addressIdx)
                            val body = it.getString(bodyIdx)
                            val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L

                            // اگر پیام جدید است (مثلاً در ۲ دقیقه گذشته)
                            if (System.currentTimeMillis() - date < 2 * 60 * 1000) {
                                showNewMessageNotification(address, body)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking SMS provider: ${e.message}")
            }
        }
    }

    // ==================== متدهای کمکی ====================

    /**
     * نمایش نوتیفیکیشن پیام جدید حتی وقتی اپ بسته است
     */
    fun showNewMessageNotification(address: String, body: String) {
        try {
            // ۱. بررسی برنامه پیش‌فرض
            val isDefaultApp = try {
                packageName == Telephony.Sms.getDefaultSmsPackage(this)
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در بررسی برنامه پیش‌فرض", e)
                false
            }

            // ۲. بررسی NotificationListener
            val isNotificationListenerEnabled = try {
                val packageName = packageName
                val flat = Settings.Secure.getString(
                    contentResolver,
                    "enabled_notification_listeners"
                )
                flat?.contains(packageName) == true
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در بررسی NotificationListener", e)
                false
            }

            // ۳. منطق تصمیم‌گیری
            when {
                // اگر برنامه پیش‌فرض است → نوتیفیکیشن نده (سیستم خودش می‌دهد)
                isDefaultApp -> {
                    Log.d(TAG, "✅ برنامه پیش‌فرض است - نوتیفیکیشن نمایش داده نمی‌شود")
                    return
                }

                // اگر NotificationListener فعال است → نوتیفیکیشن نده (خودمان حذف می‌کنیم)
                isNotificationListenerEnabled -> {
                    Log.d(TAG, "✅ NotificationListener فعال است - نوتیفیکیشن نمایش داده نمی‌شود")
                    return
                }

                // در غیر این صورت → نوتیفیکیشن بده
                else -> {
                    Log.d(TAG, "📢 نمایش نوتیفیکیشن (برنامه پیش‌فرض نیست و NotificationListener فعال نیست)")
                }
            }

            // ۴. دریافت نام مخاطب
            val displayName = getContactName(address) ?: address

            // ۵. ایجاد کانال نوتیفیکیشن
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val messageChannel = NotificationChannel(
                    "sms_message_channel",
                    "پیام‌های دریافتی",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "پیام‌های SMS دریافتی"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    vibrationPattern = longArrayOf(100, 200, 100, 200)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(null, null) // بدون صدا - فقط ویبره
                }
                notificationManager.createNotificationChannel(messageChannel)
            }

            // ۶. Intent برای باز کردن مستقیم چت
            val chatIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_chat", true)
                putExtra("contact_address", address)
                putExtra("notification_clicked", true)
                putExtra("contact_name", displayName)
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    this,
                    address.hashCode(),
                    chatIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getActivity(
                    this,
                    address.hashCode(),
                    chatIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // ۷. ایجاد Action برای پاسخ سریع
            val replyIntent = Intent(this, SmsReceiver::class.java).apply {
                action = "REPLY_ACTION"
                putExtra("address", address)
                putExtra("message_id", "temp_${System.currentTimeMillis()}")
            }

            val replyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 1,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 1,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // ۸. ساخت نوتیفیکیشن
            val notification = NotificationCompat.Builder(this, "sms_message_channel")
                .setContentTitle("📩 از: $displayName")
                .setContentText(body.take(50))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE) // فقط ویبره
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .addAction(
                    android.R.drawable.ic_menu_send,
                    "پاسخ",
                    replyPendingIntent
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()

            // ۹. نمایش نوتیفیکیشن
            notificationManager.notify(address.hashCode() and 0x7FFFFFFF, notification)
            Log.d(TAG, "✅ نوتیفیکیشن نمایش داده شد برای: $displayName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در نمایش نوتیفیکیشن: ${e.message}")
        }
    }

    // تابع کمکی برای دریافت نام مخاطب
    private fun getContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در دریافت نام مخاطب", e)
            null
        }
    }
}