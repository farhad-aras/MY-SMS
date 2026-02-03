package com.example.mysms.ui.theme

import com.example.mysms.ui.theme.getContactNameState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mysms.viewmodel.HomeViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import android.content.Context
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InternalChatScreen(
    messages: List<SmsEntity>,
    context: Context,
    onSendClick: (String) -> Unit,
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    address: String,
    onBack: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val sortedMessages = remember(messages) { messages.sortedBy { it.date } }

    val groupedMessages = remember(sortedMessages) {
        sortedMessages.groupBy { JalaliDateUtil.getDateOnly(it.date) }
    }

    val sortedKeys = remember(groupedMessages) {
        groupedMessages.keys.sortedBy { dateStr ->
            try {
                val parts = dateStr.split("/").map { it.toInt() }
                if (parts.size == 3) {
                    val (year, month, day) = parts
                    year * 10000 + month * 100 + day
                } else 0
            } catch (e: Exception) {
                0
            }
        }
    }

    val listState = rememberLazyListState()

    // *** اضافه کردن این بلوک:***
    // مشاهده وضعیت expand/collapse از ViewModel
    val expandedDates by vm.expandedDates.collectAsState()

    // تنظیم وضعیت پیش‌فرض هنگام اولین بار
    LaunchedEffect(sortedKeys) {
        if (sortedKeys.isNotEmpty()) {
            // فقط اگر برای این تاریخ‌ها وضعیتی تنظیم نشده، پیش‌فرض را اعمال کن
            val hasAnyState = sortedKeys.any { vm.isDateExpanded(it) }
            if (!hasAnyState) {
                vm.setDefaultExpansionState(sortedKeys)
            }

            // اسکرول به آخرین پیام
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
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
                // *** اضافه کردن دکمه Back:***
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // استفاده از ArrowBack
                        contentDescription = "بازگشت",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val contactName by getContactNameState(context, address)
                    Text(
                        text = contactName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = address,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
            contentPadding = PaddingValues(bottom = 8.dp, top = 8.dp)
        ) {
            sortedKeys.forEach { dateKey ->
                val isExpanded = expandedDates[dateKey] ?: false
                val messagesOfDay = groupedMessages[dateKey] ?: emptyList()

                item(key = "date_$dateKey") {
                    // UI جدید برای تاریخ مثل تلگرام
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x80000000), // سیاه با transparency 50%
                            modifier = Modifier
                                .clickable {
                                    vm.toggleDateExpansion(dateKey, !isExpanded)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateKey,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Badge با transparency
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${messagesOfDay.size}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isExpanded) {
                    items(messagesOfDay.sortedBy { it.date }) { message ->
                        MessageBubble(message)
                    }
                }
            }
        }

        // ورود پیام
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("پیام خود را بنویسید...")
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = {
                        if (draftMessage.isNotBlank()) {
                            onSendClick(draftMessage)
                            onDraftChange("")
                        }
                    },
                    enabled = draftMessage.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "ارسال",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: SmsEntity) {
    val isMe = message.type == 2
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = JalaliDateUtil.getTimeOnly(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}