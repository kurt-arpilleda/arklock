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

    override fun onBackPressed() {
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
    }

    PasscodeScreen(
        onPasscodeVerified = onUnlock,
        appName = appName
    )
}
