package com.example.mysms.ui.theme

import android.R.attr.description
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.mysms.R
import com.example.mysms.data.AppDatabase
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "🔔 onReceive called! Action: ${intent.action}")

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
                    saveToDatabase(context, smsList)
                    Log.d("SmsReceiver", "✅ Successfully saved ${smsList.size} SMS to database")

                    // اطلاع‌رسانی به UI
                    sendBroadcastToUpdateUI(context)
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
                    saveToDatabase(context, smsList)
                    Log.d("SmsReceiver", "✅ Successfully saved ${smsList.size} legacy SMS")

                    // اطلاع‌رسانی به UI
                    sendBroadcastToUpdateUI(context)
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

        // استخراج subId از intent
        var subId = -1
        val extras = intent.extras

        // روش‌های مختلف استخراج subId
        if (extras != null) {
            // روش 1: کلیدهای مختلف
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

        return SmsEntity(
            id = "sms_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}",
            address = address,
            body = body,
            date = timestamp,
            type = 1, // دریافتی
            subId = subId, // استفاده از subId واقعی
            read = false
        )
    }

    private suspend fun saveToDatabase(context: Context, smsList: List<SmsEntity>) {
        try {
            val database = AppDatabase.getDatabase(context)
            database.smsDao().insertAll(smsList)
            Log.d("SmsReceiver", "💾 Database save successful")

            // نمایش نوتیفیکیشن برای هر پیام
            smsList.forEach { sms ->
                showNotification(context, sms)
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "💥 Database save failed: ${e.message}", e)
        }
    }

    private fun sendBroadcastToUpdateUI(context: Context) {
        // ارسال broadcast برای رفرش UI
        val updateIntent = Intent("com.example.mysms.SMS_RECEIVED")
        context.sendBroadcast(updateIntent)
        Log.d("SmsReceiver", "📡 Sent UI update broadcast")
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
    private fun showNotification(context: Context, sms: SmsEntity) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // ایجاد کانال برای اندروید 8+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "sms_channel",
                    "پیام‌های جدید",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "پیام‌های دریافتی SMS"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(100, 200, 100, 200)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // ایجاد intent برای باز کردن برنامه
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_chat", sms.address)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sms.address.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // ساخت نوتیفیکیشن
            val notification = NotificationCompat.Builder(context, "sms_channel")
                .setContentTitle("پیام جدید")
                .setContentText("از: ${sms.address}\n${sms.body.take(50)}${if (sms.body.length > 50) "..." else ""}")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .build()

            // نمایش نوتیفیکیشن
            notificationManager.notify(sms.address.hashCode(), notification)
            Log.d("SmsReceiver", "📢 Notification shown for ${sms.address}")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error showing notification: ${e.message}")
        }
    }
}