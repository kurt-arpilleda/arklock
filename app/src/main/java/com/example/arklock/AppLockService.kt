package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
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
    private lateinit var sharedPref: SharedPreferences
    private lateinit var alarmManager: AlarmManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var isMonitoringActive = false

    companion object {
        private const val CHANNEL_ID = "app_lock_channel"
        private const val NOTIFICATION_ID = 2
        private const val ALARM_INTERVAL = 15 * 60 * 1000L // 15 minutes
        private const val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val ALARM_REQUEST_CODE = 1001
        private const val HEARTBEAT_REQUEST_CODE = 1002
        private const val BOOT_CHECK_DELAY = 30 * 1000L // 30 seconds after boot

        const val ACTION_ALARM_TRIGGER = "com.example.arklock.ALARM_TRIGGER"
        const val ACTION_HEARTBEAT = "com.example.arklock.HEARTBEAT"
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

    override fun onCreate() {
        super.onCreate()
        Log.d("AppLockService", "Service created")
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        createNotificationChannel()
        startForeground()
        registerReceivers()
        scheduleNextAlarm()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AppLockService", "Service started with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_FORCE_CHECK -> {
                Log.d("AppLockService", "Force checking current app")
                forceCheckCurrentApp()
            }
            ACTION_BOOT_COMPLETED -> {
                Log.d("AppLockService", "Boot completed received")
                // Delay initial check after boot to allow system to stabilize
                scope.launch {
                    delay(BOOT_CHECK_DELAY)
                    forceCheckCurrentApp()
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for app lock"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
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
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(ACTION_ALARM_TRIGGER)
            addAction(ACTION_HEARTBEAT)
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
            Log.d("AppLockService", "Broadcast received: ${intent.action}")
            when (intent.action) {
                ACTION_ALARM_TRIGGER -> {
                    if (!isMonitoringActive) startMonitoring()
                    scheduleNextAlarm()
                }
                ACTION_HEARTBEAT -> {
                    scheduleNextHeartbeat()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!isMonitoringActive) startMonitoring()
                }
                Intent.ACTION_USER_PRESENT -> {
                    forceCheckCurrentApp()
                }
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    if (!isMonitoringActive) startMonitoring()
                    scheduleNextAlarm()
                    scheduleNextHeartbeat()
                    forceCheckCurrentApp()
                }
            }
        }
    }

    private fun scheduleNextAlarm() {
        val alarmIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }

        val pendingIntent = PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + ALARM_INTERVAL

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AppLockService", "Next alarm scheduled in ${ALARM_INTERVAL / 1000} seconds")
        } catch (e: Exception) {
            Log.e("AppLockService", "Error scheduling alarm", e)
        }
    }

    private fun scheduleNextHeartbeat() {
        val heartbeatIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_HEARTBEAT
        }

        val pendingIntent = PendingIntent.getService(
            this,
            HEARTBEAT_REQUEST_CODE,
            heartbeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + HEARTBEAT_INTERVAL

        try {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d("AppLockService", "Next heartbeat scheduled in ${HEARTBEAT_INTERVAL / 1000} seconds")
        } catch (e: Exception) {
            Log.e("AppLockService", "Error scheduling heartbeat", e)
        }
    }

    private fun startMonitoring() {
        if (isMonitoringActive) return

        Log.d("AppLockService", "Starting monitoring")
        isMonitoringActive = true
        monitoringJob?.cancel()

        monitoringJob = scope.launch {
            while (isMonitoringActive) {
                try {
                    val currentApp = getForegroundApp()
                    if (currentApp != lastForegroundApp && currentApp.isNotEmpty()) {
                        Log.d("AppLockService", "Foreground app changed to $currentApp")
                        if (lastForegroundApp.isNotEmpty()) {
                            resetAppUnlockStatus(lastForegroundApp)
                        }
                        lastForegroundApp = currentApp
                        checkAndLockApp(currentApp)
                    }

                    // Adaptive delay - longer when screen is off
                    val isScreenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .isInteractive
                    delay(if (isScreenOn) 500L else 2000L)
                } catch (e: Exception) {
                    Log.e("AppLockService", "Monitoring error", e)
                    delay(5000L) // Recovery delay on error
                }
            }
        }
    }

    private fun stopMonitoring() {
        Log.d("AppLockService", "Stopping monitoring")
        isMonitoringActive = false
        monitoringJob?.cancel()
        releaseWakeLock()
    }

    private fun forceCheckCurrentApp() {
        scope.launch {
            Log.d("AppLockService", "Performing force check")
            val currentApp = getForegroundApp()
            if (currentApp.isNotEmpty()) {
                checkAndLockApp(currentApp)
            }
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
                    now - 1000 * 60, // 1 minute window
                    now
                )
                stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
            } else {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName ?: ""
            }
        } catch (e: Exception) {
            Log.e("AppLockService", "Error getting foreground app", e)
            ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return

        Log.d("AppLockService", "Checking app: $packageName")
        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()
        if (lockedApps.contains(packageName) && !sharedPref.getBoolean("unlocked_$packageName", false)) {
            Log.d("AppLockService", "Locking app: $packageName")
            acquireWakeLock(5000L)

            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra("package_name", packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            startActivity(intent)
        }
    }

    private fun resetAppUnlockStatus(packageName: String) {
        if (sharedPref.getStringSet("locked_apps", emptySet())?.contains(packageName) == true) {
            sharedPref.edit().putBoolean("unlocked_$packageName", false).apply()
            Log.d("AppLockService", "Reset unlock status for $packageName")
        }
    }

    private fun acquireWakeLock(timeout: Long = 0) {
        if (wakeLock?.isHeld == true) return

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ArkLock:AppLockWakeLock"
            ).apply {
                setReferenceCounted(false)
                if (timeout > 0) acquire(timeout) else acquire()
                Log.d("AppLockService", "WakeLock acquired for ${if (timeout > 0) "$timeout ms" else "indefinite"}")
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("AppLockService", "WakeLock released")
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        Log.d("AppLockService", "Service destroyed")
        stopMonitoring()
        unregisterReceiver(receiver)
        releaseWakeLock()
        super.onDestroy()
    }
}