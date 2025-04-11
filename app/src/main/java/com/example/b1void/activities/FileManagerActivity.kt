
package com.example.b1void.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var fileAdapter: FileAdapter
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var zipDirectory: File
    private lateinit var addInspBtn: Button
    private lateinit var showInspBtn: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shareButton: Button


    private var imgGalUriString: String? = null
    private var imgGalUri: Uri? = null
    private var selectedFolderGalUri: Uri? = null
    private val PICK_FOLDER_REQUEST = 2

    //  Для Multiple Select
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>() // Use a Set for efficient contains/remove

    private var currentFileForMenu: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        addInspBtn = findViewById(R.id.add_inspection_button)
        showInspBtn = findViewById(R.id.show_inspection_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        shareButton = findViewById(R.id.share_button)


        if(intent.getStringExtra("imageUri") != null){
            imgGalUriString = intent.getStringExtra("imageUri")
            imgGalUri = Uri.parse(imgGalUriString)


            showCreateFolderDialog { newDir ->
                saveImageToDirectory(newDir)
            }

        }

        addInspBtn.setOnClickListener {
            val intent = Intent(this, CameraV2Activity::class.java)
            intent.putExtra("current_directory", getCurrentDirectory().absolutePath)
            startActivity(intent)
        }
        showInspBtn.setOnClickListener {
            val intent = Intent(this, WorkerActivity::class.java)
            startActivity(intent)
        }

        shareButton.setOnClickListener {
            shareSelectedFiles()
        }

        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

        // Use internal storage
        appDirectory = File(filesDir, "InspectorAppFolder")

        if (!appDirectory.exists()) {
            try {
                if (appDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка приложения создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Не удалось создать папку приложения",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка: Недостаточно прав для создания папки",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка ввода/вывода при создании папки",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        zipDirectory = File(filesDir, "zipFolder")

        if (!zipDirectory.exists()) {
            try {
                if (zipDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка zip-файлов создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Не удалось создать папку zip-файлов",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка: Недостаточно прав для создания папки zip-файлов ",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка ввода/вывода при создании папки zip-файлов",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        loadDirectoryContent(appDirectory)

        createFolderButton.setOnClickListener {
            showCreateFolderDialog { newDir ->
                loadDirectoryContent(getCurrentDirectory())
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadDirectoryContent(getCurrentDirectory())
        }
    }

    private fun saveImageToDirectory(directory: File) { // Изменили название, чтобы было понятнее
        val fname = "Image-" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, fname)

        try {
            // Получаем InputStream из URI изображения
            val inputStream = contentResolver.openInputStream(imgGalUri!!)

            // Создаем OutputStream для записи данных в файл
            val outputStream = FileOutputStream(file)

            // Буфер для чтения и записи данных (рекомендуемый размер)
            val buffer = ByteArray(4096) // 4KB

            var bytesRead: Int
            while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            // Закрываем потоки (очень важно!)
            outputStream.close()
            inputStream.close()

            runOnUiThread {
                Toast.makeText(this, "Изображение сохранено в: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                loadDirectoryContent(directory) // Обновляем контент
            }

        } catch (e: IOException) {
            // Обрабатываем ошибки ввода/вывода
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Ошибка при сохранении изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.file_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rename -> {
                showRenameDialog(currentFileForMenu!!)
                true
            }
            R.id.action_delete -> {
                deleteFile(currentFileForMenu!!)
                true
            }
            R.id.action_share_single -> {
                shareFile(currentFileForMenu!!)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: appDirectory
    }

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        Thread { // Run the loading in a background thread
            if (directory.exists() && directory.isDirectory) {
                val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()

                runOnUiThread { // Post the UI update back to the main thread
                    if (!this::fileAdapter.isInitialized) {
                        fileAdapter = FileAdapter(
                            filesAndDirs,
                            this,
                            { file -> // single click
                                if (isSelectionMode) {
                                    toggleFileSelection(file)
                                } else if (file.isDirectory) {
                                    openDirectory(file)
                                } else {
                                    // Check if the file is an image, and open preview if so.
                                    if (fileAdapter.isImage(file)) {
                                        openImagePreview(file)
                                    } else {
                                        Toast.makeText(
                                            this,
                                            "Выбран файл: ${file.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            { file ->  //long click
                                if (!isSelectionMode) {
                                    startSelectionMode()
                                    toggleFileSelection(file)
                                }
                            },
                            { file, isSelected -> //selection change
                                onFileSelectionChanged(file, isSelected)
                            },
                            { file -> // show option click
                                currentFileForMenu = file
                                registerForContextMenu(recyclerView)
                                openContextMenu(recyclerView)
                            },
                            selectedFiles // Pass selectedFiles to the adapter
                        )
                        recyclerView.adapter = fileAdapter
                    } else {
                        fileAdapter.updateFiles(filesAndDirs)
                        fileAdapter.notifyDataSetChanged()
                    }
                    title = directory.name
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Папка не найдена", Toast.LENGTH_SHORT).show()
                }
            }
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }.start()

    }

    // Method to open the image preview activity
    private fun openImagePreview(imageFile: File) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        intent.putExtra("image_path", imageFile.absolutePath)
        startActivity(intent)
    }

    private fun openDirectory(file: File) {
        if (directoryStack.isEmpty() || directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            clearSelection()
        } else if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            val previousDirectory = directoryStack.lastOrNull() ?: appDirectory
            loadDirectoryContent(previousDirectory)
        } else {
            super.onBackPressed()
        }
    }

    private fun showCreateFolderDialog(onFolderCreated: (File) -> Unit) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        builder.setTitle("Создать новую папку")
        builder.setView(input)

        builder.setPositiveButton("Создать") { dialog, _ ->
            val folderName = input.text.toString()
            val newDir = File(getCurrentDirectory(), folderName)

            try {
                if (newDir.mkdir()) {
                    onFolderCreated(newDir) // Call the callback with the newly created directory
                    //loadDirectoryContent(getCurrentDirectory()) // Обновляем контент текущей директории
                } else {
                    Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating folder: ${e.message}")
                Toast.makeText(this, "Ошибка безопасности при создании папки", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating folder: ${e.message}")
                Toast.makeText(this, "Ошибка ввода/вывода при создании папки", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showRenameDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.setText(file.name)
        builder.setTitle("Переименовать")
        builder.setView(input)

        builder.setPositiveButton("Переименовать") { dialog, _ ->
            val newName = input.text.toString()


            val newFile = File(file.parentFile, newName)

            try {
                if (file.renameTo(newFile)) {
                    loadDirectoryContent(getCurrentDirectory())
                } else {
                    Toast.makeText(this, "Ошибка при переименовании", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException renaming file: ${e.message}")
                Toast.makeText(this, "Ошибка безопасности при переименовании файла", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException renaming file: ${e.message}")
                Toast.makeText(this, "Ошибка ввода/вывода при переименовании файла", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun deleteFile(file: File) {
        try {
            if (file.delete()) {
                Log.d("File Manager", "File ${file.name} deleted successfully")
                loadDirectoryContent(getCurrentDirectory())
            } else {
                Log.e("File Manager", "Error deleting file ${file.name}")
                Toast.makeText(
                    this@FileManagerActivity,
                    "Ошибка при удалении файла",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
            Toast.makeText(this, "Ошибка безопасности при удалении файла", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {

        val mimeType: String

        val uri: Uri

        try {
            if (file.isDirectory) {

                val zipFileName = "${file.name}.zip"
                val zipFile = File(zipDirectory, zipFileName)

                ZipFile(zipFile).addFolder(file)


                uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    zipFile
                )
                mimeType = "application/zip"

                zipFile.deleteOnExit()

            } else {
                uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                mimeType = "image/*"
            }

            ShareCompat.IntentBuilder(this)
                .setStream(uri)
                .setType(mimeType)
                .setChooserTitle("Поделиться файлом")
                .startChooser()
        } catch (e: IllegalArgumentException) {
            Log.e("FileManager", "FileProvider error: ${e.message}")
            Toast.makeText(this, "Ошибка при обмене файлом", Toast.LENGTH_SHORT).show()
        }
    }

    // Multiple Selection Logic

    private fun startSelectionMode() {
        isSelectionMode = true
        shareButton.visibility = View.VISIBLE
        fileAdapter.notifyDataSetChanged()
    }

    private fun clearSelection() {
        isSelectionMode = false
        shareButton.visibility = View.GONE
        selectedFiles.clear()
        fileAdapter.notifyDataSetChanged()
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.notifyDataSetChanged()

        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }

    private fun onFileSelectionChanged(file: File, isSelected: Boolean) {
        if (isSelected) {
            selectedFiles.add(file)
        } else {
            selectedFiles.remove(file)
        }

        if (isSelectionMode && selectedFiles.isEmpty()) {
            clearSelection()
        }

        shareButton.visibility = if (selectedFiles.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun zipFolder(folderToZip: File, zipFile: File) {
        try {
            val zipParameters = ZipParameters()
            zipParameters.compressionMethod = CompressionMethod.DEFLATE
            zipParameters.compressionLevel = CompressionLevel.NORMAL

            ZipFile(zipFile.absolutePath).addFolder(folderToZip, zipParameters)

        } catch (e: Exception) {
            Log.e("FileManager", "Error zipping folder: ${e.message}")
            Toast.makeText(this, "Ошибка при архивации папки", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSelectedFiles() {
        if (selectedFiles.isNotEmpty()) {
            Thread {
                val filesUris = ArrayList<Uri>()
                val tempDir = File(cacheDir, "temp_zip")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                var errorOccurred = false // Флаг для отслеживания ошибок

                try {
                    for (file in selectedFiles) {
                        try { // Дополнительный try-catch для обработки ошибок архивации каждого файла
                            if (file.isDirectory) {
                                // Zip the folder
                                val zipFile = File(tempDir, "${file.name}.zip")
                                zipFolder(file, zipFile)

                                if (zipFile.exists()) {  //  Проверка существования ZIP-архива
                                    val uri = FileProvider.getUriForFile(
                                        this,
                                        "${packageName}.fileprovider", // Use your app's package name
                                        zipFile
                                    )
                                    filesUris.add(uri)
                                } else {
                                    Log.e("FileManager", "Failed to create ZIP archive for ${file.name}")
                                    errorOccurred = true // Устанавливаем флаг ошибки
                                    runOnUiThread {
                                        Toast.makeText(
                                            this,
                                            "Ошибка при создании архива для ${file.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    this,
                                    "${packageName}.fileprovider", // Use your app's package name
                                    file
                                )
                                filesUris.add(uri)
                            }
                        } catch (e: Exception) {
                            Log.e("FileManager", "Error processing file ${file.name}: ${e.message}")
                            errorOccurred = true // Устанавливаем флаг ошибки
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "Ошибка при обработке файла ${file.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    if (filesUris.isNotEmpty()) { //  Проверка, что есть что отправлять
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
                        shareIntent.type = "*/*" // Используем универсальный MIME type
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesUris)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        runOnUiThread {
                            startActivity(Intent.createChooser(shareIntent, "Share selected files"))
                            clearSelection()  // Clear selection after sharing
                        }
                    } else {
                        runOnUiThread {
                            if (errorOccurred) {
                                Toast.makeText(
                                    this,
                                    "Не удалось подготовить ни один файл для отправки.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(this, "Нечего отправлять.", Toast.LENGTH_SHORT).show()
                            }

                        }
                    }


                } catch (e: IllegalArgumentException) {
                    Log.e("FileManager", "FileProvider error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка при обмене файлами", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    // Delete temp files
                    tempDir.listFiles()?.forEach { it.delete() }
                }
            }.start()

        } else {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
        }
    }
}
