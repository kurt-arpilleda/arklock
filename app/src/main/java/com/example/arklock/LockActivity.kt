package com.example.arklock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.arklock.ui.theme.ArkLockTheme

class LockActivity : ComponentActivity() {
    private lateinit var packageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra("package_name") ?: ""

        setContent {
            ArkLockTheme {
                LockScreen {
                    // Passcode verified - allow app access
                    startActivity(packageManager.getLaunchIntentForPackage(packageName))
                    finish()
                }
            }
        }
    }

    override fun onBackPressed() {
        // Prevent bypassing the lock screen
        moveTaskToBack(true)
    }

    @Composable
    fun LockScreen(onPasscodeVerified: () -> Unit) {
        PasscodeScreen(
            onPasscodeVerified = onPasscodeVerified
        )
    }
}