package com.example.arklock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AppLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                startAppLockService(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                // Restart service when screen turns on
                startAppLockService(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                // User unlocked the device
                startAppLockService(context)
            }
        }
    }

    private fun startAppLockService(context: Context) {
        val serviceIntent = Intent(context, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}