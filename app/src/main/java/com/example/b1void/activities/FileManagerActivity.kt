
package com.example.b1void.activities

import android.app.Activity
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
    private lateinit var captureButton: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button
    private lateinit var moveButton: Button

    private val OPEN_FILE = 1

    private var imageUri: Uri? = null

    private var imgGalUriString: String? = null
    private var imgGalUri: Uri? = null

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private var currentFileForMenu: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        shareButton = findViewById(R.id.share_button)
        deleteButton = findViewById(R.id.delete_button)
        moveButton = findViewById(R.id.move_button)

        val uploadButton = findViewById<View>(R.id.upload_button)

        if (intent.getStringExtra("imageUri") != null) {
            imgGalUriString = intent.getStringExtra("imageUri")
            imgGalUri = Uri.parse(imgGalUriString)

            showCreateFolderDialog { newDir ->
                saveImageToDirectory(newDir)
            }
        }

        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, OPEN_FILE)
        }

        captureButton.setOnClickListener {
            val intent = Intent(this, CameraV2Activity::class.java)
            intent.putExtra("save_path", getCurrentDirectory().absolutePath)
            startActivity(intent)
        }

        shareButton.setOnClickListener {
            shareSelectedFiles()
        }

        deleteButton.setOnClickListener {
            deleteSelectedFiles()
        }

        moveButton.setOnClickListener {
            showMoveDialog()
        }

        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

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



    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            // Handle multiple images selection
            if (data.clipData != null) {
                val clipData = data.clipData
                val count = clipData!!.itemCount

                val uris = mutableListOf<Uri>()
                for (i in 0 until count) {
                    val imageUri = clipData.getItemAt(i).uri
                    uris.add(imageUri)
                }
                showCreateFolderDialog { newDir ->
                    saveImagesToDirectory(newDir, uris)
                }


            } else if (data.data != null) {
                // Handle single image selection (fallback)
                imageUri = data.data
                val uris = mutableListOf<Uri>()
                uris.add(imageUri!!)
                showCreateFolderDialog { newDir ->
                    saveImagesToDirectory(newDir, uris)
                }
            }
        }
    }

    private fun saveImagesToDirectory(directory: File, uris: List<Uri>) {
        for (uri in uris) {
            val fname = "Image-" + System.currentTimeMillis() + ".jpg"
            val file = File(directory, fname)

            try {
                // Получаем InputStream из URI изображения
                val inputStream = contentResolver.openInputStream(uri)

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
                return // Stop processing if there's an error
            }
        }

        runOnUiThread {
            loadDirectoryContent(directory) // Обновляем контент
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
                                onItemClick(file)
                            },
                            { file ->  //long click
                                onItemLongClick(file)
                            },
                            { file -> // show option click
                                currentFileForMenu = file
                                registerForContextMenu(recyclerView)
                                openContextMenu(recyclerView)
                            },
                            isSelectionMode,
                            selectedFiles // Pass selectedFiles to the adapter
                        )
                        recyclerView.adapter = fileAdapter
                    } else {
                        fileAdapter.isSelectionMode = isSelectionMode
                        fileAdapter.selectedFiles = selectedFiles
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

    private fun onItemClick(file: File) {
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
    }

    private fun onItemLongClick(file: File) {
        if (!isSelectionMode) {
            startSelectionMode()
            toggleFileSelection(file)
        }
    }

    // Method to open the image preview activity
    private fun openImagePreview(imageFile: File) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        // Create a list containing only the clicked image's path
        val imagePaths = arrayListOf(imageFile.absolutePath)
        intent.putStringArrayListExtra("image_paths", imagePaths)
        intent.putExtra("current_image_index", 0) // Always start at the first image (index 0)
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
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                if (file.delete()) {
                    Log.d("File Manager", "File ${file.name} deleted successfully")
                } else {
                    Log.e("File Manager", "Error deleting file ${file.name}")
                }
            }
            loadDirectoryContent(getCurrentDirectory())
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
        }
    }


    private fun deleteDirectory(directory: File): Boolean {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    if (!file.delete()) {
                        Log.e("FileManager", "Failed to delete file: " + file.absolutePath)
                        return false
                    }
                }
            }
        }
        return directory.delete()
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
        deleteButton.visibility = View.VISIBLE // Показываем кнопку удаления
        moveButton.visibility = View.VISIBLE   // Показываем кнопку перемещения
        loadDirectoryContent(getCurrentDirectory()) //refresh adapter
    }

    private fun clearSelection() {
        isSelectionMode = false
        shareButton.visibility = View.GONE
        deleteButton.visibility = View.GONE  // Скрываем кнопку удаления
        moveButton.visibility = View.GONE    // Скрываем кнопку перемещения
        selectedFiles.clear()
        loadDirectoryContent(getCurrentDirectory()) //refresh adapter
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.notifyItemChanged(fileAdapter.files.indexOf(file))
        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }

    private fun deleteSelectedFiles() {
        if (selectedFiles.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Удалить выбранные элементы?")
                .setMessage("Вы уверены, что хотите удалить выбранные элементы?")
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    Thread {
                        selectedFiles.forEach { file ->
                            try {
                                if (file.isDirectory) {
                                    deleteDirectory(file)
                                } else {
                                    if (file.delete()) {
                                        Log.d("File Manager", "File ${file.name} deleted successfully")
                                    } else {
                                        Log.e("File Manager", "Error deleting file ${file.name}")
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@FileManagerActivity,
                                                "Не удалось удалить файл ${file.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                runOnUiThread { // Обновляем UI в основном потоке после каждого удаления
                                    loadDirectoryContent(getCurrentDirectory()) // Обновляем RecyclerView
                                }

                            } catch (e: SecurityException) {
                                Log.e("FileManager", "SecurityException deleting file: ${e.message}")
                                runOnUiThread {
                                    Toast.makeText(
                                        this@FileManagerActivity,
                                        "Ошибка безопасности при удалении файла ${file.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        runOnUiThread { // Обновление UI в основном потоке
                            Toast.makeText(
                                this@FileManagerActivity,
                                "Выбранные файлы удалены",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearSelection()
                            loadDirectoryContent(getCurrentDirectory())
                        }
                    }.start()
                }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        } else {
            Toast.makeText(this, "Не выбраны файлы для удаления", Toast.LENGTH_SHORT).show()
        }
    }



                                // Move selected files
    private fun showMoveDialog() {
        if (selectedFiles.isNotEmpty()) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Выберите папку для перемещения")

            // Создаем список папок для отображения в диалоговом окне
            val directories = getCurrentDirectory().listFiles { file -> file.isDirectory }
            val directoryNames = directories?.map { it.name }?.toTypedArray() ?: emptyArray()

            if (directoryNames.isNotEmpty()) {
                builder.setItems(directoryNames) { dialog, which ->
                    val destinationDirectory = directories!![which]
                    moveSelectedFiles(destinationDirectory)
                    dialog.dismiss()
                }

                builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
                builder.show()
            } else {
                Toast.makeText(this, "Нет доступных папок для перемещения", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Не выбраны файлы для перемещения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveSelectedFiles(destinationDirectory: File) {
        Thread {
            selectedFiles.forEach { file ->
                val newFile = File(destinationDirectory, file.name)
                try {
                    if (file.renameTo(newFile)) {
                        Log.d("FileManager", "File ${file.name} moved successfully to ${destinationDirectory.absolutePath}")
                    } else {
                        Log.e("FileManager", "Error moving file ${file.name} to ${destinationDirectory.absolutePath}")
                        runOnUiThread { // Обновление UI в основном потоке
                            Toast.makeText(
                                this@FileManagerActivity,
                                "Ошибка при перемещении файла ${file.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("FileManager", "SecurityException moving file: ${e.message}")
                    runOnUiThread { // Обновление UI в основном потоке
                        Toast.makeText(this, "Ошибка безопасности при перемещении файла ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    Log.e("FileManager", "IOException moving file: ${e.message}")
                    runOnUiThread { // Обновление UI в основном потоке
                        Toast.makeText(this, "Ошибка ввода/вывода при перемещении файла ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            runOnUiThread { // Обновление UI в основном потоке
                Toast.makeText(
                    this@FileManagerActivity,
                    "Выбранные файлы перемещены",
                    Toast.LENGTH_SHORT
                ).show()
                clearSelection()
                loadDirectoryContent(getCurrentDirectory())
            }
        }.start()
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
                    tempDir.listFiles()?.forEach { it.delete() }
                }
            }.start()

        } else {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
        }
    }
}
