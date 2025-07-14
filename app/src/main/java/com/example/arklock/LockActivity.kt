// LockActivity.kt
package com.example.arklock

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class LockActivity : ComponentActivity() {
    private lateinit var packageName: String
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra("package_name") ?: ""
        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            LockScreenContent(
                packageName = packageName,
                onUnlock = {
                    // Set app as unlocked in SharedPreferences
                    sharedPref.edit().putBoolean("unlocked_$packageName", true).apply()
                    finishAndRemoveTask()
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onBackPressed() {
        // Disable back button
    }

    override fun onDestroy() {
        super.onDestroy()
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