package com.example.mysms.viewmodel

import com.example.mysms.data.DatabaseStats
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.example.mysms.data.MultipartKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import android.provider.Telephony
import android.content.Intent
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.withContext
import android.util.Log
import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysms.data.AppDatabase
import com.example.mysms.data.SmsEntity
import com.example.mysms.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val smsDao = AppDatabase.getDatabase(application).smsDao()
    private val repository = SmsRepository(application, smsDao)

    // SharedPreferences برای ذخیره نام تب‌های سیم‌کارت
    private val tabPrefs = getApplication<Application>()
        .getSharedPreferences("tab_names_prefs", Context.MODE_PRIVATE)

    // ==================== SharedPreferences برای وضعیت expand/collapse ====================
    private val dateExpansionPrefs = getApplication<Application>()
        .getSharedPreferences("date_expansion_state", Context.MODE_PRIVATE)


    // لیست تمام پیام‌ها از دیتابیس (برای صفحه چت با یک مخاطب)
    private val _smsList = MutableStateFlow<List<SmsEntity>>(emptyList())
    val smsList = _smsList.asStateFlow()

    // ✅ لیست مکالمات (آخرین پیام هر مخاطب) - برای صفحه اصلی
    private val _conversations = MutableStateFlow<List<SmsEntity>>(emptyList())
    val conversations = _conversations.asStateFlow()

    // وضعیت سینک
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress = _loadingProgress.asStateFlow()

    // سیم‌کارت‌ها
    private val _sim1Id = MutableStateFlow<Int?>(null)
    val sim1Id = _sim1Id.asStateFlow()

    private val _sim2Id = MutableStateFlow<Int?>(null)
    val sim2Id = _sim2Id.asStateFlow()

    // نام‌های سفارشی تب‌های سیم‌کارت
    private val _sim1TabName = MutableStateFlow("سیم ۱")
    val sim1TabName = _sim1TabName.asStateFlow()

    private val _sim2TabName = MutableStateFlow("سیم ۲")
    val sim2TabName = _sim2TabName.asStateFlow()

    // پیام‌های موقت (برای ارسال فوری)
    private val _tempMessages = MutableStateFlow<List<SmsEntity>>(emptyList())
    val tempMessages = _tempMessages.asStateFlow()

    // وضعیت ارسال هر مخاطب
    private val _sendingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sendingState = _sendingState.asStateFlow()

    // ====================  State برای وضعیت expand/collapse تاریخ‌ها ====================
    private val _expandedDates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val expandedDates = _expandedDates.asStateFlow()

    // ==================== Stateهای Onboarding ====================
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _permissionsState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsState = _permissionsState.asStateFlow()

    private val _onboardingStep = MutableStateFlow(0)
    val onboardingStep = _onboardingStep.asStateFlow()

    // ==================== State برای وضعیت برنامه پیش‌فرض ====================
    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp = _isDefaultSmsApp.asStateFlow()


    // پیش‌نویس‌ها
    val drafts = mutableStateMapOf<String, String>()
    private val prefs =
        getApplication<Application>().getSharedPreferences("drafts_prefs", Context.MODE_PRIVATE)

    // ==================== Stateهای جدید برای سینک هوشمند ====================
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _isSmartSyncing = MutableStateFlow(false)
    val isSmartSyncing = _isSmartSyncing.asStateFlow()

    private val _smartSyncProgress = MutableStateFlow(0)
    val smartSyncProgress = _smartSyncProgress.asStateFlow()

    private val _syncStats = MutableStateFlow<SyncStats>(SyncStats())
    val syncStats = _syncStats.asStateFlow()

    private val syncPrefs = getApplication<Application>()
        .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    // ==================== کلاس‌های داده برای سینک هوشمند ====================

    /**
     * آمار سینک
     */
    data class SyncStats(
        val totalMessages: Int = 0,
        val newMessages: Int = 0,
        val syncDuration: Long = 0,
        val lastSyncTime: Long = 0,
        val syncMethod: String = "full"
    )

    /**
     * تنظیمات سینک هوشمند
     */
    data class SyncSettings(
        val incrementalSyncEnabled: Boolean = true,
        val backgroundSyncInterval: Long = 5 * 60 * 1000, // 5 دقیقه
        val maxMessagesPerSync: Int = 100,
        val onlyUnread: Boolean = false,
        val autoSyncOnAppOpen: Boolean = true
    )

    /**
     * نتیجه سینک
     */
    sealed class SyncResult {
        data class Success(val stats: SyncStats) : SyncResult()
        data class PartialSuccess(val stats: SyncStats, val failedCount: Int) : SyncResult()
        data class Error(val message: String, val retryable: Boolean) : SyncResult()
        object NoNewMessages : SyncResult()
        object Skipped : SyncResult()
    }


    init {
        try {
            Log.d("HomeViewModel", "🟢 ViewModel init started")

            // 1. ابتدا شناسه سیم‌کارت‌ها
            refreshSimIds()

            // 2. بازیابی پیش‌نویس‌ها
            restoreDrafts()

            // 3. بارگذاری نام‌های ذخیره شده تب‌ها
            loadTabNames()

            // 5. بارگذاری وضعیت Onboarding
            checkOnboardingStatus()

            // 6. بررسی وضعیت برنامه پیش‌فرض
            checkDefaultSmsAppStatus()


            // ==================== بارگذاری وضعیت expand/collapse تاریخ‌ها ====================
            // 4. بارگذاری وضعیت expand/collapse تاریخ‌ها
            loadDateExpansionState()

            // 5. بارگذاری وضعیت سینک
            loadSyncState()

            // 6. شروع سینک هوشمند پس‌زمینه
            startBackgroundSmartSyncCheck()


            // 3. مشاهده دیتابیس (همه پیام‌ها و مکالمات)
            viewModelScope.launch {
                // مشاهده تمام پیام‌ها (برای صفحه چت)
                launch { observeAllSms() }
                // مشاهده مکالمات (برای صفحه اصلی)
                launch { observeConversations() }
            }

            Log.d("HomeViewModel", "✅ ViewModel init completed")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "💥 Error in init: ${e.message}", e)
            _smsList.value = emptyList()
            _conversations.value = emptyList()
        }
    }

    // ==================== توابع مدیریت وضعیت expand/collapse تاریخ‌ها ====================

    /**
     * بارگذاری وضعیت expand/collapse تاریخ‌ها از SharedPreferences
     */
    private fun loadDateExpansionState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allEntries = dateExpansionPrefs.all
                val expansionMap = mutableMapOf<String, Boolean>()

                allEntries.forEach { (dateKey, isExpanded) ->
                    if (isExpanded is Boolean) {
                        expansionMap[dateKey] = isExpanded
                    }
                }

                _expandedDates.value = expansionMap
                Log.d("HomeViewModel", "📅 Loaded date expansion state: ${expansionMap.size} dates")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error loading date expansion state: ${e.message}", e)
            }
        }
    }

    /**
     * بارگذاری وضعیت سینک از SharedPreferences
     */
    private fun loadSyncState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastSync = syncPrefs.getLong("last_sync_time", 0L)
                _lastSyncTime.value = lastSync

                Log.d("HomeViewModel", "📊 Sync state loaded: lastSync=${lastSync}")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error loading sync state: ${e.message}", e)
            }
        }
    }

    /**
     * شروع چک دوره‌ای برای سینک هوشمند
     */
    private fun startBackgroundSmartSyncCheck() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    delay(30 * 1000) // هر 30 ثانیه چک کن

                    // فقط اگر برنامه در foreground است سینک کن
                    val shouldSync = checkIfShouldSync()
                    if (shouldSync && !_isSmartSyncing.value) {
                        Log.d("HomeViewModel", "🔄 Background sync check: starting incremental sync")
                        syncNewMessagesIncremental()
                    }

                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ Error in background sync check", e)
                    delay(60 * 1000) // در صورت خطا 1 دقیقه صبر کن
                }
            }
        }
    }


    /**
     * شروع پاکسازی دوره‌ای دیتابیس
     */
    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    delay(24 * 60 * 60 * 1000) // هر 24 ساعت

                    // 1. پاکسازی پیام‌های حذف شده قدیمی
                    cleanupOldDeletedMessages()

                    // 2. ترکیب پیام‌های چندبخشی ناقص
                    combineCompleteMultipartMessages()

                    // 3. دریافت آمار برای گزارش
                    val stats = getDatabaseStatistics()
                    Log.d("HomeViewModel", "📊 Database stats: total=${stats.total}, pendingSync=${stats.pendingSync}, incompleteMultipart=${stats.incompleteMultipart}")

                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ Error in periodic cleanup", e)
                    delay(60 * 60 * 1000) // در صورت خطا، 1 ساعت صبر کن
                }
            }
        }
    }

    /**
     * سینک افزایشی پیام‌های جدید
     */
    fun syncNewMessagesIncremental() {
        viewModelScope.launch {
            try {
                if (_isSmartSyncing.value) {
                    Log.d("HomeViewModel", "⏸️ Smart sync already in progress, skipping")
                    return@launch
                }

                Log.d("HomeViewModel", "🚀 Starting incremental sync")
                _isSmartSyncing.value = true
                _smartSyncProgress.value = 0

                val startTime = System.currentTimeMillis()
                val lastSync = _lastSyncTime.value

                // 1. سینک پیام‌های جدید
                val result: Int = withContext(Dispatchers.IO) {
                    repository.syncNewMessages(lastSync)
                }

                // 2. آپدیت آمار
                val syncDuration = System.currentTimeMillis() - startTime
                val newStats = SyncStats(
                    totalMessages = smsList.value.size,
                    newMessages = result,
                    syncDuration = syncDuration,
                    lastSyncTime = System.currentTimeMillis(),
                    syncMethod = "incremental"
                )

                _syncStats.value = newStats
                _lastSyncTime.value = System.currentTimeMillis()

                // 3. ذخیره زمان سینک
                syncPrefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

                // 4. آپدیت progress
                _smartSyncProgress.value = 100
                _isSmartSyncing.value = false

                Log.d("HomeViewModel", "✅ Incremental sync completed: $result new messages in ${syncDuration}ms")

                // 5. نمایش Toast اگر پیام جدیدی بود
                if (result > 0) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "✅ $result پیام جدید دریافت شد",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Incremental sync failed: ${e.message}", e)
                _isSmartSyncing.value = false
                _smartSyncProgress.value = 0
            }
        }
    }

    // ==================== توابع مدیریت سینک هوشمند ====================

    /**
     * شروع سینک هوشمند (انتخاب خودکار بین full و incremental)
     */

    // ==================== توابع جدید برای کار با دیتابیس Migration یافته ====================

    /**
     * دریافت پیام‌هایی که نیاز به سینک دارند
     */
    fun getPendingSyncMessages(limit: Int = 100): Flow<List<SmsEntity>> {
        return flow {
            withContext(Dispatchers.IO) {
                val lastSync = _lastSyncTime.value
                val messages = smsDao.getMessagesForSync(lastSync, limit)
                emit(messages)
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * دریافت آمار دیتابیس
     */
    suspend fun getDatabaseStatistics(): DatabaseStats {
        return withContext(Dispatchers.IO) {
            smsDao.getDatabaseStats()
        }
    }

    /**
     * پاکسازی پیام‌های حذف شده قدیمی
     */
    fun cleanupOldDeletedMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deletedCount = smsDao.cleanupDeletedMessages()
                Log.d("HomeViewModel", "🧹 Cleaned up $deletedCount old deleted messages")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error cleaning up deleted messages", e)
            }
        }
    }

    /**
     * Soft delete یک پیام
     */
    fun softDeleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsDao.softDeleteById(messageId)
                Log.d("HomeViewModel", "🗑️ Message $messageId soft deleted")

                // رفرش لیست
                refreshSmsList()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error soft deleting message", e)
            }
        }
    }

    /**
     * دریافت آخرین زمان سینک از دیتابیس
     */
    suspend fun refreshLastSyncTimeFromDb() {
        withContext(Dispatchers.IO) {
            try {
                val lastSync = smsDao.getLastSyncTime() ?: 0L
                _lastSyncTime.value = lastSync
                Log.d("HomeViewModel", "🔄 Last sync time from DB: $lastSync")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting last sync time from DB", e)
            }
        }
    }


    fun startSmartSync() {
        viewModelScope.launch {
            try {
                val lastSync = _lastSyncTime.value
                val now = System.currentTimeMillis()

                // اگر بیش از 1 ساعت از آخرین سینک گذشته یا اولین سینک است
                if (lastSync == 0L || (now - lastSync) > (60 * 60 * 1000)) {
                    Log.d("HomeViewModel", "⏰ Last sync was too long ago, starting full sync")
                    startInitialSync()
                } else {
                    Log.d("HomeViewModel", "⚡ Last sync was recent, starting incremental sync")
                    syncNewMessagesIncremental()
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error in smart sync decision", e)
                // Fallback به سینک کامل
                startInitialSync()
            }
        }
    }

    /**
     * دریافت تنظیمات سینک
     */
    fun getSyncSettings(): SyncSettings {
        return SyncSettings(
            incrementalSyncEnabled = syncPrefs.getBoolean("incremental_sync_enabled", true),
            backgroundSyncInterval = syncPrefs.getLong("background_sync_interval", 5 * 60 * 1000),
            maxMessagesPerSync = syncPrefs.getInt("max_messages_per_sync", 100),
            onlyUnread = syncPrefs.getBoolean("only_unread", false),
            autoSyncOnAppOpen = syncPrefs.getBoolean("auto_sync_on_app_open", true)
        )
    }

    /**
     * ذخیره تنظیمات سینک
     */
    fun saveSyncSettings(settings: SyncSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncPrefs.edit().apply {
                    putBoolean("incremental_sync_enabled", settings.incrementalSyncEnabled)
                    putLong("background_sync_interval", settings.backgroundSyncInterval)
                    putInt("max_messages_per_sync", settings.maxMessagesPerSync)
                    putBoolean("only_unread", settings.onlyUnread)
                    putBoolean("auto_sync_on_app_open", settings.autoSyncOnAppOpen)
                    apply()
                }
                Log.d("HomeViewModel", "✅ Sync settings saved")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error saving sync settings", e)
            }
        }
    }

    /**
     * پاک کردن cache سینک (برای debug)
     */
    fun clearSyncCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncPrefs.edit().clear().apply()
                _lastSyncTime.value = 0L
                _syncStats.value = SyncStats()
                Log.d("HomeViewModel", "🧹 Sync cache cleared")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error clearing sync cache", e)
            }
        }
    }

    /**
     * بررسی وضعیت سینک
     */
    fun getSyncStatus(): String {
        val lastSync = _lastSyncTime.value
        val now = System.currentTimeMillis()

        return if (lastSync == 0L) {
            "⏳ اولین سینک انجام نشده"
        } else {
            val minutesAgo = (now - lastSync) / (60 * 1000)
            if (minutesAgo < 1) {
                "✅ هم‌اکنون سینک شده"
            } else if (minutesAgo < 60) {
                "✅ $minutesAgo دقیقه پیش"
            } else {
                "⚠️ ${minutesAgo / 60} ساعت پیش"
            }
        }
    }

    /**
     * بررسی آیا باید سینک انجام شود
     */
    private fun checkIfShouldSync(): Boolean {
        // تنظیمات سینک هوشمند را از SharedPreferences بگیر
        val incrementalEnabled = syncPrefs.getBoolean("incremental_sync_enabled", true)
        val lastSyncTime = _lastSyncTime.value
        val now = System.currentTimeMillis()

        // اگر سینک افزایشی فعال نیست یا کمتر از 1 دقیقه از آخرین سینک گذشته
        if (!incrementalEnabled || (now - lastSyncTime < 60 * 1000)) {
            return false
        }

        return true
    }

    /**
     * بررسی آیا یک تاریخ expand شده است یا نه
     * @param dateKey تاریخ به فرمت شمسی (مثلاً 1403/10/15)
     */
    fun isDateExpanded(dateKey: String): Boolean {
        return _expandedDates.value[dateKey] ?: false
    }

    /**
     * تغییر وضعیت expand/collapse یک تاریخ
     * @param dateKey تاریخ به فرمت شمسی
     * @param isExpanded وضعیت جدید
     */
    fun toggleDateExpansion(dateKey: String, isExpanded: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // آپدیت state
                val newMap = _expandedDates.value.toMutableMap()
                newMap[dateKey] = isExpanded
                _expandedDates.value = newMap

                // ذخیره در SharedPreferences
                dateExpansionPrefs.edit().putBoolean(dateKey, isExpanded).apply()

                Log.d("HomeViewModel", "💾 Date expansion state saved: $dateKey = $isExpanded")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error saving date expansion state: ${e.message}", e)
            }
        }
    }

    /**
     * تنظیم وضعیت پیش‌فرض برای لیست تاریخ‌ها
     * پیش‌فرض: همه بسته، فقط آخرین تاریخ باز
     * @param dateKeys لیست تمام تاریخ‌ها به صورت مرتب شده
     */
    fun setDefaultExpansionState(dateKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dateKeys.isEmpty()) return@launch

                // ایجاد مپ جدید
                val newMap = mutableMapOf<String, Boolean>()

                // همه تاریخ‌ها رو بسته می‌کنیم
                dateKeys.forEach { dateKey ->
                    newMap[dateKey] = false
                }

                // فقط آخرین تاریخ رو باز می‌کنیم (اگر قبلاً ذخیره نشده باشد)
                val lastDateKey = dateKeys.lastOrNull()
                if (lastDateKey != null) {
                    // اگر قبلاً وضعیتی برای این تاریخ ذخیره شده، تغییر نمی‌دهیم
                    if (!_expandedDates.value.containsKey(lastDateKey)) {
                        newMap[lastDateKey] = true
                        dateExpansionPrefs.edit().putBoolean(lastDateKey, true).apply()
                    } else {
                        // از وضعیت ذخیره شده استفاده می‌کنیم
                        newMap[lastDateKey] = _expandedDates.value[lastDateKey] ?: false
                    }
                }

                // آپدیت state
                _expandedDates.value = newMap

                Log.d("HomeViewModel", "📅 Default expansion state set for ${dateKeys.size} dates")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error setting default expansion state: ${e.message}", e)
            }
        }
    }

    /**
     * پاک کردن همه وضعیت‌های expand/collapse
     */
    fun clearAllExpansionStates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dateExpansionPrefs.edit().clear().apply()
                _expandedDates.value = emptyMap()
                Log.d("HomeViewModel", "🧹 All date expansion states cleared")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error clearing expansion states: ${e.message}", e)
            }
        }
    }


    // ---------------------------
    // مشاهده flow تمام پیام‌ها
    // ---------------------------
    private fun observeAllSms() {
        viewModelScope.launch {
            repository.getAllSmsFlow().collect { list ->
                Log.d("HomeViewModel", "📊 All SMS Flow update: ${list.size} SMS")
                _smsList.value = list
            }
        }
    }

    // ---------------------------
    // ✅ مشاهده flow مکالمات (آخرین پیام هر مخاطب)
    // ---------------------------
    private fun observeConversations() {
        viewModelScope.launch {
            repository.getConversationsFlow().collect { list ->
                Log.d("HomeViewModel", "📞 Conversations Flow update: ${list.size} conversations")
                if (list.isNotEmpty()) {
                    list.forEachIndexed { index, sms ->
                        Log.d("HomeViewModel", "  ${index + 1}. ${sms.address} - ${sms.body.take(20)} - ${sms.date}")
                    }
                } else {
                    Log.d("HomeViewModel", "📭 Conversations list is EMPTY")
                }
                _conversations.value = list
            }
        }
    }

    // ---------------------------
    // تست دیتابیس
    // ---------------------------

    private suspend fun testDatabase() {
        withContext(Dispatchers.IO) {
            try {
                // ۱. تست connection دیتابیس
                val db = AppDatabase.getDatabase(getApplication())
                Log.d("HomeViewModel", "🔗 Database connected")

                // ۲. خواندن تعداد رکوردها
                val count = db.smsDao().getAllSms().size
                Log.d("HomeViewModel", "📊 Total records in DB: $count")

                // ۳. اگر خالی بود، یک رکورد تستی اضافه کن
                if (count == 0) {
                    Log.d("HomeViewModel", "📝 Adding test SMS...")
                    val testSms = SmsEntity(
                        id = "test_${System.currentTimeMillis()}",
                        address = "09123456789",
                        body = "این یک پیام تست برای نمایش است",
                        date = System.currentTimeMillis(),
                        type = 1,
                        subId = 1,
                        read = false
                    )
                    db.smsDao().insertAll(listOf(testSms))
                    Log.d("HomeViewModel", "✅ Test SMS added to DB")
                } else {
                    // نمونه‌ای از رکوردها رو نشون بده
                    val sample = db.smsDao().getAllSms().take(3)
                    sample.forEachIndexed { index, sms ->
                        Log.d("HomeViewModel", "📋 Sample $index: ${sms.address} - ${sms.body.take(30)}")
                    }
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "💥 Database test failed: ${e.message}", e)
            }
        }
    }

    // ---------------------------
    // تست دستی دیتابیس (برای دکمه تست)
    // ---------------------------

    fun manualTestDatabase() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "🔧 Manual DB test triggered")
            testDatabase()
        }
    }

    // ---------------------------
    // Drafts
    // ---------------------------

    private fun restoreDrafts() {
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                drafts[key] = value
            }
        }
    }

    fun updateDraft(address: String, text: String) {
        drafts[address] = text
        prefs.edit().putString(address, text).apply()
    }

    // ---------------------------
    // SIM
    // ---------------------------

    fun refreshSimIds() {
        val ids = repository.getSimIds()
        _sim1Id.value = ids.first
        _sim2Id.value = ids.second
        Log.d("HomeViewModel", "📱 SIM IDs: SIM1=${ids.first}, SIM2=${ids.second}")
    }

    // ---------------------------
    // Send SMS
    // ---------------------------

    fun sendSms(address: String, message: String, subId: Int) {
        viewModelScope.launch {

            _sendingState.value = _sendingState.value + (address to true)

            val tempSms = SmsEntity(
                id = "temp_${System.currentTimeMillis()}",
                address = address,
                body = message,
                date = System.currentTimeMillis(),
                type = 2,
                subId = subId,
                read = true
            )

            _tempMessages.value = _tempMessages.value + tempSms

            try {
                val success = withContext(Dispatchers.IO) {
                    repository.sendSms(address, message, subId)
                }

                if (success) {
                    _tempMessages.value =
                        _tempMessages.value.filterNot { it.id == tempSms.id }

                    drafts.remove(address)
                    prefs.edit().remove(address).apply()

                    Log.d("HomeViewModel", "✅ SMS sent to $address")
                } else {
                    markTempAsFailed(tempSms)
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Send error", e)
                markTempAsFailed(tempSms)
            } finally {
                _sendingState.value = _sendingState.value - address
            }
        }
    }
    // تابع ایمپورت سریع پیام‌ها
    suspend fun quickImportSms(limit: Int = 50): Int {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("HomeViewModel", "🚀 Quick importing $limit messages")
                val count = repository.quickImportSms(limit)
                android.util.Log.d("HomeViewModel", "✅ Quick import completed: $count messages")
                count
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ Quick import failed", e)
                0
            }
        }
    }

    private fun markTempAsFailed(tempSms: SmsEntity) {
        _tempMessages.value = _tempMessages.value.map {
            if (it.id == tempSms.id) {
                it.copy(body = "${it.body} (ارسال ناموفق)")
            } else it
        }
    }

    // ---------------------------
    // Sync
    // ---------------------------

    fun startInitialSync(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                // اگر در حال سینک هستیم و forceRefresh نیست، انجام نده
                if (_isSyncing.value && !forceRefresh) {
                    Log.d("HomeViewModel", "⏸️ Sync already in progress, skipping")
                    return@launch
                }

                Log.d("HomeViewModel", "🔄 Starting initial sync")
                val startTime = System.currentTimeMillis()
                _isSyncing.value = true
                _loadingProgress.value = 0

                // 1. ابتدا شناسه سیم‌کارت‌ها رو بگیر
                refreshSimIds()

                // 2. سینک با timeout
                withTimeout(30_000) { // 30 ثانیه timeout
                    repository.syncSms().collect { progress ->
                        _loadingProgress.value = progress
                        Log.d("HomeViewModel", "📊 Sync progress: $progress%")

                        if (progress >= 100) {
                            _isSyncing.value = false
                            Log.d("HomeViewModel", "✅ Initial sync completed successfully")

                            // 3. بعد از سینک، پیام‌های چندبخشی را چک کن
                            checkMultipartAfterSync()

                            // 4. شروع چک دوره‌ای پیام‌های چندبخشی
                            startMultipartCombinationCheck()

                            // 5. آپدیت زمان آخرین سینک
                            _lastSyncTime.value = System.currentTimeMillis()
                            syncPrefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

                            // 6. آپدیت آمار (با delay کمی تا مطمئن شویم stateها آپدیت شده‌اند)
                            viewModelScope.launch {
                                delay(500) // کمی صبر کن
                                val syncDuration = System.currentTimeMillis() - startTime
                                _syncStats.value = SyncStats(
                                    totalMessages = smsList.value.size,
                                    newMessages = smsList.value.size,
                                    syncDuration = syncDuration,
                                    lastSyncTime = System.currentTimeMillis(),
                                    syncMethod = "full"
                                )

                                _lastSyncTime.value = System.currentTimeMillis()
                                syncPrefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

                                Log.d("HomeViewModel", "💾 Full sync completed in ${syncDuration}ms")
                            }

                        }
                    }
                }

            } catch (e: TimeoutCancellationException) {
                Log.e("HomeViewModel", "⏰ Sync timeout after 30 seconds")
                _isSyncing.value = false
                _loadingProgress.value = 0
                // می‌توانی یک Toast یا Snackbar نشان بدی

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Sync failed: ${e.message}", e)
                _isSyncing.value = false
                _loadingProgress.value = 0
                // خطا رو به UI گزارش بده

            } finally {
                // مطمئن شو که state حتما reset شده
                if (_isSyncing.value) {
                    _isSyncing.value = false
                }
            }
        }
    }

    // ---------------------------
    // Read
    // ---------------------------

    fun markAsRead(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAsRead(address)
        }
    }

    // ---------------------------
    // Combined Messages
    // ---------------------------

    fun getCombinedMessages(address: String): List<SmsEntity> {
        val db = _smsList.value.filter { it.address == address }
        val temp = _tempMessages.value.filter { it.address == address }
        return (db + temp).sortedByDescending { it.date }
    }

    // ---------------------------
    // Combined Messages (بهبود یافته)
    // ---------------------------

    /**
     * دریافت پیام‌های ترکیب شده (تک‌بخشی + چندبخشی کامل)
     */
    suspend fun getCombinedMessagesImproved(address: String): List<SmsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                // اول پیام‌های کامل شده را بگیر
                val combinedMessages = getCombinedMessagesByAddress(address)

                // پیام‌های موقت را اضافه کن
                val temp = _tempMessages.value.filter { it.address == address }

                (combinedMessages + temp)
                    .sortedByDescending { it.date }
                    .distinctBy { it.id }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting improved combined messages", e)
                getCombinedMessages(address) // fallback به تابع قدیمی
            }
        }
    }

    // تابع جدید برای mark single message
    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // استفاده از تابع جدید با نام صحیح
                smsDao.markMessageAsRead(messageId)
                Log.d("HomeViewModel", "✅ Message $messageId marked as read")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error marking message as read", e)
            }
        }
    }

    // ==================== توابع جدید برای مدیریت پیام‌های چندبخشی ====================

    /**
     * دریافت پیام‌های چندبخشی ناقص
     */
    suspend fun getIncompleteMultipartMessages(): List<MultipartKey> {
        return withContext(Dispatchers.IO) {
            try {
                val timeThreshold = System.currentTimeMillis() - (30 * 60 * 1000) // 30 دقیقه گذشته
                smsDao.getIncompleteMultipartMessages(timeThreshold)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting incomplete multipart messages", e)
                emptyList()
            }
        }
    }

    /**
     * ترکیب پیام‌های چندبخشی کامل شده
     */
    suspend fun combineCompleteMultipartMessages() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("HomeViewModel", "🔗 ترکیب پیام‌های چندبخشی کامل شده...")

                val incompleteMessages = getIncompleteMultipartMessages()
                Log.d("HomeViewModel", "📋 تعداد پیام‌های ناقص: ${incompleteMessages.size}")

                incompleteMessages.forEach { key ->
                    try {
                        val parts = smsDao.getMultipartPartsByKey(key.address, key.messageId, key.referenceNumber)
                        val expectedCount = parts.firstOrNull()?.partCount ?: 1

                        Log.d("HomeViewModel", "🔍 بررسی پیام: ${key.address}, قطعات: ${parts.size}/$expectedCount")

                        // اگر تمام قطعات دریافت شده‌اند
                        if (parts.size >= expectedCount) {
                            val sortedParts = parts.sortedBy { it.partIndex }

                            // بررسی توالی قطعات
                            val hasAllParts = (1..expectedCount).all { partNum ->
                                sortedParts.any { it.partIndex == partNum }
                            }

                            if (hasAllParts) {
                                // ترکیب متن
                                val combinedBody = StringBuilder()
                                sortedParts.forEach { part ->
                                    combinedBody.append(part.body)
                                }

                                // ایجاد پیام کامل
                                val firstPart = sortedParts.first()
                                val completeSms = firstPart.copy(
                                    id = "multipart_complete_${key.messageId}_${System.currentTimeMillis()}",
                                    body = combinedBody.toString(),
                                    isComplete = true,
                                    status = 2,
                                    partIndex = 0
                                )

                                // ذخیره پیام کامل
                                smsDao.insert(completeSms)

                                // آپدیت state
                                refreshSmsList()

                                Log.d("HomeViewModel", "✅ پیام چندبخشی ترکیب شد: ${key.address}, طول: ${combinedBody.length}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "❌ خطا در ترکیب پیام: ${key.address}", e)
                    }
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error combining multipart messages", e)
            }
        }
    }

    /**
     * دریافت پیام‌های ترکیب شده برای یک مخاطب
     */
    suspend fun getCombinedMessagesByAddress(address: String): List<SmsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val allMessages = smsDao.getSmsByAddressFlow(address).firstOrNull() ?: emptyList()
                // فیلتر پیام‌های کامل یا تک‌بخشی
                allMessages.filter { message ->
                    !message.isMultipart || message.isComplete
                }.sortedByDescending { it.date }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting combined messages", e)
                emptyList()
            }
        }
    }

    /**
     * بررسی و ترکیب دوره‌ای پیام‌های چندبخشی
     */
    fun startMultipartCombinationCheck() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    delay(30 * 1000) // هر 30 ثانیه
                    combineCompleteMultipartMessages()
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ Error in multipart check", e)
                    delay(60 * 1000) // در صورت خطا، 1 دقیقه صبر کن
                }
            }
        }
    }

    /**
     * رفرش لیست پیام‌ها
     */
    private suspend fun refreshSmsList() {
        withContext(Dispatchers.IO) {
            try {
                val updatedList = smsDao.getAllSms()
                _smsList.value = updatedList

                // همچنین مکالمات را آپدیت کن
                val updatedConversations = smsDao.getConversationsFlow().firstOrNull() ?: emptyList()
                _conversations.value = updatedConversations

                Log.d("HomeViewModel", "🔄 لیست پیام‌ها رفرش شد: ${updatedList.size} پیام")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error refreshing SMS list", e)
            }
        }
    }

    /**
     * تابع کمکی برای چک کردن پیام‌های چندبخشی هنگام سینک
     */
    fun checkMultipartAfterSync() {
        viewModelScope.launch {
            delay(2000) // 2 ثانیه بعد از سینک
            combineCompleteMultipartMessages()
        }
    }

    // تابع اصلاح شده برای mark conversation
    fun markConversationAsRead(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsDao.markAsRead(address)
                Log.d("HomeViewModel", "✅ Conversation with $address marked as read")

                // فورس آپدیت لیست
                val updatedList = smsDao.getAllSms()
                _smsList.value = updatedList

            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error marking conversation as read", e)
            }
        }
    }

    // ---------------------------
    // ✅ تابع جدید: دریافت پیام‌های یک مخاطب خاص
    // ---------------------------
    fun getMessagesByAddressFlow(address: String) = repository.getSmsByAddressFlow(address)

    // ==================== توابع مدیریت نام تب‌ها ====================

    /**
     * بارگذاری نام‌های ذخیره شده تب‌ها از SharedPreferences
     */
    private fun loadTabNames() {
        val sim1Name = tabPrefs.getString("sim1_tab_name", "سیم ۱") ?: "سیم ۱"
        val sim2Name = tabPrefs.getString("sim2_tab_name", "سیم ۲") ?: "سیم ۲"

        _sim1TabName.value = sim1Name
        _sim2TabName.value = sim2Name
        Log.d("HomeViewModel", "📝 Loaded tab names: SIM1='$sim1Name', SIM2='$sim2Name'")
    }

    /**
     * دریافت نام نمایشی برای سیم‌کارت با توجه به تب انتخاب شده
     * @param tabIndex 0 برای SIM1, 1 برای SIM2
     */
    fun getSimDisplayName(tabIndex: Int): String {
        return when (tabIndex) {
            0 -> sim1TabName.value
            1 -> sim2TabName.value
            else -> "سیم‌کارت"
        }
    }

    /**
     * به‌روزرسانی نام تب سیم‌کارت
     * @param tabIndex 0 برای SIM1, 1 برای SIM2
     * @param newName نام جدید (اگر خالی باشد، نام پیش‌فرض ذخیره می‌شود)
     */
    fun updateSimTabName(tabIndex: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalName = if (newName.isBlank()) {
                    when (tabIndex) {
                        0 -> "سیم ۱"
                        1 -> "سیم ۲"
                        else -> "سیم‌کارت"
                    }
                } else {
                    newName
                }

                // ذخیره در SharedPreferences
                when (tabIndex) {
                    0 -> {
                        tabPrefs.edit().putString("sim1_tab_name", finalName).apply()
                        _sim1TabName.value = finalName
                    }
                    1 -> {
                        tabPrefs.edit().putString("sim2_tab_name", finalName).apply()
                        _sim2TabName.value = finalName
                    }
                }

                Log.d("HomeViewModel", "💾 Updated tab $tabIndex name to: '$finalName'")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error updating tab name: ${e.message}", e)
            }
        }
    }

    /**
     * بازیابی نام فعلی تب
     * @param tabIndex 0 برای SIM1, 1 برای SIM2
     */
    fun getCurrentTabName(tabIndex: Int): String {
        return when (tabIndex) {
            0 -> sim1TabName.value
            1 -> sim2TabName.value
            else -> "سیم‌کارت"
        }
    }

    // ==================== توابع مدیریت Onboarding ====================

    /**
     * بررسی وضعیت Onboarding
     */
    private fun checkOnboardingStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>()
                    .getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

                val isCompleted = prefs.getBoolean("onboarding_completed", false)
                _onboardingCompleted.value = isCompleted

                Log.d("HomeViewModel", "📋 Onboarding status: $isCompleted")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error checking onboarding status: ${e.message}", e)
            }
        }
    }

    /**
     * تکمیل Onboarding
     */
    fun completeOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>()
                    .getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

                prefs.edit().putBoolean("onboarding_completed", true).apply()
                _onboardingCompleted.value = true

                Log.d("HomeViewModel", "✅ Onboarding completed")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error completing onboarding: ${e.message}", e)
            }
        }
    }

    /**
     * ریست کردن Onboarding (برای تست یا وقتی کاربر مجوز لغو کرده)
     */
    fun resetOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>()
                    .getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

                prefs.edit().putBoolean("onboarding_completed", false).apply()
                _onboardingCompleted.value = false
                _onboardingStep.value = 0

                Log.d("HomeViewModel", "🔄 Onboarding reset")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error resetting onboarding: ${e.message}", e)
            }
        }
    }

    /**
     * بررسی وضعیت یک مجوز خاص
     */
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * بررسی همه مجوزهای ضروری
     */
    fun checkAllRequiredPermissions(): Boolean {
        val requiredPermissions = listOfNotNull(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
        )

        return requiredPermissions.all { permission ->
            checkPermission(permission)
        }
    }

    /**
     * گرفتن لیست مجوزهای ضروری که داده نشده‌اند
     */
    fun getMissingPermissions(): List<String> {
        val requiredPermissions = listOfNotNull(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
        )

        return requiredPermissions.filter { permission ->
            !checkPermission(permission)
        }
    }

    /**
     * گرفتن نام نمایشی مجوزها
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

    // ==================== توابع مدیریت برنامه پیش‌فرض ====================

    /**
     * بررسی آیا برنامه به عنوان برنامه پیش‌فرض پیامک تنظیم شده است
     */
    fun checkDefaultSmsAppStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isDefault = getApplication<Application>().packageName ==
                        Telephony.Sms.getDefaultSmsPackage(getApplication())

                _isDefaultSmsApp.value = isDefault
                Log.d("HomeViewModel", "📱 Default SMS App status: $isDefault")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error checking default SMS app: ${e.message}", e)
                _isDefaultSmsApp.value = false
            }
        }
    }

    init {
        try {
            Log.d("HomeViewModel", "🟢 ViewModel init started")

            // 1. ابتدا شناسه سیم‌کارت‌ها
            refreshSimIds()

            // 2. بازیابی پیش‌نویس‌ها
            restoreDrafts()

            // 3. بارگذاری نام‌های ذخیره شده تب‌ها
            loadTabNames()

            // 4. بارگذاری وضعیت expand/collapse تاریخ‌ها
            loadDateExpansionState()

            // 5. بارگذاری وضعیت سینک
            loadSyncState()

            // 6. بارگذاری آخرین زمان سینک از دیتابیس
            viewModelScope.launch {
                refreshLastSyncTimeFromDb()
            }

            // 7. بارگذاری وضعیت Onboarding
            checkOnboardingStatus()

            // 8. بررسی وضعیت برنامه پیش‌فرض
            checkDefaultSmsAppStatus()

            // 9. شروع سینک هوشمند پس‌زمینه
            startBackgroundSmartSyncCheck()

            // 10. شروع پاکسازی دوره‌ای
            startPeriodicCleanup()

            // 11. مشاهده دیتابیس (همه پیام‌ها و مکالمات)
            viewModelScope.launch {
                // مشاهده تمام پیام‌ها (برای صفحه چت)
                launch { observeAllSms() }
                // مشاهده مکالمات (برای صفحه اصلی)
                launch { observeConversations() }
            }

            Log.d("HomeViewModel", "✅ ViewModel init completed")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "💥 Error in init: ${e.message}", e)
            _smsList.value = emptyList()
            _conversations.value = emptyList()
        }
    }

    /**
     * باز کردن صفحه تنظیمات برای انتخاب برنامه پیش‌فرض پیامک
     */
    fun openDefaultSmsAppSettings() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                    getApplication<Application>().packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)

                Log.d("HomeViewModel", "⚙️ Opening default SMS app settings")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error opening SMS settings: ${e.message}", e)
                // Fallback به تنظیمات اصلی برنامه
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package",
                    getApplication<Application>().packageName, null)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
            }
        }
    }

    /**
     * بررسی آیا برنامه پیش‌فرض است یا همه مجوزهای ضروری داده شده‌اند
     */
    fun isSetupComplete(): Boolean {
        return _isDefaultSmsApp.value || checkAllRequiredPermissions()
    }

    /**
     * گرفتن لیست تمام موارد تنظیم نشده (مجوزها + برنامه پیش‌فرض)
     */
    fun getAllMissingSetupItems(): List<SetupItem> {
        val missingItems = mutableListOf<SetupItem>()

        // بررسی مجوزهای ضروری
        val missingPermissions = getMissingPermissions()
        missingPermissions.forEach { permission ->
            missingItems.add(SetupItem.Permission(permission, getPermissionDisplayName(permission)))
        }

        // بررسی برنامه پیش‌فرض
        if (!_isDefaultSmsApp.value) {
            missingItems.add(SetupItem.DefaultSmsApp)
        }

        return missingItems
    }
}

// ==================== مدل برای آیتم‌های تنظیم ====================
sealed class SetupItem {
    data class Permission(val permission: String, val displayName: String) : SetupItem()
    object DefaultSmsApp : SetupItem() {
        const val DISPLAY_NAME = "برنامه پیش‌فرض پیامک"
    }
}