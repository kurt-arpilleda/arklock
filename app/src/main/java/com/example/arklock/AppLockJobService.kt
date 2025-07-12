package com.example.arklock

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("SpecifyJobSchedulerIdRange")
class AppLockJobService : JobService() {
    private val jobScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            // Start the main service
            AppLockService.startService(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if stopped
    }
}