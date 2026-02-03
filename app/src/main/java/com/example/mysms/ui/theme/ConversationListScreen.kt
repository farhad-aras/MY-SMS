package com.example.mysms.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun ConversationListScreen(
    sortedConversations: List<ConversationData>,
    context: android.content.Context,
    pinnedList: MutableList<String>,
    pinnedPrefs: android.content.SharedPreferences,
    listState: LazyListState,
    onContactClick: (String) -> Unit,
    scrollToPosition: Int = 0,
    refreshKey: Int = 0
) {
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            // وقتی refreshKey تغییر کرد و بزرگتر از 0 شد
            delay(50)
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(scrollToPosition) {
        if (scrollToPosition > 0 && refreshKey == 0) {
            // فقط در اولین بار و اگر موقعیت ذخیره شده داشته باشیم
            delay(50)
            listState.scrollToItem(scrollToPosition)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = sortedConversations,
            key = { it.sms.address }
        ) { data ->
            SwipeToPinWithConfirm(
                data = data,
                context = context,
                isPinned = data.isPinned,
                onPinAction = {
                    if (pinnedList.contains(data.sms.address)) {
                        pinnedList.remove(data.sms.address)
                        pinnedPrefs.edit().remove(data.sms.address).apply()
                    } else {
                        pinnedList.add(data.sms.address)
                        pinnedPrefs.edit().putBoolean(data.sms.address, true).apply()
                    }
                },
                onClick = { onContactClick(data.sms.address) }
            )
        }
    }
}