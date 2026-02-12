package com.example.mysms.manager

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UIPreferencesManager(
    private val application: Application,
    private val viewModelScope: CoroutineScope
) {

    // ==================== نام تب‌های سیم‌کارت ====================
    private val tabPrefs = application.getSharedPreferences("tab_names_prefs", Context.MODE_PRIVATE)

    private val _sim1TabName = MutableStateFlow("سیم ۱")
    val sim1TabName = _sim1TabName.asStateFlow()

    private val _sim2TabName = MutableStateFlow("سیم ۲")
    val sim2TabName = _sim2TabName.asStateFlow()

    // ==================== وضعیت expand/collapse تاریخ‌ها ====================
    private val dateExpansionPrefs = application.getSharedPreferences("date_expansion_state", Context.MODE_PRIVATE)

    private val _expandedDates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val expandedDates = _expandedDates.asStateFlow()

    // ==================== پیش‌نویس‌ها ====================
    private val draftPrefs = application.getSharedPreferences("drafts_prefs", Context.MODE_PRIVATE)
    val drafts = mutableStateMapOf<String, String>()

    init {
        loadTabNames()
        loadDateExpansionState()
        restoreDrafts()
    }

    // ==================== توابع مدیریت نام تب‌ها ====================

    private fun loadTabNames() {
        val sim1Name = tabPrefs.getString("sim1_tab_name", "سیم ۱") ?: "سیم ۱"
        val sim2Name = tabPrefs.getString("sim2_tab_name", "سیم ۲") ?: "سیم ۲"

        _sim1TabName.value = sim1Name
        _sim2TabName.value = sim2Name
        Log.d("UIPrefsManager", "📝 Loaded tab names: SIM1='$sim1Name', SIM2='$sim2Name'")
    }

    fun getSimDisplayName(tabIndex: Int): String {
        return when (tabIndex) {
            0 -> sim1TabName.value
            1 -> sim2TabName.value
            else -> "سیم‌کارت"
        }
    }

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

                Log.d("UIPrefsManager", "💾 Updated tab $tabIndex name to: '$finalName'")
            } catch (e: Exception) {
                Log.e("UIPrefsManager", "❌ Error updating tab name", e)
            }
        }
    }

    fun getCurrentTabName(tabIndex: Int): String {
        return when (tabIndex) {
            0 -> sim1TabName.value
            1 -> sim2TabName.value
            else -> "سیم‌کارت"
        }
    }

    // ==================== توابع مدیریت expand/collapse تاریخ‌ها ====================

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
                Log.d("UIPrefsManager", "📅 Loaded date expansion state: ${expansionMap.size} dates")
            } catch (e: Exception) {
                Log.e("UIPrefsManager", "❌ Error loading date expansion state", e)
            }
        }
    }

    fun isDateExpanded(dateKey: String): Boolean {
        return _expandedDates.value[dateKey] ?: false
    }

    fun toggleDateExpansion(dateKey: String, isExpanded: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newMap = _expandedDates.value.toMutableMap()
                newMap[dateKey] = isExpanded
                _expandedDates.value = newMap

                dateExpansionPrefs.edit().putBoolean(dateKey, isExpanded).apply()
                Log.d("UIPrefsManager", "💾 Date expansion state saved: $dateKey = $isExpanded")
            } catch (e: Exception) {
                Log.e("UIPrefsManager", "❌ Error saving date expansion state", e)
            }
        }
    }

    fun setDefaultExpansionState(dateKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dateKeys.isEmpty()) return@launch

                val newMap = mutableMapOf<String, Boolean>()
                dateKeys.forEach { dateKey ->
                    newMap[dateKey] = false
                }

                val lastDateKey = dateKeys.lastOrNull()
                if (lastDateKey != null) {
                    if (!_expandedDates.value.containsKey(lastDateKey)) {
                        newMap[lastDateKey] = true
                        dateExpansionPrefs.edit().putBoolean(lastDateKey, true).apply()
                    } else {
                        newMap[lastDateKey] = _expandedDates.value[lastDateKey] ?: false
                    }
                }

                _expandedDates.value = newMap
                Log.d("UIPrefsManager", "📅 Default expansion state set for ${dateKeys.size} dates")
            } catch (e: Exception) {
                Log.e("UIPrefsManager", "❌ Error setting default expansion state", e)
            }
        }
    }

    fun clearAllExpansionStates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dateExpansionPrefs.edit().clear().apply()
                _expandedDates.value = emptyMap()
                Log.d("UIPrefsManager", "🧹 All date expansion states cleared")
            } catch (e: Exception) {
                Log.e("UIPrefsManager", "❌ Error clearing expansion states", e)
            }
        }
    }

    // ==================== توابع مدیریت پیش‌نویس‌ها ====================

    private fun restoreDrafts() {
        draftPrefs.all.forEach { (key, value) ->
            if (value is String) {
                drafts[key] = value
            }
        }
        Log.d("UIPrefsManager", "📝 Restored ${drafts.size} drafts")
    }

    fun updateDraft(address: String, text: String) {
        drafts[address] = text
        draftPrefs.edit().putString(address, text).apply()
        Log.d("UIPrefsManager", "💾 Draft updated for $address")
    }

    fun removeDraft(address: String) {
        drafts.remove(address)
        draftPrefs.edit().remove(address).apply()
        Log.d("UIPrefsManager", "🗑️ Draft removed for $address")
    }

    fun getDraft(address: String): String? = drafts[address]
}