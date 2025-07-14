package com.example.arklock

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_REBOOT) {

            Log.d("BootReceiver", "Device booted, starting AppLockService")

            // Start service immediately with high priority
            val serviceIntent = Intent(context, AppLockService::class.java).apply {
                action = AppLockService.ACTION_BOOT_COMPLETED
                putExtra("boot_completed", true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Schedule immediate check after 1 second
            scheduleImmediateCheck(context)
        }
    }

    @SuppressLint("ServiceCast")
    private fun scheduleImmediateCheck(context: Context) {
        val checkIntent = Intent(context, AppLockService::class.java).apply {
            action = AppLockService.ACTION_FORCE_CHECK
        }

        val pendingIntent = PendingIntent.getService(
            context,
            0,
            checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + 1000 // 1 second

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
            Log.e("BootReceiver", "Error scheduling immediate check", e)
        }
    }
}