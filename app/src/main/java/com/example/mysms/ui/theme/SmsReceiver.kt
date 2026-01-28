package com.example.mysms.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.mysms.data.AppDatabase
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "🔔 onReceive called! Action: ${intent.action}")

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
                        val entity = createSmsEntity(sms)
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

                        val entity = createSmsEntity(sms)
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

    private fun createSmsEntity(sms: SmsMessage): SmsEntity {
        val address = sms.originatingAddress ?: "Unknown"
        val body = sms.messageBody ?: ""
        val timestamp = if (sms.timestampMillis > 0) sms.timestampMillis else System.currentTimeMillis()

        return SmsEntity(
            id = "sms_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}",
            address = address,
            body = body,
            date = timestamp,
            type = 1, // دریافتی
            subId = 0, // پیش‌فرض
            read = false
        )
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
}