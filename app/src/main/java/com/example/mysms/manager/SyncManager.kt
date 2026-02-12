package com.example.mysms.manager

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.mysms.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncManager(
    private val application: Application,
    private val repository: SmsRepository,
    private val viewModelScope: CoroutineScope
) {

    private val syncPrefs = application.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    // Stateهای سینک
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _isSmartSyncing = MutableStateFlow(false)
    val isSmartSyncing = _isSmartSyncing.asStateFlow()

    private val _smartSyncProgress = MutableStateFlow(0)
    val smartSyncProgress = _smartSyncProgress.asStateFlow()

    private val _syncStats = MutableStateFlow<SyncStats>(SyncStats())
    val syncStats = _syncStats.asStateFlow()

    // ==================== کلاس‌های داده ====================
    data class SyncStats(
        val totalMessages: Int = 0,
        val newMessages: Int = 0,
        val syncDuration: Long = 0,
        val lastSyncTime: Long = 0,
        val syncMethod: String = "full"
    )

    data class SyncSettings(
        val incrementalSyncEnabled: Boolean = true,
        val backgroundSyncInterval: Long = 5 * 60 * 1000,
        val maxMessagesPerSync: Int = 100,
        val onlyUnread: Boolean = false,
        val autoSyncOnAppOpen: Boolean = true
    )

    sealed class SyncResult {
        data class Success(val stats: SyncStats) : SyncResult()
        data class PartialSuccess(val stats: SyncStats, val failedCount: Int) : SyncResult()
        data class Error(val message: String, val retryable: Boolean) : SyncResult()
        object NoNewMessages : SyncResult()
        object Skipped : SyncResult()
    }

    init {
        loadSyncState()
    }

    // ==================== توابع عمومی ====================

    fun startSmartSync(onFullSync: () -> Unit, onIncrementalSync: () -> Unit) {
        viewModelScope.launch {
            try {
                val lastSync = _lastSyncTime.value
                val now = System.currentTimeMillis()

                if (lastSync == 0L || (now - lastSync) > (60 * 60 * 1000)) {
                    Log.d("SyncManager", "⏰ Starting full sync")
                    onFullSync.invoke()
                } else {
                    Log.d("SyncManager", "⚡ Starting incremental sync")
                    onIncrementalSync.invoke()
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Error in smart sync decision", e)
                onFullSync.invoke()
            }
        }
    }

    suspend fun syncNewMessagesIncremental(lastSyncTime: Long): Int {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SyncManager", "🚀 Starting incremental sync since $lastSyncTime")
                val newCount = repository.syncNewMessages(lastSyncTime)

                // آپدیت زمان آخرین سینک
                val currentTime = System.currentTimeMillis()
                _lastSyncTime.value = currentTime
                syncPrefs.edit().putLong("last_sync_time", currentTime).apply()

                // آپدیت آمار
                updateSyncStats(newCount, "incremental")

                Log.d("SyncManager", "✅ Incremental sync completed: $newCount messages")
                newCount
            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Incremental sync failed", e)
                0
            }
        }
    }

    suspend fun refreshLastSyncTimeFromDb(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val lastSync = repository.getLastSyncTime() ?: 0L
                _lastSyncTime.value = lastSync
                Log.d("SyncManager", "🔄 Last sync time from DB: $lastSync")
                lastSync
            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Error getting last sync time from DB", e)
                0L
            }
        }
    }

    fun getSyncStatus(): String {
        val lastSync = _lastSyncTime.value
        val now = System.currentTimeMillis()

        return if (lastSync == 0L) {
            "⏳ اولین سینک انجام نشده"
        } else {
            val minutesAgo = (now - lastSync) / (60 * 1000)
            when {
                minutesAgo < 1 -> "✅ هم‌اکنون سینک شده"
                minutesAgo < 60 -> "✅ $minutesAgo دقیقه پیش"
                else -> "⚠️ ${minutesAgo / 60} ساعت پیش"
            }
        }
    }

    fun clearSyncCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncPrefs.edit().clear().apply()
                _lastSyncTime.value = 0L
                _syncStats.value = SyncStats()
                Log.d("SyncManager", "🧹 Sync cache cleared")
            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Error clearing sync cache", e)
            }
        }
    }

    // ==================== توابع خصوصی ====================

    private fun loadSyncState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastSync = syncPrefs.getLong("last_sync_time", 0L)
                _lastSyncTime.value = lastSync
                Log.d("SyncManager", "📊 Sync state loaded: lastSync=${lastSync}")
            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Error loading sync state", e)
            }
        }
    }

    private fun updateSyncStats(newMessages: Int, method: String) {
        viewModelScope.launch {
            _syncStats.value = SyncStats(
                totalMessages = 0,
                newMessages = newMessages,
                syncDuration = 0,
                lastSyncTime = System.currentTimeMillis(),
                syncMethod = method
            )
        }
    }

}