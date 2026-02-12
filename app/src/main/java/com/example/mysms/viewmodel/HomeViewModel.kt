package com.example.mysms.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mysms.data.AppDatabase
import com.example.mysms.data.SmsDao
import com.example.mysms.data.SmsEntity
import com.example.mysms.manager.MultipartManager
import com.example.mysms.manager.OnboardingManager
import com.example.mysms.manager.SyncManager
import com.example.mysms.manager.UIPreferencesManager
import com.example.mysms.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.firstOrNull

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val smsDao: SmsDao = AppDatabase.getDatabase(application).smsDao()
    private val repository = SmsRepository(application, smsDao)

    // ==================== Managers ====================
    val syncManager = SyncManager(application, repository, viewModelScope)
    val multipartManager = MultipartManager(smsDao, viewModelScope)
    val onboardingManager = OnboardingManager(application, viewModelScope)
    val uiPrefsManager = UIPreferencesManager(application, viewModelScope)

    // ==================== Stateهای اصلی ====================
    private val _smsList = MutableStateFlow<List<SmsEntity>>(emptyList())
    val smsList = _smsList.asStateFlow()

    private val _conversations = MutableStateFlow<List<SmsEntity>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _tempMessages = MutableStateFlow<List<SmsEntity>>(emptyList())
    val tempMessages = _tempMessages.asStateFlow()

    private val _sendingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sendingState = _sendingState.asStateFlow()

    // ==================== Stateهای سیم‌کارت ====================
    private val _sim1Id = MutableStateFlow<Int?>(null)
    val sim1Id = _sim1Id.asStateFlow()

    private val _sim2Id = MutableStateFlow<Int?>(null)
    val sim2Id = _sim2Id.asStateFlow()

    // ==================== Stateهای سینک ====================
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress = _loadingProgress.asStateFlow()

    // ==================== Stateهای Onboarding ====================
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _permissionsState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsState = _permissionsState.asStateFlow()

    private val _onboardingStep = MutableStateFlow(0)
    val onboardingStep = _onboardingStep.asStateFlow()

    // ==================== Stateهای برنامه پیش‌فرض ====================
    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp = _isDefaultSmsApp.asStateFlow()

    init {
        Log.d("HomeViewModel", "🟢 ViewModel init started")

        refreshSimIds()

        // بارگذاری وضعیت Onboarding از SharedPreferences
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>()
                .getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            val isCompleted = prefs.getBoolean("onboarding_completed", false)
            _onboardingCompleted.value = isCompleted
            Log.d("HomeViewModel", "📋 Onboarding status: $isCompleted")
        }

        // بررسی وضعیت برنامه پیش‌فرض
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isDefault = getApplication<Application>().packageName ==
                        Telephony.Sms.getDefaultSmsPackage(getApplication())
                _isDefaultSmsApp.value = isDefault
                Log.d("HomeViewModel", "📱 Default SMS App status: $isDefault")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error checking default SMS app", e)
                _isDefaultSmsApp.value = false
            }
        }

        viewModelScope.launch {
            launch { observeAllSms() }
            launch { observeConversations() }
        }

        // شروع پاکسازی دوره‌ای
        startPeriodicCleanup()

        Log.d("HomeViewModel", "✅ ViewModel init completed")
    }

    // ==================== مشاهده دیتابیس ====================
    private fun observeAllSms() {
        viewModelScope.launch {
            repository.getAllSmsFlow().collect { list ->
                Log.d("HomeViewModel", "📊 All SMS Flow update: ${list.size} SMS")
                _smsList.value = list
            }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            repository.getConversationsFlow().collect { list ->
                Log.d("HomeViewModel", "📞 Conversations Flow update: ${list.size} conversations")
                _conversations.value = list
            }
        }
    }

    // ==================== SIM ====================
    fun refreshSimIds() {
        val ids = repository.getSimIds()
        _sim1Id.value = ids.first
        _sim2Id.value = ids.second
        Log.d("HomeViewModel", "📱 SIM IDs: SIM1=${ids.first}, SIM2=${ids.second}")
    }

    // ==================== ارسال پیام ====================
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
                    _tempMessages.value = _tempMessages.value.filterNot { it.id == tempSms.id }
                    uiPrefsManager.removeDraft(address)
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

    // ==================== دریافت پیام‌های ترکیب شده ====================
    suspend fun getCombinedMessages(address: String): List<SmsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val db = smsDao.getSmsByAddressFlow(address).firstOrNull() ?: emptyList()
                val temp = _tempMessages.value.filter { it.address == address }
                (db + temp).sortedByDescending { it.date }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting combined messages", e)
                emptyList()
            }
        }
    }

    fun getMessagesByAddressFlow(address: String): Flow<List<SmsEntity>> =
        repository.getSmsByAddressFlow(address)

    // ==================== Mark as Read ====================
    fun markConversationAsRead(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsDao.markAsRead(address)
                Log.d("HomeViewModel", "✅ Conversation with $address marked as read")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error marking conversation as read", e)
            }
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsDao.markMessageAsRead(messageId)
                Log.d("HomeViewModel", "✅ Message $messageId marked as read")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error marking message as read", e)
            }
        }
    }
    // ==================== پاکسازی پیام‌ها ====================
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

                // رفرش لیست - اگر تابع refreshSmsList را نداریم،直接从 دیتابیس بگیریم
                viewModelScope.launch {
                    val updatedList = smsDao.getAllSms()
                    _smsList.value = updatedList

                    val updatedConversations = smsDao.getConversationsFlow().firstOrNull() ?: emptyList()
                    _conversations.value = updatedConversations
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error soft deleting message", e)
            }
        }
    }

    /**
     * دریافت آمار دیتابیس
     */
    suspend fun getDatabaseStatistics(): com.example.mysms.data.DatabaseStats {
        return withContext(Dispatchers.IO) {
            try {
                smsDao.getDatabaseStats()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error getting database stats", e)
                com.example.mysms.data.DatabaseStats(0,0,0,0)
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

                    // 2. دریافت آمار برای گزارش
                    val stats = getDatabaseStatistics()
                    Log.d("HomeViewModel", "📊 Database stats: total=${stats.total}, pendingSync=${stats.pendingSync}, incompleteMultipart=${stats.incompleteMultipart}, deleted=${stats.deleted}")

                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ Error in periodic cleanup", e)
                    delay(60 * 60 * 1000) // در صورت خطا، 1 ساعت صبر کن
                }
            }
        }
    }


    // ==================== سینک ====================
    fun startInitialSync(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                if (_isSyncing.value && !forceRefresh) {
                    Log.d("HomeViewModel", "⏸️ Sync already in progress, skipping")
                    return@launch
                }

                Log.d("HomeViewModel", "🔄 Starting initial sync")
                val startTime = System.currentTimeMillis()
                _isSyncing.value = true
                _loadingProgress.value = 0

                refreshSimIds()

                withTimeout(30_000) {
                    repository.syncSms().collect { progress ->
                        _loadingProgress.value = progress
                        Log.d("HomeViewModel", "📊 Sync progress: $progress%")

                        if (progress >= 100) {
                            _isSyncing.value = false
                            Log.d("HomeViewModel", "✅ Initial sync completed successfully")

                            // ترکیب پیام‌های چندبخشی بعد از سینک
                            multipartManager.combineAfterSync()

                            val syncDuration = System.currentTimeMillis() - startTime
                            Log.d("HomeViewModel", "💾 Full sync completed in ${syncDuration}ms")
                        }
                    }
                }

            } catch (e: TimeoutCancellationException) {
                Log.e("HomeViewModel", "⏰ Sync timeout after 30 seconds")
                _isSyncing.value = false
                _loadingProgress.value = 0
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Sync failed: ${e.message}", e)
                _isSyncing.value = false
                _loadingProgress.value = 0
            } finally {
                if (_isSyncing.value) {
                    _isSyncing.value = false
                }
            }
        }
    }

    // ==================== توابع Onboarding ====================
    fun completeOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = getApplication<Application>()
                    .getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("onboarding_completed", true).apply()
                _onboardingCompleted.value = true
                Log.d("HomeViewModel", "✅ Onboarding completed")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Error completing onboarding", e)
            }
        }
    }

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
                Log.e("HomeViewModel", "❌ Error resetting onboarding", e)
            }
        }
    }

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

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

    fun isSetupComplete(): Boolean {
        return _isDefaultSmsApp.value || checkAllRequiredPermissions()
    }

    fun getAllMissingSetupItems(): List<SetupItem> {
        val missingItems = mutableListOf<SetupItem>()
        val missingPermissions = getMissingPermissions()
        missingPermissions.forEach { permission ->
            missingItems.add(SetupItem.Permission(permission, getPermissionDisplayName(permission)))
        }
        if (!_isDefaultSmsApp.value) {
            missingItems.add(SetupItem.DefaultSmsApp)
        }
        return missingItems
    }

    fun openDefaultSmsAppSettings() {
        try {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                getApplication<Application>().packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
            Log.d("HomeViewModel", "⚙️ Opening default SMS app settings")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "❌ Error opening SMS settings", e)
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package",
                getApplication<Application>().packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }

    // ==================== توابع کمکی ====================
    fun getContactName(phoneNumber: String): Pair<String, String?> {
        return repository.getContactName(phoneNumber)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("HomeViewModel", "🧹 ViewModel cleared")
    }
}

// ==================== مدل برای آیتم‌های تنظیم ====================
sealed class SetupItem {
    data class Permission(val permission: String, val displayName: String) : SetupItem()
    object DefaultSmsApp : SetupItem() {
        const val DISPLAY_NAME = "برنامه پیش‌فرض پیامک"
    }
}