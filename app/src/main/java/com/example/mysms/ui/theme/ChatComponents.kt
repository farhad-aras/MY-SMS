package com.example.mysms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log


// توابع کمکی
@Composable
fun getContactNameState(context: Context, phoneNumber: String): androidx.compose.runtime.State<String> {
    return produceState(initialValue = phoneNumber, key1 = phoneNumber) {
        value = withContext(Dispatchers.IO) {
            getContactName(context, phoneNumber)
        }
    }
}

fun getContactName(context: Context, phoneNumber: String): String {
    return try {
        Log.d("ContactLookup", "🔍 جستجوی نام برای شماره: $phoneNumber")

        // پاکسازی شماره (حذف فاصله، خط تیره و ...)
        val cleanNumber = phoneNumber.replace(Regex("[\\s\\-()+]"), "")

        // لیست فرمت‌های ممکن
        val possibleFormats = mutableListOf<String>()

        // فرمت اصلی
        possibleFormats.add(cleanNumber)

        // بررسی فرمت‌های مختلف
        if (cleanNumber.startsWith("+98") && cleanNumber.length > 3) {
            // +98912... -> 0912...
            possibleFormats.add("0" + cleanNumber.substring(3))
        }

        if (cleanNumber.startsWith("0") && cleanNumber.length >= 10) {
            // 0912... -> +98912...
            possibleFormats.add("+98" + cleanNumber.substring(1))
        }

        // اگر شماره 10 رقمی است و با 9 شروع می‌شود
        if (cleanNumber.length == 10 && cleanNumber.startsWith("9")) {
            possibleFormats.add("0$cleanNumber")
            possibleFormats.add("+98$cleanNumber")
        }

        // حذف تکرارها
        val uniqueFormats = possibleFormats.distinct()
        Log.d("ContactLookup", "🔤 فرمت‌های آزمایشی: $uniqueFormats")

        var foundName = phoneNumber

        for (format in uniqueFormats) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(format)
                )
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER
                )

                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)

                        if (nameIndex >= 0) {
                            foundName = cursor.getString(nameIndex) ?: phoneNumber
                            val foundNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else "Unknown"

                            Log.d("ContactLookup", "✅ نام پیدا شد: $foundName برای شماره: $foundNumber")
                            return foundName
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactLookup", "❌ خطا در فرمت $format: ${e.message}")
            }
        }

        Log.d("ContactLookup", "⚠️ نامی پیدا نشد، نمایش شماره: $phoneNumber")
        phoneNumber

    } catch (e: Exception) {
        Log.e("ContactLookup", "🔥 خطای کلی: ${e.message}")
        phoneNumber
    }
}

// کامپوننت‌های رابط کاربری
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToPinWithConfirm(
    data: ConversationData,
    context: Context,
    isPinned: Boolean,
    onPinAction: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onPinAction()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFF4CAF50)
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFE57373)
                    else -> Color.Transparent
                }, label = "BgColor"
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 1.2f else 0.8f,
                label = "IconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Confirm",
                            modifier = Modifier.scale(scale),
                            tint = Color.White
                        )
                        Text(
                            text = if (isPinned) "لغو پین" else "پین کردن",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.scale(scale),
                            tint = Color.White
                        )
                        Text(
                            text = "لغو",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) {
        ConversationItem(
            sms = data.sms,
            isDraft = data.isDraft,
            unreadCount = data.unreadCount,
            isPinned = isPinned,
            context = context,
            onClick = onClick
        )
    }
}

@Composable
fun ConversationItem(sms: SmsEntity, isDraft: Boolean, unreadCount: Int, isPinned: Boolean, context: Context, onClick: () -> Unit) {
    val displayName by getContactNameState(context, sms.address)
    val firstChar = displayName.take(1).uppercase()

    val cardColor = if (isPinned) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val hasUnreadBorder = unreadCount > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPinned) 6.dp else 2.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            if (hasUnreadBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(bottom = 1.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = firstChar,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (isPinned) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(Color(0xFFFFA000), CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "پین شده",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isDraft) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "پیش‌نویس",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = JalaliDateUtil.getDateOnly(sms.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = JalaliDateUtil.getTimeOnly(sms.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sms.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isDraft) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = if (isDraft) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (unreadCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "SIM ${sms.subId}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}