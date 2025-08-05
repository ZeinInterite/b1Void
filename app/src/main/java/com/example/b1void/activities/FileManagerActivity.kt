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
    private lateinit var titleTextView: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var sharedPreferences: SharedPreferences
    private var currentProgress = 0

    // --- Новый UI для режима выделения ---
    private lateinit var selectionTopToolbar: LinearLayout
    private lateinit var selectionCountTextView: TextView
    private lateinit var selectAllToggleButton: Button
    private lateinit var confirmSelectionButton: Button
    // -----------------------------------------

    private val OPEN_FILE = 1
    private var imageUri: Uri? = null
    private var imgGalUriString: String? = null
    private var imgGalUri: Uri? = null

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private var currentFileForMenu: File? = null
    private var sortAscending = false

    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1
    private var gestureDetector: GestureDetector? = null

    companion object {
        private const val PREF_SEEK_BAR_PROGRESS = "seek_bar_progress"
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

        if (savedInstanceState != null) {
            val savedDirectoryStack = savedInstanceState.getStringArrayList("directory_stack")
            savedDirectoryStack?.forEach { path ->
                val file = File(path)
                if (file.exists()) directoryStack.add(file)
            }

            isSelectionMode = savedInstanceState.getBoolean("is_selection_mode", false)
            val savedSelectedFiles = savedInstanceState.getStringArrayList("selected_files")
            savedSelectedFiles?.forEach { path ->
                val file = File(path)
                if (file.exists()) selectedFiles.add(file)
            }
        }

        loadDirectoryContent(getCurrentDirectory())
        if (isSelectionMode) {
            startSelectionMode(selectedFiles.firstOrNull())
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        titleTextView = findViewById(R.id.titleTextView)
        progressBar = findViewById(R.id.progressBar)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // --- Инициализация нового UI ---
        selectionTopToolbar = findViewById(R.id.selection_top_toolbar)
        selectionCountTextView = findViewById(R.id.selection_count_text)
        selectAllToggleButton = findViewById(R.id.select_all_toggle_button)
        confirmSelectionButton = findViewById(R.id.confirm_selection_button)
        // ---------------------------------
    }

    private fun setupPreferences() {
        currentProgress = sharedPreferences.getInt(PREF_SEEK_BAR_PROGRESS, 75)
        progressBar.progress = currentProgress
    }

    private fun setupButtons() {
        val sortButton: ImageButton = findViewById(R.id.sort_button)
        val uploadButton = findViewById<View>(R.id.upload_button)

        sortButton.setOnClickListener { toggleSortOrder() }

        if (intent.getStringExtra("imageUri") != null) {
            imgGalUriString = intent.getStringExtra("imageUri")
            imgGalUri = Uri.parse(imgGalUriString)
            showCreateFolderDialog { newDir -> saveImageToDirectory(newDir) }
        }

        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, OPEN_FILE)
        }

        captureButton.setOnClickListener {
            val intent = Intent(this, CameraV2Activity::class.java)
            intent.putExtra("save_path", getCurrentDirectory().absolutePath)
            startActivity(intent)
        }

        createFolderButton.setOnClickListener {
            showCreateFolderDialog { loadDirectoryContent(getCurrentDirectory()) }
        }

        swipeRefreshLayout.setOnRefreshListener { loadDirectoryContent(getCurrentDirectory()) }

        // --- Новые обработчики кнопок ---
        selectAllToggleButton.setOnClickListener { toggleSelectAll() }
        confirmSelectionButton.setOnClickListener { showActionsMenu() }
        // -----------------------------------

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
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (isSelectionMode) {
                    gestureDetector?.onTouchEvent(e)
                    if (e.action == MotionEvent.ACTION_UP && isSwipeSelectionActive) {
                        stopSwipeSelection()
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isSwipeSelectionActive) {
                    recyclerView.findChildViewUnder(e2.x, e2.y)?.let { childView ->
                        val position = recyclerView.getChildAdapterPosition(childView)
                        if (position != RecyclerView.NO_POSITION && position != lastTouchedPosition) {
                            val file = fileAdapter.files[position]
                            if (!selectedFiles.contains(file)) {
                                toggleFileSelection(file)
                            }
                            lastTouchedPosition = position
                        }
                    }
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isSelectionMode) {
                    recyclerView.findChildViewUnder(e.x, e.y)?.let { childView ->
                        val position = recyclerView.getChildAdapterPosition(childView)
                        if (position != RecyclerView.NO_POSITION) {
                            val file = fileAdapter.files[position]
                            startSelectionMode(file)
                            startSwipeSelection()
                        }
                    }
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
        appDirectory = File(filesDir, "InspectorAppFolder").apply { mkdirs() }
        zipDirectory = File(filesDir, "zipFolder").apply { mkdirs() }
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        findViewById<ImageView>(R.id.sort_button).scaleY = if (sortAscending) -1f else 1f
        loadDirectoryContent(getCurrentDirectory())
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            val uris = data.clipData?.let {
                (0 until it.itemCount).map { i -> it.getItemAt(i).uri }
            } ?: listOfNotNull(data.data)
            if (uris.isNotEmpty()) {
                saveImagesToDirectory(getCurrentDirectory(), uris)
            }
        }
    }

    private fun saveImagesToDirectory(directory: File, uris: List<Uri>) {
        thread {
            uris.forEach { uri ->
                val fname = "Image-${System.currentTimeMillis()}.jpg"
                val file = File(directory, fname)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Изображения сохранены", Toast.LENGTH_SHORT).show()
                loadDirectoryContent(directory)
            }
        }
    }

    private fun saveImageToDirectory(directory: File) {
        imgGalUri?.let { saveImagesToDirectory(directory, listOf(it)) }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val file = currentFileForMenu ?: return super.onContextItemSelected(item)
        return when (item.itemId) {
            R.id.action_move -> { showMoveDialogForFile(file); true }
            R.id.action_delete -> { deleteFile(file); true }
            R.id.action_share -> { shareFile(file); true }
            R.id.action_move_up -> { moveFileUp(file); true }
            R.id.action_select_multiple -> { startSelectionMode(file); true }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun getCurrentDirectory(): File = directoryStack.lastOrNull() ?: appDirectory

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        thread {
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
                        { file, view -> onItemLongClick(file, view) }
                    )
                    recyclerView.adapter = fileAdapter
                    updateProgress(currentProgress)
                } else {
                    fileAdapter.isSelectionMode = isSelectionMode
                    fileAdapter.selectedFiles = selectedFiles
                    fileAdapter.updateFiles(sortedFilesAndDirs)
                    fileAdapter.notifyDataSetChanged()
                }
                titleTextView.text = if (directory == appDirectory) "Основная директория" else directory.name
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun onItemClick(file: File) {
        if (isSelectionMode) {
            toggleFileSelection(file)
        } else if (file.isDirectory) {
            openDirectory(file)
        } else if (fileAdapter.isImage(file)) {
            openImagePreview(file)
        }
    }

    private fun onItemLongClick(file: File, view: View) {
        if (!isSelectionMode) {
            startSelectionMode(file)
        }
    }

    private fun openImagePreview(clickedImage: File) {
        val allImageFiles = fileAdapter.files.filter { fileAdapter.isImage(it) }
        val imagePaths = ArrayList(allImageFiles.map { it.absolutePath })
        val clickedImageIndex = allImageFiles.indexOf(clickedImage)
        if (imagePaths.isNotEmpty()) {
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putStringArrayListExtra("image_paths", imagePaths)
                putExtra("current_image_index", clickedImageIndex)
            }
            startActivity(intent)
        }
    }

    private fun openDirectory(file: File) {
        if (directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            loadDirectoryContent(directoryStack.lastOrNull() ?: appDirectory)
        } else {
            super.onBackPressed()
        }
    }

    private fun showCreateFolderDialog(onFolderCreated: (File) -> Unit) {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Создать новую папку")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val newDir = File(getCurrentDirectory(), input.text.toString())
                if (newDir.mkdir()) {
                    onFolderCreated(newDir)
                } else {
                    Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this).apply { setText(file.name) }
        AlertDialog.Builder(this)
            .setTitle("Переименовать")
            .setView(input)
            .setPositiveButton("Переименовать") { _, _ ->
                val newFile = File(file.parentFile, input.text.toString())
                if (file.renameTo(newFile)) {
                    loadDirectoryContent(getCurrentDirectory())
                } else {
                    Toast.makeText(this, "Ошибка при переименовании", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun deleteFile(file: File) {
        try {
            if (file.isDirectory) deleteDirectory(file) else file.delete()
            loadDirectoryContent(getCurrentDirectory())
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
        }
    }

    private fun deleteDirectory(directory: File) {
        directory.walkBottomUp().forEach { it.delete() }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        ShareCompat.IntentBuilder(this)
            .setStream(uri)
            .setType(if (file.isDirectory) "application/zip" else "image/*")
            .setChooserTitle("Поделиться файлом")
            .startChooser()
    }

    // --- Новая логика режима выделения ---

    private fun startSelectionMode(initialFile: File?) {
        isSelectionMode = true
        initialFile?.let { selectedFiles.add(it) }
        fileAdapter.isSelectionMode = true
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.notifyDataSetChanged()

        selectionTopToolbar.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_top)
        selectionTopToolbar.startAnimation(slideIn)

        swipeRefreshLayout.isEnabled = false
        updateSelectionState()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        stopSwipeSelection()
        selectedFiles.clear()
        fileAdapter.isSelectionMode = false
        fileAdapter.notifyDataSetChanged()

        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_top)
        selectionTopToolbar.startAnimation(slideOut)
        selectionTopToolbar.visibility = View.GONE

        swipeRefreshLayout.isEnabled = true
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.notifyItemChanged(fileAdapter.files.indexOf(file))
        updateSelectionState()

        if (selectedFiles.isEmpty()) {
            exitSelectionMode()
        }
    }

    private fun toggleSelectAll() {
        if (selectedFiles.size == fileAdapter.files.size) {
            selectedFiles.clear()
        } else {
            selectedFiles.clear()
            selectedFiles.addAll(fileAdapter.files)
        }
        fileAdapter.notifyDataSetChanged()
        updateSelectionState()
    }

    private fun updateSelectionState() {
        val count = selectedFiles.size
        selectionCountTextView.text = "Выбрано: $count"
        confirmSelectionButton.isEnabled = count > 0
    }

    private fun showActionsMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_actions, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.action_share).setOnClickListener {
            dialog.dismiss()
            shareSelectedFiles()
        }
        view.findViewById<TextView>(R.id.action_delete).setOnClickListener {
            dialog.dismiss()
            deleteSelectedFiles()
        }
        view.findViewById<TextView>(R.id.action_move).setOnClickListener {
            dialog.dismiss()
            showMoveDialogForSelectedFiles()
        }
        dialog.show()
    }

    private fun deleteSelectedFiles() {
        if (selectedFiles.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Удалить выбранные элементы?")
            .setMessage("Вы уверены, что хотите удалить ${selectedFiles.size} элемент(ов)?")
            .setPositiveButton("Да") { _, _ ->
                thread {
                    selectedFiles.forEach { file ->
                        try {
                            if (file.isDirectory) deleteDirectory(file) else file.delete()
                        } catch (e: SecurityException) {
                            Log.e("FileManager", "SecurityException on delete: ${e.message}")
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this, "Выбранные файлы удалены", Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                        loadDirectoryContent(getCurrentDirectory())
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun shareSelectedFiles() {
        if (selectedFiles.isEmpty()) return
        thread {
            val filesUris = ArrayList<Uri>()
            val tempDir = File(cacheDir, "temp_zip").apply { mkdirs() }

            try {
                selectedFiles.forEach { file ->
                    val fileToShare = if (file.isDirectory) {
                        File(tempDir, "${file.name}.zip").also { zipFolder(file, it) }
                    } else {
                        file
                    }
                    filesUris.add(FileProvider.getUriForFile(this, "${packageName}.provider", fileToShare))
                }

                if (filesUris.isNotEmpty()) {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesUris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runOnUiThread {
                        startActivity(Intent.createChooser(shareIntent, "Поделиться файлами"))
                        exitSelectionMode()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileManager", "FileProvider error: ${e.message}")
                runOnUiThread { Toast.makeText(this, "Ошибка при обмене файлами", Toast.LENGTH_SHORT).show() }
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private fun zipFolder(folderToZip: File, zipFile: File) {
        try {
            ZipFile(zipFile.absolutePath).addFolder(folderToZip)
        } catch (e: Exception) {
            Log.e("FileManager", "Ошибка архивации папки: ${e.message}", e)
        }
    }

    private fun showMoveDialogForFile(file: File) {
        // This can be refactored or removed if single file move is not needed outside selection mode
        showMoveDialogForSelectedFiles(setOf(file))
    }

    private fun showMoveDialogForSelectedFiles(filesToMove: Set<File> = selectedFiles) {
        if (filesToMove.isEmpty()) return

        val currentDir = getCurrentDirectory()
        val parentDir = currentDir.parentFile
        val directories = currentDir.listFiles { f -> f.isDirectory }?.toMutableList() ?: mutableListOf()
        val directoryNames = mutableListOf<String>()

        if (parentDir != null && currentDir.absolutePath != appDirectory.absolutePath) {
            directoryNames.add(".. (переместить на уровень выше)")
        }
        directoryNames.addAll(directories.map { it.name })

        AlertDialog.Builder(this)
            .setTitle("Выберите папку назначения")
            .setItems(directoryNames.toTypedArray()) { _, which ->
                val destinationDir = if (parentDir != null && currentDir.absolutePath != appDirectory.absolutePath && which == 0) {
                    parentDir
                } else {
                    val index = if (parentDir != null && currentDir.absolutePath != appDirectory.absolutePath) which - 1 else which
                    directories.getOrNull(index)
                }
                destinationDir?.let { moveSelectedFiles(it, filesToMove) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun moveSelectedFiles(destination: File, filesToMove: Set<File>) {
        thread {
            var movedCount = 0
            filesToMove.forEach { file ->
                try {
                    if (file.renameTo(File(destination, file.name))) movedCount++
                } catch (e: Exception) {
                    Log.e("FileManager", "Error moving file ${file.name}: ${e.message}")
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Перемещено файлов: $movedCount", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadDirectoryContent(getCurrentDirectory())
            }
        }
    }

    fun updateProgress(progress: Int) {
        progressBar.progress = progress
        fileAdapter.setProgress(progress)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = calculateNoOfColumns(progress)
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
        outState.putStringArrayList("directory_stack", ArrayList(directoryStack.map { it.absolutePath }))
        outState.putBoolean("is_selection_mode", isSelectionMode)
        outState.putStringArrayList("selected_files", ArrayList(selectedFiles.map { it.absolutePath }))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = calculateNoOfColumns(progressBar.progress)
    }

    private fun moveFileUp(file: File) {
        val parentDir = getCurrentDirectory().parentFile
        if (parentDir != null && getCurrentDirectory().absolutePath != appDirectory.absolutePath) {
            moveFileToDirectory(file, parentDir)
        } else {
            Toast.makeText(this, "Невозможно переместить файл выше", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveFileToDirectory(file: File, destination: File) {
        moveSelectedFiles(destination, setOf(file))
    }

    private fun showPopupMenu(file: File, view: View) {
        currentFileForMenu = file
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.file_actions_menu, menu)
            menu.findItem(R.id.action_move_up).isVisible =
                getCurrentDirectory().parentFile != null && getCurrentDirectory().absolutePath != appDirectory.absolutePath
            setOnMenuItemClickListener { item -> onContextItemSelected(item) }
            show()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isSelectionMode && event != null) {
            gestureDetector?.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isSwipeSelectionActive) {
                stopSwipeSelection()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
} 