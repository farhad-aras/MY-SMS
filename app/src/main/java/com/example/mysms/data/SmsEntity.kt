package com.example.mysms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.TypeConverters

@Entity(tableName = "sms_table")
@TypeConverters(Converters::class)
data class SmsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "type")
    val type: Int, // 1 = دریافت، 2 = ارسال

    @ColumnInfo(name = "subId")
    val subId: Int,

    @ColumnInfo(name = "read")
    val read: Boolean = false,

    // ==================== فیلدهای جدید برای پشتیبانی از پیام‌های چندبخشی ====================
    @ColumnInfo(name = "threadId", defaultValue = "0")
    val threadId: Long = 0,

    @ColumnInfo(name = "messageId", defaultValue = "0")
    val messageId: Long = 0,

    @ColumnInfo(name = "partCount", defaultValue = "1")
    val partCount: Int = 1,

    @ColumnInfo(name = "partIndex", defaultValue = "1")
    val partIndex: Int = 1,

    @ColumnInfo(name = "referenceNumber", defaultValue = "0")
    val referenceNumber: Int = 0,

    @ColumnInfo(name = "isMultipart", defaultValue = "0")
    val isMultipart: Boolean = false,

    @ColumnInfo(name = "isComplete", defaultValue = "1")
    val isComplete: Boolean = true,

    @ColumnInfo(name = "contentType", defaultValue = "'text/plain'")
    val contentType: String = "text/plain",

    @ColumnInfo(name = "encoding", defaultValue = "'UTF-8'")
    val encoding: String = "UTF-8",

    @ColumnInfo(name = "status", defaultValue = "-1")
    val status: Int = -1,

    // ==================== فیلدهای جدید برای مدیریت سینک هوشمند ====================
    @ColumnInfo(name = "isSynced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "syncVersion", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "serverId")
    val serverId: String? = null,

    @ColumnInfo(name = "lastModified", defaultValue = "0")
    val lastModified: Long = 0L,

    @ColumnInfo(name = "isDeleted", defaultValue = "0")
    val isDeleted: Boolean = false
) {
    @Ignore
    constructor(
        id: String,
        address: String,
        body: String,
        date: Long,
        type: Int,
        subId: Int,
        read: Boolean
    ) : this(
        id = id,
        address = address,
        body = body,
        date = date,
        type = type,
        subId = subId,
        read = read,
        threadId = 0,
        messageId = 0,
        partCount = 1,
        partIndex = 1,
        referenceNumber = 0,
        isMultipart = false,
        isComplete = true,
        contentType = "text/plain",
        encoding = "UTF-8",
        status = -1,
        isSynced = false,
        syncVersion = 0,
        serverId = null,
        lastModified = date,
        isDeleted = false
    )

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
        this.copy(
            isSynced = true,
            syncVersion = syncVersion,
            lastModified = System.currentTimeMillis()
        )

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

    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
        const val STATUS_UNKNOWN = -1
        const val STATUS_RECEIVED = 0
        const val STATUS_COMBINING = 1
        const val STATUS_COMPLETE = 2
    }
}