package com.example.b1void.core.domain.repository

import com.example.b1void.core.model.FolderNode
import java.io.File

interface FolderRepository {
    suspend fun getFolderTree(rootDir: File): List<FolderNode>
    suspend fun moveFiles(filesToMove: List<File>, destinationDir: File): Boolean
    suspend fun createFolder(folder: File): Result<Unit>
    suspend fun deleteFolder(folder: File): Result<Unit>
}
