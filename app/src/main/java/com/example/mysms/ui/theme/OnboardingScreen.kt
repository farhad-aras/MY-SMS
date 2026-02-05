package com.example.mysms.ui.theme

import androidx.compose.runtime.mutableStateMapOf
import android.provider.Telephony
import androidx.compose.material.icons.filled.*

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Email


import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.filled.Notifications

import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

import com.example.mysms.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/**
 * بررسی آیا برنامه به عنوان برنامه پیش‌فرض پیامک تنظیم شده است
 */
fun isDefaultSmsApp(context: Context): Boolean {
    return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
}


/**
 * صفحه اصلی Onboarding Wizard برای درخواست مجوزها
 */


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: HomeViewModel? = null
) {
    val context = LocalContext.current

    // حالت‌های onboarding
    var currentStep by remember { mutableIntStateOf(0) }
    var showWelcome by remember { mutableStateOf(true) }

    // وضعیت real-time تمام مجوزها
    val permissionsState = remember { mutableStateMapOf<String, Boolean>() }

    // بروزرسانی وضعیت مجوزها
    LaunchedEffect(currentStep) {
        // لیست تمام مجوزهای مهم
        val allPermissions = listOfNotNull(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
        )

        // چک real-time وضعیت هر مجوز
        allPermissions.forEach { permission ->
            val hasPermission = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
            permissionsState[permission] = hasPermission
        }
    }

    // SharedPreferences برای پیگیری وضعیت onboarding
    val prefs = remember {
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    }

    // بررسی اگر onboarding قبلاً کامل شده
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("onboarding_completed", false)) {
            // اگر قبلاً کامل شده، مستقیم برو به صفحه اصلی
            onComplete()
        } else {
            // نمایش welcome screen برای 2 ثانیه
            delay(2000)
            showWelcome = false
        }
    }

    // صفحه Welcome
    if (showWelcome) {
        WelcomeScreen()
        return
    }

    // مراحل onboarding
    val steps = listOf(
        OnboardingStep(
            title = "خوش آمدید به پیام‌رسان",
            description = "برای استفاده کامل از امکانات برنامه، نیاز به چند مجوز داریم",
            icon = Icons.Default.Email,
            iconColor = Color(0xFF2196F3),
            background = Color(0xFFE3F2FD)
        ),
        OnboardingStep(
            title = "دسترسی به پیامک‌ها",
            description = "برای نمایش و مدیریت پیام‌های شما نیاز به دسترسی به پیامک‌ها داریم",
            icon = Icons.Filled.Email,
            iconColor = Color(0xFF4CAF50),
            background = Color(0xFFE8F5E9),
            permission = Manifest.permission.READ_SMS,
            isRequired = true
        ),
        OnboardingStep(
            title = "دریافت پیامک جدید",
            description = "برای دریافت پیام‌های جدید حتی وقتی برنامه بسته است",
            icon = Icons.Default.Notifications,
            iconColor = Color(0xFFFF9800),
            background = Color(0xFFFFF3E0),
            permission = Manifest.permission.RECEIVE_SMS,
            isRequired = true
        ),
        OnboardingStep(
            title = "دفترچه تلفن",
            description = "برای نمایش نام مخاطبین به جای شماره",
            icon = Icons.Default.Person,
            iconColor = Color(0xFF9C27B0),
            background = Color(0xFFF3E5F5),
            permission = Manifest.permission.READ_CONTACTS,
            isRequired = true
        ),
        OnboardingStep(
            title = "تشخیص سیم‌کارت",
            description = "برای تشخیص SIM1 و SIM2 و مدیریت جداگانه پیام‌ها",
            icon = Icons.Default.Phone,
            iconColor = Color(0xFFF44336),
            background = Color(0xFFFFEBEE),
            permission = Manifest.permission.READ_PHONE_STATE,
            isRequired = true
        ),
        OnboardingStep(
            title = "اعلان‌ها",
            description = "برای نمایش نوتیفیکیشن پیام‌های جدید",
            icon = Icons.Default.Notifications,
            iconColor = Color(0xFF00BCD4),
            background = Color(0xFFE0F7FA),
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null,
            isRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ),
        // ============ مرحله جدید: برنامه پیش‌فرض پیامک ============
        OnboardingStep(
            title = "برنامه پیش‌فرض پیامک",
            description = "برای جلوگیری از دریافت دو نوتیفیکیشن\nلطفاً این برنامه را به عنوان برنامه پیش‌فرض پیامک تنظیم کنید",
            icon = Icons.Default.Star,
            iconColor = Color(0xFFFFC107),
            background = Color(0xFFFFF8E1),
            permission = null,
            isRequired = false
        ),
        OnboardingStep(
            title = "آماده استفاده!",
            description = "تمام مراحل با موفقیت تکمیل شد\nاکنون می‌توانید از برنامه استفاده کنید",
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF8BC34A),
            background = Color(0xFFF1F8E9)
        )
    )

    // وضعیت مجوز مرحله فعلی
    val currentStepData = steps[currentStep]
    val currentHasPermission = currentStepData.permission?.let { permission ->
        permissionsState[permission] ?: false
    } ?: true

    // آیا می‌تواند به مرحله بعد برود؟
    val canGoNext = when {
        currentStepData.isRequired && currentStepData.permission != null ->
            currentHasPermission
        else -> true
    }

    Scaffold(
        topBar = {
            if (currentStep > 0) {
                TopAppBar(
                    title = {
                        Text(
                            "مرحله ${currentStep + 1} از ${steps.size}",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (currentStep > 0) currentStep--
                            },
                            enabled = currentStep > 0
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "مرحله قبل"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(currentStepData.background)
        ) {
            // انیمیشن بین مراحل
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(
                    initialOffsetX = { if (currentStep > 0) it else -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { if (currentStep > 0) -it else it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut()
            ) {
                OnboardingStepContent(
                    step = currentStepData,
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    hasPermission = currentHasPermission,
                    canGoNext = canGoNext,
                    onNext = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            // تکمیل onboarding
                            prefs.edit().putBoolean("onboarding_completed", true).apply()
                            onComplete()
                        }
                    },
                    onPrev = { if (currentStep > 0) currentStep-- },
                    onSkip = {
                        // رد کردن onboarding
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        onComplete()
                    }
                )
            }

            // نشانگر پیشرفت در پایین
            if (currentStep < steps.size - 1) {
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp) // افزایش padding
                )
            }
        }
    }
}

/**
 * صفحه Welcome اولیه
 */
@Composable
fun WelcomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // لوگو یا آیکون
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Email,  // یا Icons.Default.Sms
                    contentDescription = "پیام‌رسان",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "پیام‌رسان",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "مدیریت هوشمند پیامک‌ها",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * محتوای هر مرحله از onboarding
 */
@Composable
fun OnboardingStepContent(
    step: OnboardingStep,
    currentStep: Int,
    totalSteps: Int,
    hasPermission: Boolean,
    canGoNext: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    // state برای کنترل auto-advance بعد از دادن مجوز
    var shouldAutoAdvance by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // علامت‌گذاری برای auto-advance
            shouldAutoAdvance = true
        }
    }

    // LaunchedEffect برای auto-advance
    LaunchedEffect(shouldAutoAdvance) {
        if (shouldAutoAdvance) {
            delay(500) // تاخیر 0.5 ثانیه
            onNext()
            shouldAutoAdvance = false // ریست
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // آیکون
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(step.iconColor.copy(alpha = 0.1f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                step.icon,
                contentDescription = step.title,
                modifier = Modifier.size(60.dp),
                tint = step.iconColor
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // عنوان
        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // توضیحات
        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // وضعیت مجوز یا برنامه پیش‌فرض
        if (step.permission != null) {
            PermissionStatusCard(
                permission = step.permission,
                isRequired = step.isRequired,
                hasPermission = hasPermission,
                onRequestPermission = {
                    requestPermissionLauncher.launch(step.permission)
                },
                onOpenSettings = { openAppSettings(context) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else if (step.title == "برنامه پیش‌فرض پیامک") {
            DefaultSmsAppStatusCard(
                context = context, // اضافه کردن context
                onOpenSettings = {
                    try {
                        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        openAppSettings(context)
                    }
                },
                onSkip = {
                    onNext()
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // دکمه‌های ناوبری
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // دکمه قبلی
            if (currentStep > 0) {
                Button(
                    onClick = onPrev,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = "قبلی",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("قبلی")
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            // دکمه بعدی/شروع
            Button(
                onClick = onNext,
                enabled = canGoNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentStep == totalSteps - 1)
                        Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(2f)
            ) {
                Text(
                    text = if (currentStep == totalSteps - 1) "شروع استفاده" else "ادامه",
                    fontWeight = FontWeight.Bold
                )
                if (currentStep < totalSteps - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "ادامه",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // دکمه رد کردن (فقط برای مراحل غیرضروری)
        if (!step.isRequired && step.permission != null && !hasPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSkip) {
                Text("رد کردن این مجوز")
            }
        }
    }
}

/**
 * کارت وضعیت مجوز
 */
@Composable
fun PermissionStatusCard(
    permission: String,
    isRequired: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    // وضعیت real-time مجوز
    val currentPermissionState by remember(permission) {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ترکیب وضعیت ورودی و وضعیت real-time
    val actualHasPermission = hasPermission || currentPermissionState

    val permissionName = getPermissionDisplayName(permission)
    val statusText = if (actualHasPermission) "تأیید شده" else "تأیید نشده"
    val statusColor = if (actualHasPermission) Color(0xFF4CAF50) else Color(0xFFF44336)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (actualHasPermission)
                Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = permissionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isRequired) "ضروری" else "اختیاری",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!actualHasPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("درخواست مجوز")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تنظیمات")
                    }
                }
            } else {
                // نمایش پیام تأیید شده
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "تأیید شده",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مجوز تأیید شده است",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


/**
 * کارت وضعیت برنامه پیش‌فرض پیامک
 */
@Composable
fun DefaultSmsAppStatusCard(
    context: Context, // اضافه کردن context به پارامترها
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit = {}
) {
    val isDefaultSmsApp by remember {
        derivedStateOf {
            context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
        }
    }

    val statusText = if (isDefaultSmsApp) "تنظیم شده" else "تنظیم نشده"
    val statusColor = if (isDefaultSmsApp) Color(0xFF4CAF50) else Color(0xFFFF9800)
    val description = if (isDefaultSmsApp)
        "برنامه شما به عنوان برنامه پیش‌فرض پیامک تنظیم شده است. ✅"
    else
        "برای جلوگیری از دریافت دو نوتیفیکیشن، لطفاً این برنامه را به عنوان برنامه پیش‌فرض تنظیم کنید."

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefaultSmsApp)
                Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "برنامه پیش‌فرض پیامک",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "توصیه شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!isDefaultSmsApp) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "تنظیمات",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تنظیم برنامه پیش‌فرض")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بعداً انجام می‌دهم")
                }
            }
        }
    }
}


/**
 * نشانگر مراحل
 */
@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentStep)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
            )
            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * داده‌های هر مرحله
 */
data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val background: Color,
    val permission: String? = null,
    val isRequired: Boolean = false
)

/**
 * مدیریت وضعیت مجوزها
 */
/**
 * مدیریت وضعیت مجوزها - نسخه بروز شده
 */
@Composable
fun rememberPermissionStates(context: Context): PermissionStates {
    val permissionStates = remember { mutableStateMapOf<String, Boolean>() }

    // بررسی real-time وضعیت مجوزها
    LaunchedEffect(Unit) {
        while (true) {
            // بررسی تمام مجوزهای مهم
            val permissions = listOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS else null
            ).filterNotNull()

            permissions.forEach { permission ->
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, permission
                ) == PackageManager.PERMISSION_GRANTED

                if (permissionStates[permission] != hasPermission) {
                    permissionStates[permission] = hasPermission
                }
            }

            delay(500) // بررسی هر نیم ثانیه
        }
    }

    // Launcher برای درخواست مجوزها
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // بلافاصله وضعیت را آپدیت کن
        // این بخش در LaunchedEffect بالا هم آپدیت می‌شود
    }

    // Launcher برای درخواست چندگانه مجوزها
    val requestMultiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, isGranted) ->
            permissionStates[permission] = isGranted
        }
    }

    return remember {
        object : PermissionStates {
            override fun getPermissionState(permission: String): Boolean {
                // همیشه از سیستم چک کن
                return ContextCompat.checkSelfPermission(
                    context, permission
                ) == PackageManager.PERMISSION_GRANTED
            }

            override fun requestPermission(permission: String) {
                requestPermissionLauncher.launch(permission)
            }

            override fun requestPermissions(permissions: List<String>) {
                requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
            }
        }
    }
}
interface PermissionStates {
    fun getPermissionState(permission: String): Boolean
    fun requestPermission(permission: String)
    fun requestPermissions(permissions: List<String>)
}

/**
 * نام نمایشی مجوزها
 */
fun getPermissionDisplayName(permission: String): String {
    return when (permission) {
        Manifest.permission.READ_SMS -> "خواندن پیامک‌ها"
        Manifest.permission.RECEIVE_SMS -> "دریافت پیامک جدید"
        Manifest.permission.SEND_SMS -> "ارسال پیامک"
        Manifest.permission.READ_CONTACTS -> "دفترچه تلفن"
        Manifest.permission.READ_PHONE_STATE -> "تشخیص سیم‌کارت"
        Manifest.permission.POST_NOTIFICATIONS -> "اعلان‌ها"
        else -> permission
    }
}

/**
 * باز کردن صفحه تنظیمات برنامه
 */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

/**
 * بررسی کلیه مجوزهای ضروری
 */
fun checkAllRequiredPermissions(context: Context): Boolean {
    val requiredPermissions = listOfNotNull(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else null
    )

    return requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * بررسی آیا onboarding لازم است نمایش داده شود
 */
fun shouldShowOnboarding(context: Context): Boolean {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    val isCompleted = prefs.getBoolean("onboarding_completed", false)

    // اگر قبلاً کامل شده، بررسی کن که آیا همه مجوزها داده شده‌اند
    return if (isCompleted) {
        !checkAllRequiredPermissions(context)
    } else {
        true
    }
}

/**
 * ریست کردن وضعیت onboarding
 */
fun resetOnboarding(context: Context) {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_completed", false).apply()
}