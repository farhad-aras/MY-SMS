package com.example.mysms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    address: String,
    messages: List<SmsEntity>,
    onBack: () -> Unit,
    onSendClick: (String) -> Unit,
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    isSending: Boolean = false,
    sendingError: String? = null
) {
    val context = LocalContext.current
    val displayName by getContactNameState(context, address) // استفاده از تابع import شده

    // وضعیت نمایش خطا
    var showError by remember { mutableStateOf(sendingError != null) }

    LaunchedEffect(sendingError) {
        showError = sendingError != null
        if (sendingError != null) {
            delay(3000)
            showError = false
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val sortedMessages = remember(messages) { messages.sortedBy { it.date } }

    Column(modifier = Modifier.fillMaxSize()) {
        // هدر صفحه چت
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    // تغییر اینجا: address به displayName
                    Text(displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isSending) {
                        Text("در حال ارسال...", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }

        // نمایش خطا (اگر وجود دارد)
        if (showError && sendingError != null) {
            Surface(
                color = Color(0xFFFFEBEE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = sendingError,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // لیست پیام‌ها
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sortedMessages) { sms ->
                val isTempMessage = sms.id.startsWith("temp_") ||
                        sms.id.startsWith("error_") ||
                        sms.id.startsWith("sent_")
                val isError = sms.id.startsWith("error_") || sms.body.contains("ارسال ناموفق")
                val isSendingTemp = sms.id.startsWith("temp_")

                ChatBubble(
                    message = sms.body,
                    isReceived = sms.type == 1,
                    isTemp = isTempMessage,
                    isError = isError,
                    isSending = isSendingTemp
                )
            }
        }

        // ورودی پیام
        MessageInput(
            text = draftMessage,
            onTextChange = onDraftChange,
            onSend = {
                if (draftMessage.isNotBlank() && !isSending) {
                    onSendClick(draftMessage)
                    onDraftChange("")
                }
            },
            isSending = isSending
        )
    }
}

@Composable
fun ChatBubble(
    message: String,
    isReceived: Boolean,
    isTemp: Boolean = false,
    isError: Boolean = false,
    isSending: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isReceived) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = when {
                isError -> Color(0xFFFFEBEE)
                isTemp && !isError -> Color(0xFFE3F2FD)
                isReceived -> Color.White
                else -> Color(0xFFEFFDDE)
            },
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isReceived) 0.dp else 12.dp,
                bottomEnd = if (isReceived) 12.dp else 0.dp
            ),
            shadowElevation = if (isTemp) 0.dp else 1.dp,
            border = if (isError) CardDefaults.outlinedCardBorder() else null
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = if (isError) Color.Red else Color.Black,
                    fontStyle = if (isTemp) FontStyle.Italic else FontStyle.Normal,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // آیکون وضعیت با Unicode
                if (isTemp) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = Color(0xFF0088CC)
                        )
                    } else if (isError) {
                        Text(
                            text = "❌",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "✅",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "پیام خود را بنویسید...",
                    color = if (isSending) Color.Gray else Color(0xFF666666)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            maxLines = 3,
            enabled = !isSending
        )

        IconButton(
            onClick = {
                if (text.isNotBlank() && !isSending) {
                    onSend()
                }
            },
            enabled = text.isNotBlank() && !isSending
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF0088CC)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ارسال",
                    tint = if (text.isNotBlank()) Color(0xFF0088CC) else Color(0xFFCCCCCC)
                )
            }
        }
    }
}