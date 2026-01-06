package com.example.b1void.core.network.storage

import android.net.Uri

interface CloudStorage {
    suspend fun uploadFile(localUri: Uri, remotePath: String): Result<Uri>
    suspend fun downloadFile(remotePath: String, localUri: Uri): Result<Unit>
    suspend fun deleteFile(remotePath: String): Result<Unit>
    suspend fun listFiles(remotePath: String): Result<List<Uri>>
}
