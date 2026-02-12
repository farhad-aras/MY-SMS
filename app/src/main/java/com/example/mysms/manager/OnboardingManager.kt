package com.example.mysms.manager

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingManager(
    private val application: Application,
    private val viewModelScope: CoroutineScope
) {

    private val prefs = application.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    // Stateهای Onboarding
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _permissionsState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsState = _permissionsState.asStateFlow()

    private val _onboardingStep = MutableStateFlow(0)
    val onboardingStep = _onboardingStep.asStateFlow()

    // State برنامه پیش‌فرض
    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp = _isDefaultSmsApp.asStateFlow()

    init {
        checkOnboardingStatus()
        checkDefaultSmsAppStatus()
    }

    // ==================== توابع Onboarding ====================

    /**
     * بررسی وضعیت Onboarding
     */
    private fun checkOnboardingStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isCompleted = prefs.getBoolean("onboarding_completed", false)
                _onboardingCompleted.value = isCompleted
                Log.d("OnboardingManager", "📋 Onboarding status: $isCompleted")
            } catch (e: Exception) {
                Log.e("OnboardingManager", "❌ Error checking onboarding status", e)
            }
        }
    }

    /**
     * تکمیل Onboarding
     */
    fun completeOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                prefs.edit().putBoolean("onboarding_completed", true).apply()
                _onboardingCompleted.value = true
                Log.d("OnboardingManager", "✅ Onboarding completed")
            } catch (e: Exception) {
                Log.e("OnboardingManager", "❌ Error completing onboarding", e)
            }
        }
    }

    /**
     * ریست کردن Onboarding
     */
    fun resetOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                prefs.edit().putBoolean("onboarding_completed", false).apply()
                _onboardingCompleted.value = false
                _onboardingStep.value = 0
                Log.d("OnboardingManager", "🔄 Onboarding reset")
            } catch (e: Exception) {
                Log.e("OnboardingManager", "❌ Error resetting onboarding", e)
            }
        }
    }

    /**
     * تنظیم مرحله Onboarding
     */
    fun setOnboardingStep(step: Int) {
        _onboardingStep.value = step
    }

    // ==================== توابع مجوزها ====================

    /**
     * بررسی وضعیت یک مجوز خاص
     */
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            application,
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

    /**
     * بررسی آیا برنامه پیش‌فرض است یا همه مجوزهای ضروری داده شده‌اند
     */
    fun isSetupComplete(): Boolean {
        return _isDefaultSmsApp.value || checkAllRequiredPermissions()
    }

    /**
     * گرفتن لیست تمام موارد تنظیم نشده
     */
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

    // ==================== توابع برنامه پیش‌فرض ====================

    /**
     * بررسی آیا برنامه به عنوان برنامه پیش‌فرض پیامک تنظیم شده است
     */
    fun checkDefaultSmsAppStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isDefault = application.packageName ==
                        Telephony.Sms.getDefaultSmsPackage(application)
                _isDefaultSmsApp.value = isDefault
                Log.d("OnboardingManager", "📱 Default SMS App status: $isDefault")
            } catch (e: Exception) {
                Log.e("OnboardingManager", "❌ Error checking default SMS app", e)
                _isDefaultSmsApp.value = false
            }
        }
    }

    /**
     * باز کردن صفحه تنظیمات برای انتخاب برنامه پیش‌فرض
     */
    fun openDefaultSmsAppSettings() {
        try {
            val intent = android.content.Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, application.packageName)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
            Log.d("OnboardingManager", "⚙️ Opening default SMS app settings")
        } catch (e: Exception) {
            Log.e("OnboardingManager", "❌ Error opening SMS settings", e)
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", application.packageName, null)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
        }
    }
}

// ==================== مدل برای آیتم‌های تنظیم ====================
sealed class SetupItem {
    data class Permission(val permission: String, val displayName: String) : SetupItem()
    object DefaultSmsApp : SetupItem() {
        const val DISPLAY_NAME = "برنامه پیش‌فرض پیامک"
    }
}