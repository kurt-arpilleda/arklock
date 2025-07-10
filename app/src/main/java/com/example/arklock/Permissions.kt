package com.example.arklock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat.startActivity

@Composable
fun CheckRequiredPermissions() {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var displayOverAppsEnabled by remember { mutableStateOf(false) }
    var usageAccessEnabled by remember { mutableStateOf(false) }

    // Check permissions when composable is launched or recomposed
    LaunchedEffect(Unit) {
        displayOverAppsEnabled = canDisplayOverOtherApps(context)
        usageAccessEnabled = hasUsageAccessPermission(context)
        showPermissionDialog = !displayOverAppsEnabled || !usageAccessEnabled
    }

    if (showPermissionDialog) {
        PermissionDialog(
            displayOverAppsEnabled = displayOverAppsEnabled,
            usageAccessEnabled = usageAccessEnabled,
            onDisplayOverAppsClick = {
                openDisplayOverOtherAppsSettings(context)
                showPermissionDialog = false
            },
            onUsageAccessClick = {
                openUsageAccessSettings(context)
                showPermissionDialog = false
            },
            onDismiss = { showPermissionDialog = false }
        )
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
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
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
                    enabled = !displayOverAppsEnabled,
                    onClick = onDisplayOverAppsClick,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Usage access permission
                PermissionItem(
                    title = "Usage Access",
                    description = "Allows ArkLock to detect when other apps are opened",
                    enabled = !usageAccessEnabled,
                    onClick = onUsageAccessClick,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "After enabling permissions, return to ArkLock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        onClick = (if (enabled) onClick else {}) as () -> Unit,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (enabled) "Tap to enable" else "Already enabled",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// Utility functions
private fun canDisplayOverOtherApps(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
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