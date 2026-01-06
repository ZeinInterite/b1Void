package com.example.b1void.core.network.storage.impl

import android.net.Uri
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.example.b1void.core.network.storage.CloudStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class DropboxStorageSource @Inject constructor(
    private val dbxClient: DbxClientV2,
    private val requestConfig: DbxRequestConfig // Необходимо для инициализации DbxClient
) : CloudStorage {
    override suspend fun uploadFile(localUri: Uri, remotePath: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                // Предполагаем, что localUri указывает на локальный файл, который можно открыть
                val localFile = File(localUri.path!!) // Path может быть null, если URI не файловый
                val inputStream = localFile.inputStream()
                val uploadedFile = dbxClient.files().uploadBuilder(remotePath)
                    .uploadAndFinish(inputStream)
                inputStream.close()
                Result.success(Uri.parse(uploadedFile.pathLower)) // Dropbox возвращает pathLower
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadFile(remotePath: String, localUri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val outputStream = FileOutputStream(File(localUri.path!!))
                dbxClient.files().download(remotePath).download(outputStream)
                outputStream.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                dbxClient.files().deleteV2(remotePath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun listFiles(remotePath: String): Result<List<Uri>> {
        return withContext(Dispatchers.IO) {
            try {
                val listResult = dbxClient.files().listFolder(remotePath)
                val uris = listResult.entries.map { entry ->
                    Uri.parse(entry.pathLower)
                }
                Result.success(uris)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
