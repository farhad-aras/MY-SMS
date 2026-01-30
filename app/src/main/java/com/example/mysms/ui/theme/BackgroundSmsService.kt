package com.example.mysms.ui.theme

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.example.mysms.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BackgroundSmsService : Service() {

    private var job: Job? = null
    private lateinit var viewModel: HomeViewModel

    override fun onCreate() {
        super.onCreate()
        Log.d("BackgroundService", "🔄 Background SMS Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BackgroundService", "🎯 Service started")

        // شروع کار تکراری
        job = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    Log.d("BackgroundService", "⏰ Checking for new SMS...")
                    // هر 30 ثانیه چک کن
                    delay(30000) // 30 ثانیه

                    // اینجا می‌توانید کوئری دیتابیس را چک کنید
                    // یا سینک انجام دهید

                } catch (e: Exception) {
                    Log.e("BackgroundService", "Error in background check", e)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        Log.d("BackgroundService", "🛑 Background SMS Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}