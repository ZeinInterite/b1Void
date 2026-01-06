package com.example.b1void.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.b1void.core.domain.usecase.SyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncUseCase: SyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = syncUseCase()
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "SyncWorker"
    }
}
