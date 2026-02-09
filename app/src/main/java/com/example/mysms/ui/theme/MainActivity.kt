    package com.example.mysms.ui.theme

    import android.app.NotificationManager as AndroidNotificationManager
    import MySMSApp
    import android.provider.Telephony
    import android.content.Intent
    import android.content.Context
    import android.content.pm.PackageManager
    import android.os.Build
    import android.os.Bundle
    import android.util.Log
    import android.widget.Toast
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
     import androidx.compose.material3.*
     import com.example.mysms.data.SmsEntity

    
    class MainActivity : ComponentActivity() {
    
        private var backPressTime: Long = 0
    
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Log.d("MainActivity", "🟢 Activity created")
    
            // ============  بررسی اپ پیش‌فرض ============
    
    
            // ۱. درخواست نقش اپ پیش‌فرض SMS
            DefaultSmsDisabler.disableDefaultSmsNotifications(this)
    
            // ۲. مخفی کردن نوتیفیکیشن‌های پیش‌فرض
            DefaultSmsDisabler.hideDefaultNotifications(this)

// بررسی Intent برای بازشدن از نوتیفیکیشن و حذف نوتیفیکیشن
            handleNotificationIntent(intent)

// حذف نوتیفیکیشن اگر از طریق کلیک باز شده باشد
            if (intent?.hasExtra("notification_clicked") == true ||
                intent?.hasExtra("notification_id") == true) {
                cancelNotificationFromIntent(intent)
            }

// بررسی Intent برای پاسخ سریع از NotificationActionReceiver
            val showQuickReplyDialog = intent.getBooleanExtra("show_quick_reply_dialog", false)
            val replyAddress = intent.getStringExtra("address")
            val notificationId = intent.getIntExtra("notification_id", 0)
            val fromNotification = intent.getBooleanExtra("from_notification", false)

// همچنین برای سازگاری با نسخه قدیمی
            val quickReply = intent.getBooleanExtra("quick_reply", false)
            val quickReplyTest = intent.getBooleanExtra("quick_reply_test", false)

            if ((showQuickReplyDialog || fromNotification || quickReply || quickReplyTest)
                && !replyAddress.isNullOrEmpty()) {

                Log.d("MainActivity", "💬 دریافت درخواست پاسخ سریع برای: $replyAddress (notificationId: $notificationId)")

                // محاسبه notificationId اگر 0 بود
                val actualNotificationId = if (notificationId == 0) {
                    replyAddress.hashCode() and 0x7FFFFFFF
                } else {
                    notificationId
                }

                // ذخیره در SharedPreferences برای استفاده در Composable
                val prefs = getSharedPreferences("quick_reply_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("show_quick_reply_dialog", true)
                    putString("reply_address", replyAddress)
                    putInt("notification_id", actualNotificationId)
                    apply()
                }

                // لاگ برای دیباگ
                Log.d("MainActivity", "💾 Saved to prefs: address=$replyAddress, id=$actualNotificationId")
            }
    
            setContent {
                MaterialTheme {
                    MySMSApp()
                }
            }
        }

        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            Log.d("MainActivity", "🔄 New Intent received")

            // بررسی Intent جدید (مثلاً کلیک روی نوتیفیکیشن)
            handleNotificationIntent(intent)

            // حذف نوتیفیکیشن اگر از طریق کلیک باز شده باشد
            if (intent.hasExtra("notification_clicked") ||
                intent.hasExtra("notification_id")) {
                cancelNotificationFromIntent(intent)
            }
        }
    
        private fun handleNotificationIntent(intent: Intent?) {
            if (intent == null) return
    
            Log.d("MainActivity", "🔍 Checking intent extras: ${intent.extras?.keySet()}")
    
            // بررسی آیا از نوتیفیکیشن باز شده است؟
            val openChat = intent.getBooleanExtra("open_chat", false)
            val contactAddress = intent.getStringExtra("contact_address")
            val notificationClicked = intent.getBooleanExtra("notification_clicked", false)
    
            if ((openChat || notificationClicked) && !contactAddress.isNullOrEmpty()) {
                Log.d("MainActivity", "🎯 Opening chat from notification for: $contactAddress")
    
                // ذخیره اطلاعات برای استفاده در Composable
                val prefs = getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("should_open_chat", true)
                    putString("chat_address", contactAddress)
                    putString("chat_name", intent.getStringExtra("contact_name"))
                    apply()
                }
    
                // نمایش Toast
                Toast.makeText(
                    this,
                    "در حال بازکردن چت با $contactAddress",
                    Toast.LENGTH_SHORT
                ).show()
// حذف نوتیفیکیشن این مخاطب
                val notificationId = intent.getIntExtra("notification_id", 0)
                if (notificationId != 0) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                    notificationManager.cancel(notificationId)
                    Log.d("MainActivity", "🗑️ نوتیفیکیشن $notificationId حذف شد (از handleNotificationIntent)")
                }
            }
        }

        /**
         * حذف نوتیفیکیشن بر اساس notification_id از Intent
         */
        private fun cancelNotificationFromIntent(intent: Intent) {
            try {
                val notificationId = intent.getIntExtra("notification_id", 0)
                if (notificationId != 0) {
                    // حذف نوتیفیکیشن با استفاده از NotificationManager
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                    notificationManager.cancel(notificationId)

                    Log.d("MainActivity", "🗑️ نوتیفیکیشن $notificationId حذف شد (از Intent)")

                    // همچنین notification با hashCode آدرس را هم حذف کن
                    val address = intent.getStringExtra("contact_address")
                    if (!address.isNullOrEmpty()) {
                        val alternativeNotificationId = address.hashCode() and 0x7FFFFFFF
                        if (alternativeNotificationId != notificationId) {
                            notificationManager.cancel(alternativeNotificationId)
                            Log.d("MainActivity", "🗑️ نوتیفیکیشن جایگزین $alternativeNotificationId حذف شد")
                        }
                    }
                } else {
                    Log.w("MainActivity", "⚠️ notification_id = 0, حذف انجام نشد")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ خطا در حذف نوتیفیکیشن از Intent", e)
            }
        }



        fun startForegroundServiceIfNeeded() {
            try {
                Log.d("MainActivity", "🚀 Starting services...")

                // 1. شروع JobScheduler (برای اندروید 5+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    SmsJobService.scheduleJob(this)
                }

                // 2. شروع Foreground Service (برای نمایش نوتیفیکیشن)
                val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasNotificationPermission) {
                    ForegroundSmsService.startService(this)
                    Log.d("MainActivity", "✅ Services started")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error starting services: ${e.message}", e)
            }
        }


        // ==================== توابع بررسی برنامه پیش‌فرض ====================

        /**
         * بررسی آیا برنامه به عنوان برنامه پیش‌فرض پیامک تنظیم شده است
         */
        fun isDefaultSmsApp(): Boolean {
            return packageName == Telephony.Sms.getDefaultSmsPackage(this)
        }

        /**
         * باز کردن صفحه تنظیمات برای انتخاب برنامه پیش‌فرض پیامک
         */


    
        private fun stopForegroundServiceIfNeeded() {
            try {
                Log.d("MainActivity", "🛑 Stopping foreground service...")
                ForegroundSmsService.stopService(this)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping foreground service: ${e.message}")
            }
        }
    
        // ==================== کنترل دکمه فیزیکی Back ====================
    
        override fun onBackPressed() {
            // اجازه دهید BackHandler در Composable کنترل کند
            // اگر BackHandler نبود، super فراخوانی می‌شود
            super.onBackPressed()
        }
    
        // ==================== پایان کنترل Back ====================
    
    }
    
 /*mysms*/
    
    // داده‌های مدل
    data class ConversationData(
        val sms: SmsEntity,
        val isDraft: Boolean,
        val unreadCount: Int,
        val isPinned: Boolean,
        val originalDate: Long
    )