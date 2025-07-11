package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private var isUnlockedTemporarily = false
    private var tempUnlockedPackage = ""
    private lateinit var sharedPref: SharedPreferences
    private var lastCheckTime = 0L

    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArkLock:AppLockWakeLock")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        startForeground()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always restart if killed
        if (monitoringJob?.isActive != true) {
            startMonitoring()
        }
        return START_STICKY // Critical: Auto-restart if killed
    }

    override fun onDestroy() {
        stopMonitoring()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Restart service
        val restartIntent = Intent(this, AppLockService::class.java)
        startService(restartIntent)

        super.onDestroy()
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, "arklock_channel")
            .setContentTitle("ArkLock is running")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Make it persistent
            .setAutoCancel(false)
            .build()

        startForeground(1, notification)
    }

    private fun startMonitoring() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(60 * 60 * 1000L) // 1 hour, will be renewed
        }

        monitoringJob = scope.launch {
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()

                    // Renew wakelock every 30 minutes
                    if (currentTime - lastCheckTime > 30 * 60 * 1000L) {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                        wakeLock.acquire(60 * 60 * 1000L)
                        lastCheckTime = currentTime
                    }

                    val currentApp = getForegroundApp()
                    if (currentApp != lastForegroundApp && currentApp.isNotEmpty()) {
                        lastForegroundApp = currentApp
                        checkAndLockApp(currentApp)
                    }

                    delay(500) // Slightly longer interval for better performance
                } catch (e: Exception) {
                    // Log error but continue monitoring
                    e.printStackTrace()
                    delay(1000)
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
    }

    @SuppressLint("ServiceCast")
    private fun getForegroundApp(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 2000, // Reduced time window
                    now
                )
                stats?.filter { it.lastTimeUsed >= now - 2000 }
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName ?: ""
            } else {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = am.getRunningTasks(1)
                tasks?.firstOrNull()?.topActivity?.packageName ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return

        // Check if this app was recently unlocked
        if (isUnlockedTemporarily && tempUnlockedPackage == packageName) {
            return
        }

        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lockedApps.contains(packageName)) {
            // Check if this is a temporary unlock
            if (sharedPref.getBoolean("temp_unlock_$packageName", false)) {
                sharedPref.edit().remove("temp_unlock_$packageName").apply()
                tempUnlockedPackage = packageName
                isUnlockedTemporarily = true
                return
            }

            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra("package_name", packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            startActivity(intent)
        } else {
            // Reset temporary unlock if switching to a different app
            if (tempUnlockedPackage.isNotEmpty() && tempUnlockedPackage != packageName) {
                isUnlockedTemporarily = false
                tempUnlockedPackage = ""
            }
        }
    }
}