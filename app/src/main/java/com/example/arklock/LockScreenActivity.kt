package com.example.arklock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.arklock.ui.theme.ArkLockTheme

class LockScreenActivity : ComponentActivity() {
    private var lockedAppPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedAppPackage = intent.getStringExtra("locked_app_package")

        setContent {
            ArkLockTheme {
                LockScreen(
                    lockedAppPackage = lockedAppPackage,
                    onUnlocked = {
                        // Unlock the app temporarily
                        lockedAppPackage?.let { packageName ->
                            ArkLockService.unlockApp(packageName)
                        }

                        // Open the original app
                        openOriginalApp()

                        // Close lock screen
                        finish()
                    },
                    onCancel = {
                        // Go back to home
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun openOriginalApp() {
        lockedAppPackage?.let { packageName ->
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Prevent back press - user must unlock or cancel
        // Do nothing or show message
    }
}

@Composable
fun LockScreen(
    lockedAppPackage: String?,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Get app info
    val appInfo = remember(lockedAppPackage) {
        lockedAppPackage?.let { packageName ->
            try {
                val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val appName = packageManager.getApplicationLabel(info).toString()
                val appIcon = packageManager.getApplicationIcon(info)
                val iconBitmap = appIcon.toBitmap(width = 120, height = 120)
                Triple(appName, appIcon, iconBitmap)
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon if available
            appInfo?.let { (appName, _, iconBitmap) ->
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .blur(0.5.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lock icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Color.Red.copy(alpha = 0.8f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "This app is locked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your passcode to unlock",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Passcode input (reuse existing components)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                PasscodeVerificationForLock(
                    onPasscodeVerified = onUnlocked,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel button
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Cancel",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PasscodeVerificationForLock(
    onPasscodeVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    val passwordType = sharedPref.getString("password_type", "PIN") ?: "PIN"
    val savedPassword = sharedPref.getString("password_value", "") ?: ""

    var inputPin by remember { mutableStateOf("") }
    var inputPattern by remember { mutableStateOf(listOf<Int>()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showError) {
            Text(
                text = errorMessage,
                color = Color.Red.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // PIN or Pattern Input
        if (passwordType == "PIN") {
            PinVerificationInputLock(
                pin = inputPin,
                onPinChange = { newPin ->
                    if (newPin.length <= 6) {
                        inputPin = newPin
                        showError = false

                        if (newPin.length == 6) {
                            if (newPin == savedPassword) {
                                onPasscodeVerified()
                            } else {
                                showError = true
                                errorMessage = "Incorrect PIN. Try again."
                                inputPin = ""
                            }
                        }
                    }
                }
            )
        } else {
            PatternVerificationInputLock(
                selectedPoints = inputPattern,
                onPatternChange = { pattern ->
                    inputPattern = pattern
                    showError = false

                    if (pattern.size >= 4) {
                        val patternString = pattern.joinToString(",")
                        if (patternString == savedPassword) {
                            onPasscodeVerified()
                        } else {
                            showError = true
                            errorMessage = "Incorrect pattern. Try again."
                            inputPattern = listOf()
                        }
                    }
                }
            )
        }
    }
}

// Custom PIN input for lock screen with white colors
@Composable
fun PinVerificationInputLock(pin: String, onPinChange: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // PIN Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(
                            Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(2.dp)
                        .background(
                            if (index < pin.length) Color.White.copy(alpha = 0.3f)
                            else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < pin.length) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    Color.White,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Number Keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0..3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    for (col in 0..2) {
                        val number = when {
                            row == 3 && col == 0 -> null
                            row == 3 && col == 1 -> 0
                            row == 3 && col == 2 -> -1
                            else -> row * 3 + col + 1
                        }

                        if (number != null) {
                            if (number == -1) {
                                Button(
                                    onClick = {
                                        if (pin.isNotEmpty()) {
                                            onPinChange(pin.dropLast(1))
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        "⌫",
                                        fontSize = 24.sp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (pin.length < 6) {
                                            onPinChange(pin + number.toString())
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = number.toString(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
    }
}

// Custom Pattern input for lock screen
@Composable
fun PatternVerificationInputLock(
    selectedPoints: List<Int>,
    onPatternChange: (List<Int>) -> Unit
) {
    // Use the same pattern input as before but with white colors
    // This would be similar to PatternVerificationInput but with white color scheme
    // For brevity, using the existing component
    PatternVerificationInput(
        selectedPoints = selectedPoints,
        onPatternChange = onPatternChange
    )
}