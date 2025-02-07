package com.example.b1void.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.Metadata
import com.example.b1void.DBX.DropboxClient
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import com.example.b1void.tasks.CreateFolderTask
import com.example.b1void.tasks.DeleteTask
import com.example.b1void.tasks.DownloadTask
import com.example.b1void.tasks.ListFolderTask
import com.example.b1void.tasks.UploadTask
import java.io.File
import java.util.LinkedList

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var fileAdapter: FileAdapter
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var addInspBtn: Button
    private lateinit var showInspBtn: Button
    private var accessToken: String? = null
    private var dropboxClient: DbxClientV2? = null
    private var inspectorDropboxPath = ""
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        addInspBtn = findViewById(R.id.add_inspection_button)
        showInspBtn = findViewById(R.id.show_inspection_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        addInspBtn.setOnClickListener {
            val intent = Intent(this, InspectionAddActivity::class.java)
            intent.putExtra("current_directory", getCurrentDirectory().absolutePath) // Передаем абсолютный путь
            startActivity(intent)
        }
        showInspBtn.setOnClickListener {
            val intent = Intent(this, WorkerActivity::class.java)
            startActivity(intent)
        }

        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

        appDirectory = getExternalFilesDir(null)?.let { File(it, "InspectorAppFolder") }
            ?: File(filesDir, "InspectorAppFolder")

        if (!appDirectory.exists()) {
            if (appDirectory.mkdirs()) {
                Toast.makeText(this, "Папка приложения создана", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось создать папку приложения", Toast.LENGTH_SHORT).show()
            }
        }

        accessToken = retrieveAccessToken()
        if (accessToken == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        dropboxClient = DropboxClient.getClient(accessToken!!)
        val inspectorName = "inspector_test_inspector"

        // Формируем путь без корня InspectorApp
        inspectorDropboxPath = "/$inspectorName"

        createDropboxFolder(inspectorDropboxPath)
        loadDirectoryContent(appDirectory)

        createFolderButton.setOnClickListener {
            showCreateFolderDialog(getCurrentDirectory())
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadDirectoryContent(getCurrentDirectory())
        }
    }

    private fun createDropboxFolder(path: String) {
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
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error creating folder", error)
                        Toast.makeText(
                            this@FileManagerActivity,
                            "Ошибка при создании папки в Dropbox",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, path).execute()
        }
    }

    private fun retrieveAccessToken(): String? {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getString("access-token", null).also {
            if (it == null) Log.d("AccessToken Status", "No token found")
            else Log.d("AccessToken Status", "Token exists")
        }
    }

    private fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: appDirectory
    }

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        if (directory.exists() && directory.isDirectory) {
            val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()

            val dropboxPath = if (directory == appDirectory) {
                inspectorDropboxPath
            } else {
                inspectorDropboxPath + directory.absolutePath.removePrefix(appDirectory.absolutePath)
            }

            loadFromDropbox(dropboxPath, filesAndDirs)

            if (!this::fileAdapter.isInitialized) {
                fileAdapter = FileAdapter(filesAndDirs, this) { file ->
                    if (file.isDirectory) {
                        openDirectory(file)
                    } else {
                        Toast.makeText(this, "Выбран файл: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                recyclerView.adapter = fileAdapter
            } else {
                fileAdapter.updateFiles(filesAndDirs)
            }
            title = directory.name
        } else {
            Toast.makeText(this, "Папка не найдена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFromDropbox(dropboxPath: String, localFiles: List<File>) {
        dropboxClient?.let { client ->
            Log.d("Dropbox Path", "Loading files from: $dropboxPath")
            ListFolderTask(client, object : ListFolderTask.TaskDelegate {
                override fun onFilesReceived(files: List<Metadata>) {
                    val localFilesSet = localFiles.map { it.name }.toSet() // Set для быстрого поиска

                    if (files.isNotEmpty()) {
                        files.forEach { metadata ->
                            val localPath = appDirectory.absolutePath + metadata.pathDisplay!!.removePrefix(inspectorDropboxPath)
                            val file = File(localPath)
                            if (metadata is FileMetadata) {
                                if (!localFilesSet.contains(metadata.name)) {
                                    Log.d("Dropbox", "Downloading ${metadata.name} to $localPath")
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

                        localFiles.forEach { localFile ->
                            if (!files.any { it.pathDisplay?.endsWith(localFile.name) == true } && !localFile.isDirectory) {
                                if (localFile.exists()) {
                                    Log.d("Dropbox", "Uploading ${localFile.name}")
                                    uploadFileToDropbox(localFile)
                                }
                            }
                        }
                    } else {
                        localFiles.forEach { localFile ->
                            if (!localFile.isDirectory) {
                                Log.d("Dropbox", "Uploading ${localFile.name}")
                                uploadFileToDropbox(localFile)
                            }
                        }
                        Log.d("Dropbox", "There is no files in $dropboxPath")
                    }
                    fileAdapter.updateFiles(localFiles)
                    swipeRefreshLayout.isRefreshing = false
                }

                override fun onError(error: DbxException) {
                    swipeRefreshLayout.isRefreshing = false
                    if(error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error receiving list of files", error)
                        Toast.makeText(
                            this@FileManagerActivity,
                            "Ошибка загрузки списка файлов с Dropbox",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, dropboxPath).execute()
        }
    }

    private fun downloadFile(metadata: FileMetadata, localFile: File) {
        dropboxClient?.let { client ->
            DownloadTask(client, metadata.pathLower, localFile, object : DownloadTask.TaskDelegate {
                override fun onDownloadComplete() {
                    Log.d("Dropbox", "File ${metadata.name} downloaded successfully to ${localFile.absolutePath}")
                    loadDirectoryContent(getCurrentDirectory())
                }

                override fun onError(error: DbxException) {
                    if (error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error downloading file ${metadata.name}", error)
                        Toast.makeText(
                            this@FileManagerActivity,
                            "Ошибка при загрузке файла из Dropbox",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }).execute()
        }
    }

    private fun openDirectory(file: File) {
        if (directoryStack.isEmpty() || directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    override fun onBackPressed() {
        if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            val previousDirectory = directoryStack.lastOrNull() ?: appDirectory
            loadDirectoryContent(previousDirectory)
        } else {
            super.onBackPressed()
        }
    }

    private fun showCreateFolderDialog(parentDir: File) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        builder.setTitle("Создать новую папку")
        builder.setView(input)

        builder.setPositiveButton("Создать") { dialog, _ ->
            val folderName = input.text.toString()
            val newDir = File(parentDir, folderName)

            val dropboxFolderPath = if (parentDir == appDirectory) {
                inspectorDropboxPath + "/$folderName"
            } else {
                inspectorDropboxPath + parentDir.absolutePath.removePrefix(appDirectory.absolutePath) + "/$folderName"
            }

            if (newDir.mkdir()) {
                createDropboxFolder(dropboxFolderPath)
                loadDirectoryContent(parentDir)
            } else {
                Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun uploadFileToDropbox(file: File) {
        val dropboxPath =  if (directoryStack.isEmpty() || directoryStack.last() == appDirectory) {
            inspectorDropboxPath + "/" + file.name
        } else {
            inspectorDropboxPath + file.absolutePath.removePrefix(appDirectory.absolutePath)
        }

        dropboxClient?.let { client ->
            val uploadTask = UploadTask(client, file, dropboxPath, object : UploadTask.TaskDelegate {
                override fun onUploadComplete() {
                    Log.d("Dropbox", "File ${file.name} uploaded successfully")
                    loadDirectoryContent(getCurrentDirectory())
                }

                override fun onError(error: DbxException) {
                    if(error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error uploading file ${file.name}", error)
                        Toast.makeText(
                            this@FileManagerActivity,
                            "Ошибка при загрузке файла",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
            uploadTask.execute()
        }
    }

    fun deleteFileFromDropbox(file: File) {
        val dropboxPath = if (directoryStack.isEmpty() || directoryStack.last() == appDirectory) {
            inspectorDropboxPath + "/" + file.name
        } else {
            inspectorDropboxPath + file.absolutePath.removePrefix(appDirectory.absolutePath)
        }

        dropboxClient?.let { client ->
            val deleteTask = DeleteTask(client, dropboxPath, object : DeleteTask.TaskDelegate {
                override fun onDeleteComplete() {
                    Log.d("Dropbox", "File/Folder $dropboxPath deleted")
                    loadDirectoryContent(getCurrentDirectory())
                }
                override fun onError(error: DbxException) {
                    if(error is InvalidAccessTokenException) {
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error deleting file $dropboxPath", error)
                        Toast.makeText(
                            this@FileManagerActivity,
                            "Ошибка при удалении файла",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
            deleteTask.execute()
        }
    }
    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
