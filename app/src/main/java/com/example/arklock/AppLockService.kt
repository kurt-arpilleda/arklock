package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AppLockService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitoringJob: Job? = null
    private var lastForegroundApp = ""
    private var isUnlockedTemporarily = false
    private var tempUnlockedPackage = ""
    private lateinit var sharedPref: SharedPreferences
    private lateinit var alarmManager: AlarmManager
    private var alarmPendingIntent: PendingIntent? = null
    private var heartbeatPendingIntent: PendingIntent? = null
    private var restartPendingIntent: PendingIntent? = null

    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArkLock:AppLockWakeLock")
        }
    }
    private fun startWithHighPriority() {
        val intent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        startMonitoring()
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }
    companion object {
        private const val CHANNEL_ID = "app_lock_channel"
        private const val NOTIFICATION_ID = 2
        private const val ALARM_INTERVAL = 10 * 60 * 1000L // 10 minutes
        private const val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val RESTART_INTERVAL = 30 * 1000L // 30 seconds
        private const val ALARM_REQUEST_CODE = 1001
        private const val HEARTBEAT_REQUEST_CODE = 1002
        private const val RESTART_REQUEST_CODE = 1003

        const val ACTION_ALARM_TRIGGER = "com.example.arklock.ALARM_TRIGGER"
        const val ACTION_HEARTBEAT = "com.example.arklock.HEARTBEAT"
        const val ACTION_RESTART_SERVICE = "com.example.arklock.RESTART_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            if (context is AppLockService) {
                context.startWithHighPriority()
            }
        }

        fun scheduleServiceRestart(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(context, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + RESTART_INTERVAL

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
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
        startForeground()
        startMonitoring()
        registerAlarmReceiver()
        scheduleNextAlarm()
        scheduleNextHeartbeat()
        scheduleServiceRestart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerAlarmReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_ALARM_TRIGGER)
            addAction(ACTION_HEARTBEAT)
            addAction(ACTION_RESTART_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(alarmReceiver, filter)
        }
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ALARM_TRIGGER -> {
                    if (monitoringJob?.isActive != true) {
                        startMonitoring()
                    }
                    scheduleNextAlarm()
                }
                ACTION_HEARTBEAT -> {
                    refreshWakeLock()
                    scheduleNextHeartbeat()
                }
                ACTION_RESTART_SERVICE -> {
                    // Service restart logic
                    if (monitoringJob?.isActive != true) {
                        startMonitoring()
                    }
                    scheduleServiceRestart()
                }
            }
        }
    }

    private fun scheduleServiceRestart() {
        val restartIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_RESTART_SERVICE
        }

        restartPendingIntent = PendingIntent.getService(
            this,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + RESTART_INTERVAL

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    restartPendingIntent!!
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    restartPendingIntent!!
                )
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun refreshWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun scheduleNextAlarm() {
        val alarmIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }

        alarmPendingIntent = PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + ALARM_INTERVAL

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    alarmPendingIntent!!
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    alarmPendingIntent!!
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    alarmPendingIntent!!
                )
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun scheduleNextHeartbeat() {
        val heartbeatIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_HEARTBEAT
        }

        heartbeatPendingIntent = PendingIntent.getService(
            this,
            HEARTBEAT_REQUEST_CODE,
            heartbeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + HEARTBEAT_INTERVAL

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    heartbeatPendingIntent!!
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    heartbeatPendingIntent!!
                )
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun cancelAlarms() {
        alarmPendingIntent?.let {
            alarmManager.cancel(it)
            alarmPendingIntent = null
        }
        heartbeatPendingIntent?.let {
            alarmManager.cancel(it)
            heartbeatPendingIntent = null
        }
        restartPendingIntent?.let {
            alarmManager.cancel(it)
            restartPendingIntent = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for app lock service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ArkLock is running")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        } catch (e: Exception) {
            // Handle exception
        }

        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (true) {
                try {
                    val currentApp = getForegroundApp()
                    if (currentApp != lastForegroundApp) {
                        lastForegroundApp = currentApp
                        checkAndLockApp(currentApp)
                    }
                    delay(300) // Check every 300ms
                } catch (e: Exception) {
                    // Handle exception and continue monitoring
                    delay(1000) // Wait a bit longer if there's an error
                }
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
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.getRunningTasks(1)
            tasks?.firstOrNull()?.topActivity?.packageName ?: ""
        }
    }

    private fun checkAndLockApp(packageName: String) {
        if (packageName.isEmpty() || packageName == this.packageName) return

        if (isUnlockedTemporarily && tempUnlockedPackage == packageName) {
            return
        }

        val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lockedApps.contains(packageName)) {
            if (sharedPref.getBoolean("temp_unlock_$packageName", false)) {
                sharedPref.edit().remove("temp_unlock_$packageName").apply()
                tempUnlockedPackage = packageName
                isUnlockedTemporarily = true
                return
            }

            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra("package_name", packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

    override fun onDestroy() {
        scheduleServiceRestart(this)
        stopMonitoring()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        cancelAlarms()
        try {
            unregisterReceiver(alarmReceiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleServiceRestart(this)
        super.onTaskRemoved(rootIntent)
    }
}

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLockService.startService(context)
    }
}