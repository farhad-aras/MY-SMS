import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.mysms.ui.theme.ConversationData
import com.example.mysms.ui.theme.ConversationListScreen
import com.example.mysms.ui.theme.InternalChatScreen
import com.example.mysms.ui.theme.MainActivity
import com.example.mysms.ui.theme.OnboardingScreen
import com.example.mysms.ui.theme.SettingsScreen
import com.example.mysms.ui.theme.checkAllRequiredPermissions
import com.example.mysms.ui.theme.shouldShowOnboarding
import com.example.mysms.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.isNullOrBlank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySMSApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // توابع بررسی برنامه پیش‌فرض
    fun isDefaultSmsApp(): Boolean {
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }

    fun openDefaultSmsAppSettings() {
        try {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            context.startActivity(intent)
            Log.d("MySMSApp", "⚙️ Opening default SMS app settings")
        } catch (e: Exception) {
            Log.e("MySMSApp", "❌ Error opening SMS settings: ${e.message}", e)
            // Fallback به تنظیمات اصلی برنامه
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        }
    }





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
    // State برای دیالوگ پاسخ سریع
    var showQuickReplyDialog by remember { mutableStateOf(false) }
    var quickReplyAddress by remember { mutableStateOf("") }
    var quickReplyNotificationId by remember { mutableIntStateOf(0) }
    var quickReplyMessage by remember { mutableStateOf("") }

    var showSettingsScreen by remember { mutableStateOf(false) }
    // ====================  ====================



    // ==================== حالت نمایش Onboarding ====================
    var shouldShowOnboarding by remember {
        mutableStateOf(shouldShowOnboarding(context))
    }

    // ==================== مدیریت بازکردن از نوتیفیکیشن ====================
    val notificationPrefs = remember { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) }



// شروع سرویس‌ها
    LaunchedEffect(Unit) {
        delay(1000)
        (context as? MainActivity)?.startForegroundServiceIfNeeded()
    }

    // سینک خودکار هنگام باز شدن برنامه
    LaunchedEffect(Unit) {
        if (isFirstLoadDone && !isSyncing) {
            delay(1000) // تأخیر ۱ ثانیه
            vm.startInitialSync()
        }
    }

// بررسی دسترسی Notification Listener
    LaunchedEffect(Unit) {
        delay(2000) // تاخیر ۲ ثانیه

        val isNotificationAccessEnabled =
            com.example.mysms.ui.theme.NotificationListener.isNotificationServiceEnabled(context)

        if (!isNotificationAccessEnabled) {
            // فقط یک بار هشدار بده
            val prefs = context.getSharedPreferences("notification_access_prefs", Context.MODE_PRIVATE)
            val hasShownWarning = prefs.getBoolean("has_shown_notification_warning", false)

            if (!hasShownWarning) {
                delay(3000) // تاخیر بیشتر
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("دسترسی اعلان‌ها")
                        .setMessage("برای جلوگیری از نمایش دو نوتیفیکیشن (اپ شما + Google Messages)، لطفاً دسترسی به اعلان‌های سیستم را فعال کنید.\n\nمی‌توانید از طریق منوی برنامه این کار را انجام دهید.")
                        .setPositiveButton("باشه") { dialog, _ ->
                            dialog.dismiss()
                            prefs.edit().putBoolean("has_shown_notification_warning", true).apply()
                        }
                        .setNegativeButton("بعداً") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    // بررسی آیا باید چت باز شود؟
    val shouldOpenChat = remember {
        mutableStateOf(notificationPrefs.getBoolean("should_open_chat", false))
    }
    val chatAddressToOpen = remember {
        mutableStateOf(notificationPrefs.getString("chat_address", null))
    }

    // ==================== ذخیره وضعیت UI برای کنترل Back ====================
    val uiStatePrefs = remember { context.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }

    // ==================== مدیریت پاسخ سریع ====================
    val quickReplyPrefs = remember { context.getSharedPreferences("quick_reply_prefs", Context.MODE_PRIVATE) }


// بررسی نمایش دیالوگ پاسخ سریع از Intent
    LaunchedEffect(Unit) {
        val shouldShow = quickReplyPrefs.getBoolean("show_quick_reply_dialog", false)
        val address = quickReplyPrefs.getString("reply_address", "")
        val notifId = quickReplyPrefs.getInt("notification_id", 0)

        if (shouldShow && !address.isNullOrEmpty()) {
            showQuickReplyDialog = true
            quickReplyAddress = address
            quickReplyNotificationId = notifId

            // پاک کردن فلگ
            quickReplyPrefs.edit().clear().apply()
        }
    }


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

                        // در onCreate، بعد از خط DefaultSmsDisabler:
                        if (!isDefaultSmsApp()) {
                            Toast.makeText(
                                context,
                                "⚠️ برای جلوگیری از دو نوتیفیکیشن، برنامه را به پیش‌فرض تنظیم کنید",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        // اگر هنوز مجوزها کامل نیست، دوباره onboarding نشان بده
                        shouldShowOnboarding = true
                    }
                },
                viewModel = vm
            )


            // LaunchedEffect جداگانه برای نمایش هشدار
            LaunchedEffect(Unit) {
                val warningPrefs = context.getSharedPreferences("sms_warning_prefs", Context.MODE_PRIVATE)
                val shouldShowWarning = warningPrefs.getBoolean("show_default_sms_warning", false)

                if (shouldShowWarning && !isDefaultSmsApp()) {
                    delay(2000) // تاخیر 2 ثانیه
                    Toast.makeText(
                        context,
                        "💡 نکته: برای جلوگیری از دو نوتیفیکیشن، لطفاً از منو (⋯) گزینه 'تنظیم برنامه پیش‌فرض' را انتخاب کنید",
                        Toast.LENGTH_LONG
                    ).show()
                    // پاک کردن فلگ
                    warningPrefs.edit().putBoolean("show_default_sms_warning", false).apply()
                }
            }

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
                        // آیتم اصلی: تغییر نام تب‌های سیم‌کارت
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

                        // آیتم تنظیم برنامه پیش‌فرض
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDefaultSmsApp()) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isDefaultSmsApp())
                                            "برنامه پیش‌فرض ✅"
                                        else
                                            "تنظیم برنامه پیش‌فرض"
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                openDefaultSmsAppSettings()
                            }
                        )

// آیتم دسترسی اعلان‌های سیستم
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (com.example.mysms.ui.theme.NotificationListener.isNotificationServiceEnabled(context))
                                            Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (com.example.mysms.ui.theme.NotificationListener.isNotificationServiceEnabled(context))
                                            "دسترسی اعلان‌ها ✅"
                                        else
                                            "فعال‌سازی دسترسی اعلان‌ها"
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                com.example.mysms.ui.theme.NotificationListener.openNotificationSettings(context)
                                Toast.makeText(context, "لطفاً در تنظیمات دسترسی را فعال کنید", Toast.LENGTH_LONG).show()
                            }
                        )

// آیتم تست پاسخ سریع
                        var showTestDialog by remember { mutableStateOf(false) }

                        Divider()
                        DropdownMenuItem(
                            text = { Text("تست پاسخ سریع") },
                            onClick = {
                                showMenu = false
                                showTestDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Reply,
                                    contentDescription = null
                                )
                            }
                        )

// دیالوگ تست پاسخ سریع
                        if (showTestDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showTestDialog = false },
                                title = { Text("تست پاسخ سریع") },
                                text = { Text("آیا می‌خواهید پاسخ سریع را تست کنید؟") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            showTestDialog = false
                                            // ایجاد Intent برای پاسخ سریع
                                            val testAddress = "09123456789"
                                            val testNotificationId = testAddress.hashCode() and 0x7FFFFFFF

                                            val replyIntent = android.content.Intent(context, MainActivity::class.java).apply {
                                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                putExtra("quick_reply_test", true)
                                                putExtra("address", testAddress)
                                                putExtra("notification_id", testNotificationId)
                                            }
                                            context.startActivity(replyIntent)
                                        }
                                    ) {
                                        Text("بله")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = { showTestDialog = false }
                                    ) {
                                        Text("خیر")
                                    }
                                }
                            )
                        }
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

            // وضعیت پیام‌ها
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
                            listRefreshKey++
                            scrollPositionPrefs.edit().remove("last_scroll_position").apply()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = if (showUnreadFirst) "نمایش همه" else "نمایش نخوانده‌ها اول",
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
                                    Toast.makeText(context, "همه پیام‌ها خوانده شدند", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("خواندن همه", fontSize = 12.sp)
                        }
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

// لیست مکالمات با قابلیت Pull-to-Refresh
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
                refreshKey = listRefreshKey,
                // پارامترهای جدید برای SwipeRefresh
                isRefreshing = isSyncing,
                onRefresh = {
                    if (!isSyncing) {
                        coroutineScope.launch {
                            vm.startInitialSync()
                        }
                    }
                }
            )

// دیالوگ پاسخ سریع
            if (showQuickReplyDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showQuickReplyDialog = false },
                    title = { Text("💬 پاسخ سریع به $quickReplyAddress") },
                    text = {
                        Column {
                            Text("پیام خود را وارد کنید:", modifier = Modifier.padding(bottom = 8.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = quickReplyMessage,
                                onValueChange = { quickReplyMessage = it },
                                placeholder = { Text("متن پیام...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                maxLines = 3
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                if (quickReplyMessage.isNotEmpty()) {
                                    // ارسال پیام
                                    val defaultSimId = when(selectedTab) {
                                        0 -> sim1Id ?: -1
                                        1 -> sim2Id ?: -1
                                        else -> -1
                                    }

                                    if (defaultSimId != -1) {
                                        vm.sendSms(quickReplyAddress, quickReplyMessage, defaultSimId)
                                    } else {
                                        vm.sendSms(quickReplyAddress, quickReplyMessage, -1)
                                    }

                                    // حذف نوتیفیکیشن
                                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    notificationManager.cancel(quickReplyNotificationId)

                                    android.widget.Toast.makeText(context, "✅ پاسخ ارسال شد", android.widget.Toast.LENGTH_SHORT).show()
                                    showQuickReplyDialog = false
                                    quickReplyMessage = ""
                                } else {
                                    android.widget.Toast.makeText(context, "لطفاً متن پیام را وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = quickReplyMessage.isNotEmpty()
                        ) {
                            Text("📤 ارسال")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showQuickReplyDialog = false
                                quickReplyMessage = ""
                            }
                        ) {
                            Text("لغو")
                        }
                    }
                )
            }
        }
    }





}