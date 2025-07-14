package com.example.arklock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class LockActivity : ComponentActivity() {
    private lateinit var packageName: String
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra("package_name") ?: ""
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)

        // Prevent screenshots and recent apps
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            LockScreenContent(
                packageName = packageName,
                onUnlock = {
                    // Mark this app as temporarily unlocked
                    sharedPref.edit().putBoolean("temp_unlock_$packageName", true).apply()

                    // Simply finish the activity - don't relaunch the app
                    finishAndRemoveTask()
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
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

    // Show the same passcode screen but with app-specific UI
    PasscodeScreen(
        onPasscodeVerified = onUnlock,
        appName = appName
    )
}