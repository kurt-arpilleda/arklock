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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AppLockService : Service() {
    private var lastForegroundApp = ""
    private lateinit var sharedPref: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isServiceActive = false
    private var timer: Timer? = null
    private var scheduledExecutor: ScheduledExecutorService? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "app_lock_channel"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_FAST = 300L // 300ms for very fast response
        private const val CHECK_INTERVAL_NORMAL = 1000L // 1 second
        private const val CHECK_INTERVAL_SCREEN_OFF = 5000L // 5 seconds when screen is off
        private const val TAG = "AppLockService"

        const val ACTION_FORCE_CHECK = "com.example.arklock.FORCE_CHECK"
        const val ACTION_BOOT_COMPLETED = "com.example.arklock.BOOT_COMPLETED"

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

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        isServiceActive = true

        // Acquire partial wake lock to prevent service from being killed
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ArkLock:ServiceWakeLock"
        )
        wakeLock?.acquire()

        createNotificationChannel()
        startForeground()
        registerReceivers()
        startMultipleMonitoringMethods()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        if (!isServiceActive) {
            isServiceActive = true
            startMultipleMonitoringMethods()
        }

        when (intent?.action) {
            ACTION_FORCE_CHECK -> {
                Log.d(TAG, "Force checking current app")
                performAppCheck()
            }
            ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed received")
                handler.postDelayed({ performAppCheck() }, 2000)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Broadcast received: ${intent.action}")
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned on - starting fast monitoring")
                    if (!isServiceActive) {
                        isServiceActive = true
                        startMultipleMonitoringMethods()
                    }
                    switchToFastMonitoring()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned off - switching to slow monitoring")
                    switchToSlowMonitoring()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User present - immediate check")
                    performAppCheck()
                    switchToFastMonitoring()
                }
                ACTION_FORCE_CHECK -> {
                    performAppCheck()
                }
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    Log.d(TAG, "Boot completed")
                    if (!isServiceActive) {
                        isServiceActive = true
                        startMultipleMonitoringMethods()
                    }
                    handler.postDelayed({ performAppCheck() }, 2000)
                }
            }
        }
    }

    private fun startMultipleMonitoringMethods() {
        if (!isServiceActive) return

        Log.d(TAG, "Starting multiple monitoring methods")

        // Method 1: Timer-based monitoring (most reliable)
        startTimerMonitoring()

        // Method 2: ScheduledExecutor monitoring (backup)
        startScheduledExecutorMonitoring()

        // Method 3: Handler-based monitoring (additional backup)
        startHandlerMonitoring()

        // Initial check
        performAppCheck()
    }

    private fun startTimerMonitoring() {
        timer?.cancel()
        timer = Timer("AppLockTimer", true)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val interval = if (powerManager.isInteractive) CHECK_INTERVAL_FAST else CHECK_INTERVAL_SCREEN_OFF

        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isServiceActive) {
                    performAppCheck()
                }
            }
        }, 0, interval)
    }

    private fun startScheduledExecutorMonitoring() {
        scheduledExecutor?.shutdown()
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val interval = if (powerManager.isInteractive) CHECK_INTERVAL_FAST else CHECK_INTERVAL_SCREEN_OFF

        scheduledExecutor?.scheduleAtFixedRate({
            if (isServiceActive) {
                performAppCheck()
            }
        }, 0, interval, TimeUnit.MILLISECONDS)
    }

    private fun startHandlerMonitoring() {
        val runnable = object : Runnable {
            override fun run() {
                if (isServiceActive) {
                    performAppCheck()

                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val interval = if (powerManager.isInteractive) CHECK_INTERVAL_NORMAL else CHECK_INTERVAL_SCREEN_OFF

                    handler.postDelayed(this, interval)
                }
            }
        }
        handler.post(runnable)
    }

    private fun switchToFastMonitoring() {
        Log.d(TAG, "Switching to fast monitoring")

        // Restart timer with fast interval
        timer?.cancel()
        timer = Timer("AppLockTimerFast", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isServiceActive) {
                    performAppCheck()
                }
            }
        }, 0, CHECK_INTERVAL_FAST)

        // Restart scheduled executor with fast interval
        scheduledExecutor?.shutdown()
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor?.scheduleAtFixedRate({
            if (isServiceActive) {
                performAppCheck()
            }
        }, 0, CHECK_INTERVAL_FAST, TimeUnit.MILLISECONDS)
    }

    private fun switchToSlowMonitoring() {
        Log.d(TAG, "Switching to slow monitoring")

        // Restart timer with slow interval
        timer?.cancel()
        timer = Timer("AppLockTimerSlow", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isServiceActive) {
                    performAppCheck()
                }
            }
        }, 0, CHECK_INTERVAL_SCREEN_OFF)

        // Restart scheduled executor with slow interval
        scheduledExecutor?.shutdown()
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor?.scheduleAtFixedRate({
            if (isServiceActive) {
                performAppCheck()
            }
        }, 0, CHECK_INTERVAL_SCREEN_OFF, TimeUnit.MILLISECONDS)
    }

    private fun performAppCheck() {
        try {
            val currentApp = getForegroundApp()
            if (currentApp != lastForegroundApp && currentApp.isNotEmpty()) {
                Log.d(TAG, "Foreground app changed from '$lastForegroundApp' to '$currentApp'")

                // Reset previous app unlock status
                if (lastForegroundApp.isNotEmpty()) {
                    resetAppUnlockStatus(lastForegroundApp)
                }

                lastForegroundApp = currentApp

                // Check immediately without any delay
                handler.post {
                    checkAndLockApp(currentApp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in performAppCheck", e)
        }
    }

    @SuppressLint("ServiceCast")
    private fun getForegroundApp(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Method 1: Usage Stats (most reliable for newer versions)
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 1000 * 10, // 10 seconds window
                    now
                )
                val foregroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""

                if (foregroundApp.isNotEmpty()) {
                    return foregroundApp
                }
            }

            // Method 2: Recent Tasks (fallback)
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val recentTasks = am.getRecentTasks(1, ActivityManager.RECENT_WITH_EXCLUDED)
            if (recentTasks.isNotEmpty()) {
                val topTask = recentTasks[0]
                val topApp = topTask.topActivity?.packageName
                if (!topApp.isNullOrEmpty()) {
                    return topApp
                }
            }

            // Method 3: Running App Processes (additional fallback)
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
            Log.e(TAG, "Error getting foreground app", e)
            ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return

        Log.d(TAG, "Checking app: $packageName")
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lockedApps.contains(packageName)) {
            val isUnlocked = sharedPref.getBoolean("unlocked_$packageName", false)
            Log.d(TAG, "App $packageName is locked. Currently unlocked: $isUnlocked")

            if (!isUnlocked) {
                Log.d(TAG, "Immediately locking app: $packageName")

                // Show lock screen immediately
                val intent = Intent(this, LockActivity::class.java).apply {
                    putExtra("package_name", packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_HISTORY or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                startActivity(intent)

                // Additional step: Try to bring our app to foreground
                handler.postDelayed({
                    val bringToFrontIntent = Intent(this, LockActivity::class.java).apply {
                        putExtra("package_name", packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(bringToFrontIntent)
                }, 100)
            }
        }
    }

    private fun resetAppUnlockStatus(packageName: String) {
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()
        if (lockedApps.contains(packageName)) {
            sharedPref.edit().putBoolean("unlocked_$packageName", false).apply()
            Log.d(TAG, "Reset unlock status for $packageName")
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring")
        isServiceActive = false

        timer?.cancel()
        timer = null

        scheduledExecutor?.shutdown()
        scheduledExecutor = null

        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopMonitoring()

        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        super.onDestroy()

        // Restart service if it was killed
        val intent = Intent(this, AppLockService::class.java)
        startService(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Restart service when task is removed
        val intent = Intent(this, AppLockService::class.java)
        startService(intent)
    }
}