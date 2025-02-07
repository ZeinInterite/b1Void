
package com.example.b1void.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.example.b1void.DBX.DropboxClient
import com.example.b1void.tasks.CreateFolderTask
import com.example.b1void.tasks.DeleteTask
import com.example.b1void.tasks.DownloadTask
import com.example.b1void.tasks.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DropboxService private constructor(
    private val prefs: SharedPreferences,
    private val applicationContext: Context,  // Сохраняем контекст приложения
) {
    private var accessToken: String? = null
    private var dropboxClient: DbxClientV2? = null
    private val accessTokenKey = "access-token"
    private val inspectorDropboxPath = "/inspector_test_inspector"

    init {
        accessToken = retrieveAccessToken()
        if (accessToken != null) {
            dropboxClient = DropboxClient.getClient(accessToken!!)
        } else {
            Log.e("DropboxService", "Invalid access token")
        }

    }
    private fun retrieveAccessToken(): String? {
        return prefs.getString(accessTokenKey, null)
    }


    suspend fun loadDirectoryContent(directory: File, dropboxPath: String): List<File> = withContext(Dispatchers.IO){
        val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()
        val localFilesSet = filesAndDirs.map { it.name }.toSet()
        val files =  loadFromDropbox(dropboxPath)

        files.forEach { metadata ->
            val localPath = getAppDirectory().absolutePath + metadata.pathDisplay!!.removePrefix(inspectorDropboxPath)
            val file = File(localPath)
            if (metadata is FileMetadata) {
                if (!localFilesSet.contains(metadata.name)) {
                    downloadFile(metadata, file)
                } else {
                    Log.d("Dropbox", "File ${metadata.name} already exist on local")
                }
            } else {
                if(!file.exists()) {
                    file.mkdirs()
                }
                Log.d("Dropbox", "Folder ${metadata.name} already exist or created locally")
            }
        }


        filesAndDirs.forEach { localFile ->
            if (!files.any { it.pathDisplay?.endsWith(localFile.name) == true } && !localFile.isDirectory) {
                if (localFile.exists()) {
                    uploadFileToDropbox(localFile, dropboxPath)
                }
            }
        }
        return@withContext filesAndDirs
    }


    private suspend fun loadFromDropbox(dropboxPath: String) = withContext(Dispatchers.IO){
        return@withContext try {
            val files =  dropboxClient?.files()?.listFolder(dropboxPath)?.entries ?: emptyList()
            files
        } catch (e: Exception) {
            Log.e("DropboxService", "Error loading from dropbox", e)
            emptyList()
        }
    }

    private suspend fun downloadFile(metadata: FileMetadata, localFile: File) = withContext(Dispatchers.IO){
        try {
            DownloadTask(dropboxClient!!, metadata.pathLower, localFile, object : DownloadTask.TaskDelegate {
                override fun onDownloadComplete() {
                    Log.d("Dropbox", "File ${metadata.name} downloaded successfully to ${localFile.absolutePath}")
                }

                override fun onError(error: DbxException) {
                    Log.e("Dropbox", "Error downloading file ${metadata.name}", error)
                }
            }).execute().get()
        } catch (e: Exception) {
            Log.e("DropboxService", "Error downloading file", e)
        }

    }


    suspend fun createFolder(folderName: String, parentDir: File, dropboxFolderPath: String) = withContext(Dispatchers.IO){
        val newDir = File(parentDir, folderName)
        if (newDir.mkdir()) {
            createDropboxFolder(dropboxFolderPath)
        } else {
            throw Exception("Error creating local folder")
        }
    }

    suspend fun deleteFile(file: File, dropboxPath: String) = withContext(Dispatchers.IO){
        if (file.exists()) {
            if (file.isDirectory) {
                deleteDirectory(file)
                deleteFileFromDropbox(dropboxPath)
            } else {
                deleteFileFromDropbox(dropboxPath)
                file.delete()
            }
        } else {
            throw Exception("File does not exist")
        }

    }


    suspend fun createDropboxFolder(path: String) = withContext(Dispatchers.IO){
        dropboxClient?.let { client ->
            CreateFolderTask(client, object : CreateFolderTask.TaskDelegate {
                override fun onFolderCreated() {
                    Log.d("Dropbox", "Folder $path created successfully")
                }
                override fun onError(error: DbxException) {
                    if (error is CreateFolderErrorException) {
                        Log.d("Dropbox", "Folder $path already exists.")
                    } else if (error is InvalidAccessTokenException){
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                    } else {
                        Log.e("Dropbox", "Error creating folder", error)
                    }
                }
            }, path).execute().get()
        } ?: throw Exception("Dropbox Client is Null")
    }

    private suspend fun uploadFileToDropbox(file: File, dropboxPath: String)= withContext(Dispatchers.IO){
        dropboxClient?.let { client ->
            val uploadTask = UploadTask(client, file, dropboxPath, object : UploadTask.TaskDelegate {
                override fun onUploadComplete() {
                    Log.d("Dropbox", "File ${file.name} uploaded successfully")
                }

                override fun onError(error: DbxException) {
                    if(error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                    } else {
                        Log.e("Dropbox", "Error uploading file ${file.name}", error)
                    }
                }
            })
            uploadTask.execute().get()
        } ?: throw Exception("Dropbox Client is Null")
    }

    private suspend fun deleteFileFromDropbox(dropboxPath: String) = withContext(Dispatchers.IO) {
        dropboxClient?.let { client ->
            val deleteTask = DeleteTask(client, dropboxPath, object : DeleteTask.TaskDelegate {
                override fun onDeleteComplete() {
                    Log.d("Dropbox", "File/Folder $dropboxPath deleted")
                }
                override fun onError(error: DbxException) {
                    if(error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                    } else {
                        Log.e("Dropbox", "Error deleting file $dropboxPath", error)
                    }
                }
            })
            deleteTask.execute().get()
        } ?: throw Exception("Dropbox Client is Null")
    }

    fun getAppDirectory() = applicationContext.getExternalFilesDir(null)?.let { File(it, "InspectorAppFolder") }
        ?: File(applicationContext.filesDir, "InspectorAppFolder")

    fun getCurrentDropboxPath(directory: File, appDirectory: File): String {
        return if (directory == appDirectory) {
            inspectorDropboxPath
        } else {
            inspectorDropboxPath + directory.absolutePath.removePrefix(appDirectory.absolutePath)
        }
    }

    private fun deleteDirectory(directory: File) {
        val files = directory.listFiles()
        files?.forEach {
            if (it.isDirectory) {
                deleteDirectory(it)
            } else {
                it.delete()
            }
        }
        directory.delete()
    }


    companion object {
        @Volatile
        private var instance: DropboxService? = null
        fun getInstance(prefs: SharedPreferences, context: Context) : DropboxService {
            return instance ?: synchronized(this) {
                instance ?: DropboxService(prefs, context.applicationContext).also {  // Используем context.applicationContext
                    instance = it
                }
            }
        }
    }
}
