// LockActivity.kt
package com.example.arklock


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.view.View
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
    private var isUnlocking = false

    companion object {
        private var isActivityVisible = false
    }

    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            finish()
            return
        }

        // Prevent screenshots, show when locked
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Acquire WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ArkLock:LockActivityWakeLock"
        )
        wakeLock.acquire(60 * 1000L)

        // Enable immersive and pin screen
        enableImmersiveSticky()
        startLockTask()

        isActivityVisible = true

        setContent {
            LockScreenContent(
                packageName = packageName,
                onUnlock = {
                    if (!isUnlocking) {
                        isUnlocking = true
                        sharedPref.edit().putBoolean("unlocked_$packageName", true).apply()
                        stopLockTask()
                        finishAndRemoveTask()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            stopLockTask()
            finish()
        }
        enableImmersiveSticky()
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityVisible = false
        isUnlocking = false
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        stopLockTask()
    }

    override fun onBackPressed() {
        // Block back button
    }

    private fun enableImmersiveSticky() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
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