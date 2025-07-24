package com.example.arklock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class AppLaunchInterceptor : Activity() {
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)

        // Get the calling package
        val callingPackage = callingActivity?.packageName ?: intent.getStringExtra("package_name")

        if (callingPackage != null && callingPackage != packageName) {
            val lockedApps = sharedPref.getStringSet("locked_apps", emptySet()) ?: emptySet()

            if (lockedApps.contains(callingPackage)) {
                val unlockedApps = sharedPref.getStringSet("unlocked_apps", emptySet()) ?: emptySet()

                if (!unlockedApps.contains(callingPackage)) {
                    // Show lock screen
                    val lockIntent = Intent(this, LockActivity::class.java).apply {
                        putExtra("package_name", callingPackage)
                        putExtra("intercept_mode", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_NO_HISTORY or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    }
                    startActivity(lockIntent)
                    finish()
                    return
                }
            }
        }

        // If not locked, finish immediately
        finish()
    }
}
