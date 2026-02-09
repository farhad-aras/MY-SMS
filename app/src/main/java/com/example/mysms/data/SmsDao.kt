package com.example.mysms.data

import androidx.room.*
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
            AND s2.isDeleted = 0
        )
        AND s1.isDeleted = 0
        ORDER BY s1.date DESC
    """)
    fun getConversationsFlow(): Flow<List<SmsEntity>>

    // 3. کوئری برای گرفتن پیام‌های یک مخاطب خاص (فقط پیام‌های حذف نشده)
    @Query("""
        SELECT * FROM sms_table 
        WHERE address = :address 
        AND isDeleted = 0 
        ORDER BY date DESC
    """)
    fun getSmsByAddressFlow(address: String): Flow<List<SmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sms: List<SmsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: SmsEntity)

    @Update
    suspend fun update(sms: SmsEntity)

    @Query("UPDATE sms_table SET read = 1 WHERE address = :address")
    suspend fun markAsRead(address: String)

    @Query("UPDATE sms_table SET read = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: String)

    // ==================== توابع جدید برای مدیریت پیام‌های چندبخشی ====================

    // 4. دریافت قطعات ناقص یک پیام چندبخشی
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 0 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        AND isDeleted = 0
        ORDER BY partIndex ASC
    """)
    suspend fun getIncompleteMultipartParts(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): List<SmsEntity>

    // 5. دریافت تمام قطعات یک پیام چندبخشی بر اساس کلید
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        AND isDeleted = 0
        ORDER BY partIndex ASC
    """)
    suspend fun getMultipartPartsByKey(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): List<SmsEntity>

    // 6. بررسی آیا یک پیام کامل قبلاً ذخیره شده است
    @Query("""
        SELECT * FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun getCompleteMultipartMessage(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): SmsEntity?

    // 7. حذف قطعات ناقص یک پیام چندبخشی
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

    // 8. آپدیت وضعیت یک پیام به "کامل"
    @Query("""
        UPDATE sms_table 
        SET isComplete = 1, 
            status = 2,
            body = :combinedBody,
            lastModified = :currentTime
        WHERE id = :messageId
    """)
    suspend fun markMessageAsComplete(
        messageId: String,
        combinedBody: String,
        currentTime: Long = System.currentTimeMillis()
    )

    // 9. دریافت پیام‌های چندبخشی که هنوز کامل نیستند
    @Query("""
        SELECT DISTINCT address, messageId, referenceNumber 
        FROM sms_table 
        WHERE isMultipart = 1 
        AND isComplete = 0 
        AND isDeleted = 0
        AND date > :timeThreshold
    """)
    suspend fun getIncompleteMultipartMessages(timeThreshold: Long): List<MultipartKey>

    // 10. دریافت تعداد قطعات موجود برای یک پیام چندبخشی
    @Query("""
        SELECT COUNT(*) FROM sms_table 
        WHERE isMultipart = 1 
        AND messageId = :messageId 
        AND referenceNumber = :referenceNumber 
        AND address = :address
        AND isDeleted = 0
    """)
    suspend fun getMultipartPartCount(
        address: String,
        messageId: Long,
        referenceNumber: Int
    ): Int

    // 11. حذف پیام بر اساس ID (soft delete)
    @Query("""
        UPDATE sms_table 
        SET isDeleted = 1,
            lastModified = :currentTime
        WHERE id = :id
    """)
    suspend fun softDeleteById(id: String, currentTime: Long = System.currentTimeMillis())

    // 12. حذف فیزیکی پیام بر اساس ID
    @Query("DELETE FROM sms_table WHERE id = :id")
    suspend fun deleteById(id: String)

    // ==================== توابع جدید برای مدیریت سینک هوشمند ====================

    // 13. دریافت پیام‌هایی که نیاز به سینک دارند
    @Query("""
        SELECT * FROM sms_table 
        WHERE isSynced = 0 
        AND isDeleted = 0
        AND date > :lastSyncTime
        ORDER BY date ASC
        LIMIT :limit
    """)
    suspend fun getMessagesForSync(
        lastSyncTime: Long,
        limit: Int = 100
    ): List<SmsEntity>

    // 14. علامت‌گذاری پیام‌ها به عنوان سینک شده
    @Query("""
        UPDATE sms_table 
        SET isSynced = 1,
            syncVersion = :syncVersion,
            lastModified = :currentTime
        WHERE id IN (:ids)
    """)
    suspend fun markAsSynced(
        ids: List<String>,
        syncVersion: Int,
        currentTime: Long = System.currentTimeMillis()
    )

    // 15. دریافت آخرین زمان سینک از پیام‌ها
    @Query("""
        SELECT MAX(lastModified) FROM sms_table 
        WHERE isSynced = 1
    """)
    suspend fun getLastSyncTime(): Long?

    // 16. دریافت تعداد پیام‌های نیازمند سینک
    @Query("""
        SELECT COUNT(*) FROM sms_table 
        WHERE isSynced = 0 
        AND isDeleted = 0
        AND date > :lastSyncTime
    """)
    suspend fun getPendingSyncCount(lastSyncTime: Long): Int

    // 17. دریافت پیام‌های جدید از زمان مشخص
    @Query("""
        SELECT * FROM sms_table 
        WHERE date > :sinceTime 
        AND isDeleted = 0
        ORDER BY date ASC
    """)
    suspend fun getMessagesSince(sinceTime: Long): List<SmsEntity>

    // 18. آپدیت وضعیت sync یک پیام
    @Update(entity = SmsEntity::class)
    suspend fun updateSyncStatus(sms: SmsEntity)

    // 19. پاک کردن پیام‌های قدیمی حذف شده (cleanup)
    @Query("""
        DELETE FROM sms_table 
        WHERE isDeleted = 1 
        AND lastModified < :olderThan
    """)
    suspend fun cleanupDeletedMessages(olderThan: Long = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)) // 30 روز

    // 20. دریافت آمار دیتابیس
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN isSynced = 0 THEN 1 ELSE 0 END) as pendingSync,
            SUM(CASE WHEN isMultipart = 1 AND isComplete = 0 THEN 1 ELSE 0 END) as incompleteMultipart,
            SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END) as deleted
        FROM sms_table
    """)
    suspend fun getDatabaseStats(): DatabaseStats
}

// ==================== کلاس‌های کمکی ====================

/**
 * کلید پیام‌های چندبخشی
 */
data class MultipartKey(
    val address: String,
    val messageId: Long,
    val referenceNumber: Int
)

/**
 * آمار دیتابیس
 */
data class DatabaseStats(
    val total: Int = 0,
    val pendingSync: Int = 0,
    val incompleteMultipart: Int = 0,
    val deleted: Int = 0
)