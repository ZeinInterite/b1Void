package com.example.b1void.core.network.storage.impl

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.example.b1void.core.network.storage.CloudStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseStorageSource @Inject constructor(
    private val firebaseStorage: FirebaseStorage
) : CloudStorage {
    override suspend fun uploadFile(localUri: Uri, remotePath: String): Result<Uri> {
        return try {
            val ref = firebaseStorage.reference.child(remotePath)
            ref.putFile(localUri).await()
            val downloadUrl = ref.downloadUrl.await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(remotePath: String, localUri: Uri): Result<Unit> {
        return try {
            val ref = firebaseStorage.reference.child(remotePath)
            ref.getFile(localUri).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> {
        return try {
            val ref = firebaseStorage.reference.child(remotePath)
            ref.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remotePath: String): Result<List<Uri>> {
        return try {
            val ref = firebaseStorage.reference.child(remotePath)
            val listResult = ref.listAll().await()
            val uris = listResult.items.map { it.downloadUrl.await() }
            Result.success(uris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
