package com.example.mysms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_table ORDER BY date DESC")
    fun getAllSmsFlow(): Flow<List<com.example.mysms.data.SmsEntity>>

    @Query("SELECT * FROM sms_table ORDER BY date DESC")
    suspend fun getAllSms(): List<SmsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sms: List<SmsEntity>)

    // تغییر اینجا: فقط پیام‌های خوانده نشده را آپدیت کن
    @Query("UPDATE sms_table SET read = 1 WHERE address = :address AND read = 0")
    suspend fun markAsRead(address: String)

    // تابع اضافی برای آپدیت یک پیام خاص (اختیاری)
    @Query("UPDATE sms_table SET read = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: Long)
}