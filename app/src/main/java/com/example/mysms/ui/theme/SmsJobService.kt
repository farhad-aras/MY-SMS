package com.example.mysms.ui.theme

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SmsJobService : JobService() {

    companion object {
        private const val TAG = "SmsJobService"
        private const val JOB_ID = 1001

        fun scheduleJob(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler

                // اگر قبلاً schedule شده، cancel کن
                jobScheduler.cancel(JOB_ID)

                val builder = android.app.job.JobInfo.Builder(
                    JOB_ID,
                    android.content.ComponentName(context, SmsJobService::class.java)
                )
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(30 * 1000, android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setMinimumLatency(5000) // حداقل 5 ثانیه تاخیر

                // فقط اگر پرمیشن RECEIVE_BOOT_COMPLETED داریم، setPersisted رو اضافه کن
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setPeriodic(15 * 60 * 1000, 5 * 60 * 1000) // هر 15 دقیقه با flex 5 دقیقه
                } else {
                    builder.setPeriodic(15 * 60 * 1000) // هر 15 دقیقه
                }

                // فقط در اندروید ۲۴+ نیاز به چک پرمیشن داریم
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (context.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        builder.setPersisted(true) // بعد از reboot هم باقی بماند
                    } else {
                        Log.w(TAG, "⚠️ RECEIVE_BOOT_COMPLETED permission not granted, job won't persist after reboot")
                    }
                } else {
                    // برای اندروید قدیمی‌تر
                    builder.setPersisted(true)
                }

                val jobInfo = builder.build()

                val result = jobScheduler.schedule(jobInfo)

                if (result == android.app.job.JobScheduler.RESULT_SUCCESS) {
                    Log.d(TAG, "✅ Job scheduled successfully")
                } else {
                    Log.e(TAG, "❌ Failed to schedule job")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling job: ${e.message}")
            }
        }

        fun cancelJob(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
                jobScheduler.cancel(JOB_ID)
                Log.d(TAG, "Job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling job: ${e.message}")
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "🔄 Job started")

        // اجرا در background thread
        Thread {
            try {
                checkForNewMessages()

                // Job completed successfully
                jobFinished(params, false)
                Log.d(TAG, "✅ Job finished")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Job error: ${e.message}")
                jobFinished(params, true) // reschedule
            }
        }.start()

        return true // کار در background ادامه دارد
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "🛑 Job stopped")
        return true // reschedule job
    }

    private fun checkForNewMessages() {
        Log.d(TAG, "🔍 Checking for new messages...")

        // ساده‌ترین راه: BroadcastReceiver را trigger کنید
        try {
            val intent = Intent(this, SmsReceiver::class.java)
            intent.action = Telephony.Sms.Intents.SMS_RECEIVED_ACTION

            // اضافه کردن بعضی extras برای تست
            intent.putExtra("job_scheduled_check", true)
            intent.putExtra("check_time", System.currentTimeMillis())

            sendBroadcast(intent)
            Log.d(TAG, "📡 Broadcast sent to SmsReceiver")

        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast: ${e.message}")

            // راه جایگزین: مستقیماً SMS Provider را چک کن
            checkSmsProviderDirectly()
        }
    }

    private fun checkSmsProviderDirectly() {
        try {
            val cursor = contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC LIMIT 5"
            )

            cursor?.use {
                val count = it.count
                Log.d(TAG, "📊 Found $count messages in SMS Provider")

                if (it.moveToFirst()) {
                    val addressIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)

                    for (i in 0 until minOf(3, count)) {
                        if (addressIdx != -1 && bodyIdx != -1 && dateIdx != -1) {
                            val address = it.getString(addressIdx)
                            val body = it.getString(bodyIdx)
                            val date = it.getLong(dateIdx)

                            Log.d(TAG, "📱 Message $i: $address - ${body.take(20)} - ${android.text.format.DateFormat.format("HH:mm", date)}")

                            // اگر پیام جدید است (در ۱۰ دقیقه گذشته)
                            if (System.currentTimeMillis() - date < 10 * 60 * 1000) {
                                // می‌توانید اینجا نوتیفیکیشن بدهید
                                Log.d(TAG, "🆕 New message detected from $address")
                            }
                        }

                        if (!it.moveToNext()) break
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking SMS provider: ${e.message}")
        }
    }
}