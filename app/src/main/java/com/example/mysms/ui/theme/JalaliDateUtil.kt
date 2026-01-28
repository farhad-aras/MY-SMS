package com.example.mysms.ui.theme

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
}