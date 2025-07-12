package com.example.arklock

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class AppLockWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Ensure the service is running
        AppLockService.startService(applicationContext)

        // Keep the worker alive for a while to ensure service starts
        delay(5000)

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return AppLockService.createForegroundInfo(applicationContext)
    }
}