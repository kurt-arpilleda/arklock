package com.example.arklock

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class ArkLockService : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var lastPackage: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coroutineScope.launch {
            while (isActive) {
                checkForegroundApp()
                delay(1000)
            }
        }
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 5000

        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()

        var lastUsedPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastUsedPackage = event.packageName
            }
        }

        val sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lastUsedPackage != null &&
            lastUsedPackage != packageName &&
            lastUsedPackage != lastPackage &&
            lockedApps.contains(lastUsedPackage)
        ) {
            lastPackage = lastUsedPackage
            val lockIntent = Intent(this, OverlayLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("locked_package", lastUsedPackage)
            }
            startActivity(lockIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
