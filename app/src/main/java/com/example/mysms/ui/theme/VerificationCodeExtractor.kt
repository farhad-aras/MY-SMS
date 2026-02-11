package com.example.mysms.ui.theme

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تشخیص و استخراج کد تأیید از پیامک‌های بانکی و سرویس‌ها
 */
object VerificationCodeExtractor {

    private const val TAG = "VerificationCodeExtractor"

    /**
     * تشخیص اینکه آیا پیام حاوی کد تأیید است
     */
    fun isVerificationCodeMessage(message: String): Boolean {
        val verificationPatterns = listOf(
            Regex("""\b\d{4,6}\b"""), // کد ۴-۶ رقمی
            Regex("""کد.*?(\d{4,6})"""),
            Regex("""code.*?(\d{4,6})""", RegexOption.IGNORE_CASE),
            Regex("""رمز.*?(\d{4,6})"""),
            Regex("""verification.*?(\d{4,6})""", RegexOption.IGNORE_CASE),
            Regex("""تأیید.*?(\d{4,6})"""),
            Regex("""otp.*?(\d{4,6})""", RegexOption.IGNORE_CASE)
        )

        return verificationPatterns.any { pattern ->
            pattern.containsMatchIn(message)
        }
    }

    /**
     * استخراج هوشمند کد تأیید از متن پیام
     */
    fun extractVerificationCode(text: String): String {
        try {
            Log.d(TAG, "🔍 جستجوی کد در متن: ${text.take(50)}...")

            // 1. خطوط متن را جدا کن
            val lines = text.split("\n").map { it.trim() }

            // 2. کلمات کلیدی اصلی
            val primaryKeywords = listOf(
                "رمز", "کد", "code", "Code", "پویا", "pin", "PIN", "تأیید", "ورود", "verify"
            )

            // 3. الگوهای کامل برای جستجو
            val patterns = listOf(
                // فرمت: "رمز 123456"
                Regex("""(رمز|کد|code|Code|پویا)[\s:]*(\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "رمز: 123456"
                Regex("""(رمز|کد|code|Code|پویا)[\s:]*[:]?[\s]*(\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "G-123456"
                Regex("""G[-](\d{4,8})""", RegexOption.IGNORE_CASE),
                // فرمت: "#12345"
                Regex("""#(\d{4,8})"""),
                // فرمت: "کد محرمانه ... 12345"
                Regex("""کد[\s\S]{0,30}?(\d{4,8})"""),
                // فرمت: "code is 12345"
                Regex("""(code|Code|verification)[\s\S]{0,20}?(\d{4,8})""", RegexOption.IGNORE_CASE)
            )

            // 4. اولویت ۱: جستجو در کل متن با الگوها
            for (pattern in patterns) {
                val match = pattern.find(text)
                if (match != null) {
                    // گروه 1 یا 2 را بگیر (بسته به الگو)
                    val code = when {
                        match.groups.size >= 3 && match.groups[2] != null -> match.groups[2]!!.value
                        match.groups.size >= 2 && match.groups[1] != null -> match.groups[1]!!.value
                        else -> match.value.replace(Regex("""[^\d]"""), "")
                    }

                    if (code.length in 4..8) {
                        Log.d(TAG, "✅ کد یافت شد (الگو): $code")
                        return code
                    }
                }
            }

            // 5. اولویت ۲: جستجو خط به خط
            for (line in lines) {
                // خطوطی که کلمه کلیدی دارند
                if (primaryKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
                    Log.d(TAG, "📄 بررسی خط: $line")

                    // پیدا کردن آخرین عدد ۴-۸ رقمی در این خط
                    val numbers = Regex("""\b(\d{4,8})\b""").findAll(line).toList()

                    if (numbers.isNotEmpty()) {
                        // آخرین عدد در خط (کد معمولاً آخر است)
                        val lastNumber = numbers.last().value

                        // بررسی که مبلغ نباشد
                        val isAmount = line.contains("ریال") || line.contains("مبلغ") ||
                                line.contains("تومان") || line.contains("قیمت")

                        // بررسی که زمان نباشد
                        val isTime = Regex("""\d{1,2}:\d{1,2}(:\d{1,2})?""").containsMatchIn(line)

                        if (!isAmount && !isTime) {
                            Log.d(TAG, "✅ کد یافت شد (خط): $lastNumber")
                            return lastNumber
                        } else {
                            Log.d(TAG, "⏭️ عدد رد شد (مبلغ/زمان): $lastNumber")
                        }
                    }
                }
            }

            // 6. اولویت ۳: پیدا کردن تمام اعداد و انتخاب بهترین
            val allNumbers = Regex("""\b(\d{4,8})\b""").findAll(text).toList()

            if (allNumbers.isNotEmpty()) {
                // امتیازدهی به هر عدد
                val scored = mutableListOf<Pair<String, Int>>()

                for (match in allNumbers) {
                    val number = match.value
                    val startPos = match.range.first
                    var score = 0

                    // امتیاز طول
                    when (number.length) {
                        4 -> score += 20
                        5 -> score += 30
                        6 -> score += 25
                        7 -> score += 15
                        8 -> score += 10
                    }

                    // متن اطراف عدد
                    val contextStart = maxOf(0, startPos - 10)
                    val contextEnd = minOf(text.length, startPos + number.length + 10)
                    val context = text.substring(contextStart, contextEnd).lowercase()

                    // امتیاز مثبت برای کلمات کلیدی
                    if (primaryKeywords.any { context.contains(it.lowercase()) }) {
                        score += 50
                    }

                    // امتیاز منفی برای مبلغ/زمان
                    if (context.contains("ریال") || context.contains("مبلغ") ||
                        context.contains("تومان") || context.contains("قیمت")) {
                        score -= 100
                    }

                    if (context.contains(":") && Regex("""\d{1,2}:\d{1,2}""").containsMatchIn(context)) {
                        score -= 50
                    }

                    // امتیاز موقعیت
                    if (startPos > text.length / 2) {
                        score += 20
                    }

                    scored.add(Pair(number, score))
                }

                // انتخاب بهترین امتیاز
                val best = scored.maxByOrNull { it.second }
                if (best != null && best.second > 30) {
                    Log.d(TAG, "✅ کد یافت شد (بهترین): ${best.first} (امتیاز: ${best.second})")
                    return best.first
                }
            }

            // 7. اولویت ۴: جستجوی اعداد بعد از کاراکترهای خاص
            val specialPatterns = listOf(
                Regex("""[:]\s*(\d{4,8})"""),      // بعد از :
                Regex("""[-]\s*(\d{4,8})"""),      // بعد از -
                Regex("""[#]\s*(\d{4,8})"""),      // بعد از #
                Regex("""is\s+(\d{4,8})""", RegexOption.IGNORE_CASE)  // بعد از is
            )

            for (pattern in specialPatterns) {
                val match = pattern.find(text)
                if (match != null && match.groups.size > 1) {
                    val code = match.groups[1]?.value
                    if (!code.isNullOrEmpty() && code.length in 4..8) {
                        Log.d(TAG, "✅ کد یافت شد (کاراکتر خاص): $code")
                        return code
                    }
                }
            }

            Log.d(TAG, "❌ کد یافت نشد")
            return "کد یافت نشد"

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در استخراج کد", e)
            return "خطا در شناسایی"
        }
    }

    /**
     * نسخه suspend برای استفاده در Coroutines
     */
    suspend fun extractVerificationCodeSuspend(text: String): String = withContext(Dispatchers.Default) {
        extractVerificationCode(text)
    }
}