package com.example.b1void.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dropbox.core.DbxException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
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

        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return Result.failure()
            }

            val dbxClient: DbxClientV2 = DropboxClientFactory.getClient()

            withContext(Dispatchers.IO) {
                val inputStream = FileInputStream(file)
                dbxClient.files().uploadBuilder(dropboxPath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
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

    companion object {
        private const val TAG = "DropboxUploadWorker"
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_DROPBOX_PATH = "key_dropbox_path"
    }
}
