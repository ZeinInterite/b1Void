package com.example.b1void.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dropbox.core.DbxException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.example.b1void.R
import com.example.b1void.utils.DropboxClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class DropboxUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val dropboxPath = inputData.getString(KEY_DROPBOX_PATH) ?: return Result.failure()

        // Проверяем, не убит ли процесс из-за battery optimization
        if (!canPerformBackgroundWork()) {
            Log.w(TAG, "Background work restricted by system")
            return Result.retry() // Попробуем позже
        }

        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return Result.failure()
            }

            // Показываем notification для предотвращения kill на MIUI/EMUI
            if (shouldShowProgressNotification()) {
                setForeground(createForegroundInfo(file.name))
            }

            val dbxClient: DbxClientV2 = DropboxClientFactory.getClient()

            withContext(Dispatchers.IO) {
                FileInputStream(file).use { inputStream ->
                    dbxClient.files().uploadBuilder(dropboxPath)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream)
                }
            }

            Result.success()

        } catch (e: DbxException) {
            Log.e(TAG, "Dropbox upload failed", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Error during Dropbox upload", e)
            Result.failure()
        }
    }

    private fun canPerformBackgroundWork(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            // Проверяем, не в режиме Doze ли устройство
            if (powerManager.isDeviceIdleMode) {
                Log.d(TAG, "Device in Doze mode")
                return false
            }
        }

        // Проверяем производителя
        val manufacturer = Build.MANUFACTURER.lowercase()
        when (manufacturer) {
            "xiaomi", "redmi" -> {
                // На MIUI проверяем, разрешен ли автозапуск
                // К сожалению, нет API для проверки, можем только логировать
                Log.d(TAG, "Running on MIUI, background work may be restricted")
            }
            "huawei", "honor" -> {
                Log.d(TAG, "Running on EMUI, background work may be restricted")
            }
        }

        return true
    }

    private fun shouldShowProgressNotification(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        // Показываем notification на проблемных производителях
        return manufacturer in listOf("xiaomi", "redmi", "huawei", "honor", "oppo", "vivo", "realme")
    }

    private fun createForegroundInfo(fileName: String): ForegroundInfo {
        // Создаем notification channel для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Загрузка файлов",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогресс загрузки в Dropbox"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Загрузка в Dropbox")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "DropboxUploadWorker"
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_DROPBOX_PATH = "key_dropbox_path"
        private const val CHANNEL_ID = "dropbox_upload_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
