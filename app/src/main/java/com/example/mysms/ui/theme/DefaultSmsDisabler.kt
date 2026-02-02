package com.example.mysms.ui.theme

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

object DefaultSmsDisabler {

    fun disableDefaultSmsNotifications(context: Context) {
        try {
            // روش ۱: برای اندروید ۱۰+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d("SmsDisabler", "✅ Requested SMS role")
                }
            }

            // روش ۲: تنظیم اپ به عنوان اپ پیش‌فرض SMS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val defaultApp = Telephony.Sms.getDefaultSmsPackage(context)
                val ourPackage = context.packageName

                if (defaultApp != ourPackage) {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ourPackage)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d("SmsDisabler", "📱 Requested to be default SMS app")
                } else {
                    Log.d("SmsDisabler", "✅ Already default SMS app")
                }
            }

        } catch (e: Exception) {
            Log.e("SmsDisabler", "❌ Error disabling default SMS: ${e.message}")
        }
    }

    fun hideDefaultNotifications(context: Context) {
        try {
            // غیرفعال کردن نوتیفیکیشن اپلیکیشن Messages (اگر نصب باشد)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            // لیست کانال‌های نوتیفیکیشن Messages را پیدا و غیرفعال کن
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.notificationChannels?.forEach { channel ->
                    if (channel.id.contains("sms") || channel.id.contains("message") ||
                        channel.id.contains("text") || channel.id.contains("mms")) {
                        channel.setImportance(android.app.NotificationManager.IMPORTANCE_NONE)
                        Log.d("SmsDisabler", "🔕 Disabled notification channel: ${channel.id}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SmsDisabler", "Error hiding notifications: ${e.message}")
        }
    }
}