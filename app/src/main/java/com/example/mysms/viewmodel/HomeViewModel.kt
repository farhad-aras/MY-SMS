package com.example.mysms.viewmodel


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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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


    // پیش‌نویس‌ها
    val drafts = mutableStateMapOf<String, String>()
    private val prefs =
        getApplication<Application>().getSharedPreferences("drafts_prefs", Context.MODE_PRIVATE)

    init {
        try {
            Log.d("HomeViewModel", "🟢 ViewModel init started")

            // 1. ابتدا شناسه سیم‌کارت‌ها
            refreshSimIds()

            // 2. بازیابی پیش‌نویس‌ها
            restoreDrafts()

            // 3. بارگذاری نام‌های ذخیره شده تب‌ها
            loadTabNames()

            // ==================== بارگذاری وضعیت expand/collapse تاریخ‌ها ====================
            // 4. بارگذاری وضعیت expand/collapse تاریخ‌ها
            loadDateExpansionState()


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

    fun startInitialSync() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "🔄 Starting initial sync")
            _isSyncing.value = true
            refreshSimIds()

            repository.syncSms().collect { progress ->
                _loadingProgress.value = progress
                if (progress >= 100) {
                    _isSyncing.value = false
                    Log.d("HomeViewModel", "✅ Initial sync completed")
                    // نیازی به آپدیت دستی نیست - flowها به‌طور خودکار آپدیت می‌شوند
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

    // تابع جدید برای mark single message
    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsDao.markSingleMessageAsRead(messageId)
                Log.d("HomeViewModel", "✅ Message $messageId marked as read")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error marking message as read", e)
            }
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
}