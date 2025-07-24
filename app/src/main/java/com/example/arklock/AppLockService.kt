package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppLockService : Service() {
    private var lastForegroundApp = ""
    private lateinit var sharedPref: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isServiceActive = false
    private var scheduledExecutor: ScheduledExecutorService? = null
    private var currentTask: ScheduledFuture<*>? = null
    private val isChecking = AtomicBoolean(false)
    private var isScreenOn = true
    private var currentInterval = CHECK_INTERVAL_NORMAL

    companion object {
        private const val CHANNEL_ID = "app_lock_channel"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_FAST = 800L
        private const val CHECK_INTERVAL_NORMAL = 2000L
        private const val CHECK_INTERVAL_SLOW = 8000L
        private const val CHECK_INTERVAL_SCREEN_OFF = 15000L

        const val ACTION_FORCE_CHECK = "com.example.arklock.FORCE_CHECK"
        const val ACTION_BOOT_COMPLETED = "com.example.arklock.BOOT_COMPLETED"
        const val ACTION_APP_UNLOCKED = "com.example.arklock.APP_UNLOCKED"

        fun startService(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        isServiceActive = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        createNotificationChannel()
        startForeground()
        registerReceivers()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceActive) {
            isServiceActive = true
            startMonitoring()
        }

        when (intent?.action) {
            ACTION_FORCE_CHECK -> performAppCheck()
            ACTION_BOOT_COMPLETED -> handler.postDelayed({ performAppCheck() }, 3000)
            ACTION_APP_UNLOCKED -> {
                val packageName = intent.getStringExtra("package_name") ?: ""
                if (packageName.isNotEmpty()) {
                    lastForegroundApp = packageName
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background service for app lock"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ArkLock Protection Active")
            .setContentText("Monitoring locked apps")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(ACTION_FORCE_CHECK)
            addAction(ACTION_APP_UNLOCKED)
            addAction(Intent.ACTION_BOOT_COMPLETED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (!isServiceActive) {
                        isServiceActive = true
                        startMonitoring()
                    } else {
                        adjustMonitoringInterval(CHECK_INTERVAL_FAST)
                    }
                    performAppCheck()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    adjustMonitoringInterval(CHECK_INTERVAL_SCREEN_OFF)
                }
                Intent.ACTION_USER_PRESENT -> {
                    performAppCheck()
                    adjustMonitoringInterval(CHECK_INTERVAL_FAST)
                }
                ACTION_FORCE_CHECK -> performAppCheck()
                ACTION_APP_UNLOCKED -> {
                    val packageName = intent.getStringExtra("package_name") ?: ""
                    if (packageName.isNotEmpty()) {
                        lastForegroundApp = packageName
                        adjustMonitoringInterval(CHECK_INTERVAL_SLOW)
                    }
                }
                Intent.ACTION_BOOT_COMPLETED -> {
                    if (!isServiceActive) {
                        isServiceActive = true
                        startMonitoring()
                    }
                    handler.postDelayed({ performAppCheck() }, 3000)
                }
            }
        }
    }

    private fun startMonitoring() {
        if (!isServiceActive) return
        scheduledExecutor?.shutdown()
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        currentInterval = if (isScreenOn) CHECK_INTERVAL_NORMAL else CHECK_INTERVAL_SCREEN_OFF
        scheduleNextCheck()
    }

    private fun scheduleNextCheck() {
        currentTask?.cancel(false)
        currentTask = scheduledExecutor?.schedule({
            if (isServiceActive && !isChecking.get()) {
                performAppCheck()
                scheduleNextCheck()
            }
        }, currentInterval, TimeUnit.MILLISECONDS)
    }

    private fun adjustMonitoringInterval(newInterval: Long) {
        if (currentInterval != newInterval) {
            currentInterval = newInterval
            scheduleNextCheck()
        }
    }

    private fun performAppCheck() {
        if (!isChecking.compareAndSet(false, true)) return
        try {
            val currentApp = getForegroundApp()
            if (currentApp != lastForegroundApp && currentApp.isNotEmpty()) {
                if (lastForegroundApp.isNotEmpty() && lastForegroundApp != currentApp) {
                    resetAppUnlockStatus(lastForegroundApp)
                }
                lastForegroundApp = currentApp
                handler.post { checkAndLockApp(currentApp) }

                if (isScreenOn) {
                    adjustMonitoringInterval(CHECK_INTERVAL_NORMAL)
                }
            }
        } catch (e: Exception) {
        } finally {
            isChecking.set(false)
        }
    }

    @SuppressLint("ServiceCast")
    private fun getForegroundApp(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 5000,
                    now
                )
                val foregroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
                if (foregroundApp.isNotEmpty()) return foregroundApp
            }

            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = am.runningAppProcesses
            if (!runningApps.isNullOrEmpty()) {
                for (processInfo in runningApps) {
                    if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return processInfo.processName
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()
        if (lockedApps.contains(packageName)) {
            val isUnlocked = sharedPref.getBoolean("unlocked_$packageName", false)
            if (!isUnlocked) {
                val intent = Intent(this, LockActivity::class.java).apply {
                    putExtra("package_name", packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_HISTORY or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                startActivity(intent)
            }
        }
    }

    private fun resetAppUnlockStatus(packageName: String) {
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()
        if (lockedApps.contains(packageName)) {
            sharedPref.edit().putBoolean("unlocked_$packageName", false).apply()
        }
    }

    private fun stopMonitoring() {
        isServiceActive = false
        currentTask?.cancel(false)
        scheduledExecutor?.shutdown()
        scheduledExecutor = null
    }

    override fun onDestroy() {
        stopMonitoring()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        super.onDestroy()

        handler.postDelayed({
            val intent = Intent(this, AppLockService::class.java)
            startService(intent)
        }, 2000)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val intent = Intent(this, AppLockService::class.java)
        startService(intent)
    }
}