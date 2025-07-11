package com.example.arklock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class OverlayLockActivity : ComponentActivity() {
    private var lockedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedPackageName = intent.getStringExtra("locked_package")

        setContent {
            MaterialTheme {
                LockScreenUI(
                    onSuccess = {
                        // Unlock the app and finish this activity
                        finish()
                    },
                    onCancel = {
                        // Go back to home if cancelled
                        startActivity(Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LockScreenUI(onSuccess: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    val passwordType = sharedPref.getString("password_type", "PIN") ?: "PIN"
    val savedPassword = sharedPref.getString("password_value", "") ?: ""

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter Password", fontSize = 22.sp)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = {
                if (it.length <= 6) pinInput = it
            },
            label = { Text("6-digit PIN") },
            singleLine = true
        )

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                if (pinInput == savedPassword) {
                    onSuccess()
                } else {
                    errorMessage = "Incorrect Password"
                    pinInput = ""
                }
            }) {
                Text("Unlock")
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
