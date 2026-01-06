package com.example.b1void.data

import com.example.b1void.core.model.FolderNode
import java.io.File

// Stubbed class to fix build
class FolderRepository {
    suspend fun getFolderTree(root: File): List<FolderNode> {
        return emptyList()
    }

    suspend fun moveFiles(files: List<File>, destination: File): Boolean {
        return false
    }
}