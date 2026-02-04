    package com.example.mysms.ui.theme



    import com.example.mysms.ui.theme.OnboardingScreen
    import com.example.mysms.ui.theme.shouldShowOnboarding
    import com.example.mysms.ui.theme.checkAllRequiredPermissions
    import kotlinx.coroutines.flow.collect
    import androidx.compose.material.icons.filled.CheckCircle
    import androidx.compose.material.icons.outlined.CheckCircle
    import androidx.activity.compose.BackHandler
    import com.example.mysms.ui.theme.SettingsScreen
    import androidx.compose.material.icons.filled.MoreVert
    import androidx.compose.material.icons.filled.Edit
    import androidx.compose.material.icons.filled.Settings
    import androidx.compose.material3.Divider
    import androidx.compose.material3.DropdownMenu
    import androidx.compose.material3.DropdownMenuItem
    import com.example.mysms.ui.theme.ForegroundSmsService
    import kotlinx.coroutines.launch
    import androidx.compose.runtime.rememberCoroutineScope
    import android.Manifest
    import android.content.Context
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.os.Build
    import android.os.Bundle
    import android.util.Log
    import android.widget.Toast
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.material.icons.filled.Edit
    import androidx.compose.material.icons.filled.MoreVert
    import androidx.compose.material.icons.filled.Refresh
    import androidx.compose.material.icons.filled.Settings
    import androidx.compose.material.icons.filled.Star
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.core.content.ContextCompat
    import androidx.lifecycle.ViewModelProvider
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.example.mysms.data.SmsEntity
    import com.example.mysms.viewmodel.HomeViewModel
    import kotlinx.coroutines.delay
    
    class MainActivity : ComponentActivity() {
    
        private var backPressTime: Long = 0
    
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Log.d("MainActivity", "🟢 Activity created")
    
            // ============  بررسی اپ پیش‌فرض ============
    
    
            // ۱. درخواست نقش اپ پیش‌فرض SMS
            DefaultSmsDisabler.disableDefaultSmsNotifications(this)
    
            // ۲. مخفی کردن نوتیفیکیشن‌های پیش‌فرض
            DefaultSmsDisabler.hideDefaultNotifications(this)
    
            // بررسی Intent برای بازشدن از نوتیفیکیشن
            handleNotificationIntent(intent)
    
            setContent {
                MaterialTheme {
                    MySMSApp()
                }
            }
        }
    
        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            Log.d("MainActivity", "🔄 New Intent received")
    
            // بررسی Intent جدید (مثلاً کلیک روی نوتیفیکیشن)
            handleNotificationIntent(intent)
        }
    
        private fun handleNotificationIntent(intent: Intent?) {
            if (intent == null) return
    
            Log.d("MainActivity", "🔍 Checking intent extras: ${intent.extras?.keySet()}")
    
            // بررسی آیا از نوتیفیکیشن باز شده است؟
            val openChat = intent.getBooleanExtra("open_chat", false)
            val contactAddress = intent.getStringExtra("contact_address")
            val notificationClicked = intent.getBooleanExtra("notification_clicked", false)
    
            if ((openChat || notificationClicked) && !contactAddress.isNullOrEmpty()) {
                Log.d("MainActivity", "🎯 Opening chat from notification for: $contactAddress")
    
                // ذخیره اطلاعات برای استفاده در Composable
                val prefs = getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("should_open_chat", true)
                    putString("chat_address", contactAddress)
                    putString("chat_name", intent.getStringExtra("contact_name"))
                    apply()
                }
    
                // نمایش Toast
                Toast.makeText(
                    this,
                    "در حال بازکردن چت با $contactAddress",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    
        private fun startForegroundServiceIfNeeded() {
            try {
                Log.d("MainActivity", "🚀 Starting services...")
    
                // 1. شروع JobScheduler (برای اندروید 5+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    SmsJobService.scheduleJob(this)
                }
    
                // 2. شروع Foreground Service (برای نمایش نوتیفیکیشن)
                val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
    
                if (hasNotificationPermission) {
                    ForegroundSmsService.startService(this)
                    Log.d("MainActivity", "✅ Services started")
                }
    
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error starting services: ${e.message}", e)
            }
        }
    
        private fun stopForegroundServiceIfNeeded() {
            try {
                Log.d("MainActivity", "🛑 Stopping foreground service...")
                ForegroundSmsService.stopService(this)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping foreground service: ${e.message}")
            }
        }
    
        // ==================== کنترل دکمه فیزیکی Back ====================
    
        override fun onBackPressed() {
            // اجازه دهید BackHandler در Composable کنترل کند
            // اگر BackHandler نبود، super فراخوانی می‌شود
            super.onBackPressed()
        }
    
        // ==================== پایان کنترل Back ====================
    
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MySMSApp() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
    
    
    
        val application = context.applicationContext as android.app.Application
        val vm: HomeViewModel = viewModel(
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
    
        // مدیریت اولیه
        val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
        var isFirstLoadDone by remember { mutableStateOf(appPrefs.getBoolean("initial_load_done", false)) }
    
        // مدیریت پین شده‌ها
        val pinnedPrefs = remember { context.getSharedPreferences("pinned_chats", Context.MODE_PRIVATE) }
        val pinnedList = remember { mutableStateListOf<String>() }
    
        // Stateها
        val smsList by vm.smsList.collectAsState()
        val progress by vm.loadingProgress.collectAsState()
        val isSyncing by vm.isSyncing.collectAsState()
        val sim1Id by vm.sim1Id.collectAsState()
        val sim2Id by vm.sim2Id.collectAsState()
    
        // نام‌های سفارشی تب‌ها
        val sim1TabName by vm.sim1TabName.collectAsState()
        val sim2TabName by vm.sim2TabName.collectAsState()
    
        // پیام‌های موقت و وضعیت ارسال
        val tempMessages by vm.tempMessages.collectAsState()
        val sendingState by vm.sendingState.collectAsState()
    
        // ==================== متغیرهای اصلی UI ====================
        var selectedTab by remember { mutableIntStateOf(0) }
        var selectedContact by remember { mutableStateOf<String?>(null) }
        // ==================== ====================
    
        // ==================== key برای فورس ریفرش لیست ====================
        var listRefreshKey by remember { mutableIntStateOf(0) }
        // =======================================
    
        // ====================  حالت نمایش نخوانده‌ها ====================
        var showUnreadFirst by remember { mutableStateOf(false) }
        // ==================== ====================
    
    
    
        // ==================== مدیریت موقعیت اسکرول ====================
        val scrollPositionPrefs = remember { context.getSharedPreferences("scroll_positions", Context.MODE_PRIVATE) }
        var currentScrollPosition by remember { mutableIntStateOf(0) }
        // ========================================
    
        val listState = rememberLazyListState()
    
        // ==================== متغیرهای منو و تنظیمات ====================
        var showMenu by remember { mutableStateOf(false) }
        var showSettingsScreen by remember { mutableStateOf(false) }
        // ====================  ====================

        // ==================== حالت نمایش Onboarding ====================
        var shouldShowOnboarding by remember {
            mutableStateOf(shouldShowOnboarding(context))
        }
    
        // ==================== مدیریت بازکردن از نوتیفیکیشن ====================
        val notificationPrefs = remember { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) }
    
        // بررسی آیا باید چت باز شود؟
        val shouldOpenChat = remember {
            mutableStateOf(notificationPrefs.getBoolean("should_open_chat", false))
        }
        val chatAddressToOpen = remember {
            mutableStateOf(notificationPrefs.getString("chat_address", null))
        }
    
        // ==================== ذخیره وضعیت UI برای کنترل Back ====================
        val uiStatePrefs = remember { context.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }
    
        // ذخیره وضعیت چت
        LaunchedEffect(selectedContact) {
            uiStatePrefs.edit().putBoolean("is_in_chat", selectedContact != null).apply()
        }
    
    
        // ذخیره وضعیت تنظیمات
        LaunchedEffect(showSettingsScreen) {
            uiStatePrefs.edit().putBoolean("is_in_settings", showSettingsScreen).apply()
        }
    
        // ذخیره وضعیت بارگذاری
        LaunchedEffect(isFirstLoadDone) {
            uiStatePrefs.edit().putBoolean("is_loading", !isFirstLoadDone).apply()
        }
        // ==================== پایان ذخیره وضعیت ====================
    
        // پرش مستقیم به چت اگر از نوتیفیکیشن آمده باشد
        LaunchedEffect(shouldOpenChat.value, chatAddressToOpen.value, isFirstLoadDone) {
            if (shouldOpenChat.value && !chatAddressToOpen.value.isNullOrEmpty() && isFirstLoadDone) {
                // پاک کردن فلگ
                notificationPrefs.edit().remove("should_open_chat").apply()
                shouldOpenChat.value = false
    
                val address = chatAddressToOpen.value!!
                // پرش به چت
                selectedContact = address
    
                // علامت‌گذاری پیام‌های خوانده نشده
                vm.markConversationAsRead(address)
    
                // پاک کردن آدرس
                notificationPrefs.edit().remove("chat_address").apply()
                chatAddressToOpen.value = null
    
                Log.d("MySMSApp", "🚀 Auto-opening chat for: $address")
            }
        }
        // ==================== پایان بخش نوتیفیکیشن ====================
    
        // در تابع MySMSApp
        var hasNewMessages by remember { mutableStateOf(false) }
        var newMessageCount by remember { mutableStateOf(0) }
    
    // محاسبه پیام‌های خوانده‌نشده
        val unreadMessages by remember(smsList) {
            derivedStateOf {
                smsList.count { !it.read && it.type == 1 }
            }
        }
    
    // رفرش خودکار هر 10 ثانیه
        LaunchedEffect(Unit) {
            while (true) {
                delay(10000) // 10 ثانیه
    
                // فقط اگر برنامه در حال اجراست
                if (isFirstLoadDone) {
                    // چک برای پیام‌های جدید
                    val currentUnread = smsList.count { !it.read && it.type == 1 }
                    if (currentUnread > newMessageCount) {
                        newMessageCount = currentUnread
                        hasNewMessages = true
                        Log.d("AutoRefresh", "🆕 New messages detected: $currentUnread")
                    }
                }
            }
        }
    
        // تعداد پیام‌های خوانده نشده برای هر سیم‌کارت
        val unreadCounts by remember(smsList, sim1Id, sim2Id) {
            derivedStateOf {
                val sim1Unread = smsList.count { sms ->
                    !sms.read && sms.type == 1 && sms.subId == sim1Id
                }
                val sim2Unread = smsList.count { sms ->
                    !sms.read && sms.type == 1 && sms.subId == sim2Id
                }
                Pair(sim1Unread, sim2Unread)
            }
        }

        // درخواست مجوز
        val requestPermissionsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Toast.makeText(context, "✅ مجوزها تأیید شد", Toast.LENGTH_SHORT).show()
                // بلافاصله سینک را شروع کن
                vm.startInitialSync()
                isFirstLoadDone = true
                appPrefs.edit().putBoolean("initial_load_done", true).apply()
                shouldShowOnboarding = false
            } else {
                // اگر مجوزها کامل نیست، کاربر را به Onboarding برگردان
                shouldShowOnboarding = true
                Toast.makeText(context, "لطفاً تمام مجوزها را تأیید کنید", Toast.LENGTH_SHORT).show()
            }
        }
    
        // محاسبه مکالمات - منطق از کد قدیمی
        val sortedConversations by remember(smsList, pinnedList.size, vm.drafts, selectedTab, showUnreadFirst, listRefreshKey) {
            derivedStateOf {
                val allConversations = smsList.groupBy { it.address }.map { entry ->
                    val address = entry.key
                    val messages = entry.value
                    val lastMsg = messages.maxByOrNull { it.date }!!
    
                    val unreadCount = messages.count { !it.read && it.type == 1 }
                    val draft = vm.drafts[address]
                    val showDraft = !draft.isNullOrBlank()
                    val isPinned = pinnedList.contains(address)
    
                    val displayMsg = lastMsg.copy(
                        body = if (showDraft) "پیش‌نویس: $draft" else lastMsg.body,
                        date = if (showDraft) System.currentTimeMillis() else lastMsg.date
                    )
    
                    ConversationData(
                        sms = displayMsg,
                        isDraft = showDraft,
                        unreadCount = unreadCount,
                        isPinned = isPinned,
                        originalDate = displayMsg.date
                    )
                }
    
                val filtered = when (selectedTab) {
                    0 -> allConversations.filter { it.sms.subId == sim1Id }
                    1 -> allConversations.filter { it.sms.subId == sim2Id }
                    else -> allConversations
                }
    
                // *** تغییر جدید: مرتب‌سازی بر اساس نخوانده‌ها اگر فعال باشد
                if (showUnreadFirst) {
                    filtered.sortedWith(
                        compareByDescending<ConversationData> { it.isPinned }
                            .thenByDescending { it.unreadCount > 0 }
                            .thenByDescending { it.originalDate }
                    )
                } else {
                    filtered.sortedWith(
                        compareByDescending<ConversationData> { it.isPinned }
                            .thenByDescending { it.originalDate }
                    )
                }
            }
        }
    
        LaunchedEffect(Unit) {
            pinnedList.clear()
            pinnedList.addAll(pinnedPrefs.all.keys)
        }
    
        // تشخیص اتمام بارگذاری اولیه
        LaunchedEffect(isSyncing, progress) {
            if (!isSyncing && progress == 100 && !isFirstLoadDone && smsList.isNotEmpty()) {
                isFirstLoadDone = true
                appPrefs.edit().putBoolean("initial_load_done", true).apply()
            }
        }
    
        // صفحه 1: بارگذاری اولیه (اگر قبلا انجام نشده)
        // صفحه 1: بارگذاری اولیه
        // صفحه 1: Onboarding یا Loading
        if (!isFirstLoadDone || shouldShowOnboarding) {
            if (shouldShowOnboarding) {
                // نمایش صفحه Onboarding
                OnboardingScreen(
                    onComplete = {
                        shouldShowOnboarding = false
                        // بررسی مجدد مجوزها بعد از تکمیل onboarding
                        if (checkAllRequiredPermissions(context)) {
                            // شروع سینک
                            vm.startInitialSync()
                            isFirstLoadDone = true
                            appPrefs.edit().putBoolean("initial_load_done", true).apply()
                        } else {
                            // اگر هنوز مجوزها کامل نیست، دوباره onboarding نشان بده
                            shouldShowOnboarding = true
                        }
                    },
                    viewModel = vm
                )
            } else {
                // صفحه Loading قدیمی (فقط برای بارگذاری)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "در حال آماده‌سازی...",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "در حال بارگذاری پیامک‌ها",
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        if (isSyncing) {
                            LinearProgressIndicator(
                                progress = progress / 100f, // تغییر از { progress / 100f } به progress / 100f
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "%$progress تکمیل شد",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Button(
                                onClick = {
                                    // بررسی مجوزها
                                    val requiredPermissions = arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )

                                    val missingPermissions = requiredPermissions.filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }

                                    if (missingPermissions.isNotEmpty()) {
                                        // برگشت به Onboarding
                                        shouldShowOnboarding = true
                                    } else {
                                        // شروع سینک
                                        vm.startInitialSync()
                                        isFirstLoadDone = true
                                        appPrefs.edit().putBoolean("initial_load_done", true).apply()
                                        Toast.makeText(context, "در حال بارگذاری پیامک‌ها...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("بارگذاری پیامک‌ها")
                            }
                        }
                    }
                }
            }
        }
        // صفحه 2: چت داخلی
        else if (selectedContact != null) {
            // کنترل دکمه Back برای صفحه چت
            BackHandler {
                selectedContact = null
            }
    
    
            val contactAddress = selectedContact!!
            LaunchedEffect(contactAddress) {
                // علامت‌گذاری پیام‌های این مخاطب به عنوان خوانده شده
                vm.markConversationAsRead(contactAddress)
            }
            // دریافت پیام‌های این مخاطب
            val contactMessages by remember(contactAddress, smsList, tempMessages) {
                derivedStateOf {
                    vm.getCombinedMessages(contactAddress)
                }
            }
    
            // وضعیت ارسال
            val isSendingForThisContact by remember(sendingState) {
                derivedStateOf {
                    sendingState[contactAddress] == true
                }
            }
    
            // پیش‌نویس فعلی
            val currentDraft by remember(vm.drafts[contactAddress]) {
                mutableStateOf(vm.drafts[contactAddress] ?: "")
            }
    
    
    
    
            Column(modifier = Modifier.fillMaxSize()) {
                InternalChatScreen(
                    messages = contactMessages,
                    context = context,
                    onSendClick = { message ->
                        val defaultSimId = when(selectedTab) {
                            0 -> sim1Id ?: -1
                            1 -> sim2Id ?: -1
                            else -> -1
                        }
                        if (defaultSimId != -1 && message.isNotBlank()) {
                            vm.sendSms(contactAddress, message, defaultSimId)
                        }
                    },
                    draftMessage = currentDraft,
                    onDraftChange = { newText ->
                        vm.updateDraft(contactAddress, newText)
                    },
                    address = contactAddress,
                    onBack = { selectedContact = null }
    
                )
            }
        }
    
    
        // صفحه 2.5: تنظیمات نام تب‌ها
        else if (showSettingsScreen) {
            // کنترل دکمه Back برای صفحه تنظیمات - باید در ابتدای بلوک باشد
            BackHandler {
                showSettingsScreen = false
            }
            SettingsScreen(
                onBack = { showSettingsScreen = false },
                viewModel = vm,
                currentTab = selectedTab
            )
        }
    
        // صفحه 3: لیست اصلی
        else {
            // کنترل دکمه Back برای صفحه لیست اصلی - باید در ابتدای بلوک باشد
            var backPressTime by remember { mutableLongStateOf(0L) }
    
            BackHandler {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressTime < 2000) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    backPressTime = currentTime
                    Toast.makeText(context, "برای خروج دوباره Back را بزنید", Toast.LENGTH_SHORT).show()
                }
            }
            // بازیابی موقعیت اسکرول هنگام بازگشت
            LaunchedEffect(Unit) {
                val savedPosition = scrollPositionPrefs.getInt("last_scroll_position", 0)
                if (savedPosition > 0) {
                    delay(100) // تاخیر کوچک برای اطمینان از لود شدن
                    currentScrollPosition = savedPosition
                    // پاک کردن موقعیت ذخیره شده
                    scrollPositionPrefs.edit().remove("last_scroll_position").apply()
                }
            }
    
    
    
            Column(modifier = Modifier.fillMaxSize()) {
                // TopAppBar با منو
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "پیام‌رسان",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    actions = {
                        IconButton(
                            onClick = { showMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "منو",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
    
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تغییر نام تب‌های سیم‌کارت") },
                                onClick = {
                                    showMenu = false
                                    showSettingsScreen = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null
                                    )
                                }
                            )
    
                            Divider()
    
                            DropdownMenuItem(
                                text = { Text("تنظیمات") },
                                onClick = {
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                )
    
                // تب‌های سیم‌کارت
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sim1TabName)
                                if (unreadCounts.first > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge {
                                        Text(unreadCounts.first.toString())
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sim2TabName)
                                if (unreadCounts.second > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge {
                                        Text(unreadCounts.second.toString())
                                    }
                                }
                            }
                        }
                    )
                }
    
                // Progress Indicator
                if (isSyncing || (progress > 0 && progress < 100)) {
                    LinearProgressIndicator(
                        progress = progress / 100f, // تغییر از { progress / 100f } به progress / 100f
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
    
                // دکمه Refresh
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "پیام‌ها: ${smsList.size}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (unreadMessages > 0) {
                            Text(
                                "خوانده‌نشده: $unreadMessages",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
    
                    Row {
                        // دکمه نمایش نخوانده‌ها
                        IconButton(
                            onClick = {
                                showUnreadFirst = !showUnreadFirst
                                // *** تغییر: افزایش عددی
                                listRefreshKey++
                                // پاک کردن موقعیت اسکرول
                                scrollPositionPrefs.edit().remove("last_scroll_position").apply()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (showUnreadFirst)
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.CheckCircle,
                                contentDescription = if (showUnreadFirst)
                                    "نمایش همه"
                                else
                                    "نمایش نخوانده‌ها اول",
                                tint = if (showUnreadFirst)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (unreadMessages > 0) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val unreadMessages = smsList.filter { !it.read && it.type == 1 }
                                        unreadMessages.forEach { sms ->
                                            vm.markMessageAsRead(sms.id)
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "همه پیام‌ها خوانده شدند", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("خواندن همه", fontSize = 12.sp)
                            }
                        }
    
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    vm.startInitialSync()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "در حال بروزرسانی...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "بارگذاری مجدد",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
    
                // لیست مکالمات
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = currentScrollPosition
                )
    
                // مشاهده تغییرات اسکرول برای ذخیره موقعیت فعلی
                LaunchedEffect(listState.firstVisibleItemIndex) {
                    currentScrollPosition = listState.firstVisibleItemIndex
                }
    
                // لیست مکالمات
                ConversationListScreen(
                    sortedConversations = sortedConversations,
                    context = context,
                    pinnedList = pinnedList,
                    pinnedPrefs = pinnedPrefs,
                    listState = listState,
                    onContactClick = { address ->
                        scrollPositionPrefs.edit().putInt("last_scroll_position", currentScrollPosition).apply()
                        selectedContact = address
                    },
                    scrollToPosition = currentScrollPosition,
                    // *** تغییر: پاس دادن خود key
                    refreshKey = listRefreshKey
                )
            }
        }
    
    
    
    }
    
    // داده‌های مدل
    data class ConversationData(
        val sms: SmsEntity,
        val isDraft: Boolean,
        val unreadCount: Int,
        val isPinned: Boolean,
        val originalDate: Long
    )