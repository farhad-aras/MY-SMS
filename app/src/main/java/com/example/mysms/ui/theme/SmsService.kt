package com.example.mysms.ui.theme

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class SmsService : Service() {
    override fun onCreate() {
        super.onCreate()
        Log.d("SmsService", "✅ سرویس SMS ایجاد شد")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}