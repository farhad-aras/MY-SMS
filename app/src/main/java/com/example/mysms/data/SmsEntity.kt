package com.example.mysms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_table")
data class SmsEntity(
    @PrimaryKey val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val subId: Int,
    val read: Boolean,
    // ==================== فیلدهای جدید برای پشتیبانی از پیام‌های چندبخشی ====================
    val threadId: Long = 0,                    // شناسه ترد برای گروه‌بندی مکالمات
    val messageId: Long = 0,                   // شناسه یکتا برای هر پیام کامل
    val partCount: Int = 1,                    // تعداد کل قطعات (پیش‌فرض 1)
    val partIndex: Int = 1,                    // شماره قطعه فعلی (پیش‌فرض 1)
    val referenceNumber: Int = 0,              // شماره مرجع برای ارتباط قطعات
    val isMultipart: Boolean = false,          // آیا پیام چندبخشی است؟
    val isComplete: Boolean = true,            // آیا پیام کامل است؟
    val contentType: String = "text/plain",    // نوع محتوا
    val encoding: String = "UTF-8",            // encoding پیام
    val status: Int = -1,                       // وضعیت پیام (-1=ناشناخته, 0=دریافت شده, 1=درحال ترکیب, 2=کامل)

    // ==================== فیلدهای جدید برای مدیریت سینک هوشمند ====================
    val isSynced: Boolean = false,              // آیا در سینک هوشمند ذخیره شده؟
    val syncVersion: Int = 0,                   // نسخه سینک (برای مدیریت تغییرات)
    val serverId: String? = null,               // شناسه یکتا در سرور (اگر استفاده شود) - NULLABLE
    val lastModified: Long = 0L,                // زمان آخرین تغییر (برای سینک افزایشی)
    val isDeleted: Boolean = false              // آیا حذف شده؟ (soft delete)
    ) {
    // تابع کمکی برای بررسی آیا این قطعه بخشی از یک پیام چندبخشی است
    fun isPartOfMultipart(): Boolean = isMultipart && partCount > 1

    // تابع کمکی برای تولید کلید یکتا برای گروه‌بندی قطعات
    fun getMultipartKey(): String = "$address-$messageId-$referenceNumber"

    // تابع کمکی برای بررسی آیا این آخرین قطعه است
    fun isLastPart(): Boolean = isMultipart && partIndex == partCount

    // ==================== توابع جدید برای مدیریت سینک هوشمند ====================

    /**
     * بررسی آیا پیام جدیدتر از زمان مشخص شده است
     * @param threshold زمان حداقل برای جدید بودن (milliseconds)
     */
    fun isNewerThan(threshold: Long): Boolean = date > threshold

    /**
     * بررسی آیا پیام در بازه زمانی مشخص شده است
     * @param startTime زمان شروع (milliseconds)
     * @param endTime زمان پایان (milliseconds)
     */
    fun isWithinTimeRange(startTime: Long, endTime: Long): Boolean =
        date in startTime..endTime

    /**
     * بررسی آیا پیام از آخرین سینک جدیدتر است
     * @param lastSyncTime زمان آخرین سینک
     */
    fun isNewSinceLastSync(lastSyncTime: Long): Boolean =
        date > lastSyncTime && !isSynced

    /**
     * بررسی آیا پیام نیاز به سینک دارد
     * @param currentSyncVersion نسخه فعلی سینک
     */
    fun needsSync(currentSyncVersion: Int): Boolean =
        !isSynced || syncVersion < currentSyncVersion

    /**
     * علامت‌گذاری پیام به عنوان سینک شده
     * @param syncVersion نسخه سینک
     */
    fun markAsSynced(syncVersion: Int = 1): SmsEntity =
        this.copy(isSynced = true, syncVersion = syncVersion, lastModified = System.currentTimeMillis())

    /**
     * علامت‌گذاری پیام به عنوان حذف شده (soft delete)
     */
    fun markAsDeleted(): SmsEntity =
        this.copy(isDeleted = true, lastModified = System.currentTimeMillis())

    /**
     * تولید کلید یکتا برای تشخیص تغییرات
     */
    fun getSyncKey(): String =
        "${address}-${body.hashCode()}-${date}"

    /**
     * بررسی آیا پیام با پیام دیگر یکسان است (برای جلوگیری از duplicate)
     */
    fun isDuplicateOf(other: SmsEntity): Boolean =
        this.address == other.address &&
                this.body == other.body &&
                Math.abs(this.date - other.date) < 1000 // اختلاف کمتر از 1 ثانیه
}