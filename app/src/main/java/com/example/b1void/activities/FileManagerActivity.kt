package com.example.b1void.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
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
import com.example.b1void.utils.FileManagerUtils
import com.example.b1void.utils.ImageOptimizer
import com.example.b1void.workers.DropboxUploadWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.b1void.ui.MoveFilesBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    private lateinit var sharedPreferences: SharedPreferences

    // --- Новый UI для режима выделения ---
    private lateinit var selectionTopToolbar: LinearLayout
    private lateinit var selectionCountTextView: TextView
    private lateinit var selectAllToggleButton: Button
    private lateinit var confirmSelectionButton: Button
    // ----------------------------------------->

    private val OPEN_FILE = 1

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private var currentFileForMenu: File? = null
    private var sortAscending = false

    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1
    private var gestureDetector: GestureDetector? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var spanCount = 4 // default value
    private val MIN_SPAN_COUNT = 2
    private val MAX_SPAN_COUNT = 6

    companion object {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initializeViews()
        setupButtons()
        setupRecyclerView()
        setupGestureDetector()
        setupDirectories()
        setupMoveResultListener() // Добавляем листенер

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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        selectionTopToolbar = findViewById(R.id.selection_top_toolbar)
        selectionCountTextView = findViewById(R.id.selection_count_text)
        selectAllToggleButton = findViewById(R.id.select_all_toggle_button)
        confirmSelectionButton = findViewById(R.id.confirm_selection_button)
    }

    private fun setupButtons() {
        val sortButton: ImageButton = findViewById(R.id.sort_button)
        val uploadButton = findViewById<View>(R.id.upload_button)

        sortButton.setOnClickListener { toggleSortOrder() }

        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*" // General type
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")) // Specific types
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, OPEN_FILE)
        }

        captureButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CameraActivity.EXTRA_SAVE_PATH, getCurrentDirectory().absolutePath)
            startActivity(intent)
        }

        createFolderButton.setOnClickListener {
            showCreateFolderDialog { loadDirectoryContent(getCurrentDirectory()) }
        }

        swipeRefreshLayout.setOnRefreshListener { loadDirectoryContent(getCurrentDirectory()) }

        selectAllToggleButton.setOnClickListener { toggleSelectAll() }
        confirmSelectionButton.setOnClickListener { showActionsMenu() }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var scaleFactor = 1.0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            swipeRefreshLayout.isEnabled = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 2.0f))

            if (scaleFactor > 1.2f && spanCount > MIN_SPAN_COUNT) {
                spanCount--
                updateGridLayout()
                scaleFactor = 1.0f
            } else if (scaleFactor < 0.8f && spanCount < MAX_SPAN_COUNT) {
                spanCount++
                updateGridLayout()
                scaleFactor = 1.0f
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            swipeRefreshLayout.isEnabled = true
        }
    }

    private fun setupRecyclerView() {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredItemWidthDp = 120
        spanCount = sharedPreferences.getInt("span_count", (screenWidthDp / desiredItemWidthDp).toInt().coerceAtLeast(1))
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                scaleGestureDetector.onTouchEvent(e)
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

    private fun updateGridLayout() {
        (recyclerView.layoutManager as GridLayoutManager).spanCount = spanCount
        sharedPreferences.edit().putInt("span_count", spanCount).apply()
        fileAdapter.notifyDataSetChanged()
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
        val (appDir, zipDir) = FileManagerUtils.createAppDirectories(this)
        appDirectory = appDir
        zipDirectory = zipDir
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
                saveMediaToDirectory(getCurrentDirectory(), uris)
            }
        }
    }

    private fun saveMediaToDirectory(directory: File, uris: List<Uri>) {
        thread {
            uris.forEach { uri ->
                val mimeType = contentResolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "file"
                val prefix = if (mimeType?.startsWith("video") == true) "Video" else "Image"
                val fname = "$prefix-${System.currentTimeMillis()}.$extension"
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
                loadDirectoryContent(directory)
            }
        }
    }

    private fun showPopupMenu(file: File, view: View) {
        currentFileForMenu = file
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.file_actions_menu, menu)
            menu.findItem(R.id.action_move_up).isVisible =
                getCurrentDirectory().parentFile != null && getCurrentDirectory().absolutePath != appDirectory.absolutePath

            setOnMenuItemClickListener { item ->
                onContextItemSelected(item)
            }
            show()
        }
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
        } else if (fileAdapter.isVideo(file)) {
            playVideo(file)
        }
    }

    private fun playVideo(file: File) {
        val fileUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW, fileUri).apply {
            setDataAndType(fileUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Не найдено приложение для воспроизведения видео", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onItemLongClick(file: File, view: View) {
        showPopupMenu(file, view)
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
            ImageOptimizer.clearImageCache(this)
            loadDirectoryContent(getCurrentDirectory())
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
        }
    }

    private fun deleteDirectory(directory: File) {
        directory.walkBottomUp().forEach { it.delete() }
    }

    private fun shareFile(file: File) {
        if (file.isDirectory) {
            thread {
                val sharedZipsDir = File(cacheDir, "shared_zips").apply { mkdirs() }
                sharedZipsDir.listFiles()?.forEach { it.delete() }

                val zipFile = File(sharedZipsDir, "${file.name}.zip")

                try {
                    FileManagerUtils.zipDirectory(file, zipFile)

                    val uri = FileProvider.getUriForFile(this, "${packageName}.provider", zipFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runOnUiThread {
                        startActivity(Intent.createChooser(shareIntent, "Поделиться папкой как ZIP"))
                    }
                } catch (e: Exception) {
                    Log.e("FileManager", "Ошибка при отправке папки как ZIP: ${e.message}", e)
                    runOnUiThread { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        } else {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val mimeType = contentResolver.getType(uri) ?: "*/*"
            ShareCompat.IntentBuilder(this)
                .setStream(uri)
                .setType(mimeType)
                .setChooserTitle("Поделиться файлом")
                .startChooser()
        }
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
        view.findViewById<TextView>(R.id.action_upload_to_dropbox).setOnClickListener {
            dialog.dismiss()
            uploadSelectedFilesToDropbox()
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
                        ImageOptimizer.clearImageCache(this)
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
            val sharedZipsDir = File(cacheDir, "shared_zips").apply { mkdirs() }
            sharedZipsDir.listFiles()?.forEach { it.delete() }

            val zipFile = File(sharedZipsDir, "archive-${System.currentTimeMillis()}.zip")

            try {
                val tempDir = File(cacheDir, "temp_share").apply { mkdirs() }
                selectedFiles.forEach { file ->
                    if (file.isDirectory) {
                        file.copyRecursively(File(tempDir, file.name), true)
                    } else {
                        file.copyTo(File(tempDir, file.name), true)
                    }
                }

                FileManagerUtils.zipDirectory(tempDir, zipFile)
                tempDir.deleteRecursively()

                if (!zipFile.exists() || zipFile.length() == 0L) {
                    throw IOException("Не удалось создать или был создан пустой ZIP-файл.")
                }

                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(shareIntent, "Поделиться файлами"))
                    exitSelectionMode()
                }
            } catch (e: Exception) {
                Log.e("FileManager", "Ошибка при обмене файлами: ${e.message}", e)
                runOnUiThread { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showMoveDialogForFile(file: File) {
        showMoveDialogForSelectedFiles(setOf(file))
    }

    private fun showMoveDialogForSelectedFiles(filesToMove: Set<File> = selectedFiles) {
        if (filesToMove.isEmpty()) return

        val filePaths = filesToMove.map { it.absolutePath }
        val sourceFolderPath = getCurrentDirectory().absolutePath
        val rootFolderPath = appDirectory.absolutePath

        MoveFilesBottomSheet.newInstance(filePaths, sourceFolderPath, rootFolderPath)
            .show(supportFragmentManager, MoveFilesBottomSheet.TAG)
    }

    private fun setupMoveResultListener() {
        supportFragmentManager.setFragmentResultListener(MoveFilesBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val moved = bundle.getBoolean(MoveFilesBottomSheet.RESULT_MOVED)
            if (moved) {
                Toast.makeText(this, "Файлы успешно перемещены", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadDirectoryContent(getCurrentDirectory())
            }
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
        setupRecyclerView()
    }

    private fun moveFileUp(file: File) {
        val parentDir = getCurrentDirectory().parentFile
        if (parentDir != null && getCurrentDirectory().absolutePath != appDirectory.absolutePath) {
            showMoveDialogForSelectedFiles(setOf(file)) // Используем новый диалог
        } else {
            Toast.makeText(this, "Невозможно переместить файл выше", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadSelectedFilesToDropbox() {
        if (selectedFiles.isEmpty()) return

        thread {
            val zipFile = File(zipDirectory, "archive-" + System.currentTimeMillis() + ".zip")
            try {
                val tempDir = File(cacheDir, "temp_upload").apply { mkdirs() }
                selectedFiles.forEach { file ->
                    file.copyTo(File(tempDir, file.name), true)
                }
                FileManagerUtils.zipDirectory(tempDir, zipFile)
                tempDir.deleteRecursively()

                val dropboxPath = "/" + zipFile.name
                val data = workDataOf(
                    DropboxUploadWorker.KEY_FILE_PATH to zipFile.absolutePath,
                    DropboxUploadWorker.KEY_DROPBOX_PATH to dropboxPath
                )

                val uploadWorkRequest = OneTimeWorkRequestBuilder<DropboxUploadWorker>()
                    .setInputData(data)
                    .build()

                WorkManager.getInstance(this).enqueue(uploadWorkRequest)

                runOnUiThread {
                    Toast.makeText(this, "Загрузка в Dropbox началась", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            } catch (e: Exception) {
                Log.e("FileManager", "Error preparing for Dropbox upload", e)
                runOnUiThread { Toast.makeText(this, "Ошибка подготовки к загрузке", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
