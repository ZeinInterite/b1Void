package com.example.b1void.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.OptimizedFileAdapter
import com.example.b1void.utils.MemoryManager
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
import android.view.GestureDetector
import kotlinx.coroutines.*

class FileManagerActivityOptimized : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var fileAdapter: OptimizedFileAdapter
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var zipDirectory: File
    private lateinit var captureButton: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button
    private lateinit var moveButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var buttonContainer: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    private var currentProgress = 0

    private val OPEN_FILE = 1
    private var imageUri: Uri? = null
    private var imgGalUriString: String? = null
    private var imgGalUri: Uri? = null
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private var currentFileForMenu: File? = null
    private var sortAscending = false
    private var gestureDetector: GestureDetector? = null

    // Оптимизация для слабых устройств
    private val isLowEndDevice = MemoryManager.isLowEndDevice(this)
    private val optimizedSettings = MemoryManager.getOptimizedSettings(this)
    private var currentPage = 0
    private val pageSize = if (isLowEndDevice) 15 else 30
    private var allFiles = listOf<File>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val PREF_SEEK_BAR_PROGRESS = "seek_bar_progress"
        private const val TAG = "FileManagerOptimized"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initializeViews()
        setupSharedPreferences()
        setupRecyclerView()
        setupButtons()
        setupSwipeRefresh()
        loadInitialDirectory()
        
        // Проверяем память при запуске
        MemoryManager.clearMemoryIfNeeded(this)
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        shareButton = findViewById(R.id.share_button)
        deleteButton = findViewById(R.id.delete_button)
        moveButton = findViewById(R.id.move_button)
        titleTextView = findViewById(R.id.titleTextView)
        progressBar = findViewById(R.id.progressBar)
        buttonContainer = findViewById(R.id.button_container)
    }

    private fun setupSharedPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        currentProgress = sharedPreferences.getInt(PREF_SEEK_BAR_PROGRESS, 0)
        progressBar.progress = currentProgress
    }

    private fun setupRecyclerView() {
        // Оптимизированный layout manager
        val spanCount = if (isLowEndDevice) 3 else 4
        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager

        // Оптимизированный адаптер
        fileAdapter = OptimizedFileAdapter(
            files = emptyList(),
            context = this,
            onItemClickListener = { file -> onFileClick(file) },
            onItemLongClickListener = { file -> onFileLongClick(file) },
            onMoreOptionsClickListener = { file -> showMoreOptions(file) }
        )

        recyclerView.adapter = fileAdapter

        // Добавляем пагинацию для слабых устройств
        if (isLowEndDevice) {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount &&
                        firstVisibleItemPosition >= 0 &&
                        totalItemCount >= pageSize) {
                        loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.sort_button).setOnClickListener {
            toggleSortOrder()
        }

        createFolderButton.setOnClickListener {
            showCreateFolderDialog()
        }

        captureButton.setOnClickListener {
            openCamera()
        }

        shareButton.setOnClickListener {
            shareSelectedFiles()
        }

        deleteButton.setOnClickListener {
            deleteSelectedFiles()
        }

        moveButton.setOnClickListener {
            moveSelectedFiles()
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshCurrentDirectory()
        }
        
        // Отключаем анимации для слабых устройств
        if (isLowEndDevice) {
            swipeRefreshLayout.isEnabled = false
        }
    }

    private fun loadInitialDirectory() {
        coroutineScope.launch {
            try {
                val directory = getExternalFilesDir(null) ?: filesDir
                appDirectory = File(directory, "B1Void")
                zipDirectory = File(appDirectory, "ZIP")
                
                if (!appDirectory.exists()) {
                    appDirectory.mkdirs()
                }
                if (!zipDirectory.exists()) {
                    zipDirectory.mkdirs()
                }

                loadDirectory(appDirectory)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки начальной директории", e)
            }
        }
    }

    private fun loadDirectory(directory: File) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Проверяем память перед загрузкой
                if (!MemoryManager.hasEnoughMemory(this@FileManagerActivityOptimized, 5 * 1024 * 1024)) {
                    MemoryManager.clearMemoryIfNeeded(this@FileManagerActivityOptimized)
                }

                val files = directory.listFiles()?.filter { it.exists() }?.sortedWith(
                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                ) ?: emptyList()

                withContext(Dispatchers.Main) {
                    allFiles = files
                    currentPage = 0
                    loadCurrentPage()
                    updateTitle(directory)
                    swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки директории", e)
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun loadCurrentPage() {
        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, allFiles.size)
        
        val pageFiles = if (startIndex < allFiles.size) {
            allFiles.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        fileAdapter.updateFiles(pageFiles)
    }

    private fun loadNextPage() {
        currentPage++
        loadCurrentPage()
    }

    private fun refreshCurrentDirectory() {
        val currentDir = directoryStack.lastOrNull() ?: appDirectory
        loadDirectory(currentDir)
    }

    private fun onFileClick(file: File) {
        if (file.isDirectory) {
            directoryStack.addLast(file)
            loadDirectory(file)
        } else {
            openFile(file)
        }
    }

    private fun onFileLongClick(file: File): Boolean {
        if (!isSelectionMode) {
            isSelectionMode = true
            selectedFiles.add(file)
            updateSelectionMode()
        }
        return true
    }

    private fun updateSelectionMode() {
        val visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        shareButton.visibility = visibility
        deleteButton.visibility = visibility
        moveButton.visibility = visibility
        
        fileAdapter.isSelectionMode = isSelectionMode
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.notifyDataSetChanged()
    }

    private fun updateTitle(directory: File) {
        titleTextView.text = directory.name
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Создать папку")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val folderName = input.text.toString()
                if (folderName.isNotEmpty()) {
                    createFolder(folderName)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createFolder(folderName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val currentDir = directoryStack.lastOrNull() ?: appDirectory
                val newFolder = File(currentDir, folderName)
                
                if (newFolder.mkdir()) {
                    withContext(Dispatchers.Main) {
                        refreshCurrentDirectory()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка создания папки", e)
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraV2Activity::class.java)
        startActivityForResult(intent, OPEN_FILE)
    }

    private fun openFile(file: File) {
        // Реализация открытия файла
    }

    private fun showMoreOptions(file: File) {
        currentFileForMenu = file
        // Реализация показа опций
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        refreshCurrentDirectory()
    }

    private fun shareSelectedFiles() {
        // Реализация шаринга файлов
    }

    private fun deleteSelectedFiles() {
        // Реализация удаления файлов
    }

    private fun moveSelectedFiles() {
        // Реализация перемещения файлов
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK) {
            refreshCurrentDirectory()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        MemoryManager.clearMemoryIfNeeded(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryManager.onTrimMemory(level)
    }
} 