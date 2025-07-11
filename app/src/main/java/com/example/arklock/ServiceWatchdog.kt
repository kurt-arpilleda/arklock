package com.example.arklock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ServiceWatchdog(private val context: Context) {
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startWatching() {
        watchdogJob = scope.launch {
            while (true) {
                try {
                    if (!isServiceRunning()) {
                        startAppLockService()
                    }
                    delay(10000) // Check every 10 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }

    fun stopWatching() {
        watchdogJob?.cancel()
    }

    private fun isServiceRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

        for (service in runningServices) {
            if (AppLockService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startAppLockService() {
        val serviceIntent = Intent(context, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}