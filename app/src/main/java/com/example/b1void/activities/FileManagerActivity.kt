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
import android.provider.MediaStore
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
import com.example.b1void.utils.FileManagerUtils
import com.example.b1void.utils.ImageOptimizer
import com.example.b1void.ui.MoveFilesBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.*
import java.util.concurrent.TimeUnit
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
    private lateinit var trashDirectory: File
    private lateinit var openTrashButton: ImageButton
    private lateinit var clearTrashButton: ImageButton
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

    private enum class SwipeSelectionMode { NONE, ADD, REMOVE }
    private var swipeSelectionMode = SwipeSelectionMode.NONE

    companion object {
        private const val KEY_LAST_TRASH_AUTO_CLEAR = "last_trash_auto_clear"
        private val AUTO_TRASH_CLEAR_INTERVAL_MS = TimeUnit.DAYS.toMillis(30)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initializeViews()
        setupButtons()
        setupRecyclerView()
        setupGestureDetector()
        setupDirectories()
        maybeAutoClearTrash()
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

        handleTargetDirectoryIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTargetDirectoryIntent(intent)
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        titleTextView = findViewById(R.id.titleTextView)
        openTrashButton = findViewById(R.id.open_trash_button)
        clearTrashButton = findViewById(R.id.clear_trash_button)
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
        openTrashButton.setOnClickListener { openTrashDirectory() }
        clearTrashButton.setOnClickListener { showClearTrashConfirmation() }

        uploadButton.setOnClickListener {
            val galleryIntent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            try {
                val chooserTitle = getString(R.string.select_images_from_gallery)
                startActivityForResult(
                    Intent.createChooser(galleryIntent, chooserTitle),
                    OPEN_FILE
                )
            } catch (e: ActivityNotFoundException) {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try {
                    startActivityForResult(fallbackIntent, OPEN_FILE)
                } catch (fallbackError: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        R.string.gallery_app_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
                gestureDetector?.onTouchEvent(e)

                if (isSelectionMode) {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            recyclerView.findChildViewUnder(e.x, e.y)?.let { childView ->
                                val position = recyclerView.getChildAdapterPosition(childView)
                                if (position != RecyclerView.NO_POSITION) {
                                    val touchedFile = fileAdapter.files[position]
                                    val mode = if (selectedFiles.contains(touchedFile)) {
                                        SwipeSelectionMode.REMOVE
                                    } else {
                                        SwipeSelectionMode.ADD
                                    }
                                    startSwipeSelection(position, mode)
                                } else {
                                    stopSwipeSelection()
                                }
                            } ?: stopSwipeSelection()
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            if (isSwipeSelectionActive) {
                                stopSwipeSelection()
                            }
                        }
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
                if (!isSwipeSelectionActive) {
                    return false
                }
                recyclerView.findChildViewUnder(e2.x, e2.y)?.let { childView ->
                    val position = recyclerView.getChildAdapterPosition(childView)
                    if (position != RecyclerView.NO_POSITION && position != lastTouchedPosition) {
                        val anchorPosition = lastTouchedPosition
                        if (anchorPosition in 0 until fileAdapter.itemCount) {
                            val anchorFile = fileAdapter.files[anchorPosition]
                            when (swipeSelectionMode) {
                                SwipeSelectionMode.ADD -> selectFile(anchorFile)
                                SwipeSelectionMode.REMOVE -> deselectFile(anchorFile)
                                SwipeSelectionMode.NONE -> Unit
                            }
                        }
                        if (!isSelectionMode) {
                            return@let
                        }
                        val file = fileAdapter.files[position]
                        handleSwipeSelection(file)
                        if (isSelectionMode) {
                            lastTouchedPosition = position
                        }
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isSelectionMode) {
                    return
                }
                recyclerView.findChildViewUnder(e.x, e.y)?.let { childView ->
                    val position = recyclerView.getChildAdapterPosition(childView)
                    if (position != RecyclerView.NO_POSITION) {
                        val file = fileAdapter.files[position]
                        startSelectionMode(file, position)
                        startSwipeSelection(position, SwipeSelectionMode.ADD)
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


    private fun startSwipeSelection(position: Int, mode: SwipeSelectionMode) {
        isSwipeSelectionActive = true
        swipeSelectionMode = mode
        lastTouchedPosition = position
    }

    private fun stopSwipeSelection() {
        isSwipeSelectionActive = false
        swipeSelectionMode = SwipeSelectionMode.NONE
        lastTouchedPosition = -1
    }

    private fun setupDirectories() {
        val (appDir, _, trashDir) = FileManagerUtils.createAppDirectories(this)
        appDirectory = appDir
        trashDirectory = trashDir
    }

    private fun maybeAutoClearTrash() {
        val now = System.currentTimeMillis()
        val lastCleanup = sharedPreferences.getLong(KEY_LAST_TRASH_AUTO_CLEAR, 0L)
        if (now - lastCleanup < AUTO_TRASH_CLEAR_INTERVAL_MS) {
            return
        }

        val trashHadItems = trashDirectory.exists() && (trashDirectory.listFiles()?.isNotEmpty() == true)

        thread {
            val cleared = FileManagerUtils.clearTrash(trashDirectory)
            if (cleared) {
                sharedPreferences.edit().putLong(KEY_LAST_TRASH_AUTO_CLEAR, now).apply()
                if (trashHadItems) {
                    runOnUiThread {
                        if (getCurrentDirectory() == trashDirectory) {
                            loadDirectoryContent(trashDirectory)
                        }
                        Toast.makeText(this, R.string.trash_cleared_automatically, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.w("FileManager", "Automatic trash cleanup failed")
            }
        }
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        findViewById<ImageView>(R.id.sort_button).scaleY = if (sortAscending) -1f else 1f
        loadDirectoryContent(getCurrentDirectory())
    }

    private fun openTrashDirectory() {
        if (getCurrentDirectory() == trashDirectory) {
            return
        }

        if (isSelectionMode) {
            exitSelectionMode()
        }

        if (!trashDirectory.exists()) {
            trashDirectory.mkdirs()
        }

        openDirectory(trashDirectory)
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
            FileManagerUtils.importUrisToDirectory(this, directory, uris)
            runOnUiThread {
                loadDirectoryContent(directory)
            }
        }
    }

    private fun handleTargetDirectoryIntent(intent: Intent?) {
        val targetPath = intent?.getStringExtra(ShareImportActivity.EXTRA_TARGET_DIRECTORY) ?: return
        val targetDirectory = File(targetPath)
        if (!targetDirectory.exists() || !targetDirectory.isDirectory) {
            intent.removeExtra(ShareImportActivity.EXTRA_TARGET_DIRECTORY)
            return
        }

        if (directoryStack.lastOrNull() != targetDirectory) {
            openDirectory(targetDirectory)
        } else {
            loadDirectoryContent(targetDirectory)
        }

        intent.removeExtra(ShareImportActivity.EXTRA_TARGET_DIRECTORY)
    }

    private fun showPopupMenu(file: File, view: View) {
        currentFileForMenu = file
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.file_actions_menu, menu)
            

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
            
            R.id.action_select_multiple -> { startSelectionMode(file); true }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun getCurrentDirectory(): File = directoryStack.lastOrNull() ?: appDirectory

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        thread {
            val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()
            val visibleFiles = if (directory == appDirectory) {
                filesAndDirs.filterNot { it == trashDirectory }
            } else {
                filesAndDirs
            }
            val sortedVisibleFiles = visibleFiles.sortedWith(
                if (sortAscending) {
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
                } else {
                    compareBy<File> { !it.isDirectory }.thenByDescending { it.name.lowercase(Locale.ROOT) }
                }
            )
            runOnUiThread {
                if (!this::fileAdapter.isInitialized) {
                    fileAdapter = FileAdapter(
                        sortedVisibleFiles,
                        this,
                        { file -> onItemClick(file) },
                        { file, view -> onItemLongClick(file, view) }
                    )
                    recyclerView.adapter = fileAdapter
                } else {
                    fileAdapter.isSelectionMode = isSelectionMode
                    fileAdapter.selectedFiles = selectedFiles
                    fileAdapter.updateFiles(sortedVisibleFiles)
                    fileAdapter.notifyDataSetChanged()
                }
                val isTrashDirectory = directory == trashDirectory
                clearTrashButton.visibility = if (isTrashDirectory) View.VISIBLE else View.GONE
                clearTrashButton.isEnabled = isTrashDirectory
                openTrashButton.isEnabled = !isTrashDirectory
                openTrashButton.alpha = if (openTrashButton.isEnabled) 1f else 0.5f
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
        if (isSelectionMode) {
            toggleFileSelection(file)
            return
        }
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
            val movedToTrash = FileManagerUtils.moveToTrash(file, trashDirectory)
            if (!movedToTrash) {
                Toast.makeText(this, "Не удалось переместить в корзину", Toast.LENGTH_SHORT).show()
            }
            ImageOptimizer.clearImageCache(this)
            loadDirectoryContent(getCurrentDirectory())
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
        }
    }

    private fun showClearTrashConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_trash_title)
            .setMessage(R.string.confirm_clear_trash_message)
            .setPositiveButton("Да") { _, _ -> clearTrashDirectory() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearTrashDirectory() {
        thread {
            val cleared = FileManagerUtils.clearTrash(trashDirectory)
            runOnUiThread {
                if (cleared) {
                    sharedPreferences.edit().putLong(KEY_LAST_TRASH_AUTO_CLEAR, System.currentTimeMillis()).apply()
                    Toast.makeText(this, R.string.trash_cleared, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.trash_clear_failed, Toast.LENGTH_SHORT).show()
                }
                ImageOptimizer.clearImageCache(this)
                loadDirectoryContent(getCurrentDirectory())
            }
        }
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

    private fun startSelectionMode(initialFile: File?, initialPosition: Int? = null) {
        val wasSelectionMode = isSelectionMode
        isSelectionMode = true
        stopSwipeSelection()

        initialFile?.let { selectedFiles.add(it) }

        fileAdapter.isSelectionMode = true
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.notifyDataSetChanged()

        if (!wasSelectionMode) {
            selectionTopToolbar.visibility = View.VISIBLE
            val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_top)
            selectionTopToolbar.startAnimation(slideIn)
            swipeRefreshLayout.isEnabled = false
        }

        updateSelectionState()
        initialPosition?.let { lastTouchedPosition = it }
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
            deselectFile(file)
        } else {
            selectFile(file)
        }
    }

    private fun selectFile(file: File) {
        if (selectedFiles.contains(file)) return
        val previousSelection = selectedFiles.toSet()
        selectedFiles.add(file)
        fileAdapter.notifySelectionChanged(previousSelection, selectedFiles)
        updateSelectionState()
    }

    private fun deselectFile(file: File) {
        if (!selectedFiles.contains(file)) return
        val previousSelection = selectedFiles.toSet()
        selectedFiles.remove(file)
        fileAdapter.notifySelectionChanged(previousSelection, selectedFiles)
        updateSelectionState()
        if (selectedFiles.isEmpty()) {
            exitSelectionMode()
        }
    }

    private fun handleSwipeSelection(file: File) {
        if (!isSelectionMode) return
        when (swipeSelectionMode) {
            SwipeSelectionMode.ADD -> selectFile(file)
            SwipeSelectionMode.REMOVE -> deselectFile(file)
            SwipeSelectionMode.NONE -> toggleFileSelection(file)
        }
    }

    private fun toggleSelectAll() {
        val selectableFiles = fileAdapter.files.filterNot { it.isDirectory }
        val previousSelection = selectedFiles.toSet()
        val hasAllSelectable = selectableFiles.isNotEmpty() && selectableFiles.all { it in selectedFiles }

        if (hasAllSelectable) {
            selectedFiles.clear()
        } else {
            selectedFiles.removeAll { it.isDirectory }
            selectedFiles.addAll(selectableFiles)
        }

        fileAdapter.notifySelectionChanged(previousSelection, selectedFiles)
        updateSelectionState()

        if (selectedFiles.isEmpty()) {
            exitSelectionMode()
        }
    }

    private fun updateSelectionState() {
        val count = selectedFiles.size
        selectionCountTextView.text = getString(R.string.file_manager_selection_count, count)

        val selectableFiles = fileAdapter.files.filterNot { it.isDirectory }
        val allSelectableSelected = selectableFiles.isNotEmpty() && selectableFiles.all { selectedFiles.contains(it) }
        selectAllToggleButton.text = getString(if (allSelectableSelected) R.string.file_manager_select_none else R.string.file_manager_select_all)

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
                val filesToRemove = selectedFiles.toList()
                thread {
                    val failed = mutableListOf<File>()
                    filesToRemove.forEach { file ->
                        try {
                            if (!FileManagerUtils.moveToTrash(file, trashDirectory)) {
                                failed.add(file)
                            }
                        } catch (e: SecurityException) {
                            Log.e("FileManager", "SecurityException on delete: ${e.message}")
                            failed.add(file)
                        }
                    }
                    runOnUiThread {
                        if (failed.isNotEmpty()) {
                            Toast.makeText(this, "Не удалось переместить ${failed.size} элементов в корзину", Toast.LENGTH_SHORT).show()
                        }
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

        val filesToShare = selectedFiles.toList()
        val shareAsImages = filesToShare.isNotEmpty() && filesToShare.all { it.isFile && FileManagerUtils.isImageFile(it) }
        val shareAsVideos = filesToShare.isNotEmpty() && filesToShare.all { it.isFile && FileManagerUtils.isVideoFile(it) }

        if (shareAsImages) {
            val imageUris = ArrayList<Uri>(filesToShare.size)
            filesToShare.forEach { file ->
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
                imageUris.add(uri)
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_photos)))
            exitSelectionMode()
            return
        }

        if (shareAsVideos) {
            val videoUris = ArrayList<Uri>(filesToShare.size)
            filesToShare.forEach { file ->
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
                videoUris.add(uri)
            }

            val shareIntent = if (videoUris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, videoUris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "video/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, videoUris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_videos)))
            exitSelectionMode()
            return
        }

        thread {
            val sharedZipsDir = File(cacheDir, "shared_zips").apply { mkdirs() }
            sharedZipsDir.listFiles()?.forEach { it.delete() }

            val zipFile = File(sharedZipsDir, "archive-${System.currentTimeMillis()}.zip")

            try {
                val tempDir = File(cacheDir, "temp_share").apply { mkdirs() }
                filesToShare.forEach { file ->
                    val destination = File(tempDir, file.name)
                    if (file.isDirectory) {
                        file.copyRecursively(destination, true)
                    } else {
                        file.copyTo(destination, true)
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
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_archive)))
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

}
