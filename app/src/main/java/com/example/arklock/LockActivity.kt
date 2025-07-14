// LockActivity.kt
package com.example.arklock

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class LockActivity : ComponentActivity() {
    private lateinit var packageName: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isUnlocking = false

    companion object {
        private var isActivityVisible = false
    }

    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent multiple instances
        if (isActivityVisible) {
            finish()
            return
        }

        packageName = intent.getStringExtra("package_name") ?: ""
        if (packageName.isEmpty()) {
            finish()
            return
        }

        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)

        // Check if app is already unlocked
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            finish()
            return
        }

        // Security and wake lock setup
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ArkLock:LockActivityWakeLock"
        )
        wakeLock.acquire(60 * 1000L)

        isActivityVisible = true

        setContent {
            LockScreenContent(
                packageName = packageName,
                onUnlock = {
                    if (!isUnlocking) {
                        isUnlocking = true
                        sharedPref.edit().putBoolean("unlocked_$packageName", true).apply()
                        finishAndRemoveTask()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true

        // Double-check if app is still locked
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't set isActivityVisible to false here to prevent multiple instances
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityVisible = false
        isUnlocking = false

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBackPressed() {

    }
}

@Composable
fun LockScreenContent(packageName: String, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appName = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    BackHandler {
        // Disable back button
    }

    PasscodeScreen(
        onPasscodeVerified = onUnlock,
        appName = appName
    )
}