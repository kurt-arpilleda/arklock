package com.example.arklock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start service on boot
            context.startService(Intent(context, AppLockService::class.java))
        }
    }
}