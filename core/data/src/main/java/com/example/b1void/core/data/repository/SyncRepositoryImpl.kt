package com.example.b1void.core.data.repository

import android.content.Context
import android.net.Uri
import com.example.b1void.core.data.database.dao.InspectorDao
import com.example.b1void.core.data.database.entity.InspectorEntity
import com.example.b1void.core.network.di.DropboxStorageQualifier
import com.example.b1void.core.network.di.FirebaseStorageQualifier
import com.example.b1void.core.network.storage.CloudStorage
import com.example.b1void.core.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    @FirebaseStorageQualifier private val firebaseStorage: CloudStorage,
    @DropboxStorageQualifier private val dropboxStorage: CloudStorage,
    private val inspectorDao: InspectorDao,
    @ApplicationContext private val context: Context
) : SyncRepository {
    override suspend fun syncPendingFiles(): Result<Unit> {
        return try {
            val pendingInspectors = inspectorDao.getAllInspectors().first().filter { it.localPhotoPath != null }

            pendingInspectors.forEach { inspectorEntity ->
                val localFile = File(inspectorEntity.localPhotoPath!!)
                if (localFile.exists()) {
                    val remotePath = "inspectors/${inspectorEntity.id}/${localFile.name}"
                    val uploadResult = firebaseStorage.uploadFile(Uri.fromFile(localFile), remotePath)

                    if (uploadResult.isSuccess) {
                        val updatedInspector = inspectorEntity.copy(photoPath = uploadResult.getOrNull()?.toString(), localPhotoPath = null)
                        inspectorDao.insertInspector(updatedInspector)
                        localFile.delete() // Удалить локальный файл после успешной загрузки
                    } else {
                        // Логировать ошибку, но продолжить
                        println("Failed to upload file for inspector ${inspectorEntity.id}: ${uploadResult.exceptionOrNull()?.message}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
