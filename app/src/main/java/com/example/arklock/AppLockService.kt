// AppLockService.kt
package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitoringJob: Job? = null
    private var lastForegroundApp = ""
    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArkLock:AppLockWakeLock")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startMonitoring()
    }

    override fun onDestroy() {
        stopMonitoring()
        wakeLock.release()
        super.onDestroy()
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, "arklock_channel")
            .setContentTitle("ArkLock is running")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun startMonitoring() {
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
        monitoringJob = scope.launch {
            while (true) {
                val currentApp = getForegroundApp()
                if (currentApp != lastForegroundApp) {
                    lastForegroundApp = currentApp
                    checkAndLockApp(currentApp)
                }
                delay(300) // Check every 300ms
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
    }

    @SuppressLint("ServiceCast")
    private fun getForegroundApp(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 1000 * 1000,
                now
            )
            stats?.filter { it.lastTimeUsed >= now - 1000 * 1000 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName ?: ""
        } else {
            // Fallback for older versions (less reliable)
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.getRunningTasks(1)
            tasks?.firstOrNull()?.topActivity?.packageName ?: ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return

        val sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lockedApps.contains(packageName)) {
            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra("package_name", packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }
}