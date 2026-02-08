package com.example.mysms.repository

import kotlinx.coroutines.flow.firstOrNull

import android.telephony.SmsMessage
import java.util.*
import android.provider.Telephony

import kotlinx.coroutines.delay
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.example.mysms.data.SmsDao
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.Manifest

import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context, private val smsDao: SmsDao) {

    companion object {
        const val SMS_SENT_ACTION = "com.example.mysms.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.example.mysms.SMS_DELIVERED"
    }

    // ==================== توابع جدید برای مدیریت پیام‌های چندبخشی ====================

    /**
     * پردازش و ترکیب پیام‌های چندبخشی
     */
    suspend fun processMultipartMessage(sms: SmsEntity): SmsEntity {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SmsRepository", "🔧 پردازش پیام چندبخشی: ${sms.address}, part ${sms.partIndex}/${sms.partCount}")

                // اگر پیام تک‌بخشی است، مستقیم برگردان
                if (!sms.isMultipart || sms.partCount <= 1) {
                    Log.d("SmsRepository", "📭 پیام تک‌بخشی، ذخیره مستقیم")
                    smsDao.insert(sms)
                    return@withContext sms
                }

                // ذخیره قطعه فعلی
                smsDao.insert(sms)

                // دریافت تمام قطعات این پیام
                val allParts = smsDao.getMultipartPartsByKey(sms.address, sms.messageId, sms.referenceNumber)

                Log.d("SmsRepository", "📊 قطعات موجود: ${allParts.size}/${sms.partCount}")

                // اگر تمام قطعات دریافت شده‌اند
                if (allParts.size >= sms.partCount) {
                    // مرتب‌سازی بر اساس شماره قطعه
                    val sortedParts = allParts.sortedBy { it.partIndex }

                    // بررسی که آیا تمام قطعات از 1 تا partCount وجود دارند
                    val hasAllParts = (1..sms.partCount).all { partNum ->
                        sortedParts.any { it.partIndex == partNum }
                    }

                    if (hasAllParts) {
                        // ترکیب متن تمام قطعات
                        val combinedBody = StringBuilder()
                        sortedParts.forEach { part ->
                            combinedBody.append(part.body)
                        }

                        // ایجاد پیام ترکیبی کامل
                        val combinedSms = sms.copy(
                            id = "multipart_complete_${sms.messageId}_${System.currentTimeMillis()}",
                            body = combinedBody.toString(),
                            isComplete = true,
                            status = 2,
                            partIndex = 0 // 0 نشان‌دهنده پیام کامل است
                        )

                        // ذخیره پیام کامل
                        smsDao.insert(combinedSms)

                        // حذف قطعات جداگانه (اختیاری)
                        // smsDao.deleteIncompleteMultipartParts(sms.address, sms.messageId, sms.referenceNumber)

                        Log.d("SmsRepository", "✅ پیام چندبخشی کامل شد: ${sms.address}, طول: ${combinedBody.length}")

                        return@withContext combinedSms
                    }
                }

                // اگر هنوز کامل نشده، پیام ناقص برگردان
                Log.d("SmsRepository", "⏳ منتظر قطعات بیشتر: ${allParts.size}/${sms.partCount}")
                return@withContext sms

            } catch (e: Exception) {
                Log.e("SmsRepository", "❌ خطا در پردازش پیام چندبخشی", e)
                return@withContext sms
            }
        }
    }

    /**
     * استخراج اطلاعات پیام چندبخشی از SmsMessage
     */
    private fun extractMultipartInfo(sms: SmsMessage, intent: Intent): SmsEntity {
        val address = sms.originatingAddress ?: "Unknown"
        val body = sms.messageBody ?: ""
        val timestamp = if (sms.timestampMillis > 0) sms.timestampMillis else System.currentTimeMillis()

        // استخراج subId
        var subId = -1
        val extras = intent.extras

        if (extras != null) {
            when {
                extras.containsKey("subscription") -> subId = extras.getInt("subscription", -1)
                extras.containsKey("sub_id") -> subId = extras.getInt("sub_id", -1)
                extras.containsKey("phone") -> subId = extras.getInt("phone", -1)
                extras.containsKey("simId") -> subId = extras.getInt("simId", -1)
            }
        }

        // بررسی آیا پیام چندبخشی است
        val isMultipart = sms.isMultipartMessage()
        val messageId = System.currentTimeMillis() / 1000 // استفاده از timestamp به عنوان messageId
        val referenceNumber = sms.referenceNumber() // باید این تابع را اضافه کنید

        return SmsEntity(
            id = "sms_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}",
            address = address,
            body = body,
            date = timestamp,
            type = 1,
            subId = subId,
            read = false,
            threadId = calculateThreadId(address),
            messageId = messageId,
            partCount = if (isMultipart) sms.partCount() else 1,
            partIndex = if (isMultipart) sms.partIndex() else 1,
            referenceNumber = referenceNumber,
            isMultipart = isMultipart,
            isComplete = !isMultipart,
            status = if (isMultipart) 0 else -1,
            encoding = sms.encoding()
        )
    }

    /**
     * محاسبه threadId برای گروه‌بندی مکالمات
     */
    private fun calculateThreadId(address: String): Long {
        return kotlin.math.abs(address.hashCode().toLong())
    }

    /**
     * بررسی دوره‌ای پیام‌های چندبخشی ناقص و تلاش برای ترکیب آنها
     */
    suspend fun checkAndCompleteMultipartMessages() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SmsRepository", "🔄 بررسی پیام‌های چندبخشی ناقص...")

                // پیام‌های ناقص در 10 دقیقه گذشته
                val timeThreshold = System.currentTimeMillis() - (10 * 60 * 1000)
                val incompleteMessages = smsDao.getIncompleteMultipartMessages(timeThreshold)

                Log.d("SmsRepository", "📋 تعداد پیام‌های ناقص: ${incompleteMessages.size}")

                incompleteMessages.forEach { key ->
                    try {
                        val parts = smsDao.getMultipartPartsByKey(key.address, key.messageId, key.referenceNumber)
                        val partCount = smsDao.getMultipartPartCount(key.address, key.messageId, key.referenceNumber)

                        // اگر تمام قطعات وجود دارند
                        if (parts.size >= partCount) {
                            val sortedParts = parts.sortedBy { it.partIndex }

                            // بررسی توالی قطعات
                            val hasSequence = (1..partCount).all { partNum ->
                                sortedParts.any { it.partIndex == partNum }
                            }

                            if (hasSequence) {
                                // ترکیب متن
                                val combinedBody = StringBuilder()
                                sortedParts.forEach { part ->
                                    combinedBody.append(part.body)
                                }

                                // ایجاد پیام کامل
                                val firstPart = sortedParts.first()
                                val completeSms = firstPart.copy(
                                    id = "multipart_complete_${key.messageId}_${System.currentTimeMillis()}",
                                    body = combinedBody.toString(),
                                    isComplete = true,
                                    status = 2,
                                    partIndex = 0
                                )

                                // ذخیره
                                smsDao.insert(completeSms)
                                Log.d("SmsRepository", "✅ پیام ناقص کامل شد: ${key.address}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SmsRepository", "❌ خطا در پردازش پیام ناقص: ${key.address}", e)
                    }
                }

            } catch (e: Exception) {
                Log.e("SmsRepository", "❌ خطا در بررسی پیام‌های ناقص", e)
            }
        }
    }

    /**
     * توابع کمکی برای SmsMessage - برای نسخه‌های مختلف اندروید
     */
    private fun SmsMessage.isMultipartMessage(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                this.messageBody?.length ?: 0 > 160 // حدس ساده
            } else {
                // برای نسخه‌های قدیمی
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun SmsMessage.partCount(): Int {
        return try {
            // در واقعیت این اطلاعات از PDU استخراج می‌شود
            1 // مقدار پیش‌فرض
        } catch (e: Exception) {
            1
        }
    }

    private fun SmsMessage.partIndex(): Int {
        return 1 // مقدار پیش‌فرض
    }

    private fun SmsMessage.referenceNumber(): Int {
        return 0 // مقدار پیش‌فرض
    }

    private fun SmsMessage.encoding(): String {
        return "UTF-8"
    }

    /**
     * دریافت پیام‌های ترکیب شده (کامل) برای یک مخاطب
     */
    suspend fun getCompleteMessagesByAddress(address: String): List<SmsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val allMessages = smsDao.getSmsByAddressFlow(address).firstOrNull() ?: emptyList()
                // فیلتر پیام‌های کامل
                allMessages.filter { message -> !message.isMultipart || message.isComplete }
            } catch (e: Exception) {
                Log.e("SmsRepository", "❌ خطا در دریافت پیام‌های کامل", e)
                emptyList()
            }
        }
    }

    // ✅ تابع جدید: دریافت لیست مکالمات (آخرین پیام هر مخاطب)
    fun getConversationsFlow(): Flow<List<SmsEntity>> = smsDao.getConversationsFlow()

    // ✅ تابع جدید: دریافت پیام‌های یک مخاطب خاص
    fun getSmsByAddressFlow(address: String): Flow<List<SmsEntity>> = smsDao.getSmsByAddressFlow(address)

    // ✅ تابع موجود - برای سایر موارد استفاده می‌شود
    fun getAllSmsFlow(): Flow<List<SmsEntity>> = smsDao.getAllSmsFlow()

    // ✅ تابع جدید: دریافت شناسه سیم‌کارت‌ها
    fun getSimIds(): Pair<Int?, Int?> {
        var sim1Id: Int? = null
        var sim2Id: Int? = null

        // چک همه مجوزهای لازم
        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhoneStatePermission) {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubs = subscriptionManager.activeSubscriptionInfoList

                activeSubs?.forEach { info ->
                    if (info.simSlotIndex == 0) sim1Id = info.subscriptionId
                    else if (info.simSlotIndex == 1) sim2Id = info.subscriptionId
                }

                Log.d("SmsRepository", "📱 SIM IDs found: SIM1=$sim1Id, SIM2=$sim2Id")
            } catch (e: Exception) {
                Log.e("SmsRepository", "❌ Error getting SIM IDs: ${e.message}")
            }
        } else {
            Log.w("SmsRepository", "⚠️ READ_PHONE_STATE permission not granted")
        }

        return Pair(sim1Id, sim2Id)
    }

    // تابع همگام‌سازی
    fun syncSms(): Flow<Int> = flow {
        try {
            // اول مجوز را چک کن
            val hasSmsPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasSmsPermission) {
                Log.e("SmsSync", "❌ READ_SMS permission not granted!")
                emit(100) // برای جلوگیری از گیر کردن
                return@flow
            }

            Log.d("SmsSync", "✅ READ_SMS permission granted, starting sync...")

            // *** تغییر جدید: ابتدا وضعیت read فعلی را از دیتابیس ذخیره کن
            val existingReadStatus = withContext(Dispatchers.IO) {
                smsDao.getAllSms().associate { it.id to it.read }
            }

            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null, null, null, null
            )
            if (cursor == null) {
                Log.e("SmsSync", "❌ Cursor is null - cannot read SMS")
                emit(100)
                return@flow
            }
            cursor?.use {
                val total = it.count
                if (total == 0) {
                    emit(100)
                    return@flow
                }

                var current = 0
                val list = mutableListOf<SmsEntity>()

                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val subIdIdx = it.getColumnIndex("sub_id")
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    try {
                        val id = if (idIdx != -1) it.getString(idIdx) else current.toString()
                        val address = if (addrIdx != -1) it.getString(addrIdx) ?: "Unknown" else "Unknown"
                        val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                        val type = if (typeIdx != -1) it.getInt(typeIdx) else 1
                        val subId = if (subIdIdx != -1) it.getInt(subIdIdx) else -1

                        // *** تغییر جدید: وضعیت read از دیتابیس موجود اولویت دارد
                        val isRead = if (existingReadStatus.containsKey(id)) {
                            existingReadStatus[id] ?: true
                        } else {
                            if (readIdx != -1) it.getInt(readIdx) == 1 else true
                        }

                        list.add(SmsEntity(id, address, body, date, type, subId, isRead))
                    } catch (e: Exception) {
                        Log.e("SmsSync", "Error reading SMS: ${e.message}")
                    }

                    current++
                    if (current % 20 == 0) emit((current * 100) / total)
                }

                if (list.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        smsDao.insertAll(list)
                    }
                }
                emit(100)
            }
        } catch (e: Exception) {
            Log.e("SmsSync", "Sync error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getAllSmsFromDb(): List<SmsEntity> {
        return smsDao.getAllSms()
    }

    fun getContactName(phoneNumber: String): Pair<String, String?> {
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
            android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photo = cursor.getString(1)
                    return Pair(name, photo)
                }
            }
        } catch (e: Exception) {
            // هندل کردن خطای احتمالی دسترسی به مخاطبین
        }
        return Pair(phoneNumber, null)
    }

    // ✅ تابع ارسال پیامک با مدیریت وضعیت و ذخیره در دیتابیس
    suspend fun sendSms(address: String, body: String, subId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SmsRepository", "📤 Attempting to send SMS to: $address")

                // 1. ارسال پیامک
                val finalSubId = if (subId < 0) android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId() else subId

                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(finalSubId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(finalSubId)
                }

                // ارسال ساده
                smsManager.sendTextMessage(address, null, body, null, null)

                Log.d("SmsRepository", "✅ SMS sent successfully to: $address")

                // 2. کمی صبر کن تا پیام در سیستم ثبت شود
                delay(500)

                // 3. سینک پیام ارسالی از سیستم
                syncSentSmsFromSystem(address, body, subId)

                true

            } catch (e: Exception) {
                Log.e("SmsRepository", "❌ Failed to send SMS: ${e.message}")
                false
            }
        }
    }


    // تابع ایمپورت سریع پیام‌ها
    suspend fun quickImportSms(limit: Int = 100): Int {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("QuickImport", "🚀 Starting quick SMS import")

                val cursor = context.contentResolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    "${android.provider.Telephony.Sms.DATE} DESC"
                )

                var count = 0
                cursor?.use {
                    val total = it.count
                    Log.d("QuickImport", "📊 Total messages available: $total")

                    val idIdx = it.getColumnIndex(android.provider.Telephony.Sms._ID)
                    val addrIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)
                    val typeIdx = it.getColumnIndex(android.provider.Telephony.Sms.TYPE)
                    val subIdIdx = it.getColumnIndex("sub_id")
                    val readIdx = it.getColumnIndex(android.provider.Telephony.Sms.READ)

                    val smsList = mutableListOf<com.example.mysms.data.SmsEntity>()

                    // فقط limit پیام اول را بگیر
                    while (it.moveToNext() && count < limit) {
                        try {
                            val id = if (idIdx != -1) it.getString(idIdx) else "imp_${System.currentTimeMillis()}_$count"
                            val address = if (addrIdx != -1) it.getString(addrIdx) ?: "Unknown" else "Unknown"
                            val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                            val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()
                            val type = if (typeIdx != -1) it.getInt(typeIdx) else 1
                            val subId = if (subIdIdx != -1) it.getInt(subIdIdx) else -1
                            val isRead = if (readIdx != -1) it.getInt(readIdx) == 1 else true

                            smsList.add(com.example.mysms.data.SmsEntity(id, address, body, date, type, subId, isRead))
                            count++

                            if (count % 10 == 0) {
                                Log.d("QuickImport", "📥 Imported: $count")
                            }

                        } catch (e: Exception) {
                            Log.e("QuickImport", "Error reading message $count", e)
                        }
                    }

                    if (smsList.isNotEmpty()) {
                        smsDao.insertAll(smsList)
                        Log.d("QuickImport", "✅ Successfully imported $count messages")
                    }
                }

                cursor?.close()
                count

            } catch (e: Exception) {
                Log.e("QuickImport", "💥 Quick import error", e)
                0
            }
        }
    }

    // تابع جدید برای سینک پیام ارسالی از سیستم
    private suspend fun syncSentSmsFromSystem(address: String, body: String, subId: Int) {
        try {
            // خواندن از SMS Provider
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val subIdIdx = it.getColumnIndex("sub_id")

                // ۱۰ پیام آخر را چک کن
                var count = 0
                while (it.moveToNext() && count < 10) {
                    count++

                    val smsAddress = if (addrIdx != -1) it.getString(addrIdx) else ""
                    val smsBody = if (bodyIdx != -1) it.getString(bodyIdx) else ""
                    val smsType = if (typeIdx != -1) it.getInt(typeIdx) else 1

                    // اگر پیام ارسالی ما را پیدا کردیم (نوع ۲ = ارسالی)
                    if (smsType == 2 && smsAddress == address && smsBody.contains(body.substring(0, minOf(10, body.length)))) {
                        val id = if (idIdx != -1) it.getString(idIdx) else System.currentTimeMillis().toString()
                        val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()
                        val smsSubId = if (subIdIdx != -1) it.getInt(subIdIdx) else subId

                        val sentSms = SmsEntity(
                            id = "sys_$id", // پیشوند sys برای پیام‌های سینک شده از سیستم
                            address = address,
                            body = smsBody,
                            date = date,
                            type = 2,
                            subId = smsSubId,
                            read = true
                        )

                        // ذخیره در دیتابیس
                        smsDao.insertAll(listOf(sentSms))

                        Log.d("SmsRepository", "💾 Sent SMS synced from system: $id")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error syncing sent SMS from system: ${e.message}")
        }
    }

    suspend fun markAsRead(address: String) {
        smsDao.markAsRead(address)
    }


}