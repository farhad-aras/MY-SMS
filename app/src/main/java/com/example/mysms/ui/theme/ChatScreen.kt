package com.example.mysms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    messages: List<SmsEntity>,
    onSendClick: (String) -> Unit,
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    address: String,
    onBack: () -> Unit,
    context: android.content.Context
) {
    var text by remember { mutableStateOf(draftMessage) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // دریافت نام مخاطب - استفاده از تابع موجود در ChatComponents.kt
    val contactName = remember(address) {
        getContactName(context, address)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // هدر چت
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // دکمه بازگشت
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "بازگشت",
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )

                Spacer(modifier = Modifier.width(12.dp))

                // آواتار مخاطب
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = contactName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // نام و شماره مخاطب
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (contactName == address) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // لیست پیام‌ها
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(
                    message = message,
                    isOwnMessage = message.type == 2, // 2 = ارسالی
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // فیلد ورود متن
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // فیلد متن
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onDraftChange(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    "پیام خود را بنویسید...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(8.dp))

                // دکمه ارسال
                Surface(
                    color = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (text.isNotBlank()) {
                                onSendClick(text)
                                text = ""
                                onDraftChange("")

                                // اسکرول به پایین
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "ارسال",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: SmsEntity,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isOwnMessage) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = if (isOwnMessage) {
                RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            } else {
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
            },
            modifier = Modifier
                .clip(
                    if (isOwnMessage) {
                        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    } else {
                        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                    }
                )
                .background(
                    if (isOwnMessage) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // متن پیام
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = if (isOwnMessage) TextAlign.End else TextAlign.Start
                )

                Spacer(modifier = Modifier.height(4.dp))

                // زمان پیام
                Text(
                    text = JalaliDateUtil.getTimeOnly(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (isOwnMessage) TextAlign.End else TextAlign.Start
                )
            }
        }

        // وضعیت ارسال (برای پیام‌های ارسالی)
        if (isOwnMessage) {
            Text(
                text = if (message.read) "✓ خوانده شده" else "ارسال شده",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}
// تابع getContactName حذف شده - از تابع موجود در ChatComponents.kt استفاده می‌شود