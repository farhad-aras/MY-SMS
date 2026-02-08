package com.example.mysms.ui.theme

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

    // ==================== لیست سفید آدرس‌های معتبر (سایت‌های ایمن) ====================
    private val whitelistDomains = mutableSetOf(
        // سایت‌های ایرانی
        "adliran.ir",
        "divar.ir",
        "digikala.com",
        "snapp.ir",
        "tapsi.ir",
        "sheypoor.com",
        "bamilo.com",
        "torob.com",
        "iran.ir",
        "saman.bank",
        "melli.bank",
        "sepah.bank",
        "sadadpsp.ir",
        "shaparak.ir",

        // سایت‌های بین‌المللی معتبر
        "google.com",
        "youtube.com",
        "github.com",
        "stackoverflow.com",
        "wikipedia.org",
        "android.com",
        "developer.android.com",
        "telegram.org",
        "whatsapp.com",
        "twitter.com",
        "facebook.com",
        "instagram.com",
        "linkedin.com",

        // سایت‌های خدماتی
        "gmail.com",
        "yahoo.com",
        "microsoft.com",
        "apple.com",

        // سایت‌های آموزشی
        "coursera.org",
        "udemy.com",
        "khanacademy.org",

        // سایت‌های خبری معتبر
        "bbc.com",
        "cnn.com",
        "reuters.com",
        "apnews.com"
    )

    // ==================== لیست سیاه آدرس‌های خطرناک ====================
    private val blacklistDomains = mutableSetOf(
        "malware.com",
        "phishing-site.com",
        "virus-download.com",
        "hack-me.ir",
        "fake-bank.ir",
        "free-virus.com",
        "cracked-software.com",
        "pirate-bay.org"
    )

    // ==================== لیست سایت‌های نسبتاً ایمن (نیاز به تأیید کم) ====================
    private val moderateSafeDomains = mutableSetOf(
        "blogger.com",
        "wordpress.com",
        "medium.com",
        "reddit.com",
        "quora.com",
        "pinterest.com",
        "tumblr.com",
        "flickr.com",
        "imgur.com",
        "dropbox.com",
        "drive.google.com",
        "docs.google.com"
    )

    // ==================== الگوهای امن برای دامنه‌های ایرانی ====================
    private val iranianSecurePatterns = listOf(
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.ir(/.*)?$"),
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.co\\.ir(/.*)?$"),
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.ac\\.ir(/.*)?$"),
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.gov\\.ir(/.*)?$"),
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.org\\.ir(/.*)?$"),
        Pattern.compile("^https?://[a-zA-Z0-9-]+\\.net\\.ir(/.*)?$")
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
                domain = "unknown",
                requiresConfirmation = true
            )

            Log.d(TAG, "🌐 دامنه استخراج شده: $domain")

            // 2. بررسی لیست سیاه (اولویت بالا)
            if (isInBlacklist(domain)) {
                Log.w(TAG, "⛔ لینک در لیست سیاه: $domain")
                return LinkSecurityResult(
                    isSafe = false,
                    securityLevel = SecurityLevel.DANGEROUS,
                    message = "این لینک در لیست آدرس‌های خطرناک قرار دارد و مسدود شده است",
                    domain = domain,
                    reason = "BLACKLISTED",
                    requiresConfirmation = false
                )
            }

            // 3. بررسی لیست سفید (امنیت کامل - بدون نیاز به تأیید)
            if (isInWhitelist(domain)) {
                Log.d(TAG, "✅ لینک در لیست سفید: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.VERY_SAFE,
                    message = "آدرس معتبر و تأیید شده - امن",
                    domain = domain,
                    reason = "WHITELISTED",
                    requiresConfirmation = false
                )
            }

            // 4. بررسی لیست نسبتاً ایمن (نیاز به تأیید کم)
            if (isInModerateList(domain)) {
                Log.d(TAG, "⚠️ لینک نسبتاً ایمن: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.MODERATE,
                    message = "آدرس شناخته شده اما نیاز به تأیید دارد",
                    domain = domain,
                    reason = "MODERATE_SAFE",
                    requiresConfirmation = true
                )
            }

            // 5. بررسی الگوهای امن ایرانی
            if (isIranianSecureUrl(url)) {
                Log.d(TAG, "🇮🇷 لینک ایرانی: $domain")
                return LinkSecurityResult(
                    isSafe = true,
                    securityLevel = SecurityLevel.SAFE,
                    message = "آدرس ایرانی - نیاز به تأیید دارد",
                    domain = domain,
                    reason = "IRANIAN_SECURE",
                    requiresConfirmation = true
                )
            }

            // 6. بررسی پروتکل HTTPS
            val hasHttps = url.startsWith("https://", ignoreCase = true)
            if (!hasHttps) {
                Log.w(TAG, "⚠️ لینک بدون HTTPS: $domain")
                return LinkSecurityResult(
                    isSafe = false,
                    securityLevel = SecurityLevel.RISKY,
                    message = "این لینک از پروتکل امن HTTPS استفاده نمی‌کند - خطرناک",
                    domain = domain,
                    reason = "NO_HTTPS",
                    requiresConfirmation = true
                )
            }

            // 7. بررسی دامنه عمومی (کم خطر اما ناشناس)
            if (isCommonDomain(domain)) {
                Log.d(TAG, "🌍 دامنه عمومی ناشناس: $domain")
                return LinkSecurityResult(
                    isSafe = false,
                    securityLevel = SecurityLevel.UNKNOWN,
                    message = "آدرس عمومی اما ناشناس برای سیستم - نیاز به تأیید دارد",
                    domain = domain,
                    reason = "COMMON_UNKNOWN",
                    requiresConfirmation = true
                )
            }

            // 8. لینک کاملاً ناشناس - نیاز به تأیید قوی
            Log.w(TAG, "❓ لینک کاملاً ناشناس: $domain")
            LinkSecurityResult(
                isSafe = false,
                securityLevel = SecurityLevel.HIGHLY_RISKY,
                message = "آدرس کاملاً ناشناس و خطرناک",
                domain = domain,
                reason = "COMPLETELY_UNKNOWN",
                requiresConfirmation = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در بررسی لینک: ${e.message}", e)
            LinkSecurityResult(
                isSafe = false,
                securityLevel = SecurityLevel.DANGEROUS,
                message = "خطا در بررسی آدرس: ${e.message}",
                domain = "error",
                requiresConfirmation = true
            )
        }
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
     * بررسی آیا دامنه در لیست نسبتاً ایمن است
     */
    private fun isInModerateList(domain: String): Boolean {
        val cleanDomain = domain.lowercase()

        // بررسی مستقیم
        if (moderateSafeDomains.contains(cleanDomain)) {
            return true
        }

        // بررسی subdomainها
        val domainParts = cleanDomain.split(".")
        if (domainParts.size >= 2) {
            val rootDomain = domainParts.takeLast(2).joinToString(".")
            if (moderateSafeDomains.contains(rootDomain)) {
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
            "fake", "scam", "fraud", "spyware", "keylogger",
            "钓鱼", "黑客" // چینی برای فیشینگ و هک
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
            ".co", ".io", ".ai", ".dev", ".app", ".me", ".info"
        )

        return commonTlds.any { domain.endsWith(it) }
    }

    /**
     * افزودن دامنه به لیست سفید
     */
    fun addToWhitelist(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isNotEmpty()) {
            whitelistDomains.add(cleanDomain)
            Log.d(TAG, "➕ دامنه به لیست سفید اضافه شد: $cleanDomain")
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
     * دریافت لیست نسبتاً ایمن
     */
    fun getModerateList(): Set<String> {
        return moderateSafeDomains.toSet()
    }

    /**
     * باز کردن لینک با بررسی امنیت و نمایش دیالوگ برای سایت‌های ناشناس
     */
    fun openLinkWithSecurityCheck(context: Context, url: String, onConfirmation: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            val securityResult = checkLinkSecurity(url)
            val domain = securityResult.domain

            Log.d(TAG, "🔐 وضعیت امنیتی: ${securityResult.securityLevel} - نیاز به تأیید: ${securityResult.requiresConfirmation}")

            // اگر در لیست سیاه باشد، اصلاً باز نشود
            if (securityResult.securityLevel == SecurityLevel.DANGEROUS) {
                Toast.makeText(
                    context,
                    "❌ این لینک مسدود شده است: ${securityResult.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.w(TAG, "🚫 لینک خطرناک مسدود شد: $url")
                return@launch
            }

            // اگر در لیست سفید باشد، مستقیم باز شود
            if (securityResult.securityLevel == SecurityLevel.VERY_SAFE && !securityResult.requiresConfirmation) {
                Log.d(TAG, "✅ باز کردن مستقیم لینک ایمن: $domain")
                openLink(context, url)
                return@launch
            }

            // برای بقیه موارد، دیالوگ تأیید نشان داده شود
            showSecurityConfirmationDialog(
                context = context,
                securityResult = securityResult,
                url = url,
                onConfirm = { openLink(context, url) },
                onCancel = {
                    Toast.makeText(context, "❌ باز کردن لینک لغو شد", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * نمایش دیالوگ تأیید امنیتی برای سایت‌های ناشناس
     */
    private fun showSecurityConfirmationDialog(
        context: Context,
        securityResult: LinkSecurityResult,
        url: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val title = when (securityResult.securityLevel) {
            SecurityLevel.VERY_SAFE -> "✅ سایت ایمن"
            SecurityLevel.SAFE -> "⚠️ سایت ایرانی"
            SecurityLevel.MODERATE -> "⚠️ سایت نسبتاً ایمن"
            SecurityLevel.RISKY -> "⚠️ هشدار امنیتی"
            SecurityLevel.UNKNOWN -> "⚠️ سایت ناشناس"
            SecurityLevel.HIGHLY_RISKY -> "🚫 سایت بسیار خطرناک"
            SecurityLevel.DANGEROUS -> "🚫 سایت مسدود شده"
        }

        val message = buildString {
            append("آدرس: ${securityResult.domain}\n\n")
            append("وضعیت: ${securityResult.message}\n\n")

            when (securityResult.securityLevel) {
                SecurityLevel.VERY_SAFE -> append("✅ این سایت در لیست سفید قرار دارد و کاملاً ایمن است.")
                SecurityLevel.SAFE -> append("⚠️ این سایت ایرانی است اما در لیست سفید نیست.\nآیا مطمئن هستید؟")
                SecurityLevel.MODERATE -> append("⚠️ این سایت نسبتاً شناخته شده است اما نیاز به تأیید دارد.")
                SecurityLevel.RISKY -> append("⚠️ این سایت از پروتکل HTTPS استفاده نمی‌کند.\nخطر نشت اطلاعات وجود دارد!")
                SecurityLevel.UNKNOWN -> append("⚠️ این سایت برای سیستم ناشناس است.\nاحتمال خطر وجود دارد!")
                SecurityLevel.HIGHLY_RISKY -> append("🚫 این سایت کاملاً ناشناس و خطرناک است.\nتوصیه می‌شود باز نکنید!")
                SecurityLevel.DANGEROUS -> append("🚫 این سایت در لیست سیاه قرار دارد!")
            }

            append("\n\nآدرس کامل:\n$url")
        }

        val positiveButtonText = when (securityResult.securityLevel) {
            SecurityLevel.HIGHLY_RISKY, SecurityLevel.DANGEROUS -> "باز کردن (با مسئولیت خود)"
            else -> "باز کردن سایت"
        }

        val negativeButtonText = when (securityResult.securityLevel) {
            SecurityLevel.HIGHLY_RISKY, SecurityLevel.DANGEROUS -> "لغو (توصیه می‌شود)"
            else -> "لغو"
        }

        android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
                Log.d(TAG, "✅ کاربر تأیید کرد: $url")
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                onCancel()
                dialog.dismiss()
                Log.d(TAG, "❌ کاربر لغو کرد: $url")
            }
            .setNeutralButton("افزودن به لیست سفید") { dialog, _ ->
                addToWhitelist(securityResult.domain)
                Toast.makeText(context, "✅ سایت به لیست سفید اضافه شد", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // بعد از اضافه کردن، دوباره تلاش کن
                openLinkWithSecurityCheck(context, url)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * باز کردن لینک در مرورگر
     */
    fun openLink(context: Context, url: String) {
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
    val requiresConfirmation: Boolean = true  // پیش‌فرض: نیاز به تأیید
)

/**
 * سطح امنیت لینک (به‌روزرسانی شده)
 */
enum class SecurityLevel {
    VERY_SAFE,     // لیست سفید - بدون نیاز به تأیید
    SAFE,          // ایرانی امن - نیاز به تأیید
    MODERATE,      // نسبتاً ایمن - نیاز به تأیید
    RISKY,         // بدون HTTPS - نیاز به تأیید
    UNKNOWN,       // ناشناس عمومی - نیاز به تأیید
    HIGHLY_RISKY,  // کاملاً ناشناس - نیاز به تأیید قوی
    DANGEROUS      // لیست سیاه - مسدود
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