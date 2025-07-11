// LockActivity.kt
package com.example.arklock

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class LockActivity : ComponentActivity() {
    private var packageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra("package_name") ?: ""

        setContent {
            LockScreenContent(
                packageName = packageName,
                onUnlock = { finishAndRemoveTask() }
            )
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Prevent going back
        moveToHomeScreen()
    }

    override fun onPause() {
        super.onPause()
        // If activity is paused (user pressed home button), move to home screen
        if (!isFinishing) {
            moveToHomeScreen()
        }
    }

    private fun moveToHomeScreen() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
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