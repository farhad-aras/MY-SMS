package com.example.mysms.ui.theme


import com.example.mysms.ui.theme.NotificationManager as ActionNotificationManager
import androidx.core.app.RemoteInput
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

        // بررسی آیا باید نوتیفیکیشن نمایش دهیم
        if (intent?.hasExtra("show_notification") == true) {
            val address = intent.getStringExtra("address") ?: ""
            val body = intent.getStringExtra("body") ?: ""

            if (address.isNotEmpty() && body.isNotEmpty()) {
                Log.d(TAG, "📢 نمایش نوتیفیکیشن از Receiver برای: $address")
                showNewMessageNotification(address, body)
            }
        }

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
        // 1. چک دوره‌ای برای پیام‌های جدید با سینک هوشمند
        serviceScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "🔄 Background: Checking for new messages...")

                    // 2. سینک افزایشی پیام‌های جدید
                    performBackgroundIncrementalSync()

                    delay(5 * 60 * 1000) // هر 5 دقیقه

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in background task: ${e.message}")
                    delay(10 * 60 * 1000) // اگر خطا داشت، 10 دقیقه صبر کن
                }
            }
        }

        // 3. چک سلامت سرویس هر 30 دقیقه
        serviceScope.launch {
            while (isActive) {
                try {
                    delay(30 * 60 * 1000) // هر 30 دقیقه
                    Log.d(TAG, "🏥 Background: Service health check")
                    // می‌توانید لاگ‌های اضافی یا چک‌های سلامت اضافه کنید
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in health check", e)
                }
            }
        }
    }

    /**
     * انجام سینک افزایشی در پس‌زمینه
     */
    private suspend fun performBackgroundIncrementalSync() {
        try {
            // 1. دریافت زمان آخرین سینک از SharedPreferences
            val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0L)

            Log.d(TAG, "📡 Background sync: lastSync=$lastSyncTime")

            // 2. فقط اگر بیش از 1 دقیقه از آخرین سینک گذشته باشد
            val now = System.currentTimeMillis()
            if (now - lastSyncTime < 60 * 1000) {
                Log.d(TAG, "⏭️ Background sync skipped: too recent")
                return
            }

            // 3. خواندن پیام‌های جدید از SMS Provider
            val cursor = contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                null,
                "${android.provider.Telephony.Sms.DATE} > $lastSyncTime",
                null,
                "${android.provider.Telephony.Sms.DATE} DESC LIMIT 20"
            )

            var newMessageCount = 0
            cursor?.use {
                val addrIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val address = if (addrIdx != -1) it.getString(addrIdx) else "Unknown"
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) else ""
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else now

                    // نمایش نوتیفیکیشن برای پیام جدید
                    if (body.isNotEmpty() && address != "Unknown") {
                        showNewMessageNotification(address, body)
                        newMessageCount++
                    }
                }
            }

            cursor?.close()

            // 4. آپدیت زمان آخرین چک
            if (newMessageCount > 0) {
                prefs.edit().putLong("last_background_check", now).apply()
                Log.d(TAG, "✅ Background sync: Found $newMessageCount new messages")
            } else {
                Log.d(TAG, "📭 Background sync: No new messages")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "🔒 Background sync permission error", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync error", e)
        }
    }

    private suspend fun checkSmsProvider() {
        withContext(Dispatchers.IO) {
            try {
                // فقط پیام‌های خوانده نشده را چک کن (read = 0)
                val cursor = contentResolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    null,
                    "read = 0", // فقط پیام‌های خوانده نشده
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

// چک کردن آیا پیام خوانده شده است
                            val readIdx = it.getColumnIndex(android.provider.Telephony.Sms.READ)
                            val isRead = if (readIdx != -1) it.getInt(readIdx) == 1 else false

// اگر پیام جدید است (در ۲ دقیقه گذشته) و خوانده نشده
                            if (!isRead && System.currentTimeMillis() - date < 2 * 60 * 1000) {
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
            // ۱. بررسی برنامه پیش‌فرض و NotificationListener
            val isDefaultApp = try {
                packageName == Telephony.Sms.getDefaultSmsPackage(this)
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در بررسی برنامه پیش‌فرض", e)
                false
            }

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

            // فقط اگر برنامه پیش‌فرض است، نوتیفیکیشن نده
            if (isDefaultApp) {
                Log.d(TAG, "✅ برنامه پیش‌فرض است - نوتیفیکیشن نمایش داده نمی‌شود")
                return
            }

            // چک کردن آیا پیام قبلاً خوانده شده است
           /* val isAlreadyRead = isMessageAlreadyReadInDatabase(address, body)
            if (isAlreadyRead) {
                Log.d(TAG, "📭 پیام قبلاً خوانده شده است - نوتیفیکیشن نمایش داده نمی‌شود")
                return
            }*/

// NotificationListener را چک نکن - اجازه بده نوتیفیکیشن نمایش داده شود
// (NotificationListener خودش نوتیفیکیشن‌های تکراری را حذف می‌کند)
            Log.d(TAG, "📢 NotificationListener فعال است، اما نوتیفیکیشن نمایش داده می‌شود")

            // ۲. بررسی نوع پیام (کد تأیید یا پیام معمولی)
            val isVerificationCode = isVerificationCodeMessage(body)

            Log.d(TAG, "📊 تشخیص نوع پیام: verification=$isVerificationCode, متن: ${body.take(30)}...")

            // ۳. نمایش نوتیفیکیشن مناسب
            if (isVerificationCode) {
                showVerificationCodeNotification(address, body)
            } else {
                showNormalMessageNotification(address, body)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در نمایش نوتیفیکیشن: ${e.message}")
        }
    }

    /**
     * بررسی آیا پیام حاوی کد تأیید است - نسخه ساده‌تر
     */
    private fun isVerificationCodeMessage(message: String): Boolean {
        val verificationPatterns = listOf(
            Regex("""\b\d{4,6}\b"""), // کد ۴-۶ رقمی
            Regex("""کد.*?(\d{4,6})"""),
            Regex("""code.*?(\d{4,6})""", RegexOption.IGNORE_CASE),
            Regex("""رمز.*?(\d{4,6})"""),
            Regex("""verification.*?(\d{4,6})""", RegexOption.IGNORE_CASE),
            Regex("""تأیید.*?(\d{4,6})"""),
            Regex("""otp.*?(\d{4,6})""", RegexOption.IGNORE_CASE)
        )

        return verificationPatterns.any { pattern ->
            pattern.containsMatchIn(message)
        }
    }

    /**
     * نمایش نوتیفیکیشن کد تأیید
     */
    private fun showVerificationCodeNotification(address: String, body: String) {
        try {
            // استخراج کد از متن
            val code = extractVerificationCode(body)
            val displayName = getContactDisplayName(address) ?: address

            // ایجاد کانال برای کدهای تأیید
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val codeChannel = NotificationChannel(
                    "verification_code_channel",
                    "کدهای تأیید",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "کدهای تأیید SMS"
                    enableLights(true)
                    lightColor = Color.GREEN
                    enableVibration(true)
                    vibrationPattern = longArrayOf(100, 100, 100, 100)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(null, null)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(codeChannel)
            }

            // Intent برای کپی کد
            val copyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = "COPY_CODE_ACTION"
                putExtra("code", code)
                putExtra("address", address)
            }

// دکمه خوانده شده برای کدهای تأیید
            val markReadPendingIntent = ActionNotificationManager.createMarkAsReadPendingIntent(
                this, address, address.hashCode() and 0x7FFFFFFF
            )

            val copyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 100,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 100,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

// Intent برای باز کردن چت با notification_id
            val chatIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_chat", true)
                putExtra("contact_address", address)
                putExtra("notification_clicked", true)
                // اضافه کردن notification_id برای حذف نوتیفیکیشن در MainActivity
                putExtra("notification_id", address.hashCode() and 0x7FFFFFFF)
            }

            val chatPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

            // ساخت نوتیفیکیشن
            val notification = NotificationCompat.Builder(this, "verification_code_channel")
                .setContentTitle("🔐 کد تأیید از $displayName")
                .setContentText("کد: $code")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setContentIntent(chatPendingIntent)
                .setAutoCancel(false) // چون در MainActivity حذف می‌کنیم
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(chatPendingIntent, true)
                .addAction(
                    android.R.drawable.ic_menu_save,
                    "📋 کپی کد",
                    copyPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "✅ خوانده شد",
                    markReadPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "📨 باز کردن چت",
                    chatPendingIntent
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setTimeoutAfter(30000)
                .build()

            // نمایش نوتیفیکیشن
            notificationManager.notify(address.hashCode() and 0x7FFFFFFF, notification)
            Log.d(TAG, "✅ نوتیفیکیشن کد تأیید نمایش داده شد: $code")

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در نمایش نوتیفیکیشن کد تأیید", e)
        }
    }

    /**
     * نمایش نوتیفیکیشن پیام معمولی
     */
    private fun showNormalMessageNotification(address: String, body: String) {
        try {
            val displayName = getContactDisplayName(address) ?: address

            // ایجاد کانال برای پیام‌های معمولی
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
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(messageChannel)
            }

// Intent برای باز کردن چت با notification_id
            val chatIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_chat", true)
                putExtra("contact_address", address)
                putExtra("notification_clicked", true)
                // اضافه کردن notification_id برای حذف نوتیفیکیشن در MainActivity
                putExtra("notification_id", address.hashCode() and 0x7FFFFFFF)
            }

            val chatPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

// ایجاد RemoteInput برای دریافت پاسخ از نوتیفیکیشن
            val remoteInput = RemoteInput.Builder(ActionNotificationManager.KEY_REPLY)
                .setLabel("پاسخ خود را وارد کنید")
                .build()

// Intent برای پاسخ سریع
            val replyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = "QUICK_REPLY_ACTION"
                putExtra("address", address)
                putExtra("notification_id", address.hashCode() and 0x7FFFFFFF)
            }

            val replyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // برای اندروید 12+ و RemoteInput باید از FLAG_MUTABLE استفاده شود
                    PendingIntent.getBroadcast(
                        this,
                        address.hashCode() + 1,
                        replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                } else {
                    PendingIntent.getBroadcast(
                        this,
                        address.hashCode() + 1,
                        replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            } else {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 1,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

// ایجاد Action با RemoteInput
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, // استفاده از android.R به جای R
                "💬 پاسخ",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            // Intent برای علامت‌گذاری به عنوان خوانده شده
            val markReadIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = "MARK_READ_ACTION"
                putExtra("address", address)
                putExtra("notification_id", address.hashCode() and 0x7FFFFFFF)
            }

            val markReadPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // برای اندروید 12+ باید از FLAG_MUTABLE استفاده شود
                    PendingIntent.getBroadcast(
                        this,
                        address.hashCode() + 2,
                        markReadIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                } else {
                    PendingIntent.getBroadcast(
                        this,
                        address.hashCode() + 2,
                        markReadIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            } else {
                PendingIntent.getBroadcast(
                    this,
                    address.hashCode() + 2,
                    markReadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // ساخت نوتیفیکیشن
            val notification = NotificationCompat.Builder(this, "sms_message_channel")
                .setContentTitle("📩 از: $displayName")
                .setContentText(body.take(50))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setContentIntent(chatPendingIntent)
                .setAutoCancel(false) // چون در MainActivity حذف می‌کنیم
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .addAction(replyAction) // استفاده از replyAction که شامل RemoteInput است
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "✅ خوانده شد",
                    markReadPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_recent_history,
                    "📖 باز کردن",
                    chatPendingIntent
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()

            // نمایش نوتیفیکیشن
            notificationManager.notify(address.hashCode() and 0x7FFFFFFF, notification)
            Log.d(TAG, "✅ نوتیفیکیشن پیام معمولی نمایش داده شد برای: $displayName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در نمایش نوتیفیکیشن پیام معمولی", e)
        }
    }

    /**
     * استخراج هوشمند کد تأیید از متن پیام بانکی
     */
    private fun extractVerificationCode(text: String): String {
        try {
            Log.d(TAG, "🔍 جستجوی کد در متن: ${text.take(50)}...")

            // 1. خطوط متن را جدا کن
            val lines = text.split("\n").map { it.trim() }

            // 2. کلمات کلیدی اصلی
            val primaryKeywords = listOf(
                "رمز", "کد", "code", "Code", "پویا", "pin", "PIN", "تأیید", "ورود", "verify"
            )

            // 3. الگوهای کامل برای جستجو
            val patterns = listOf(
                // فرمت: "رمز 123456"
                Regex("""(رمز|کد|code|Code|پویا)[\s:]*(\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "رمز: 123456"
                Regex("""(رمز|کد|code|Code|پویا)[\s:]*[:]?[\s]*(\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "G-123456"
                Regex("""G[-](\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "#12345"
                Regex("""#(\d{4,8})"""),
                // فرمت: "کد محرمانه ... 12345"
                Regex("""کد[\s\S]{0,30}?(\d{4,8})"""),
                // فرمت: "code is 12345"
                Regex("""(code|Code|verification)[\s\S]{0,20}?(\d{4,8})""", RegexOption.IGNORE_CASE)
            )

            // 4. اولویت ۱: جستجو در کل متن با الگوها
            for (pattern in patterns) {
                val match = pattern.find(text)
                if (match != null) {
                    // گروه 1 یا 2 را بگیر (بسته به الگو)
                    val code = when {
                        match.groups.size >= 3 && match.groups[2] != null -> match.groups[2]!!.value
                        match.groups.size >= 2 && match.groups[1] != null -> match.groups[1]!!.value
                        else -> match.value.replace(Regex("""[^\d]"""), "")
                    }

                    if (code.length in 4..8) {
                        Log.d(TAG, "✅ کد یافت شد (الگو): $code")
                        return code
                    }
                }
            }

            // 5. اولویت ۲: جستجو خط به خط
            for (line in lines) {
                // خطوطی که کلمه کلیدی دارند
                if (primaryKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
                    Log.d(TAG, "📄 بررسی خط: $line")

                    // پیدا کردن آخرین عدد ۴-۸ رقمی در این خط
                    val numbers = Regex("""\b(\d{4,8})\b""").findAll(line).toList()

                    if (numbers.isNotEmpty()) {
                        // آخرین عدد در خط (کد معمولاً آخر است)
                        val lastNumber = numbers.last().value

                        // بررسی که مبلغ نباشد (خطوط مبلغ معمولاً "ریال" یا "مبلغ" دارند)
                        val isAmount = line.contains("ریال") || line.contains("مبلغ") ||
                                line.contains("تومان") || line.contains("قیمت")

                        // بررسی که زمان نباشد
                        val isTime = Regex("""\d{1,2}:\d{1,2}(:\d{1,2})?""").containsMatchIn(line)

                        if (!isAmount && !isTime) {
                            Log.d(TAG, "✅ کد یافت شد (خط): $lastNumber")
                            return lastNumber
                        } else {
                            Log.d(TAG, "⏭️ عدد رد شد (مبلغ/زمان): $lastNumber")
                        }
                    }
                }
            }

            // 6. اولویت ۳: پیدا کردن تمام اعداد و انتخاب بهترین
            val allNumbers = Regex("""\b(\d{4,8})\b""").findAll(text).toList()

            if (allNumbers.isNotEmpty()) {
                // امتیازدهی به هر عدد
                val scored = mutableListOf<Pair<String, Int>>()

                for (match in allNumbers) {
                    val number = match.value
                    val startPos = match.range.first
                    var score = 0

                    // امتیاز طول
                    when (number.length) {
                        4 -> score += 20
                        5 -> score += 30  // کدهای ۵ رقمی رایج‌تر
                        6 -> score += 25
                        7 -> score += 15
                        8 -> score += 10
                    }

                    // متن اطراف عدد (۱۰ کاراکتر قبل و بعد)
                    val contextStart = maxOf(0, startPos - 10)
                    val contextEnd = minOf(text.length, startPos + number.length + 10)
                    val context = text.substring(contextStart, contextEnd).lowercase()

                    // امتیاز مثبت برای کلمات کلیدی نزدیک
                    if (primaryKeywords.any { context.contains(it.lowercase()) }) {
                        score += 50
                    }

                    // امتیاز منفی برای کلمات مربوط به مبلغ/زمان
                    if (context.contains("ریال") || context.contains("مبلغ") ||
                        context.contains("تومان") || context.contains("قیمت")) {
                        score -= 100
                    }

                    if (context.contains(":") && Regex("""\d{1,2}:\d{1,2}""").containsMatchIn(context)) {
                        score -= 50
                    }

                    // امتیاز برای موقعیت (کد معمولاً در نیمه دوم متن است)
                    if (startPos > text.length / 2) {
                        score += 20
                    }

                    scored.add(Pair(number, score))
                }

                // انتخاب بهترین امتیاز
                val best = scored.maxByOrNull { it.second }
                if (best != null && best.second > 30) {
                    Log.d(TAG, "✅ کد یافت شد (بهترین): ${best.first} (امتیاز: ${best.second})")
                    return best.first
                }
            }

            // 7. اولویت ۴: جستجوی اعداد بعد از کاراکترهای خاص
            val specialPatterns = listOf(
                Regex("""[:]\s*(\d{4,8})"""),      // بعد از :
                Regex("""[-]\s*(\d{4,8})"""),      // بعد از -
                Regex("""[#]\s*(\d{4,8})"""),      // بعد از #
                Regex("""is\s+(\d{4,8})""", RegexOption.IGNORE_CASE)  // بعد از is
            )

            for (pattern in specialPatterns) {
                val match = pattern.find(text)
                if (match != null && match.groups.size > 1) {
                    val code = match.groups[1]?.value
                    if (!code.isNullOrEmpty() && code.length in 4..8) {
                        Log.d(TAG, "✅ کد یافت شد (کاراکتر خاص): $code")
                        return code
                    }
                }
            }

            Log.d(TAG, "❌ کد یافت نشد")
            return "کد یافت نشد"

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در استخراج کد", e)
            return "خطا در شناسایی"
        }
    }

    /**
     * دریافت نام مخاطب
     */
    private fun getContactDisplayName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
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


    // تشخیص اینکه پیام کد تأیید است یا پیام معمولی
    private fun isVerificationCode(body: String): Boolean {
        val verificationKeywords = listOf(
            "کد تأیید", "کد احراز", "کد ورود", "کد فعال‌سازی",
            "verification", "code", "رمز", "otp", "pin",
            "کد امنیتی", "کد عبور", "کد یکبار مصرف"
        )

        val containsCode = body.contains(Regex("""\d{4,6}""")) // ۴ تا ۶ رقم
        val containsKeyword = verificationKeywords.any { keyword ->
            body.contains(keyword, ignoreCase = true)
        }

        return containsCode && containsKeyword
    }

    /**
     * بررسی آیا پیام قبلاً در دیتابیس علامت‌گذاری شده است
     */
    /**
     * بررسی آیا پیام قبلاً در دیتابیس علامت‌گذاری شده است
     */
  /*  private suspend fun isMessageAlreadyReadInDatabase(address: String, body: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val database = com.example.mysms.data.AppDatabase.getDatabase(this@ForegroundSmsService)
                val smsDao = database.smsDao()

                // دریافت تمام پیام‌های این مخاطب
                val messages = smsDao.getSmsByAddressFlow(address)

                // گرفتن اولین مقدار از Flow
                var foundRead = false
                val job = launch {
                    messages.collect { messageList ->
                        // بررسی آیا پیام مشابه خوانده شده وجود دارد
                        foundRead = messageList.any { message ->
                            message.address == address &&
                                    message.body.contains(body.take(20)) && // مقایسه بخشی از متن
                                    message.read
                        }
                        if (foundRead) {
                            cancel() // اگر پیدا شد، جمع‌آوری را متوقف کن
                        }
                    }
                }

                // کمی صبر کن برای دریافت داده
                delay(500)
                job.cancel()

                Log.d(TAG, "🔍 بررسی وضعیت خوانده شدن: address=$address, foundRead=$foundRead")
                return@withContext foundRead

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در بررسی وضعیت خوانده شدن پیام", e)
                return@withContext false
            }
        }
    }*/

}