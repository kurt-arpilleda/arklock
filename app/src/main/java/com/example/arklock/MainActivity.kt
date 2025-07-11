package com.example.arklock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.arklock.ui.theme.ArkLockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateService: AppUpdateService
    private lateinit var connectivityReceiver: NetworkUtils.ConnectivityReceiver
    private var networkCallbackRegistered = false
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private val networkState = MutableStateFlow(false)
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var serviceWatchdog: ServiceWatchdog

    override fun onCreate(savedInstanceState: Bundle?) {
        RetrofitClient.updateWorkingUrl()
        appUpdateService = AppUpdateService(this)
        connectivityReceiver = NetworkUtils.ConnectivityReceiver {
            checkForUpdates()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "arklock_channel",
                "ArkLock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for ArkLock service"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        serviceWatchdog = ServiceWatchdog(this)
        serviceWatchdog.startWatching()
        startAppLockService()
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)
        observeNetworkChanges()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArkLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
    private fun checkForUpdates() {
        coroutineScope.launch {
            if (NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                appUpdateService.checkForAppUpdate()
            }
        }
    }
    private fun startAppLockService() {
        val serviceIntent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun observeNetworkChanges() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (!networkCallbackRegistered) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    networkState.value = true
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    networkState.value = false
                }
            }

            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                connectivityManager.registerNetworkCallback(request, networkCallback)
                networkCallbackRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Ensure the service is running
        val serviceIntent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            if (networkCallbackRegistered) {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                networkCallbackRegistered = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    val hasPassword = sharedPref.getInt("has_password", 0) == 1

    var currentScreen by remember { mutableStateOf(
        if (hasPassword) "passcode_verification" else "password_setup"
    ) }

    when (currentScreen) {
        "password_setup" -> {
            PasswordScreen(
                onPasswordSaved = { passwordType ->
                    currentScreen = "dashboard"
                }
            )
        }
        "passcode_verification" -> {
            PasscodeScreen(
                onPasscodeVerified = {
                    currentScreen = "dashboard"
                }
            )
        }
        "dashboard" -> {
            DashboardPage()
        }
    }
}