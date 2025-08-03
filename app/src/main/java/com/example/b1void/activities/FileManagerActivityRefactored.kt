package com.example.b1void.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import com.example.b1void.utils.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

class FileManagerActivityRefactored : AppCompatActivity() {

    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var captureButton: Button
    private lateinit var uploadButton: View
    private lateinit var sortButton: ImageButton
    private lateinit var sizeButton: ImageButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var titleTextView: TextView
    private lateinit var buttonContainer: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button
    private lateinit var moveButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var clearSelectionButton: Button
    private lateinit var dynamicSeekBarContainer: View
    private lateinit var dynamicSeekBar: SeekBar

    // Managers
    private lateinit var fileAdapter: FileAdapter
    private lateinit var selectionManager: SelectionManager
    private lateinit var gestureHandler: GestureHandler
    private lateinit var dynamicSeekBarManager: DynamicSeekBarManager
    private lateinit var fullscreenImageManager: FullscreenImageManager

    // Data
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var zipDirectory: File
    private lateinit var sharedPreferences: SharedPreferences
    private var sortAscending = false

    // Constants
    private val OPEN_FILE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initializeViews()
        initializeManagers()
        setupButtons()
        setupRecyclerView()
        setupDirectories()
        loadDirectoryContent(appDirectory)
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        uploadButton = findViewById(R.id.upload_button)
        sortButton = findViewById(R.id.sort_button)
        sizeButton = findViewById(R.id.size_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        titleTextView = findViewById(R.id.titleTextView)
        buttonContainer = findViewById(R.id.button_container)
        selectionToolbar = findViewById(R.id.selection_toolbar)
        shareButton = findViewById(R.id.share_button)
        deleteButton = findViewById(R.id.delete_button)
        moveButton = findViewById(R.id.move_button)
        selectAllButton = findViewById(R.id.select_all_button)
        clearSelectionButton = findViewById(R.id.clear_selection_button)
        dynamicSeekBarContainer = findViewById(R.id.dynamic_seekbar_wrapper)
        dynamicSeekBar = findViewById(R.id.dynamic_seekbar)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun initializeManagers() {
        // Инициализация менеджеров после создания fileAdapter
        fileAdapter = FileAdapter(
            emptyList(),
            this,
            { file -> onItemClick(file) },
            { file -> onItemLongClick(file) },
            { file -> selectionManager.toggleFileSelection(file) },
            false,
            mutableSetOf()
        )

        selectionManager = SelectionManager(
            buttonContainer,
            selectionToolbar,
            shareButton,
            deleteButton,
            moveButton,
            selectAllButton,
            clearSelectionButton,
            swipeRefreshLayout,
            fileAdapter
        )

        gestureHandler = GestureHandler(
            recyclerView,
            fileAdapter,
            { file -> selectionManager.toggleFileSelection(file) },
            { selectionManager.startSelectionMode() }
        )

        dynamicSeekBarManager = DynamicSeekBarManager(
            dynamicSeekBarContainer,
            dynamicSeekBar,
            recyclerView,
            fileAdapter,
            sharedPreferences
        )

        fullscreenImageManager = FullscreenImageManager(this)
    }

    private fun setupButtons() {
        createFolderButton.setOnClickListener { showCreateFolderDialog() }
        captureButton.setOnClickListener { startCameraActivity() }
        uploadButton.setOnClickListener { selectFile() }
        sortButton.setOnClickListener { toggleSortOrder() }
        sizeButton.setOnClickListener { showSizeSelectionDialog() }
        
        shareButton.setOnClickListener { shareSelectedFiles() }
        deleteButton.setOnClickListener { deleteSelectedFiles() }
        moveButton.setOnClickListener { moveSelectedFiles() }
        selectAllButton.setOnClickListener { selectionManager.selectAllFiles() }
        clearSelectionButton.setOnClickListener { selectionManager.clearSelection() }
        
        swipeRefreshLayout.setOnRefreshListener { refreshContent() }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = fileAdapter
        // Настройка обработки жестов будет выполнена в GestureHandler
    }

    private fun setupDirectories() {
        appDirectory = File(getExternalFilesDir(null), "B1Void")
        zipDirectory = File(getExternalFilesDir(null), "B1Void/Zip")
        
        if (!appDirectory.exists()) {
            appDirectory.mkdirs()
        }
        if (!zipDirectory.exists()) {
            zipDirectory.mkdirs()
        }
    }

    private fun loadDirectoryContent(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile || it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        fileAdapter.updateFiles(files)
        titleTextView.text = directory.name
    }

    private fun onItemClick(file: File) {
        if (file.isDirectory) {
            directoryStack.addLast(appDirectory)
            appDirectory = file
            loadDirectoryContent(file)
        } else {
            openFile(file)
        }
    }

    private fun onItemLongClick(file: File) {
        selectionManager.toggleFileSelection(file)
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Создать папку")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val folderName = input.text.toString()
                if (folderName.isNotEmpty()) {
                    val newFolder = File(appDirectory, folderName)
                    if (newFolder.mkdir()) {
                        loadDirectoryContent(appDirectory)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startCameraActivity() {
        val intent = Intent(this, CameraV2Activity::class.java)
        startActivity(intent)
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, OPEN_FILE)
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        val files = appDirectory.listFiles()?.filter { it.isFile || it.isDirectory }?.sortedWith(
            compareBy<File> { it.isFile }.thenBy { if (sortAscending) it.name else it.name.reversed() }
        ) ?: emptyList()
        fileAdapter.updateFiles(files)
    }

    private fun showSizeSelectionDialog() {
        dynamicSeekBarManager.showSeekBar()
    }

    private fun shareSelectedFiles() {
        // Реализация шаринга файлов
    }

    private fun deleteSelectedFiles() {
        AlertDialog.Builder(this)
            .setTitle("Удалить файлы")
            .setMessage("Вы уверены, что хотите удалить выбранные файлы?")
            .setPositiveButton("Удалить") { _, _ ->
                selectionManager.getSelectedFiles().forEach { file ->
                    if (file.delete()) {
                        Log.d("FileManager", "Файл удален: ${file.name}")
                    }
                }
                selectionManager.clearSelection()
                loadDirectoryContent(appDirectory)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun moveSelectedFiles() {
        // Реализация перемещения файлов
    }

    private fun refreshContent() {
        loadDirectoryContent(appDirectory)
        swipeRefreshLayout.isRefreshing = false
    }

    private fun openFile(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(file), getMimeType(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif" -> "image/*"
            "mp4", "avi", "mov" -> "video/*"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                copyFileToAppDirectory(uri)
            }
        }
    }

    private fun copyFileToAppDirectory(uri: Uri) {
        thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val fileName = getFileName(uri)
                val outputFile = File(appDirectory, fileName)
                val outputStream = FileOutputStream(outputFile)
                
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                runOnUiThread {
                    loadDirectoryContent(appDirectory)
                    Toast.makeText(this, "Файл скопирован", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка при копировании файла", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "unknown_file"
    }
}