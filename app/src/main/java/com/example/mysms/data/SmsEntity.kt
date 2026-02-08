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
    val status: Int = -1                       // وضعیت پیام (-1=ناشناخته, 0=دریافت شده, 1=درحال ترکیب, 2=کامل)
) {
    // تابع کمکی برای بررسی آیا این قطعه بخشی از یک پیام چندبخشی است
    fun isPartOfMultipart(): Boolean = isMultipart && partCount > 1

    // تابع کمکی برای تولید کلید یکتا برای گروه‌بندی قطعات
    fun getMultipartKey(): String = "$address-$messageId-$referenceNumber"

    // تابع کمکی برای بررسی آیا این آخرین قطعه است
    fun isLastPart(): Boolean = isMultipart && partIndex == partCount
}