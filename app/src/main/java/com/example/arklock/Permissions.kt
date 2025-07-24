package com.example.arklock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun CheckRequiredPermissions() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var displayOverAppsEnabled by remember { mutableStateOf(false) }
    var usageAccessEnabled by remember { mutableStateOf(false) }
    var showManufacturerSettingsDialog by remember { mutableStateOf(false) }

    fun checkPermissions() {
        displayOverAppsEnabled = canDisplayOverOtherApps(context)
        usageAccessEnabled = hasUsageAccessPermission(context)
        showPermissionDialog = !displayOverAppsEnabled || !usageAccessEnabled

        // Only check battery optimization if permissions are already granted
        if (!showPermissionDialog) {
            val batteryOptimized = !isBatteryOptimizationDisabled(context)
            showBatteryOptimizationDialog = batteryOptimized

            // Only show manufacturer settings if everything else is done
            if (!batteryOptimized) {
                showManufacturerSettingsDialog = !isManufacturerSpecificSettingsDone(context)
            }
        }
    }
    // Check permissions when composable is launched
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Check permissions when app resumes (user returns from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            displayOverAppsEnabled = displayOverAppsEnabled,
            usageAccessEnabled = usageAccessEnabled,
            onDisplayOverAppsClick = {
                openDisplayOverOtherAppsSettings(context)
            },
            onUsageAccessClick = {
                openUsageAccessSettings(context)
            },
            onDismiss = {
                if (displayOverAppsEnabled && usageAccessEnabled) {
                    showPermissionDialog = false
                    // After permissions are granted, check battery optimization
                    showBatteryOptimizationDialog = !isBatteryOptimizationDisabled(context)
                }
            }
        )
    }

    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onConfigureClick = {
                openBatteryOptimizationSettings(context)
            }
        )
    }
    if (showManufacturerSettingsDialog) {
        ManufacturerSettingsDialog(
            onDismiss = {
                val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
                sharedPref.edit().putBoolean("manufacturer_settings_shown", true).apply()
                showManufacturerSettingsDialog = false
            }
        )
    }
}
@Composable
fun ManufacturerSettingsDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Important Device Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "For ArkLock to work properly on some devices, please make sure to configure these additional settings:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Auto-start setting
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Enable Auto-Start",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allow ArkLock to start automatically after device reboot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Power saving setting
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Disable Power Saving Restrictions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Disable Power Saving or Battery Saver in settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "These settings are usually found in:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "• Settings → Apps → Special app access → Auto-start\n" +
                            "• Settings → Battery → Power saving management or Battery Saver",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "I Understand",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
@Composable
fun BatteryOptimizationDialog(
    onConfigureClick: () -> Unit,
) {  // Removed onDismiss parameter since we won't allow skipping
    Dialog(
        onDismissRequest = {},  // Empty lambda prevents dismissal
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Special Configuration Required",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "In order to prevent the App from being stopped by Android in the background, we need to disable battery optimization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "This will allow ArkLock to run reliably in the background and maintain your device's security.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = onConfigureClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Disable Battery Optimization",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
@Composable
fun PermissionDialog(
    displayOverAppsEnabled: Boolean,
    usageAccessEnabled: Boolean,
    onDisplayOverAppsClick: () -> Unit,
    onUsageAccessClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = displayOverAppsEnabled && usageAccessEnabled,
            dismissOnClickOutside = displayOverAppsEnabled && usageAccessEnabled
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permissions Required",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "ArkLock requires the following permissions to function properly:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Display over other apps permission
                PermissionItem(
                    title = "Display Over Other Apps",
                    description = "Allows ArkLock to show lock screen over other apps",
                    isEnabled = displayOverAppsEnabled,
                    onClick = if (!displayOverAppsEnabled) onDisplayOverAppsClick else null,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Usage access permission
                PermissionItem(
                    title = "Usage Access",
                    description = "Allows ArkLock to detect when other apps are opened",
                    isEnabled = usageAccessEnabled,
                    onClick = if (!usageAccessEnabled) onUsageAccessClick else null,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show different message based on permission status
                if (displayOverAppsEnabled && usageAccessEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All permissions granted! You can now use ArkLock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Continue",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Text(
                        text = "Please enable the required permissions above. The dialog will remain open until all permissions are granted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isEnabled: Boolean,
    onClick: (() -> Unit)?,  // Nullable onClick - will be null when enabled
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onClick?.let { it } ?: {}  // Only make clickable if onClick is provided
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnabled) "✓ Enabled" else "Tap to enable",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal
                )
            }

            if (isEnabled) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
// Add these new utility functions to Permission.kt
private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    intent.data = Uri.parse("package:${context.packageName}")
    startActivity(context, intent, null)
}
// Utility functions
private fun canDisplayOverOtherApps(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}
private fun isManufacturerSpecificSettingsDone(context: Context): Boolean {
    // This is hard to check programmatically, so we'll just track if user has seen the dialog
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    return sharedPref.getBoolean("manufacturer_settings_shown", false)
}
private fun hasUsageAccessPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.checkOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

private fun openDisplayOverOtherAppsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    startActivity(context, intent, null)
}

private fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    startActivity(context, intent, null)
}