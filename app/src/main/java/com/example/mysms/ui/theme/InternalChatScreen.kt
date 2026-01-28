package com.example.mysms.ui.theme

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity
import android.content.Context

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InternalChatScreen(
    messages: List<SmsEntity>,
    context: Context,
    onSendClick: (String) -> Unit,
    draftMessage: String,
    onDraftChange: (String) -> Unit
) {
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

    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(sortedKeys) {
        if (sortedKeys.isNotEmpty()) {
            val todayJalali = JalaliDateUtil.getDateOnly(System.currentTimeMillis())
            val closestDate = sortedKeys.maxByOrNull { dateStr ->
                try {
                    val parts1 = dateStr.split("/").map { it.toInt() }
                    val parts2 = todayJalali.split("/").map { it.toInt() }
                    if (parts1.size == 3 && parts2.size == 3) {
                        val diff = (parts2[0] - parts1[0]) * 365 +
                                (parts2[1] - parts1[1]) * 30 +
                                (parts2[2] - parts1[2])
                        -kotlin.math.abs(diff)
                    } else Int.MIN_VALUE
                } catch (e: Exception) {
                    Int.MIN_VALUE
                }
            }
            if (closestDate != null) {
                expandedState[closestDate] = true
            }
        }

        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
            contentPadding = PaddingValues(bottom = 8.dp, top = 8.dp)
        ) {
            sortedKeys.forEach { dateKey ->
                val isExpanded = expandedState[dateKey] ?: false
                val messagesOfDay = groupedMessages[dateKey] ?: emptyList()

                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .clickable { expandedState[dateKey] = !isExpanded }
                                .padding(horizontal = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = dateKey,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Badge(
                                    containerColor = Color.White.copy(alpha = 0.3f),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = "${messagesOfDay.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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