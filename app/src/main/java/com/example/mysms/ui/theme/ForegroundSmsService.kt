package com.example.mysms.ui.theme

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
            // ایجاد کانال جداگانه برای نوتیفیکیشن‌های پیام
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val messageChannel = NotificationChannel(
                    "sms_message_channel",
                    "پیام‌های دریافتی",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "پیام‌های SMS دریافتی"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(messageChannel)
            }

            // Intent برای باز کردن مستقیم چت
            val chatIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_chat", true)
                putExtra("contact_address", address)
                putExtra("notification_clicked", true)
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

            val notification = NotificationCompat.Builder(this, "sms_message_channel")
                .setContentTitle("پیام جدید")
                .setContentText("از: $address")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

            notificationManager.notify(address.hashCode() and 0x7FFFFFFF, notification)
            Log.d(TAG, "📢 Notification shown for: $address")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification: ${e.message}")
        }
    }
}