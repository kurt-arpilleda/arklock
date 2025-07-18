package com.example.arklock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d("BootReceiver", "Boot completed - starting AppLockService")

                // Start service immediately
                AppLockService.startService(context)

                // Also start with a small delay to ensure system is ready
                handler.postDelayed({
                    AppLockService.startService(context)
                }, 5000)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Package replaced - restarting AppLockService")
                AppLockService.startService(context)
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d("BootReceiver", "User present - ensuring service is running")
                AppLockService.startService(context)
            }

            Intent.ACTION_SCREEN_ON -> {
                Log.d("BootReceiver", "Screen on - ensuring service is running")
                AppLockService.startService(context)
            }
        }
    }
}