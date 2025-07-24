package com.example.arklock

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class LockActivity : ComponentActivity() {
    private lateinit var packageName: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageName = intent.getStringExtra("package_name") ?: run {
            finishAndRemoveTask()
            return
        }

        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean("unlocked_$packageName", false) && !intent.getBooleanExtra("intercept_mode", false)) {
            finishAndRemoveTask()
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ArkLock:LockActivity"
        )
        wakeLock.acquire(10 * 1000L)

        setContent {
            LockScreenUI(
                packageName = packageName,
                onUnlock = { handleUnlock() }
            )
        }
    }

    private fun handleUnlock() {
        val unlockedApps = sharedPref.getStringSet("unlocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        unlockedApps.add(packageName)
        sharedPref.edit().putStringSet("unlocked_apps", unlockedApps).apply()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }
}

@Composable
fun LockScreenUI(packageName: String, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appName = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    BackHandler {
    }

    PasscodeScreen(
        onPasscodeVerified = onUnlock,
        appName = appName
    )
}
