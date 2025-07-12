package com.example.arklock

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, AppLockService::class.java))
            } else {
                context.startService(Intent(context, AppLockService::class.java))
            }

            AppLockService.scheduleServiceRestart(context)
        }
    }
}