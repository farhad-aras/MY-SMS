package com.example.mysms.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.mysms.data.MultipartKey
import com.example.mysms.data.SmsDao
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultipartManager(
    private val smsDao: SmsDao,
    private val viewModelScope: CoroutineScope
) {

    companion object {
        private const val TAG = "MultipartManager"
        private const val COMBINE_DELAY = 2000L // 2 ثانیه
        private const val PERIODIC_CHECK_INTERVAL = 30 * 1000L // 30 ثانیه
        private const val INCOMPLETE_MESSAGE_TIMEOUT = 30 * 60 * 1000L // 30 دقیقه
    }

    // ==================== توابع عمومی ====================

    /**
     * دریافت پیام‌های چندبخشی ناقص
     */
    suspend fun getIncompleteMultipartMessages(): List<MultipartKey> {
        return withContext(Dispatchers.IO) {
            try {
                val timeThreshold = System.currentTimeMillis() - INCOMPLETE_MESSAGE_TIMEOUT
                smsDao.getIncompleteMultipartMessages(timeThreshold)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting incomplete multipart messages", e)
                emptyList()
            }
        }
    }

    /**
     * ترکیب پیام‌های چندبخشی کامل شده
     * @return تعداد پیام‌های ترکیب شده
     */
    suspend fun combineCompleteMultipartMessages(): Int {
        return withContext(Dispatchers.IO) {
            var combinedCount = 0
            try {
                Log.d(TAG, "🔗 ترکیب پیام‌های چندبخشی کامل شده...")

                val incompleteMessages = getIncompleteMultipartMessages()
                Log.d(TAG, "📋 تعداد پیام‌های ناقص: ${incompleteMessages.size}")

                incompleteMessages.forEach { key ->
                    try {
                        val parts = smsDao.getMultipartPartsByKey(key.address, key.messageId, key.referenceNumber)
                        val expectedCount = parts.firstOrNull()?.partCount ?: 1

                        Log.d(TAG, "🔍 بررسی پیام: ${key.address}, قطعات: ${parts.size}/$expectedCount")

                        // اگر تمام قطعات دریافت شده‌اند
                        if (parts.size >= expectedCount) {
                            val sortedParts = parts.sortedBy { it.partIndex }

                            // بررسی توالی قطعات
                            val hasAllParts = (1..expectedCount).all { partNum ->
                                sortedParts.any { it.partIndex == partNum }
                            }

                            if (hasAllParts) {
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
                                    status = SmsEntity.STATUS_COMPLETE,
                                    partIndex = 0
                                )

                                // ذخیره پیام کامل
                                smsDao.insert(completeSms)

                                combinedCount++
                                Log.d(TAG, "✅ پیام چندبخشی ترکیب شد: ${key.address}, طول: ${combinedBody.length}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ خطا در ترکیب پیام: ${key.address}", e)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error combining multipart messages", e)
            }
            combinedCount
        }
    }

    /**
     * دریافت پیام‌های ترکیب شده برای یک مخاطب
     */
    suspend fun getCombinedMessagesByAddress(address: String): List<SmsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val allMessages = smsDao.getSmsByAddressFlow(address).firstOrNull() ?: emptyList()
                // فیلتر پیام‌های کامل یا تک‌بخشی
                allMessages.filter { message ->
                    !message.isMultipart || message.isComplete
                }.sortedByDescending { it.date }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting combined messages for $address", e)
                emptyList()
            }
        }
    }

    /**
     * بررسی و ترکیب دوره‌ای پیام‌های چندبخشی
     * @param onMessageCombined callback بعد از هر بار ترکیب
     */
    fun startPeriodicCombinationCheck(onMessageCombined: (Int) -> Unit = {}) {
        viewModelScope.launch {
            while (viewModelScope.isActive) {
                try {
                    delay(PERIODIC_CHECK_INTERVAL)
                    val combinedCount = combineCompleteMultipartMessages()
                    if (combinedCount > 0) {
                        onMessageCombined.invoke(combinedCount)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in periodic multipart check", e)
                    delay(PERIODIC_CHECK_INTERVAL * 2) // در صورت خطا، 2 برابر صبر کن
                }
            }
        }
    }

    /**
     * ترکیب پیام‌های چندبخشی با تأخیر (برای بعد از سینک)
     */
    fun combineAfterSync(delayMillis: Long = COMBINE_DELAY, onCombined: (Int) -> Unit = {}) {
        viewModelScope.launch {
            delay(delayMillis)
            val combinedCount = combineCompleteMultipartMessages()
            if (combinedCount > 0) {
                onCombined.invoke(combinedCount)
            }
        }
    }

    /**
     * بررسی آیا پیام چندبخشی است و پردازش اولیه
     */
    fun processIncomingMultipart(sms: SmsEntity, onComplete: (SmsEntity) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!sms.isMultipart || sms.partCount <= 1) {
                    return@launch
                }

                Log.d(TAG, "📨 پردازش پیام چندبخشی: ${sms.address}, part ${sms.partIndex}/${sms.partCount}")

                // ذخیره قطعه
                smsDao.insert(sms)

                // بررسی کامل شدن
                val allParts = smsDao.getMultipartPartsByKey(sms.address, sms.messageId, sms.referenceNumber)

                if (allParts.size >= sms.partCount) {
                    val sortedParts = allParts.sortedBy { it.partIndex }
                    val hasAllParts = (1..sms.partCount).all { partNum ->
                        sortedParts.any { it.partIndex == partNum }
                    }

                    if (hasAllParts) {
                        val combinedBody = sortedParts.joinToString("") { it.body }
                        val completeSms = sms.copy(
                            id = "multipart_complete_${sms.messageId}_${System.currentTimeMillis()}",
                            body = combinedBody,
                            isComplete = true,
                            status = SmsEntity.STATUS_COMPLETE,
                            partIndex = 0
                        )

                        smsDao.insert(completeSms)
                        onComplete.invoke(completeSms)
                        Log.d(TAG, "✅ پیام چندبخشی در لحظه کامل شد: ${sms.address}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing incoming multipart", e)
            }
        }
    }

    /**
     * پاک کردن پیام‌های چندبخشی ناقص قدیمی
     */
    suspend fun cleanupOldIncompleteMessages(olderThanMillis: Long = INCOMPLETE_MESSAGE_TIMEOUT): Int {
        return withContext(Dispatchers.IO) {
            try {
                val timeThreshold = System.currentTimeMillis() - olderThanMillis
                val incompleteKeys = smsDao.getIncompleteMultipartMessages(timeThreshold)

                incompleteKeys.forEach { key ->
                    smsDao.deleteIncompleteMultipartParts(key.address, key.messageId, key.referenceNumber)
                }

                Log.d(TAG, "🧹 Cleaned up ${incompleteKeys.size} old incomplete multipart messages")
                incompleteKeys.size
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up incomplete messages", e)
                0
            }
        }
    }
}