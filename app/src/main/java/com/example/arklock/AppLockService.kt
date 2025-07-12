package com.example.arklock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
    private var keepAlivePendingIntent: PendingIntent? = null
    private var powerManager: PowerManager? = null
    private var isIgnoringBatteryOptimizations = false

    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ArkLock:AppLockWakeLock"
            )
        }
    }

    // Additional wake lock for critical operations
    private val criticalWakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ArkLock:CriticalWakeLock"
            )
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
        acquireWakeLocks()
    }

    private fun acquireWakeLocks() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(24 * 60 * 60 * 1000L /*24 hours*/)
            }
            // Acquire critical wake lock for shorter periods
            if (!criticalWakeLock.isHeld) {
                criticalWakeLock.acquire(30 * 60 * 1000L /*30 minutes*/)
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    companion object {
        private const val CHANNEL_ID = "app_lock_channel"
        private const val CRITICAL_CHANNEL_ID = "app_lock_critical_channel"
        private const val NOTIFICATION_ID = 2
        private const val CRITICAL_NOTIFICATION_ID = 3
        private const val ALARM_INTERVAL = 5 * 60 * 1000L // 5 minutes (reduced)
        private const val HEARTBEAT_INTERVAL = 2 * 60 * 1000L // 2 minutes (reduced)
        private const val RESTART_INTERVAL = 15 * 1000L // 15 seconds (reduced)
        private const val KEEP_ALIVE_INTERVAL = 30 * 1000L // 30 seconds
        private const val ALARM_REQUEST_CODE = 1001
        private const val HEARTBEAT_REQUEST_CODE = 1002
        private const val RESTART_REQUEST_CODE = 1003
        private const val KEEP_ALIVE_REQUEST_CODE = 1004

        const val ACTION_ALARM_TRIGGER = "com.example.arklock.ALARM_TRIGGER"
        const val ACTION_HEARTBEAT = "com.example.arklock.HEARTBEAT"
        const val ACTION_RESTART_SERVICE = "com.example.arklock.RESTART_SERVICE"
        const val ACTION_KEEP_ALIVE = "com.example.arklock.KEEP_ALIVE"
        const val ACTION_CRITICAL_OPERATION = "com.example.arklock.CRITICAL_OPERATION"

        fun startService(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun createForegroundInfo(context: Context): ForegroundInfo {
            val notification = createHighPriorityNotification(context)
            return ForegroundInfo(NOTIFICATION_ID, notification)
        }

        private fun createHighPriorityNotification(context: Context): Notification {
            return NotificationCompat.Builder(context, CRITICAL_CHANNEL_ID)
                .setContentTitle("ArkLock Protection Active")
                .setContentText("Security monitoring in progress")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SYSTEM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        fun scheduleWorkManager(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Don't require battery not low
                .setRequiresStorageNotLow(false) // Don't require storage not low
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AppLockWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "AppLockWorker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        @SuppressLint("ServiceCast")
        fun scheduleJobScheduler(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            val component = ComponentName(context, AppLockJobService::class.java)

            val jobInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                JobInfo.Builder(JOB_ID, component)
                    .setPeriodic(15 * 60 * 1000) // 15 minutes
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresStorageNotLow(false)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .build()
            } else {
                JobInfo.Builder(JOB_ID, component)
                    .setPeriodic(15 * 60 * 1000)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setRequiresCharging(false)
                    .build()
            }

            jobScheduler.schedule(jobInfo)
        }

        private const val JOB_ID = 1001

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

        // Method to check if battery optimization is disabled
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        }

        // Method to request battery optimization whitelist
        fun requestBatteryOptimizationWhitelist(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to battery optimization settings
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallbackIntent)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Check battery optimization status
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(this)

        createNotificationChannels()
        startForeground()
        startMonitoring()
        registerAlarmReceiver()

        // Schedule all keep-alive mechanisms
        scheduleNextAlarm()
        scheduleNextHeartbeat()
        scheduleKeepAlive()
        scheduleServiceRestart()
        scheduleWorkManager(this)
        scheduleJobScheduler(this)

        // Acquire wake locks
        acquireWakeLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CRITICAL_OPERATION -> {
                // Handle critical operations that need to bypass power saving
                handleCriticalOperation()
            }
            ACTION_KEEP_ALIVE -> {
                // Keep alive ping
                refreshWakeLock()
                scheduleKeepAlive()
            }
        }
        return START_STICKY
    }

    private fun handleCriticalOperation() {
        // Acquire critical wake lock for important operations
        try {
            if (!criticalWakeLock.isHeld) {
                criticalWakeLock.acquire(5 * 60 * 1000L /*5 minutes*/)
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerAlarmReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_ALARM_TRIGGER)
            addAction(ACTION_HEARTBEAT)
            addAction(ACTION_RESTART_SERVICE)
            addAction(ACTION_KEEP_ALIVE)
            addAction(ACTION_CRITICAL_OPERATION)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
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
                    if (monitoringJob?.isActive != true) {
                        startMonitoring()
                    }
                    scheduleServiceRestart()
                }
                ACTION_KEEP_ALIVE -> {
                    refreshWakeLock()
                    scheduleKeepAlive()
                }
                ACTION_CRITICAL_OPERATION -> {
                    handleCriticalOperation()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Screen turned on, refresh monitoring
                    refreshWakeLock()
                    if (monitoringJob?.isActive != true) {
                        startMonitoring()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen turned off, ensure wake locks are held
                    acquireWakeLocks()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // User unlocked device, refresh monitoring
                    refreshWakeLock()
                    if (monitoringJob?.isActive != true) {
                        startMonitoring()
                    }
                }
            }
        }
    }

    private fun scheduleKeepAlive() {
        val keepAliveIntent = Intent(this, AppLockService::class.java).apply {
            action = ACTION_KEEP_ALIVE
        }

        keepAlivePendingIntent = PendingIntent.getService(
            this,
            KEEP_ALIVE_REQUEST_CODE,
            keepAliveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + KEEP_ALIVE_INTERVAL

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    keepAlivePendingIntent!!
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    keepAlivePendingIntent!!
                )
            }
        } catch (e: Exception) {
            // Handle exception
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
            // Refresh primary wake lock
            if (!wakeLock.isHeld) {
                wakeLock.acquire(24 * 60 * 60 * 1000L /*24 hours*/)
            }

            // Refresh critical wake lock periodically
            if (!criticalWakeLock.isHeld) {
                criticalWakeLock.acquire(30 * 60 * 1000L /*30 minutes*/)
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
        keepAlivePendingIntent?.let {
            alarmManager.cancel(it)
            keepAlivePendingIntent = null
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Regular channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for app lock service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            // Critical channel with high importance
            val criticalChannel = NotificationChannel(
                CRITICAL_CHANNEL_ID,
                "App Lock Critical",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical notifications for app lock service"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(criticalChannel)
        }
    }

    private fun startForeground() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CRITICAL_CHANNEL_ID)
            .setContentTitle("ArkLock Protection Active")
            .setContentText("Security monitoring in progress - Do not disable")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startMonitoring() {
        // Acquire wake locks before starting monitoring
        acquireWakeLocks()

        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (true) {
                try {
                    val currentApp = getForegroundApp()
                    if (currentApp != lastForegroundApp) {
                        lastForegroundApp = currentApp
                        checkAndLockApp(currentApp)
                    }
                    delay(200) // Check every 200ms (more frequent)
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

            // Trigger critical operation before showing lock screen
            handleCriticalOperation()

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
        // Schedule multiple restart mechanisms
        scheduleServiceRestart(this)
        scheduleWorkManager(this)
        scheduleJobScheduler(this)

        stopMonitoring()

        // Release wake locks
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            if (criticalWakeLock.isHeld) {
                criticalWakeLock.release()
            }
        } catch (e: Exception) {
            // Handle exception
        }

        cancelAlarms()

        try {
            unregisterReceiver(alarmReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleServiceRestart(this)
        scheduleWorkManager(this)
        super.onTaskRemoved(rootIntent)
    }
}

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLockService.startService(context)
    }
}