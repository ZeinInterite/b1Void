package com.example.b1void.core.data.repository

import com.example.b1void.core.domain.repository.FolderRepository
import com.example.b1void.core.model.FolderNode
import java.io.File
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor() : FolderRepository {
    override suspend fun getFolderTree(rootDir: File): List<FolderNode> {
        // TODO: Implement
        return emptyList()
    }

    override suspend fun moveFiles(filesToMove: List<File>, destinationDir: File): Boolean {
        // TODO: Implement
        return false
    }

    override suspend fun createFolder(folder: File): Result<Unit> {
        return try {
            if (folder.mkdirs()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFolder(folder: File): Result<Unit> {
        return try {
            if (folder.deleteRecursively()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
