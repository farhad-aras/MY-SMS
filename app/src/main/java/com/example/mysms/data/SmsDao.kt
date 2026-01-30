package com.example.mysms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // 1. کوئری فعلی برای صفحه چت - بدون تغییر
    @Query("SELECT * FROM sms_table ORDER BY date DESC")
    fun getAllSmsFlow(): Flow<List<com.example.mysms.data.SmsEntity>>

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
}