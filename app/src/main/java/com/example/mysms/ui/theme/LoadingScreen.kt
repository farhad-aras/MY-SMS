package com.example.mysms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mysms.data.SmsEntity

@Composable
fun LoadingScreen(
    progress: Int,
    isSyncing: Boolean,
    smsList: List<SmsEntity>,
    onStartSync: () -> Unit,
    onContactClick: (String) -> Unit,
    selectedTabIndex: Int,
    onTabChange: (Int) -> Unit
    ,
    sim1Id: Int?,
    sim2Id: Int?
) {
    val tabs = listOf("سیم‌کارت ۱", "سیم‌کارت ۲")

    // فیلتر کردن دقیق بر اساس شناسه‌های واقعی که از ViewModel آمده
    val filteredList = remember(selectedTabIndex, smsList, sim1Id, sim2Id) {
        val targetSubId = if (selectedTabIndex == 0) sim1Id else sim2Id
        if (targetSubId != null) {
            smsList.filter { it.subId == targetSubId }
        } else {
            emptyList()
        }
    }

    val groupedSms = remember(filteredList) {
        filteredList.groupBy { it.address }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSyncing) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("در حال دریافت: $progress%", modifier = Modifier.padding(8.dp))
        } else {
            Button(onClick = onStartSync, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("بروزرسانی پیامک‌ها")
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabChange(index) },
                    text = { Text(title) }
                )
            }
        }

        if (groupedSms.isEmpty() && !isSyncing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("پیامکی یافت نشد")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(groupedSms.keys.toList()) { address ->
                    val messages = groupedSms[address] ?: emptyList()
                    val lastMsg = messages.firstOrNull()

                    // وضعیت خوانده شدن را از پیام می‌گیریم
                    val isMsgRead = lastMsg?.read ?: true

                    ConversationItem(
                        address = address,
                        lastMessage = lastMsg?.body ?: "",
                        count = messages.size,
                        subId = lastMsg?.subId ?: -1,
                        isRead = isMsgRead,
                        onItemClick = onContactClick
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    address: String,
    lastMessage: String,
    count: Int,
    subId: Int,
    isRead: Boolean,
    onItemClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayName = remember(address) {
        var name = address
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(address)
        )
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0)
            }
        } catch (e: Exception) { }
        name
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onItemClick(address) },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFFBBDEFB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayName, fontWeight = FontWeight.Bold)

                Text(
                    text = lastMessage,
                    maxLines = 1,
                    color = if (isRead) Color.Gray else Color.Black,
                    fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (!isRead) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape)
                            .padding(bottom = 4.dp)
                    )
                } else if (count > 1) {
                    Surface(color = Color.LightGray, shape = CircleShape) {
                        Text(
                            text = "$count",
                            modifier = Modifier.padding(horizontal = 6.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "SIM $subId", fontSize = 10.sp, color = Color.LightGray)
            }
        }
    }
}