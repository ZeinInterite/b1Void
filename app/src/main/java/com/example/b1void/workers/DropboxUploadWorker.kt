package com.example.b1void.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Stubbed class to fix build
class DropboxUploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
