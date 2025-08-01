
package com.example.b1void.services

import android.content.Context
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.Metadata
import com.example.b1void.DBX.DropboxClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxService @Inject constructor(
    private val context: Context
) {
    
    private var dropboxClient: DbxClientV2? = null
    private var accessToken: String? = null
    private var inspectorDropboxPath: String = ""
    
    suspend fun initialize(accessToken: String, inspectorName: String = "inspector_test_inspector") {
        this.accessToken = accessToken
        this.inspectorDropboxPath = "/$inspectorName"
        this.dropboxClient = DropboxClient.getClient(accessToken)
        
        // Создаем папку в Dropbox при инициализации
        createDropboxFolder(inspectorDropboxPath)
    }
    
    fun getAppDirectory(): File {
        return context.getExternalFilesDir(null)?.let { File(it, "InspectorAppFolder") }
            ?: File(context.filesDir, "InspectorAppFolder").also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
    }
    
    fun getCurrentDropboxPath(directory: File, appDirectory: File): String {
        return if (directory == appDirectory) {
            inspectorDropboxPath
        } else {
            inspectorDropboxPath + directory.absolutePath.removePrefix(appDirectory.absolutePath)
        }
    }
    
    suspend fun loadDirectoryContent(directory: File, dropboxPath: String): List<File> = withContext(Dispatchers.IO) {
        try {
            val localFiles = directory.listFiles()?.toList() ?: emptyList()
            
            // Загружаем файлы из Dropbox
            val dropboxFiles = getDropboxFiles(dropboxPath)
            
            // Синхронизируем локальные и удаленные файлы
            syncFiles(localFiles, dropboxFiles, directory, dropboxPath)
            
            // Возвращаем обновленный список локальных файлов
            directory.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error loading directory content")
            throw e
        }
    }
    
    private suspend fun getDropboxFiles(dropboxPath: String): List<Metadata> = withContext(Dispatchers.IO) {
        try {
            val client = dropboxClient ?: throw IllegalStateException("Dropbox client not initialized")
            
            val result = client.files().listFolder(dropboxPath)
            result.entries.toList()
        } catch (e: DbxException) {
            when (e) {
                is InvalidAccessTokenException -> {
                    Timber.e("Invalid access token")
                    throw e
                }
                else -> {
                    Timber.e(e, "Error getting Dropbox files")
                    emptyList()
                }
            }
        }
    }
    
    private suspend fun syncFiles(
        localFiles: List<File>,
        dropboxFiles: List<Metadata>,
        directory: File,
        dropboxPath: String
    ) = withContext(Dispatchers.IO) {
        val localFilesSet = localFiles.map { it.name }.toSet()
        val appDirectory = getAppDirectory()
        
        // Загружаем файлы из Dropbox, которых нет локально
        dropboxFiles.forEach { metadata ->
            val localPath = appDirectory.absolutePath + 
                metadata.pathDisplay!!.removePrefix(inspectorDropboxPath)
            val file = File(localPath)
            
            when (metadata) {
                is FileMetadata -> {
                    if (!localFilesSet.contains(metadata.name)) {
                        downloadFile(metadata, file)
                    }
                }
                else -> {
                    if (!file.exists()) {
                        file.mkdirs()
                    }
                }
            }
        }
        
        // Загружаем локальные файлы в Dropbox
        localFiles.forEach { localFile ->
            if (!localFile.isDirectory && 
                !dropboxFiles.any { it.pathDisplay?.endsWith(localFile.name) == true }) {
                uploadFile(localFile, dropboxPath)
            }
        }
    }
    
    suspend fun createFolder(folderName: String, parentDir: File, dropboxPath: String) = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parentDir, folderName)
            if (newDir.mkdir()) {
                val dropboxFolderPath = "$dropboxPath/$folderName"
                createDropboxFolder(dropboxFolderPath)
            } else {
                throw Exception("Failed to create local folder")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating folder")
            throw e
        }
    }
    
    suspend fun deleteFile(file: File, dropboxPath: String) = withContext(Dispatchers.IO) {
        try {
            val client = dropboxClient ?: throw IllegalStateException("Dropbox client not initialized")
            val appDirectory = getAppDirectory()
            
            val dropboxFilePath = if (file.parentFile == appDirectory) {
                "$dropboxPath/${file.name}"
            } else {
                dropboxPath + file.absolutePath.removePrefix(appDirectory.absolutePath)
            }
            
            client.files().deleteV2(dropboxFilePath)
            
            // Удаляем локальный файл
            if (file.exists()) {
                file.delete()
            }
        } catch (e: DbxException) {
            when (e) {
                is InvalidAccessTokenException -> throw e
                else -> {
                    Timber.e(e, "Error deleting file")
                    throw Exception("Failed to delete file")
                }
            }
        }
    }
    
    private suspend fun createDropboxFolder(path: String) = withContext(Dispatchers.IO) {
        try {
            val client = dropboxClient ?: throw IllegalStateException("Dropbox client not initialized")
            client.files().createFolderV2(path)
            Timber.d("Dropbox folder created: $path")
        } catch (e: CreateFolderErrorException) {
            Timber.d("Dropbox folder already exists: $path")
        } catch (e: DbxException) {
            Timber.e(e, "Error creating Dropbox folder")
            throw e
        }
    }
    
    private suspend fun downloadFile(metadata: FileMetadata, localFile: File) = withContext(Dispatchers.IO) {
        try {
            val client = dropboxClient ?: throw IllegalStateException("Dropbox client not initialized")
            
            val outputStream = localFile.outputStream()
            client.files().download(metadata.pathLower, metadata.rev).download(outputStream)
            outputStream.close()
            
            Timber.d("File downloaded: ${metadata.name}")
        } catch (e: DbxException) {
            Timber.e(e, "Error downloading file: ${metadata.name}")
            throw e
        }
    }
    
    private suspend fun uploadFile(file: File, dropboxPath: String) = withContext(Dispatchers.IO) {
        try {
            val client = dropboxClient ?: throw IllegalStateException("Dropbox client not initialized")
            val appDirectory = getAppDirectory()
            
            val dropboxFilePath = if (file.parentFile == appDirectory) {
                "$dropboxPath/${file.name}"
            } else {
                dropboxPath + file.absolutePath.removePrefix(appDirectory.absolutePath)
            }
            
            val inputStream = file.inputStream()
            client.files().uploadBuilder(dropboxFilePath).uploadAndFinish(inputStream)
            inputStream.close()
            
            Timber.d("File uploaded: ${file.name}")
        } catch (e: DbxException) {
            Timber.e(e, "Error uploading file: ${file.name}")
            throw e
        }
    }
}
