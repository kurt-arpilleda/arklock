package com.example.arklock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val iconBitmap: Bitmap?,
    val isSystemApp: Boolean,
    var isLocked: Boolean = false
)

@Composable
fun DashboardPage() {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)

    var appsList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showPasscodeVerification by remember { mutableStateOf(false) }
    CheckRequiredPermissions()

    LaunchedEffect(Unit) {
        isLoading = true
        appsList = withContext(Dispatchers.IO) {
            getAllInstalledApps(context)
        }
        isLoading = false
    }

    // Filter apps based on search query and system app preference
    val filteredApps = remember(appsList, searchQuery, showSystemApps) {
        appsList.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = if (showSystemApps) true else !app.isSystemApp
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Installed Apps",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${filteredApps.size} apps available",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // Add this inside the Row in the header Card, after the existing Column
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        // Show passcode verification instead of directly opening password change
                        showPasscodeVerification = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Change Password",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show system apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = showSystemApps,
                onCheckedChange = { showSystemApps = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Apps List
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Loading apps...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        onClick = {
                            // TODO: Handle app selection
                            // For now, this is just a placeholder
                        },
                        onLockToggle = { isLocked ->
                            // Update the app's locked state
                            appsList = appsList.map {
                                if (it.packageName == app.packageName) {
                                    it.copy(isLocked = isLocked)
                                } else {
                                    it
                                }
                            }
                        }
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No apps found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Try adjusting your search or filter settings",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPasscodeVerification) {
        PasscodeVerificationDialog(
            onPasscodeVerified = {
                showPasscodeVerification = false
                // Open password change screen after verification
                val intent = Intent(context, PasswordActivity::class.java).apply {
                    putExtra("isChangePassword", true)
                }
                context.startActivity(intent)
            },
            onDismiss = {
                showPasscodeVerification = false
            }
        )
    }

}

@Composable
fun AppItem(
    app: AppInfo,
    onClick: () -> Unit,
    onLockToggle: (Boolean) -> Unit
) {
    var isLocked by remember { mutableStateOf(app.isLocked) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback icon if bitmap conversion fails
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // App Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // System app indicator
                if (app.isSystemApp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "System",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Lock Toggle Button
            IconToggleButton(
                checked = isLocked,
                onCheckedChange = {
                    isLocked = it
                    onLockToggle(it)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Locked" else "Unlocked",
                    tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
@Composable
fun PasscodeVerificationDialog(
    onPasscodeVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify Identity",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Please verify your passcode to change password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PasscodeVerificationContent(
                    onPasscodeVerified = onPasscodeVerified,
                    onCancel = onDismiss
                )
            }
        }
    }
}
@Composable
fun PasscodeVerificationContent(
    onPasscodeVerified: () -> Unit,
    onCancel: () -> Unit
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        if (showError) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // PIN or Pattern Input
        if (passwordType == "PIN") {
            PinVerificationInput(
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
            PatternVerificationInput(
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

        Spacer(modifier = Modifier.height(16.dp))

        // Cancel button
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
private fun getAllInstalledApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val apps = mutableListOf<AppInfo>()

    try {
        // Try multiple approaches to get all apps
        val installedApps = try {
            // First try: Get all applications with QUERY_ALL_PACKAGES permission
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            // Fallback: Get applications through installed packages
            val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            packages.map { it.applicationInfo }
        }

        for (appInfo in installedApps) {
            try {
                // Skip apps that don't have a launcher activity (unless they're system apps we want to show)
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                // Include app if it has a launcher intent OR if it's a well-known system app
                if (launchIntent != null || isSystemApp) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val icon = packageManager.getApplicationIcon(appInfo)

                    // Convert icon to bitmap safely
                    val iconBitmap = try {
                        icon.toBitmap(
                            width = 144, // 48dp * 3 for better quality
                            height = 144
                        )
                    } catch (e: Exception) {
                        null
                    }

                    apps.add(
                        AppInfo(
                            name = appName,
                            packageName = packageName,
                            icon = icon,
                            iconBitmap = iconBitmap,
                            isSystemApp = isSystemApp,
                            isLocked = false // Default to unlocked
                        )
                    )
                }
            } catch (e: Exception) {
                // Skip apps that can't be processed
                continue
            }
        }

        // Also try to get apps from specific categories
        val additionalApps = getAppsFromIntent(context, packageManager)
        apps.addAll(additionalApps)

    } catch (e: Exception) {
        // Handle any errors during app fetching
        e.printStackTrace()
    }

    // Remove duplicates and sort apps alphabetically by name
    return apps.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
}

private fun getAppsFromIntent(context: Context, packageManager: PackageManager): List<AppInfo> {
    val apps = mutableListOf<AppInfo>()

    try {
        // Get all apps that can be launched (have MAIN/LAUNCHER intent)
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)

        for (resolveInfo in resolveInfos) {
            try {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                val icon = packageManager.getApplicationIcon(appInfo)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                // Convert icon to bitmap safely
                val iconBitmap = try {
                    icon.toBitmap(
                        width = 144,
                        height = 144
                    )
                } catch (e: Exception) {
                    null
                }

                apps.add(
                    AppInfo(
                        name = appName,
                        packageName = packageName,
                        icon = icon,
                        iconBitmap = iconBitmap,
                        isSystemApp = isSystemApp,
                        isLocked = false // Default to unlocked
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return apps
}