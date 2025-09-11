package com.example.b1void.data

import com.example.b1void.models.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FolderRepository {

    /**
     * Рекурсивно сканирует файловую систему, начиная с корневой директории, 
     * чтобы построить полное дерево папок.
     *
     * @param rootDir Корневая директория для сканирования.
     * @return Список узлов FolderNode верхнего уровня.
     */
    suspend fun getFolderTree(rootDir: File): List<FolderNode> = withContext(Dispatchers.IO) {
        val rootNode = FolderNode(file = rootDir, level = 0)
        scanDirectory(rootNode)
        rootNode.children
    }

    private fun scanDirectory(parentNode: FolderNode) {
        val directories = parentNode.file.listFiles { file -> file.isDirectory }?.sortedBy { it.name }
        directories?.forEach { dir ->
            val childNode = FolderNode(file = dir, level = parentNode.level + 1)
            parentNode.children.add(childNode)
            scanDirectory(childNode) // Рекурсивный вызов
        }
    }

    /**
     * Перемещает список файлов в папку назначения.
     *
     * @param filesToMove Список файлов для перемещения.
     * @param destinationDir Папка назначения.
     * @return true, если все файлы были успешно перемещены.
     */
    suspend fun moveFiles(filesToMove: List<File>, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        var allMovedSuccessfully = true
        filesToMove.forEach { file ->
            try {
                val destinationFile = File(destinationDir, file.name)
                if (!file.renameTo(destinationFile)) {
                    allMovedSuccessfully = false
                }
            } catch (e: Exception) {
                // Логируем ошибку, но не даем приложению упасть
                android.util.Log.e("FolderRepository", "Failed to move file: ${file.absolutePath}", e)
                allMovedSuccessfully = false
            }
        }
        return@withContext allMovedSuccessfully
    }
}
