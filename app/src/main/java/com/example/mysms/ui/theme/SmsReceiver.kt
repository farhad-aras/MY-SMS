package com.example.mysms.ui.theme


import androidx.annotation.RequiresApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mysms.R
import com.example.mysms.data.AppDatabase
import com.example.mysms.data.SmsEntity
import com.example.mysms.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.absoluteValue
import kotlinx.coroutines.runBlocking

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "sms_received_channel"
        private const val CHANNEL_NAME = "پیام‌های دریافتی"
        private const val CHANNEL_DESCRIPTION = "پیام‌های SMS دریافتی"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "🔔 onReceive called! Action: ${intent.action}")



        // چک اگر از JobScheduler آمده باشد
        val isJobCheck = intent.getBooleanExtra("job_scheduled_check", false)
        if (isJobCheck) {
            Log.d("SmsReceiver", "📅 This is a scheduled job check")
            val checkTime = intent.getLongExtra("check_time", 0L)
            Log.d("SmsReceiver", "⏰ Check time: ${android.text.format.DateFormat.format("HH:mm:ss", checkTime)}")
        }

        // لاگ تمام extras برای دیدن sub_id واقعی
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            if (key.contains("sub") || key.contains("subscription") || key.contains("sim") || key.contains("phone")) {
                Log.d("SmsReceiver", "📌 Found subId key: $key = $value")
            }
        }

        // بررسی action
        val action = intent.action
        Log.d("SmsReceiver", "Action received: $action")

        when (action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
            "android.provider.Telephony.SMS_RECEIVED" -> {
                Log.d("SmsReceiver", "📨 SMS Received action detected")
                processSmsMessages(context, intent)
            }

            "com.example.mysms.SMS_SENT" -> {
                Log.d("SmsReceiver", "📤 SMS Sent status received")
                processSentStatus(context, intent)
            }

            else -> {
                Log.w("SmsReceiver", "❌ Unknown action: $action")
            }
        }
    }

    private fun processSmsMessages(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "📱 Processing SMS messages...")

        try {
            // روش 1: متد مدرن
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Log.d("SmsReceiver", "Using KitKat+ method")
                processWithKitKatApi(context, intent)
            } else {
                // روش 2: برای اندروید قدیمی
                Log.d("SmsReceiver", "Using legacy method")
                processLegacySms(context, intent)
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "💥 Error processing SMS: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun processWithKitKatApi(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // متد استاندارد اندروید 4.4+
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                if (messages == null || messages.isEmpty()) {
                    Log.w("SmsReceiver", "No messages found using KitKat API")
                    return@launch
                }

                Log.d("SmsReceiver", "Found ${messages.size} message(s) using KitKat API")

                val smsList = mutableListOf<SmsEntity>()

                for (sms in messages) {
                    if (sms != null) {
                        val entity = createSmsEntity(sms, intent)
                        smsList.add(entity)
                        Log.d("SmsReceiver", "Processed SMS from: ${sms.originatingAddress}")
                    }
                }

                if (smsList.isNotEmpty()) {
                    // ============ پردازش پیام‌های چندبخشی ============
                    processMultipartMessages(context, smsList)

                    // ذخیره در دیتابیس (حتی پیام‌های ناقص)
                    saveToDatabase(context, smsList)
                    Log.d("SmsReceiver", "✅ Successfully saved ${smsList.size} SMS to database")

                    // ============ نمایش نوتیفیکیشن برای پیام‌های تک‌بخشی ============
                    val singlePartMessages = smsList.filter { !it.isMultipart }
                    if (singlePartMessages.isNotEmpty()) {
                        val firstSms = singlePartMessages.first()
                        try {
                            val address = firstSms.address
                            val body = firstSms.body

                            Log.d("SmsReceiver", "📨 نمایش نوتیفیکیشن برای پیام تک‌بخشی: $address")

                            // شروع سرویس برای نمایش نوتیفیکیشن
                            val serviceIntent = Intent(context, ForegroundSmsService::class.java)
                            serviceIntent.putExtra("show_notification", true)
                            serviceIntent.putExtra("address", address)
                            serviceIntent.putExtra("body", body)
                            serviceIntent.putExtra("is_complete_multipart", false)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }

                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "❌ خطا در نمایش نوتیفیکیشن پیام تک", e)
                        }
                    }

                    // ============ نمایش نوتیفیکیشن برای پیام‌های چندبخشی کامل شده ============
                    // (این کار در تابع processMultipartMessages انجام می‌شود)

                    // نمایش نوتیفیکیشن backup برای هر پیام
                    /*
                    smsList.forEach { sms ->
                        if (!sms.isMultipart) {
                            showNotificationForSingleMessage(context, sms)
                        }
                    }
                    */
                }

            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error with KitKat API: ${e.message}", e)
            }
        }
    }

    private fun processLegacySms(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // متد قدیمی برای اندروید قبل از 4.4
                val bundle = intent.extras
                if (bundle == null) {
                    Log.w("SmsReceiver", "No extras in intent")
                    return@launch
                }

                val pdus = bundle.get("pdus") as? Array<Any>
                if (pdus == null || pdus.isEmpty()) {
                    Log.w("SmsReceiver", "No PDUs found")
                    return@launch
                }

                Log.d("SmsReceiver", "Found ${pdus.size} PDU(s) using legacy method")

                val smsList = mutableListOf<SmsEntity>()
                val format = bundle.getString("format")

                for (i in pdus.indices) {
                    try {
                        val pdu = pdus[i] as ByteArray
                        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }

                        val entity = createSmsEntity(sms, intent)
                        smsList.add(entity)
                        Log.d("SmsReceiver", "Processed legacy SMS from: ${sms.originatingAddress}")

                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error parsing PDU $i: ${e.message}")
                    }
                }

                if (smsList.isNotEmpty()) {
                    // ============ پردازش پیام‌های چندبخشی ============
                    processMultipartMessages(context, smsList)

                    // ذخیره در دیتابیس
                    saveToDatabase(context, smsList)
                    Log.d("SmsReceiver", "✅ Successfully saved ${smsList.size} legacy SMS")

                    // ============ نمایش نوتیفیکیشن برای پیام‌های تک‌بخشی ============
                    val singlePartMessages = smsList.filter { !it.isMultipart }
                    if (singlePartMessages.isNotEmpty()) {
                        val firstSms = singlePartMessages.first()
                        try {
                            val address = firstSms.address
                            val body = firstSms.body

                            Log.d("SmsReceiver", "📨 نمایش نوتیفیکیشن برای پیام تک‌بخشی (legacy): $address")

                            // شروع سرویس برای نمایش نوتیفیکیشن
                            val serviceIntent = Intent(context, ForegroundSmsService::class.java)
                            serviceIntent.putExtra("show_notification", true)
                            serviceIntent.putExtra("address", address)
                            serviceIntent.putExtra("body", body)
                            serviceIntent.putExtra("is_complete_multipart", false)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }

                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "❌ خطا در نمایش نوتیفیکیشن پیام تک (legacy)", e)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error with legacy method: ${e.message}", e)
            }
        }
    }

    private fun createSmsEntity(sms: SmsMessage, intent: Intent): SmsEntity {
        val address = sms.originatingAddress ?: "Unknown"
        val body = sms.messageBody ?: ""
        val timestamp = if (sms.timestampMillis > 0) sms.timestampMillis else System.currentTimeMillis()

        // استخراج subId
        var subId = -1
        val extras = intent.extras

        // روش‌های مختلف استخراج subId
        if (extras != null) {
            when {
                extras.containsKey("subscription") -> subId = extras.getInt("subscription", -1)
                extras.containsKey("sub_id") -> subId = extras.getInt("sub_id", -1)
                extras.containsKey("phone") -> subId = extras.getInt("phone", -1)
                extras.containsKey("simId") -> subId = extras.getInt("simId", -1)
            }

            // روش 2: اگر باز هم پیدا نکردیم
            if (subId == -1) {
                extras.keySet().forEach { key ->
                    if (key.contains("sub") || key.contains("sim") || key.contains("phone")) {
                        try {
                            val value = extras.get(key)
                            if (value is Int) {
                                subId = value
                                Log.d("SmsReceiver", "🔍 Found subId in key '$key': $subId")
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }

        Log.d("SmsReceiver", "📱 Extracted subId: $subId for SMS from: $address")

        // ==================== بخش جدید: تشخیص پیام‌های چندبخشی ====================
        val isMultipart = isMultipartMessage(sms, extras)
        val messageId = extractMessageId(sms, extras, timestamp)
        val referenceNumber = extractReferenceNumber(sms, extras)
        val partCount = extractPartCount(sms, extras)
        val partIndex = extractPartIndex(sms, extras)

        Log.d("SmsReceiver", "🔗 Multipart info: isMultipart=$isMultipart, messageId=$messageId, ref=$referenceNumber, parts=$partCount/$partIndex")

        return SmsEntity(
            id = "sms_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}",
            address = address,
            body = body,
            date = timestamp,
            type = 1, // دریافتی
            subId = subId,
            read = false,
            // ==================== فیلدهای چندبخشی ====================
            threadId = calculateThreadId(address),
            messageId = messageId,
            partCount = partCount,
            partIndex = partIndex,
            referenceNumber = referenceNumber,
            isMultipart = isMultipart,
            isComplete = !isMultipart || (partIndex == partCount),
            status = if (isMultipart) 0 else -1,
            contentType = "text/plain",
            encoding = "UTF-8"
        )
    }

    // ==================== توابع کمکی برای تشخیص پیام‌های چندبخشی ====================

    /**
     * تشخیص آیا پیام چندبخشی است
     */
    private fun isMultipartMessage(sms: SmsMessage, extras: Bundle?): Boolean {
        try {
            // روش 1: بررسی از طریق extras
            if (extras != null) {
                // کلیدهای رایج برای پیام‌های چندبخشی
                val multipartKeys = listOf("isMultipart", "multipart", "concat_ref", "concat_ref_number")
                if (multipartKeys.any { extras.containsKey(it) }) {
                    return true
                }

                // بررسی مقادیر عددی مربوط به multipart
                if (extras.containsKey("concat_ref") || extras.containsKey("concat_ref_number")) {
                    return true
                }
            }

            // روش 2: بررسی طول متن (پیام‌های طولانی معمولاً چندبخشی هستند)
            val messageBody = sms.messageBody ?: ""
            if (messageBody.length >= 140) { // نزدیک به حد SMS
                return true
            }

            // روش 3: بررسی PDU header (برای متد قدیمی)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                // برای اندروید قدیمی
                return messageBody.length > 160
            }

            return false

        } catch (e: Exception) {
            Log.e("SmsReceiver", "❌ خطا در تشخیص multipart", e)
            return false
        }
    }

    /**
     * استخراج شناسه پیام
     */
    private fun extractMessageId(sms: SmsMessage, extras: Bundle?, timestamp: Long): Long {
        return try {
            if (extras != null) {
                // اولویت 1: از extras بگیر
                when {
                    extras.containsKey("message_id") -> extras.getLong("message_id", timestamp)
                    extras.containsKey("msg_id") -> extras.getLong("msg_id", timestamp)
                    extras.containsKey("transactionId") -> extras.getLong("transactionId", timestamp)
                    else -> timestamp
                }
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }

    /**
     * استخراج شماره مرجع برای پیام‌های چندبخشی
     */
    private fun extractReferenceNumber(sms: SmsMessage, extras: Bundle?): Int {
        return try {
            if (extras != null) {
                when {
                    extras.containsKey("concat_ref") -> extras.getInt("concat_ref", 0)
                    extras.containsKey("concat_ref_number") -> extras.getInt("concat_ref_number", 0)
                    extras.containsKey("reference_number") -> extras.getInt("reference_number", 0)
                    extras.containsKey("ref") -> extras.getInt("ref", 0)
                    else -> sms.originatingAddress?.hashCode()?.and(0xFFFF) ?: 0
                }
            } else {
                sms.originatingAddress?.hashCode()?.and(0xFFFF) ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * استخراج تعداد کل قطعات
     */
    private fun extractPartCount(sms: SmsMessage, extras: Bundle?): Int {
        return try {
            if (extras != null) {
                when {
                    extras.containsKey("concat_count") -> extras.getInt("concat_count", 1)
                    extras.containsKey("part_count") -> extras.getInt("part_count", 1)
                    extras.containsKey("total_parts") -> extras.getInt("total_parts", 1)
                    else -> 1
                }
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * استخراج شماره قطعه فعلی
     */
    private fun extractPartIndex(sms: SmsMessage, extras: Bundle?): Int {
        return try {
            if (extras != null) {
                when {
                    extras.containsKey("concat_seq") -> extras.getInt("concat_seq", 1)
                    extras.containsKey("part_index") -> extras.getInt("part_index", 1)
                    extras.containsKey("current_part") -> extras.getInt("current_part", 1)
                    else -> 1
                }
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * محاسبه threadId
     */
    private fun calculateThreadId(address: String): Long {
        return kotlin.math.abs(address.hashCode().toLong())
    }

    /**
     * پردازش هوشمند پیام‌های چندبخشی
     */
    private fun processMultipartMessages(context: Context, smsList: List<SmsEntity>) {
        if (smsList.isEmpty()) return

        runBlocking {
            try {
                val database = AppDatabase.getDatabase(context)
                val repository = SmsRepository(context, database.smsDao())

                // پردازش هر پیام
                smsList.forEach { sms ->
                    try {
                        val processedSms = repository.processMultipartMessage(sms)

                        // اگر پیام کامل شد، نوتیفیکیشن نمایش بده
                        if (processedSms.isComplete && processedSms.isMultipart) {
                            Log.d("SmsReceiver", "🎉 پیام چندبخشی کامل شد، نمایش نوتیفیکیشن")
                            showNotificationForCompleteMessage(context, processedSms)
                        } else if (!sms.isMultipart) {
                            // برای پیام‌های تک‌بخشی هم نوتیفیکیشن نمایش بده
                            showNotificationForSingleMessage(context, sms)
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "❌ خطا در پردازش پیام: ${sms.address}", e)
                    }
                }

            } catch (e: Exception) {
                Log.e("SmsReceiver", "❌ خطا در پردازش multipart messages", e)
            }
        }
    }

    /**
     * نمایش نوتیفیکیشن برای پیام کامل شده
     */
    private fun showNotificationForCompleteMessage(context: Context, sms: SmsEntity) {
        try {
            // استفاده از ForegroundService برای نمایش نوتیفیکیشن
            val serviceIntent = Intent(context, ForegroundSmsService::class.java)
            serviceIntent.putExtra("show_notification", true)
            serviceIntent.putExtra("address", sms.address)
            serviceIntent.putExtra("body", sms.body)
            serviceIntent.putExtra("is_complete_multipart", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d("SmsReceiver", "📢 نوتیفیکیشن برای پیام کامل ارسال شد: ${sms.address}")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "❌ خطا در نمایش نوتیفیکیشن پیام کامل", e)
        }
    }

    /**
     * نمایش نوتیفیکیشن برای پیام تک‌بخشی
     */
    private fun showNotificationForSingleMessage(context: Context, sms: SmsEntity) {
        try {
            val serviceIntent = Intent(context, ForegroundSmsService::class.java)
            serviceIntent.putExtra("show_notification", true)
            serviceIntent.putExtra("address", sms.address)
            serviceIntent.putExtra("body", sms.body)
            serviceIntent.putExtra("is_complete_multipart", false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "❌ خطا در نمایش نوتیفیکیشن پیام تک", e)
        }
    }

    private suspend fun saveToDatabase(context: Context, smsList: List<SmsEntity>) {
        try {
            val database = AppDatabase.getDatabase(context)
            database.smsDao().insertAll(smsList)
            Log.d("SmsReceiver", "💾 Database save successful")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "💥 Database save failed: ${e.message}", e)
        }
    }

    private fun processSentStatus(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resultCode = resultCode
                val smsId = intent.getStringExtra("sms_id")
                val address = intent.getStringExtra("address")

                Log.d("SmsReceiver", "📊 SMS Sent Result - Code: $resultCode, ID: $smsId, To: $address")

                when (resultCode) {
                    android.app.Activity.RESULT_OK -> {
                        Log.d("SmsReceiver", "✅ SMS sent successfully to $address")
                        // می‌توانید وضعیت پیام را در دیتابیس آپدیت کنید
                    }
                    else -> {
                        Log.w("SmsReceiver", "❌ SMS failed to send to $address")
                        // وضعیت خطا در دیتابیس
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing sent status: ${e.message}")
            }
        }
    }

    // ==================== تابع بهبودیافته نوتیفیکیشن ====================
  /*  private fun showNotificationAlways(context: Context, sms: SmsEntity) {
        try {
            // ============ تغییر مهم: استفاده از Foreground Service ============
            // اگر سرویس foreground در حال اجرا باشد، از آن استفاده کن
            // در غیر این صورت از روش قدیمی

            // 1. سعی کن از Foreground Service استفاده کنی
            try {
                // راه‌اندازی سرویس foreground اگر در حال اجرا نیست
                ForegroundSmsService.startService(context)

                // منتظر باش سرویس شروع شود
                Thread.sleep(500)

                // نمایش نوتیفیکیشن از طریق سرویس
                // Note: در اینجا نیاز به ارسال broadcast به سرویس داریم
                // اما برای سادگی، هم از سرویس و هم از روش مستقیم استفاده می‌کنیم

            } catch (e: Exception) {
                Log.w("SmsReceiver", "⚠️ Could not use foreground service: ${e.message}")
            }

            // 2. همیشه نوتیفیکیشن را مستقیماً هم نمایش بده (برای اطمینان)
            showNewMessageNotification(context, sms)

            Log.d("SmsReceiver", "📢 Notification shown for ${sms.address}")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "❌ Error showing notification: ${e.message}", e)
        }
    }*/

    private fun showNewMessageNotification(context: Context, sms: SmsEntity) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            // 1. ایجاد کانال نوتیفیکیشن (برای اندروید 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(100, 200, 100, 200)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 2. ایجاد Intent برای بازکردن مستقیم چت
            val openChatIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                // اضافه کردن اکسترا برای بازکردن چت با مخاطب خاص
                putExtra("open_chat", true)
                putExtra("contact_address", sms.address)
                putExtra("contact_name", getContactName(context, sms.address))
                putExtra("notification_clicked", true)
                putExtra("message_id", sms.id)

                // اضافه کردن action برای تمایز
                action = "OPEN_CHAT_ACTION_${sms.address.hashCode()}"
            }

            // 3. ایجاد PendingIntent
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sms.address.hashCode(), // استفاده از hashCode برای ID منحصربه‌فرد
                openChatIntent,
                pendingIntentFlags
            )

            // 4. دریافت نام مخاطب
            val contactName = getContactName(context, sms.address)
            val displayName = if (contactName != sms.address) contactName else sms.address

            // 5. ایجاد BigTextStyle برای نمایش متن کامل
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText(sms.body)
                .setBigContentTitle("پیام جدید از $displayName")
                .setSummaryText("SMS")

            // 6. ساخت نوتیفیکیشن
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("پیام جدید")
                .setContentText("از: $displayName")
                .setStyle(bigTextStyle)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup("sms_messages") // گروه‌بندی برای چندین پیام
                .setGroupSummary(false)
                .build()

            // 7. نمایش نوتیفیکیشن با ID منحصربه‌فرد
            val notificationId = NOTIFICATION_ID_BASE + (sms.address.hashCode().absoluteValue % 1000)
            notificationManager.notify(notificationId, notification)

            Log.d("SmsReceiver", "📢 Notification shown for $displayName (ID: $notificationId)")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "❌ Error showing notification: ${e.message}", e)
        }
    }

    // ==================== تابع کمکی برای دریافت نام مخاطب ====================
    private fun getContactName(context: Context, phoneNumber: String): String {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
            phoneNumber
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error getting contact name: ${e.message}")
            phoneNumber
        }
    }
}