package com.example.arklock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class PasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PasswordScreen(
                isChangePassword = intent.getBooleanExtra("isChangePassword", false),
                onPasswordSaved = {
                    finish() // Close the activity after saving
                }
            )
        }
    }
}