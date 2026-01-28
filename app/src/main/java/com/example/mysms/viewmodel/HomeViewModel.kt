package com.example.mysms.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
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

    // لیست پیام‌ها از دیتابیس
    private val _smsList = MutableStateFlow<List<SmsEntity>>(emptyList())
    val smsList = _smsList.asStateFlow()

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

    // پیام‌های موقت (برای ارسال فوری)
    private val _tempMessages = MutableStateFlow<List<SmsEntity>>(emptyList())
    val tempMessages = _tempMessages.asStateFlow()

    // وضعیت ارسال هر مخاطب
    private val _sendingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sendingState = _sendingState.asStateFlow()

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

            // 3. مشاهده دیتابیس (بدون تست خودکار)
            viewModelScope.launch {
                delay(500)
                observeDatabase()
            }

            Log.d("HomeViewModel", "✅ ViewModel init completed")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "💥 Error in init: ${e.message}", e)
            _smsList.value = emptyList()
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

                    // دوباره چک کن
                    val newCount = db.smsDao().getAllSms().size
                    Log.d("HomeViewModel", "📊 New total: $newCount")
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

            // همچنین Flow رو refresh کن
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(getApplication())
                val all = db.smsDao().getAllSms()
                _smsList.value = all
                Log.d("HomeViewModel", "🔄 SMS list updated: ${all.size} items")
            }
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
    // Database
    // ---------------------------

    private fun observeDatabase() {
        viewModelScope.launch {
            repository.getAllSmsFlow().collect { list ->
                Log.d("HomeViewModel", "📊 Flow update: ${list.size} SMS")
                if (list.isNotEmpty()) {
                    Log.d("HomeViewModel", "📞 Sample: ${list.first().address} - ${list.first().body.take(30)}")
                } else {
                    Log.d("HomeViewModel", "📭 Flow returned empty list")
                }
                _smsList.value = list
            }
        }
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

                    // بعد از سینک، Flow رو refresh کن
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(getApplication())
                        val all = db.smsDao().getAllSms()
                        _smsList.value = all
                        Log.d("HomeViewModel", "🔄 Updated list after sync: ${all.size} items")
                    }
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
        return (db + temp).sortedBy { it.date }
    }

    fun markConversationAsRead(address: String) {
        viewModelScope.launch {
            repository.markAsRead(address)
        }
    }
}