package com.example.mysms.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning

/**
 * صفحه Loading ساده برای مواقعی که نیاز به نمایش بارگذاری داریم
 * (مثلاً هنگام سینک پیام‌ها)
 */
@Composable
fun LoadingScreen(
    progress: Int,
    isSyncing: Boolean,
    message: String = "در حال بارگذاری..."
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // لوگو یا آیکون
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // عنوان
            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // توضیح
            Text(
                text = "لطفا کمی صبر کنید",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress bar (اگر در حال سینک است)
            if (isSyncing && progress > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = progress / 100f, // حذف lambda و استفاده مستقیم از Float
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // نکات یا راهنمایی‌ها
            if (!isSyncing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "نکته:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• برای استفاده کامل از برنامه، تمام مجوزها را تأیید کنید\n" +
                                    "• پیام‌های قدیمی شما در برنامه ذخیره می‌شوند\n" +
                                    "• می‌توانید بین سیم‌کارت‌ها سوئیچ کنید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * کامپوننت Loading کوچک برای استفاده در دکمه‌ها یا بخش‌های خاص
 */
@Composable
fun SmallLoadingIndicator(
    isLoading: Boolean,
    content: @Composable () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        content()

        if (isLoading) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.matchParentSize()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * کامپوننت خطای ساده
 */
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "خطا",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "خطا",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("تلاش مجدد")
            }
        }
    }
}