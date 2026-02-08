package com.example.mysms.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.layoutId
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.AlertDialog
import android.app.AlertDialog
import androidx.compose.ui.geometry.Offset
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * کامپوننت پیشرفته برای نمایش پیام با قابلیت‌های:
 * 1. لمس طولانی برای انتخاب پیام
 * 2. انتخاب متن داخل پیام
 * 3. تشخیص هوشمند اعداد
 * 4. کلیک روی لینک‌ها
 * 5. منو عملیات (کپی، اشتراک‌گذاری، اطلاعات)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdvancedMessageBubble(
    message: SmsEntity,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onNumberSelected: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    // تشخیص لینک‌ها و اعداد در متن
    val annotatedText = remember(message.body) {
        createEnhancedAnnotatedText(message.body)
    }

    Box(
        modifier = modifier,
        contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = if (isOwnMessage) {
                RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            } else {
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
            },
            color = if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier
                .clip(
                    if (isOwnMessage) {
                        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    } else {
                        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                    }
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    enabled = true,
                    onClick = { /* کاری انجام نده */ },
                    onLongClick = {
                        showMenu = true
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                ClickableText(
                    text = annotatedText,
                    onClick = { offset ->
                        handleTextClick(annotatedText, offset, context, onNumberSelected)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = JalaliDateUtil.getTimeOnly(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    if (isOwnMessage) {
                        Text(
                            text = if (message.read) "✓✓" else "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.read) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // منو عملیات
        if (showMenu) {
            MessageActionMenu(
                message = message,
                context = context,
                onDismiss = { showMenu = false }
            )
        }
    }
}

/**
 * ایجاد متن حاشیه‌نویسی شده پیشرفته
 */
private fun createEnhancedAnnotatedText(text: String): AnnotatedString {
    return buildAnnotatedString {
        // الگوی تشخیص لینک‌ها
        val linkPattern = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.|ftp://)[\\w\\-._~:/?#\\[\\]@!\\$&'()*+,;=]+\\b|" +
                    "\\b[\\w.]+@[\\w.]+\\.[a-zA-Z]{2,}\\b"
        )

        // الگوی تشخیص اعداد
        val numberPattern = Pattern.compile(
            "\\b\\d{3,}\\b|" +
                    "\\b\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:تومان|ریال|USD|\\\$|€|£)?\\b|" +
                    "\\b(?:\\+?98|0)?9\\d{9}\\b|" +
                    "\\b\\d{4,6}(?:[-\\s]?\\d{4,6})?\\b"
        )

        val linkMatcher = linkPattern.matcher(text)
        val numberMatcher = numberPattern.matcher(text)

        var lastIndex = 0
        val matches = mutableListOf<Triple<Int, Int, String>>()

        // پیدا کردن لینک‌ها
        while (linkMatcher.find()) {
            matches.add(Triple(linkMatcher.start(), linkMatcher.end(), "LINK:${linkMatcher.group()}"))
        }

        // پیدا کردن اعداد
        while (numberMatcher.find()) {
            matches.add(Triple(numberMatcher.start(), numberMatcher.end(), "NUMBER:${numberMatcher.group()}"))
        }

        // مرتب‌سازی
        matches.sortBy { it.first }

        for ((start, end, tag) in matches) {
            // متن قبل از تطابق
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            // متن تطابق
            val matchedText = text.substring(start, end)

            if (tag.startsWith("LINK:")) {
                val url = tag.substring(5)
                pushStringAnnotation("URL", url)
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF2196F3),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(matchedText)
                }
                pop()
            } else if (tag.startsWith("NUMBER:")) {
                val number = tag.substring(7)
                pushStringAnnotation("NUMBER", number)
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFFE8F5E9)
                    )
                ) {
                    append(matchedText)
                }
                pop()
            }

            lastIndex = end
        }

        // متن باقی‌مانده
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

/**
 * هندل کردن کلیک روی متن
 */
private fun handleTextClick(
    annotatedText: AnnotatedString,
    offset: Int,
    context: Context,
    onNumberSelected: (String) -> Unit
) {
    // بررسی لینک
    annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
        val url = annotation.item
        Log.d("MessageBubble", "🌐 لینک کلیک شد: $url")
        LinkSecurityManager.openLinkWithSecurityCheck(context, url)
        return
    }

    // بررسی عدد
    annotatedText.getStringAnnotations("NUMBER", offset, offset).firstOrNull()?.let { annotation ->
        val number = annotation.item
        Log.d("MessageBubble", "🔢 عدد کلیک شد: $number")
        onNumberSelected(number)
        return
    }

    // کلیک روی متن معمولی - منو را نشان بده
    Log.d("MessageBubble", "📝 کلیک روی متن معمولی")
}

/**
 * منو عملیات پیام
 */
@Composable
private fun MessageActionMenu(
    message: SmsEntity,
    context: Context,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        // کپی متن کامل
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("کپی متن")
                }
            },
            onClick = {
                copyToClipboard(context, message.body, "متن پیام")
                onDismiss()
            }
        )

        // استخراج و کپی اعداد
        val numbers = extractAllNumbers(message.body)
        if (numbers.isNotEmpty()) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("کپی اعداد (${numbers.size})")
                    }
                },
                onClick = {
                    val allNumbers = numbers.joinToString("\n")
                    copyToClipboard(context, allNumbers, "اعداد پیام")
                    onDismiss()
                }
            )
        }

        // اشتراک‌گذاری
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اشتراک‌گذاری")
                }
            },
            onClick = {
                shareMessage(context, message.body)
                onDismiss()
            }
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        // اطلاعات
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اطلاعات پیام")
                }
            },
            onClick = {
                showMessageInfo(context, message)
                onDismiss()
            }
        )
    }
}
/**
 * کلاس کمکی برای ذخیره اطلاعات تطابق
 */
private data class Match(
    val start: Int,
    val end: Int,
    val text: String,
    val isLink: Boolean
)

/**
 * استخراج تمام اعداد از متن
 */
private fun extractAllNumbers(text: String): List<String> {
    val pattern = Pattern.compile("\\b\\d{3,}\\b")
    val matcher = pattern.matcher(text)
    val numbers = mutableListOf<String>()

    while (matcher.find()) {
        numbers.add(matcher.group())
    }

    return numbers
}


/**
 * کپی متن به کلیپ‌بورد
 */
private fun copyToClipboard(context: Context, text: String, label: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "✅ متن کپی شد", Toast.LENGTH_SHORT).show()
        }

        Log.d("MessageBubble", "📋 متن کپی شد: ${text.take(50)}...")
    } catch (e: Exception) {
        Log.e("MessageBubble", "❌ خطا در کپی کردن متن", e)
    }
}

/**
 * اشتراک‌گذاری پیام
 */
private fun shareMessage(context: Context, text: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "اشتراک‌گذاری پیام")
        }

        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری پیام"))
        Log.d("MessageBubble", "📤 پیام برای اشتراک‌گذاری ارسال شد")
    } catch (e: Exception) {
        Log.e("MessageBubble", "❌ خطا در اشتراک‌گذاری", e)
        Toast.makeText(context, "❌ خطا در اشتراک‌گذاری", Toast.LENGTH_SHORT).show()
    }
}

/**
 * نمایش اطلاعات پیام
 */
private fun showMessageInfo(context: Context, message: SmsEntity) {
    val infoText = """
        📱 فرستنده: ${message.address}
        🕒 زمان: ${JalaliDateUtil.getFullJalaliDate(message.date)}
        📏 طول متن: ${message.body.length} کاراکتر
        🆔 شناسه: ${message.id.take(10)}...
        📶 سیم‌کارت: SIM ${message.subId}
        👁️ خوانده شده: ${if (message.read) "بله" else "خیر"}
        🔗 چندبخشی: ${if (message.isMultipart) "بله (${message.partIndex}/${message.partCount})" else "خیر"}
    """.trimIndent()

    AlertDialog.Builder(context)
        .setTitle("📋 اطلاعات پیام")
        .setMessage(infoText)
        .setPositiveButton("باشه") { dialog, _ -> dialog.dismiss() }
        .setNeutralButton("کپی اطلاعات") { _, _ ->
            copyToClipboard(context, infoText, "اطلاعات پیام")
        }
        .show()

    Log.d("MessageBubble", "ℹ️ اطلاعات پیام نمایش داده شد")
}

// برای backward compatibility
// برای backward compatibility
@Composable
fun SimpleMessageBubble(
    message: SmsEntity,
    onLinkClick: (String) -> Unit = {},
    onNumberSelected: (String) -> Unit = {}
) {
    val isMe = message.type == 2
    val context = LocalContext.current

    // استفاده از AdvancedMessageBubble با هندلر سفارشی
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // کپی پیاده‌سازی ساده‌تر یا استفاده از AdvancedMessageBubble
        // ...
    }
}

// Alias برای سازگاری اگر قبلاً از MessageBubble استفاده شده
@Composable
fun MessageBubbleCompat(message: SmsEntity) = SimpleMessageBubble(message)