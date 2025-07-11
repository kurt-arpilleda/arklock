// AppLockService.kt
package com.example.arklock

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ArkLockService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var lastCheckedTime = System.currentTimeMillis()

    companion object {
        private const val CHECK_INTERVAL = 500L // Check every 500ms
        private var currentForegroundApp: String? = null
        private val unlockedApps = mutableSetOf<String>()

        fun isAppUnlocked(packageName: String): Boolean {
            return unlockedApps.contains(packageName)
        }

        fun unlockApp(packageName: String) {
            unlockedApps.add(packageName)
        }

        fun lockApp(packageName: String) {
            unlockedApps.remove(packageName)
        }

        fun lockAllApps() {
            unlockedApps.clear()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        serviceScope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                    delay(CHECK_INTERVAL)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(lastCheckedTime, currentTime)
        val event = UsageEvents.Event()

        var latestEvent: UsageEvents.Event? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestEvent = UsageEvents.Event(event)
            }
        }

        latestEvent?.let { event ->
            val packageName = event.packageName

            // Skip if it's our own app
            if (packageName == this.packageName) {
                currentForegroundApp = packageName
                lastCheckedTime = currentTime
                return
            }

            // Check if app is different from current foreground app
            if (packageName != currentForegroundApp) {
                currentForegroundApp = packageName

                // Check if this app is locked
                if (isAppLocked(packageName) && !isAppUnlocked(packageName)) {
                    handler.post {
                        showLockScreen(packageName)
                    }
                }
            }
        }

        lastCheckedTime = currentTime
    }

    private fun isAppLocked(packageName: String): Boolean {
        val sharedPref = getSharedPreferences("arklock_locked_apps", Context.MODE_PRIVATE)
        return sharedPref.getBoolean(packageName, false)
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra("locked_app_package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
    }
}