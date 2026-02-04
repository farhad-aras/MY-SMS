package com.example.mysms.ui.theme

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mysms.viewmodel.HomeViewModel
import android.util.Log
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel,
    currentTab: Int
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // State برای نام‌های جدید
    var newSim1Name by remember { mutableStateOf("") }
    var newSim2Name by remember { mutableStateOf("") }

    // State برای نمایش پیام موفقیت
    var showSuccessMessage by remember { mutableStateOf(false) }

    // بارگذاری نام‌های فعلی
    LaunchedEffect(Unit) {
        newSim1Name = viewModel.getCurrentTabName(0)
        newSim2Name = viewModel.getCurrentTabName(1)
        Log.d("SettingsScreen", "📝 Loaded current names: SIM1='$newSim1Name', SIM2='$newSim2Name'")
    }

    // بستن پیام موفقیت بعد از 2 ثانیه
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            showSuccessMessage = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "تغییر نام تب‌های سیم‌کارت",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // نمایش پیام موفقیت
            if (showSuccessMessage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "تغییرات با موفقیت ذخیره شد",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // بخش توضیحات
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "توجه:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• می‌توانید نام نمایشی تب‌های سیم‌کارت را تغییر دهید\n" +
                                "• اگر فیلد را خالی بگذارید، نام پیش‌فرض استفاده می‌شود\n" +
                                "• تغییرات بلافاصله در تب‌های اصلی اعمال می‌شوند",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // فیلد نام سیم‌کارت ۱
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "نام تب سیم‌کارت اول",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // نمایش نام فعلی
                    val currentSim1Name by viewModel.sim1TabName.collectAsState()
                    Text(
                        "نام فعلی: $currentSim1Name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // فیلد ورودی جدید
                    OutlinedTextField(
                        value = newSim1Name,
                        onValueChange = { newSim1Name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام جدید") },
                        placeholder = { Text("مثال: سیم اصلی") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = newSim1Name.length > 20
                    )

                    if (newSim1Name.length > 20) {
                        Text(
                            "نام نباید بیشتر از ۲۰ کاراکتر باشد",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // فیلد نام سیم‌کارت ۲
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "نام تب سیم‌کارت دوم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // نمایش نام فعلی
                    val currentSim2Name by viewModel.sim2TabName.collectAsState()
                    Text(
                        "نام فعلی: $currentSim2Name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // فیلد ورودی جدید
                    OutlinedTextField(
                        value = newSim2Name,
                        onValueChange = { newSim2Name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام جدید") },
                        placeholder = { Text("مثال: سیم دوم") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        isError = newSim2Name.length > 20
                    )

                    if (newSim2Name.length > 20) {
                        Text(
                            "نام نباید بیشتر از ۲۰ کاراکتر باشد",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // دکمه‌های Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // دکمه بازگشت
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("بازگشت")
                }

                // دکمه ذخیره
                Button(
                    onClick = {
                        // ذخیره نام‌ها
                        viewModel.updateSimTabName(0, newSim1Name.trim())
                        viewModel.updateSimTabName(1, newSim2Name.trim())

                        // نمایش پیام موفقیت
                        showSuccessMessage = true

                        // پاک کردن focus
                        focusManager.clearFocus()

                        Log.d("SettingsScreen", "💾 Saved names: SIM1='${newSim1Name.trim()}', SIM2='${newSim2Name.trim()}'")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = newSim1Name.length <= 20 && newSim2Name.length <= 20
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ذخیره تغییرات")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // دکمه بازنشانی به پیش‌فرض
            TextButton(
                onClick = {
                    newSim1Name = "سیم ۱"
                    newSim2Name = "سیم ۲"
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بازنشانی به پیش‌فرض")
            }
        }
    }
}