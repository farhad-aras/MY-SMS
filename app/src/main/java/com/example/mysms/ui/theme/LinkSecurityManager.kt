package com.example.mysms.ui.theme


import com.example.mysms.ui.theme.LinkSecurityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import java.util.regex.Pattern

/**
 * مدیریت امنیت لینک‌ها با لیست سفید و سیستم تأیید
 */
object LinkSecurityManager {

    private const val TAG = "LinkSecurityManager"

    // ==================== لیست سفید آدرس‌های معتبر ====================
    private val whitelistDomains = mutableSetOf(
        "adliran.ir",
        "google.com",
        "github.com",
        "stackoverflow.com",
        "wikipedia.org",
        "android.com",
        "developer.android.com",
        "telegram.org",
        "whatsapp.com",
        "iran.ir",
        "saman.bank",
        "melli.bank",
        "sepah.bank",
        // افزودن دامنه‌های معتبر ایرانی
        "divar.ir",
        "digikala.com",
        "snapp.ir",
        "tapsi.ir",
        "sheypoor.com",
        "bamilo.com",
        "torob.com"
    )

    // ==================== لیست سیاه آدرس‌های خطرناک ====================
    private val blacklistDomains = mutableSetOf(
        "malware.com",
        "phishing-site.com",
        "virus-download.com",
        "hack-me.ir",
        "fake-bank.ir"
    )

    // ==================== الگوهای امن برای دامنه‌های ایرانی ====================
    private val iranianSecurePatterns = listOf(
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.ir(/.*)?$"),
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.co\\.ir(/.*)?$"),
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.ac\\.ir(/.*)?$"),
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.gov\\.ir(/.*)?$"),
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.org\\.ir(/.*)?$"),
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.net\\.ir(/.*)?$")
    )

    /**
     * بررسی امنیت یک لینک
     * @param url آدرس لینک برای بررسی
     * @return نتیجه بررسی شامل وضعیت امنیتی و پیام
     */
    fun checkLinkSecurity(url: String): LinkSecurityResult {
        return try {
            Log.d(TAG, "🔍 بررسی امنیت لینک: $url")

            // 1. اعتبارسنجی اولیه URL
            val parsedUrl = URL(url)
            val domain = parsedUrl.host?.lowercase() ?: return LinkSecurityResult(
                isSafe = false,
                securityLevel = SecurityLevel.DANGEROUS,
                message = "آدرس نامعتبر است",
                domain = "unknown"
            )

            Log.d(TAG, "🌐 دامنه استخراج شده: $domain")

            // 2. بررسی لیست سیاه (اولویت بالا)
            if (isInBlacklist(domain)) {
                Log.w(TAG, "⛔ لینک در لیست سیاه: $domain")
                return LinkSecurityResult(
                    isSafe = false,
                    securityLevel = SecurityLevel.DANGEROUS,
                    message = "این لینک در لیست آدرس‌های خطرناک قرار دارد",
                    domain = domain,
                    reason = "BLACKLISTED"
                )
            }

            // 3. بررسی لیست سفید (امنیت کامل)
            if (isInWhitelist(domain)) {
                Log.d(TAG, "✅ لینک در لیست سفید: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.VERY_SAFE,
                    message = "آدرس معتبر و تأیید شده",
                    domain = domain,
                    reason = "WHITELISTED"
                )
            }

            // 4. بررسی الگوهای امن ایرانی
            if (isIranianSecureUrl(url)) {
                Log.d(TAG, "🇮🇷 لینک ایرانی امن: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.SAFE,
                    message = "آدرس ایرانی معتبر",
                    domain = domain,
                    reason = "IRANIAN_SECURE"
                )
            }

            // 5. بررسی پروتکل HTTPS
            val hasHttps = url.startsWith("https://", ignoreCase = true)
            if (!hasHttps) {
                Log.w(TAG, "⚠️ لینک بدون HTTPS: $domain")
                return LinkSecurityResult(
                    isSafe = false,
                    securityLevel = SecurityLevel.RISKY,
                    message = "این لینک از پروتکل امن HTTPS استفاده نمی‌کند",
                    domain = domain,
                    reason = "NO_HTTPS"
                )
            }

            // 6. بررسی دامنه عمومی (کم خطر)
            if (isCommonDomain(domain)) {
                Log.d(TAG, "🌍 دامنه عمومی: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.MODERATE,
                    message = "آدرس شناخته شده",
                    domain = domain,
                    reason = "COMMON_DOMAIN"
                )
            }

            // 7. لینک ناشناس - نیاز به تأیید کاربر
            Log.w(TAG, "❓ لینک ناشناس: $domain")
            LinkSecurityResult(
                isSafe = false,
                securityLevel = SecurityLevel.UNKNOWN,
                message = "آدرس برای سیستم ناشناس است",
                domain = domain,
                reason = "UNKNOWN_DOMAIN",
                requiresConfirmation = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در بررسی لینک: ${e.message}", e)
            LinkSecurityResult(
                isSafe = false,
                securityLevel = SecurityLevel.DANGEROUS,
                message = "خطا در بررسی آدرس: ${e.message}",
                domain = "error"
            )
        }
    }

    /**
     * افزودن دامنه به لیست سفید
     */
    fun addToWhitelist(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isNotEmpty()) {
            whitelistDomains.add(cleanDomain)
            Log.d(TAG, "➕ دامنه به لیست سفید اضافه شد: $cleanDomain")

            // ذخیره در SharedPreferences برای جلسات بعدی
            saveToPreferences(cleanDomain)
        }
    }

    /**
     * حذف دامنه از لیست سفید
     */
    fun removeFromWhitelist(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        whitelistDomains.remove(cleanDomain)
        Log.d(TAG, "➖ دامنه از لیست سفید حذف شد: $cleanDomain")
    }

    /**
     * دریافت لیست سفید
     */
    fun getWhitelist(): Set<String> {
        return whitelistDomains.toSet()
    }

    /**
     * بررسی آیا دامنه در لیست سفید است
     */
    private fun isInWhitelist(domain: String): Boolean {
        // بررسی دقیق دامنه
        val cleanDomain = domain.lowercase()

        // بررسی مستقیم
        if (whitelistDomains.contains(cleanDomain)) {
            return true
        }

        // بررسی subdomainها (مثلاً blog.google.com شامل google.com است)
        val domainParts = cleanDomain.split(".")
        if (domainParts.size >= 2) {
            val rootDomain = domainParts.takeLast(2).joinToString(".")
            if (whitelistDomains.contains(rootDomain)) {
                return true
            }
        }

        return false
    }

    /**
     * بررسی آیا دامنه در لیست سیاه است
     */
    private fun isInBlacklist(domain: String): Boolean {
        val cleanDomain = domain.lowercase()

        // بررسی مستقیم
        if (blacklistDomains.contains(cleanDomain)) {
            return true
        }

        // بررسی الگوهای خطرناک
        val dangerousPatterns = listOf(
            "hack", "phish", "malware", "virus", "trojan", "exploit",
            "fake", "scam", "fraud", "钓鱼", "黑客" // چینی برای فیشینگ و هک
        )

        return dangerousPatterns.any { pattern ->
            cleanDomain.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * بررسی آیا URL ایرانی امن است
     */
    private fun isIranianSecureUrl(url: String): Boolean {
        return iranianSecurePatterns.any { pattern ->
            pattern.matcher(url).matches()
        }
    }

    /**
     * بررسی آیا دامنه عمومی و شناخته شده است
     */
    private fun isCommonDomain(domain: String): Boolean {
        val commonTlds = setOf(
            ".com", ".org", ".net", ".edu", ".gov", ".mil",
            ".co", ".io", ".ai", ".dev", ".app", ".me"
        )

        val commonDomains = setOf(
            "youtube.com", "facebook.com", "twitter.com", "instagram.com",
            "linkedin.com", "reddit.com", "pinterest.com", "tumblr.com",
            "wordpress.com", "blogspot.com", "medium.com", "quora.com"
        )

        // بررسی TLDهای رایج
        if (commonTlds.any { domain.endsWith(it) }) {
            return true
        }

        // بررسی دامنه‌های شناخته شده
        if (commonDomains.any { domain == it || domain.endsWith(".$it") }) {
            return true
        }

        return false
    }

    /**
     * ذخیره دامنه در SharedPreferences
     */
    private fun saveToPreferences(domain: String) {
        // این تابع می‌تواند برای ذخیره دائمی لیست سفید استفاده شود
        // فعلاً در حافظه موقت نگهداری می‌شود
    }

    /**
     * باز کردن لینک با بررسی امنیت
     */
    fun openLinkWithSecurityCheck(context: Context, url: String, onConfirmation: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            val securityResult = checkLinkSecurity(url)

            when (securityResult.securityLevel) {
                SecurityLevel.VERY_SAFE, SecurityLevel.SAFE -> {
                    // باز کردن مستقیم لینک امن
                    openLink(context, url)
                }

                SecurityLevel.MODERATE -> {
                    // نمایش هشدار مختصر
                    showSecurityDialog(
                        context = context,
                        title = "هشدار امنیتی",
                        message = "آدرس ${securityResult.domain} برای سیستم ناشناس است.\n\n${securityResult.message}",
                        positiveText = "باز کردن",
                        negativeText = "لغو",
                        onConfirm = { openLink(context, url) }
                    )
                }

                SecurityLevel.RISKY -> {
                    // هشدار جدی‌تر
                    showSecurityDialog(
                        context = context,
                        title = "⚠️ هشدار امنیتی مهم",
                        message = "این لینک ممکن است ناامن باشد:\n\n" +
                                "• ${securityResult.message}\n" +
                                "• دامنه: ${securityResult.domain}\n\n" +
                                "آیا مطمئن هستید که می‌خواهید ادامه دهید؟",
                        positiveText = "باز کردن (با مسئولیت خود)",
                        negativeText = "لغو",
                        onConfirm = { openLink(context, url) }
                    )
                }

                SecurityLevel.UNKNOWN -> {
                    // نیاز به تأیید صریح کاربر
                    showSecurityDialog(
                        context = context,
                        title = "🔒 لینک ناشناس",
                        message = "امنیت شناسایی نشد!\n\n" +
                                "آدرس: ${securityResult.domain}\n" +
                                "وضعیت: ${securityResult.message}\n\n" +
                                "مطمئن هستید که می‌خواهید این لینک باز شود؟",
                        positiveText = "باز کردن (تأیید می‌کنم)",
                        negativeText = "لغو",
                        onConfirm = { openLink(context, url) }
                    )
                }

                SecurityLevel.DANGEROUS -> {
                    // مسدود کردن کامل
                    Toast.makeText(
                        context,
                        "❌ این لینک مسدود شده است: ${securityResult.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.w(TAG, "🚫 لینک خطرناک مسدود شد: $url")
                }
            }
        }
    }

    /**
     * باز کردن لینک در مرورگر
     */
    private fun openLink(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "🌐 لینک باز شد: $url")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در باز کردن لینک", e)
            Toast.makeText(context, "❌ خطا در باز کردن لینک", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * نمایش دیالوگ امنیتی
     */
    private fun showSecurityDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String,
        onConfirm: () -> Unit
    ) {
        android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(negativeText) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * کامپوزبل برای استفاده در UI
     */
    @Composable
    fun rememberLinkSecurityState(url: String): LinkSecurityResult {
        return remember(url) {
            mutableStateOf(checkLinkSecurity(url))
        }.value
    }
}

// ==================== مدل‌های داده ====================

/**
 * نتیجه بررسی امنیت لینک
 */
data class LinkSecurityResult(
    val isSafe: Boolean,
    val securityLevel: SecurityLevel,
    val message: String,
    val domain: String,
    val reason: String? = null,
    val requiresConfirmation: Boolean = false
)

/**
 * سطح امنیت لینک
 */
enum class SecurityLevel {
    VERY_SAFE,    // لیست سفید
    SAFE,         // ایرانی امن
    MODERATE,     // دامنه عمومی
    RISKY,        // بدون HTTPS
    UNKNOWN,      // ناشناس
    DANGEROUS     // لیست سیاه
}

/**
 * اکستنشن برای استفاده راحت‌تر
 */
fun String.isSafeLink(): Boolean {
    return LinkSecurityManager.checkLinkSecurity(this).isSafe
}

fun String.getLinkSecurityLevel(): SecurityLevel {
    return LinkSecurityManager.checkLinkSecurity(this).securityLevel
}

@Composable
fun String.rememberLinkSecurity(): LinkSecurityResult {
    return LinkSecurityManager.rememberLinkSecurityState(this)
}