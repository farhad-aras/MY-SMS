package com.example.mysms.ui.theme

import java.text.SimpleDateFormat
import java.util.*

object JalaliDateUtil {
    fun getRelativeDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDay = 24 * 60 * 60 * 1000L

        return if (diff < oneDay && getDateOnly(now) == getDateOnly(timestamp)) {
            getTimeOnly(timestamp)
        } else {
            getDateOnly(timestamp)
        }
    }

    fun getTimeOnly(timestamp: Long): String {
        val date = Date(timestamp)
        val cal = Calendar.getInstance()
        cal.time = date
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    fun getDateOnly(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): String {
        val gDays = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDays = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jy: Int
        var jm: Int
        var jd: Int
        var gy2 = gy - 1600
        var gm2 = gm - 1
        var gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDays[i + 1]
        if (gm2 > 1 && ((gy2 % 4 == 0 && gy2 % 100 != 0) || (gy2 % 400 == 0))) gDayNo++
        gDayNo += gd2

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053
        jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        for (i in 0..10) {
            if (jDayNo < jDays[i + 1]) {
                jm = i + 1
                jd = jDayNo + 1
                return "$jy/$jm/$jd"
            }
            jDayNo -= jDays[i + 1]
        }
        jm = 12
        jd = jDayNo + 1
        return "$jy/$jm/$jd"
    }

    /**
     * گرفتن نام روز هفته به فارسی
     */
    fun getPersianDayOfWeek(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
    }

    /**
     * گرفتن نام ماه شمسی
     */
    fun getPersianMonthName(timestamp: Long): String {
        val dateStr = getDateOnly(timestamp)
        val parts = dateStr.split("/")
        if (parts.size >= 2) {
            val month = parts[1].toIntOrNull() ?: return dateStr

            return when (month) {
                1 -> "فروردین"
                2 -> "اردیبهشت"
                3 -> "خرداد"
                4 -> "تیر"
                5 -> "مرداد"
                6 -> "شهریور"
                7 -> "مهر"
                8 -> "آبان"
                9 -> "آذر"
                10 -> "دی"
                11 -> "بهمن"
                12 -> "اسفند"
                else -> dateStr
            }
        }
        return dateStr
    }

    /**
     * فرمت کامل تاریخ شمسی
     */
    fun getFullJalaliDate(timestamp: Long): String {
        val dateStr = getDateOnly(timestamp)
        val parts = dateStr.split("/")
        if (parts.size >= 3) {
            val year = parts[0]
            val month = parts[1].toIntOrNull() ?: return dateStr
            val day = parts[2]

            val monthName = when (month) {
                1 -> "فروردین"
                2 -> "اردیبهشت"
                3 -> "خرداد"
                4 -> "تیر"
                5 -> "مرداد"
                6 -> "شهریور"
                7 -> "مهر"
                8 -> "آبان"
                9 -> "آذر"
                10 -> "دی"
                11 -> "بهمن"
                12 -> "اسفند"
                else -> ""
            }

            return "$day $monthName $year"
        }
        return dateStr
    }

    /**
     * گرفتن مدت زمان نسبی (مثلاً "۲ ساعت پیش")
     */
    fun getRelativeTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days روز پیش"
            hours > 0 -> "$hours ساعت پیش"
            minutes > 0 -> "$minutes دقیقه پیش"
            else -> "همین الان"
        }
    }

    /**
     * بررسی آیا دو تاریخ در یک روز هستند یا نه
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        return getDateOnly(timestamp1) == getDateOnly(timestamp2)
    }

    /**
     * گرفتن تاریخ امروز به صورت شمسی
     */
    fun getTodayJalali(): String {
        return getDateOnly(System.currentTimeMillis())
    }
}