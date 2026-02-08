package com.example.mysms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // 1. کوئری فعلی برای صفحه چت - بدون تغییر
    @Query("SELECT * FROM sms_table ORDER BY date DESC")
    fun getAllSmsFlow(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_table ORDER BY date DESC")
    suspend fun getAllSms(): List<SmsEntity>

    // 2. کوئری جدید برای لیست مکالمات (آخرین پیام هر مخاطب)
    @Query("""
        SELECT s1.* FROM sms_table s1
        WHERE s1.date = (
            SELECT MAX(date) FROM sms_table s2 
            WHERE s2.address = s1.address
        )
        ORDER BY s1.date DESC
    """)
    fun getConversationsFlow(): Flow<List<SmsEntity>>

    // 3. کوئری برای گرفتن پیام‌های یک مخاطب خاص
    @Query("SELECT * FROM sms_table WHERE address = :address ORDER BY date DESC")
    fun getSmsByAddressFlow(address: String): Flow<List<SmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sms: List<SmsEntity>)

    @Query("UPDATE sms_table SET read = 1 WHERE address = :address")
    suspend fun markAsRead(address: String)

    @Query("UPDATE sms_table SET read = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: Long)

    // کوئری جدید برای آپدیت فقط یک پیام خاص
    @Query("UPDATE sms_table SET read = 1 WHERE id = :messageId")
    suspend fun markSingleMessageAsRead(messageId: String)

    // ==================== توابع جدید برای مدیریت پیام‌های چندبخشی ====================

    // 4. درج یک پیام جدید
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: SmsEntity)

    // 5. آپدیت وضعیت پیام چندبخشی
    @Update
    suspend fun update(sms: SmsEntity)

    // 6. دریافت قطعات ناقص یک پیام چندبخشی
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 0 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        ORDER BY partIndex ASC
    """)
    suspend fun getIncompleteMultipartParts(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): List<SmsEntity>

    // 7. دریافت تمام قطعات یک پیام چندبخشی بر اساس کلید
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        ORDER BY partIndex ASC
    """)
    suspend fun getMultipartPartsByKey(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): List<SmsEntity>

    // 8. بررسی آیا یک پیام کامل قبلاً ذخیره شده است
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        LIMIT 1
    """)
    suspend fun getCompleteMultipartMessage(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): SmsEntity?

    // 9. حذف قطعات ناقص یک پیام چندبخشی
    @Query("""
        DELETE FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 0 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
    """)
    suspend fun deleteIncompleteMultipartParts(
        address: String,
        messageId: Long,
        referenceNumber: Int
    )

    // 10. آپدیت وضعیت یک پیام به "کامل"
    @Query("""
        UPDATE sms_table 
        SET isComplete = 1, 
            status = 2,
            body = :combinedBody 
        WHERE id = :messageId
    """)
    suspend fun markMessageAsComplete(messageId: String, combinedBody: String)

    // 11. دریافت پیام‌های چندبخشی که هنوز کامل نیستند
    @Query("""
        SELECT DISTINCT address, messageId, referenceNumber 
        FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 0 
        AND date > :timeThreshold
    """)
    suspend fun getIncompleteMultipartMessages(timeThreshold: Long): List<MultipartKey>

    // 12. دریافت تعداد قطعات موجود برای یک پیام چندبخشی
    @Query("""
        SELECT COUNT(*) FROM sms_table 
        WHERE isMultipart = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
    """)
    suspend fun getMultipartPartCount(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): Int

    // 13. حذف پیام بر اساس ID
    @Query("DELETE FROM sms_table WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ==================== کلاس کمکی برای کلید پیام‌های چندبخشی ====================
data class MultipartKey(
    val address: String,
    val messageId: Long,
    val referenceNumber: Int
)