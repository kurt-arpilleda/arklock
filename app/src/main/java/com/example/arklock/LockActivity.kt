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
    private var isUnlocking = false

    companion object {
        var isVisible = false
            private set
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageName = intent.getStringExtra("package_name") ?: run {
            finish()
            return
        }

        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            finish()
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ArkLock:LockActivity"
        )
        wakeLock.acquire(60 * 1000L)

        isVisible = true

        setContent {
            LockScreenUI(
                packageName = packageName,
                onUnlock = { handleUnlock() }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
    }

    private fun handleUnlock() {
        if (isUnlocking) return
        isUnlocking = true
        sharedPref.edit().putBoolean("unlocked_$packageName", true).apply()
        sendBroadcast(
            Intent(AppLockService.ACTION_APP_UNLOCKED).apply {
                putExtra("package_name", packageName)
            }
        )
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        if (sharedPref.getBoolean("unlocked_$packageName", false)) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isVisible = false
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBackPressed() {
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