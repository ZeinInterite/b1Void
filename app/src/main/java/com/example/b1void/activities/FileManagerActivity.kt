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
import com.example.b1void.adapters.FileAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

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
    private lateinit var titleTextView: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var buttonContainer: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectAllButton: Button
    private lateinit var clearSelectionButton: Button
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

    // Новые переменные для улучшенного UX
    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1
    private var gestureDetector: GestureDetector? = null
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    companion object {
        private const val PREF_SEEK_BAR_PROGRESS = "seek_bar_progress"
        private const val LONG_PRESS_DELAY = 500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initializeViews()
        setupPreferences()
        setupButtons()
        setupRecyclerView()
        setupGestureDetector()
        setupDirectories()
        
        // Восстанавливаем состояние при повороте экрана
        if (savedInstanceState != null) {
            val savedDirectoryStack = savedInstanceState.getStringArrayList("directory_stack")
            if (savedDirectoryStack != null) {
                directoryStack.clear()
                savedDirectoryStack.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        directoryStack.add(file)
                    }
                }
            }
            
            isSelectionMode = savedInstanceState.getBoolean("is_selection_mode", false)
            val savedSelectedFiles = savedInstanceState.getStringArrayList("selected_files")
            if (savedSelectedFiles != null) {
                selectedFiles.clear()
                savedSelectedFiles.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        selectedFiles.add(file)
                    }
                }
            }
        }
        
        loadDirectoryContent(getCurrentDirectory())
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
        val uploadButton = findViewById<View>(R.id.upload_button)
        progressBar = findViewById(R.id.progressBar)
        val sortButton: ImageButton = findViewById(R.id.sort_button)
        buttonContainer = findViewById(R.id.button_container)
        selectionToolbar = findViewById(R.id.selection_toolbar)
        selectAllButton = findViewById(R.id.select_all_button)
        clearSelectionButton = findViewById(R.id.clear_selection_button)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun setupPreferences() {
        currentProgress = sharedPreferences.getInt(PREF_SEEK_BAR_PROGRESS, 0)
        progressBar.progress = currentProgress
    }

    private fun setupButtons() {
        val sortButton: ImageButton = findViewById(R.id.sort_button)
        val uploadButton = findViewById<View>(R.id.upload_button)

        sortButton.setOnClickListener {
            toggleSortOrder()
        }

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
            showMoveDialogForSelectedFiles()
        }

        selectAllButton.setOnClickListener {
            selectAllFiles()
        }

        clearSelectionButton.setOnClickListener {
            clearSelection()
        }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentProgress = progress
                    updateProgress(progress)
                    sharedPreferences.edit().putInt(PREF_SEEK_BAR_PROGRESS, progress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        createFolderButton.setOnClickListener {
            showCreateFolderDialog { newDir ->
                loadDirectoryContent(getCurrentDirectory())
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadDirectoryContent(getCurrentDirectory())
        }
    }

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager
        
        recyclerView.setOnTouchListener { _, event ->
            if (isSelectionMode) {
                gestureDetector?.onTouchEvent(event)
            }
            false
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isSwipeSelectionActive) {
                    val childView = recyclerView.findChildViewUnder(e2.x, e2.y)
                    if (childView != null) {
                        val position = recyclerView.getChildAdapterPosition(childView)
                        if (position != RecyclerView.NO_POSITION && position != lastTouchedPosition) {
                            val file = fileAdapter.files[position]
                            toggleFileSelection(file)
                            lastTouchedPosition = position
                        }
                    }
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isSelectionMode) {
                    startSelectionMode()
                }
            }
        })
    }

    private fun startSwipeSelection() {
        isSwipeSelectionActive = true
        lastTouchedPosition = -1
    }

    private fun stopSwipeSelection() {
        isSwipeSelectionActive = false
        lastTouchedPosition = -1
    }

    private fun setupDirectories() {
        appDirectory = File(filesDir, "InspectorAppFolder")
        if (!appDirectory.exists()) {
            try {
                if (appDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка приложения создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Не удалось создать папку приложения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(this, "Ошибка: Недостаточно прав для создания папки", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(this, "Ошибка ввода/вывода при создании папки", Toast.LENGTH_SHORT).show()
            }
        }

        zipDirectory = File(filesDir, "zipFolder")
        if (!zipDirectory.exists()) {
            try {
                if (zipDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка zip-файлов создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Не удалось создать папку zip-файлов", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(this, "Ошибка: Недостаточно прав для создания папки zip-файлов ", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(this, "Ошибка ввода/вывода при создании папки zip-файлов", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        val sortButton: ImageView = findViewById(R.id.sort_button)

        if (sortAscending) {
            sortButton.scaleY = -1f
        } else {
            sortButton.scaleY = 1f
            sortButton.translationY = 0f
        }

        loadDirectoryContent(getCurrentDirectory())
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            if (data.clipData != null) {
                val clipData = data.clipData
                val count = clipData!!.itemCount

                val uris = mutableListOf<Uri>()
                for (i in 0 until count) {
                    val imageUri = clipData.getItemAt(i).uri
                    uris.add(imageUri)
                }

                saveImagesToDirectory(getCurrentDirectory(), uris)

            } else if (data.data != null) {
                imageUri = data.data
                val uris = mutableListOf<Uri>()
                uris.add(imageUri!!)
                saveImagesToDirectory(getCurrentDirectory(), uris)
            }
        }
    }

    private fun saveImagesToDirectory(directory: File, uris: List<Uri>) {
        for (uri in uris) {
            val fname = "Image-" + System.currentTimeMillis() + ".jpg"
            val file = File(directory, fname)

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(4096)

                var bytesRead: Int
                while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()

                runOnUiThread {
                    Toast.makeText(this, "Изображение сохранено в: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    loadDirectoryContent(directory)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Ошибка при сохранении изображения", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        runOnUiThread {
            loadDirectoryContent(directory)
        }
    }

    private fun saveImageToDirectory(directory: File) {
        val fname = "Image-" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, fname)

        try {
            val inputStream = contentResolver.openInputStream(imgGalUri!!)
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(4096)

            var bytesRead: Int
            while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.close()
            inputStream.close()

            runOnUiThread {
                Toast.makeText(this, "Изображение сохранено в: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                loadDirectoryContent(directory)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Ошибка при сохранении изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
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
        thread {
            if (directory.exists() && directory.isDirectory) {
                val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()

                val sortedFilesAndDirs = if (sortAscending) {
                    filesAndDirs.sortedBy { it.lastModified() }
                } else {
                    filesAndDirs.sortedByDescending { it.lastModified() }
                }

                runOnUiThread {
                    if (!this::fileAdapter.isInitialized) {
                        fileAdapter = FileAdapter(
                            sortedFilesAndDirs,
                            this,
                            { file -> onItemClick(file) },
                            { file -> onItemLongClick(file) },
                            { file -> toggleFileSelection(file) },
                            isSelectionMode,
                            selectedFiles
                        )
                        recyclerView.adapter = fileAdapter
                        fileAdapter.setProgress(currentProgress)
                    } else {
                        fileAdapter.isSelectionMode = isSelectionMode
                        fileAdapter.selectedFiles = selectedFiles
                        fileAdapter.updateFiles(sortedFilesAndDirs)
                        // Принудительно обновляем все элементы для корректного отображения состояния выделения
                        fileAdapter.notifyDataSetChanged()
                    }
                    if (directory == appDirectory) {
                        titleTextView.text = "Основная директория"
                    } else {
                        titleTextView.text = directory.name
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@FileManagerActivity, "Папка не найдена", Toast.LENGTH_SHORT).show()
                }
            }
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun onItemClick(file: File) {
        if (isSelectionMode) {
            toggleFileSelection(file)
        } else if (file.isDirectory) {
            openDirectory(file)
        } else {
            if (fileAdapter.isImage(file)) {
                openImagePreview(file)
            } else {
                Toast.makeText(this, "Выбран файл: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onItemLongClick(file: File) {
        if (!isSelectionMode) {
            startSelectionMode()
        }
        toggleFileSelection(file)
        startSwipeSelection()
    }

    private fun openImagePreview(imageFile: File) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        val imagePaths = arrayListOf(imageFile.absolutePath)
        intent.putStringArrayListExtra("image_paths", imagePaths)
        intent.putExtra("current_image_index", 0)
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
                    onFolderCreated(newDir)
                    loadDirectoryContent(getCurrentDirectory())
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

                uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zipFile)
                mimeType = "application/zip"

                zipFile.deleteOnExit()
            } else {
                uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
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

    // Новое контекстное меню
    private fun showContextMenu(file: File) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.context_menu_layout, null)
        dialog.setContentView(view)

        val moveItem = view.findViewById<TextView>(R.id.context_move)
        val shareItem = view.findViewById<TextView>(R.id.context_share)
        val deleteItem = view.findViewById<TextView>(R.id.context_delete)
        val renameItem = view.findViewById<TextView>(R.id.context_rename)

        moveItem.setOnClickListener {
            dialog.dismiss()
            showMoveDialogForFile(file)
        }

        shareItem.setOnClickListener {
            dialog.dismiss()
            shareFile(file)
        }

        deleteItem.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Удалить файл?")
                .setMessage("Вы уверены, что хотите удалить файл ${file.name}?")
                .setPositiveButton("Да") { _, _ ->
                    deleteFile(file)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        renameItem.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(file)
        }

        dialog.show()
    }

    private fun startSelectionMode() {
        isSelectionMode = true
        fileAdapter.isSelectionMode = true
        buttonContainer.visibility = View.VISIBLE
        selectionToolbar.visibility = View.VISIBLE
        
        shareButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE
        moveButton.visibility = View.VISIBLE

        swipeRefreshLayout.isEnabled = false

        // Анимация появления
        val slideIn = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        buttonContainer.startAnimation(slideIn)
        selectionToolbar.startAnimation(slideIn)

        // Принудительно обновляем все элементы для корректного отображения состояния выделения
        fileAdapter.notifyDataSetChanged()
        
        loadDirectoryContent(getCurrentDirectory())
    }

    private fun clearSelection() {
        isSelectionMode = false
        stopSwipeSelection()

        buttonContainer.visibility = View.GONE
        selectionToolbar.visibility = View.GONE
        shareButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
        moveButton.visibility = View.GONE
        selectedFiles.clear()
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.isSelectionMode = false

        // Принудительно обновляем все элементы для корректного отображения состояния выделения
        fileAdapter.notifyDataSetChanged()

        swipeRefreshLayout.isEnabled = true

        // Анимация исчезновения
        val slideOut = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
        buttonContainer.startAnimation(slideOut)
        selectionToolbar.startAnimation(slideOut)

        loadDirectoryContent(getCurrentDirectory())
    }

    private fun selectAllFiles() {
        selectedFiles.clear()
        selectedFiles.addAll(fileAdapter.files)
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.notifyDataSetChanged()
        updateSelectionButtons()
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.selectedFiles = selectedFiles
        val position = fileAdapter.files.indexOf(file)
        if (position != -1) {
            fileAdapter.notifyItemChanged(position)
        }
        updateSelectionButtons()
        
        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }

    private fun updateSelectionButtons() {
        val allSelected = selectedFiles.size == fileAdapter.files.size
        selectAllButton.text = if (allSelected) "Отменить все" else "Выделить все"
        clearSelectionButton.text = "Отменить (${selectedFiles.size})"
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
                                            Toast.makeText(this@FileManagerActivity, "Не удалось удалить файл ${file.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                runOnUiThread {
                                    loadDirectoryContent(getCurrentDirectory())
                                }
                            } catch (e: SecurityException) {
                                Log.e("FileManager", "SecurityException deleting file: ${e.message}")
                                runOnUiThread {
                                    Toast.makeText(this@FileManagerActivity, "Ошибка безопасности при удалении файла ${file.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        runOnUiThread {
                            Toast.makeText(this@FileManagerActivity, "Выбранные файлы удалены", Toast.LENGTH_SHORT).show()
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

    private fun shareSelectedFiles() {
        if (selectedFiles.isNotEmpty()) {
            Thread {
                val filesUris = ArrayList<Uri>()
                val tempDir = File(cacheDir, "temp_zip")
                if (!tempDir.exists()) tempDir.mkdirs()

                var errorOccurred = false

                try {
                    for (file in selectedFiles) {
                        if (file.isDirectory) {
                            // Создаем zip архив папки
                            val zipFile = File(tempDir, "${file.name}.zip")
                            zipFolder(file, zipFile)
                            if (zipFile.exists()) {
                                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zipFile)
                                filesUris.add(uri)
                            } else {
                                errorOccurred = true
                                runOnUiThread {
                                    Toast.makeText(this, "Ошибка при создании архива для ${file.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                            filesUris.add(uri)
                        }
                    }

                    if (filesUris.isNotEmpty()) {
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
                        shareIntent.type = "*/*"
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesUris)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        runOnUiThread {
                            val chooserIntent = Intent.createChooser(shareIntent, "Поделиться файлами")
                            startActivity(chooserIntent)
                            clearSelection()
                        }
                    } else {
                        runOnUiThread {
                            if (errorOccurred) {
                                Toast.makeText(this, "Не удалось подготовить файлы для отправки.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Нет файлов для отправки.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("FileManager", "FileProvider error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка при обмене файлами", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } else {
            Toast.makeText(this, "Нет выбранных файлов", Toast.LENGTH_SHORT).show()
        }
    }

    private fun zipFolder(folderToZip: File, zipFile: File) {
        try {
            val zipParameters = ZipParameters()
            zipParameters.compressionMethod = CompressionMethod.DEFLATE
            zipParameters.compressionLevel = CompressionLevel.NORMAL

            ZipFile(zipFile.absolutePath).addFolder(folderToZip, zipParameters)
        } catch (e: Exception) {
            Log.e("FileManager", "Ошибка архивации папки: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "Ошибка архивации папки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMoveDialogForFile(file: File) {
        val currentDir = getCurrentDirectory()
        val parentDir = currentDir.parentFile

        val directories = currentDir.listFiles { f -> f.isDirectory }?.toMutableList() ?: mutableListOf()

        val directoryNames = mutableListOf<String>()
        if (parentDir != null) {
            directoryNames.add(".. (переместить на уровень выше)")
        }
        directoryNames.addAll(directories.map { it.name })

        AlertDialog.Builder(this)
            .setTitle("Выберите папку назначения")
            .setItems(directoryNames.toTypedArray()) { _, which ->
                val destinationDirectory: File? = if (parentDir != null && which == 0) {
                    parentDir
                } else {
                    val index = if (parentDir != null) which - 1 else which
                    directories.getOrNull(index)
                }

                if (destinationDirectory == null) {
                    Toast.makeText(this, "Неверная папка назначения", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                moveFileToDirectory(file, destinationDirectory)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun moveFileToDirectory(file: File, destinationDirectory: File) {
        Thread {
            val newFile = File(destinationDirectory, file.name)
            try {
                if (file.renameTo(newFile)) {
                    runOnUiThread {
                        Toast.makeText(this, "Файл перемещён в ${destinationDirectory.name}", Toast.LENGTH_SHORT).show()
                        loadDirectoryContent(getCurrentDirectory())
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка при перемещении файла", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка при перемещении: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showMoveDialogForSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "Не выбраны файлы для перемещения", Toast.LENGTH_SHORT).show()
            return
        }

        val currentDir = getCurrentDirectory()
        val parentDir = currentDir.parentFile

        val directories = currentDir.listFiles { f -> f.isDirectory }?.toMutableList() ?: mutableListOf()

        val directoryNames = mutableListOf<String>()
        if (parentDir != null) {
            directoryNames.add(".. (переместить на уровень выше)")
        }
        directoryNames.addAll(directories.map { it.name })

        AlertDialog.Builder(this)
            .setTitle("Выберите папку назначения")
            .setItems(directoryNames.toTypedArray()) { _, which ->
                val destinationDirectory: File? = if (parentDir != null && which == 0) {
                    parentDir
                } else {
                    val index = if (parentDir != null) which - 1 else which
                    directories.getOrNull(index)
                }

                if (destinationDirectory == null) {
                    Toast.makeText(this, "Неверная папка назначения", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                moveSelectedFiles(destinationDirectory)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun moveSelectedFiles(destinationDirectory: File) {
        Thread {
            var errorOccurred = false

            selectedFiles.forEach { file ->
                val newFile = File(destinationDirectory, file.name)
                try {
                    if (!file.renameTo(newFile)) {
                        errorOccurred = true
                        runOnUiThread {
                            Toast.makeText(this@FileManagerActivity, "Ошибка при перемещении файла ${file.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    errorOccurred = true
                    runOnUiThread {
                        Toast.makeText(this@FileManagerActivity, "Ошибка при перемещении файла ${file.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            runOnUiThread {
                if (!errorOccurred) {
                    Toast.makeText(this@FileManagerActivity, "Файлы перемещены", Toast.LENGTH_SHORT).show()
                }
                clearSelection()
                loadDirectoryContent(getCurrentDirectory())
            }
        }.start()
    }

    fun updateProgress(progress: Int) {
        progressBar.progress = progress
        fileAdapter.setProgress(progress)

        val noOfColumns = calculateNoOfColumns(progress)
        (recyclerView.layoutManager as GridLayoutManager).spanCount = noOfColumns
    }

    private fun calculateNoOfColumns(progress: Int): Int {
        val scaleFactor = 0.5f + (progress / 100f) * 0.5f
        return when {
            scaleFactor <= 0.65 -> 5
            scaleFactor >= 0.85 -> 3
            else -> 4
        }
    }

    override fun onResume() {
        super.onResume()
        loadDirectoryContent(getCurrentDirectory())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        // Сохраняем стек директорий
        val directoryPaths = directoryStack.map { it.absolutePath }
        outState.putStringArrayList("directory_stack", ArrayList(directoryPaths))
        
        // Сохраняем состояние выделения
        outState.putBoolean("is_selection_mode", isSelectionMode)
        val selectedFilePaths = selectedFiles.map { it.absolutePath }
        outState.putStringArrayList("selected_files", ArrayList(selectedFilePaths))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Обновляем количество колонок при изменении ориентации
        if (::recyclerView.isInitialized && ::progressBar.isInitialized) {
            val currentProgress = progressBar.progress
            val noOfColumns = calculateNoOfColumns(currentProgress)
            (recyclerView.layoutManager as GridLayoutManager).spanCount = noOfColumns
            
            // Обновляем адаптер для корректного отображения
            if (::fileAdapter.isInitialized) {
                fileAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { gestureDetector?.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }
} 