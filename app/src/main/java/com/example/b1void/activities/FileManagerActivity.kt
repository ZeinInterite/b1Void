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
import com.example.b1void.data.FileManagerSettingsManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBar

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
    @Deprecated("Use settingsManager instead")
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var settingsManager: FileManagerSettingsManager
    private lateinit var seekbarWrapper: View
    private lateinit var sizeSeekBar: VerticalSeekBar
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private val hideSeekbarRunnable = Runnable { seekbarWrapper.visibility = View.GONE }

    // --- Новый UI для режима выделения ---
    private lateinit var selectionTopToolbar: LinearLayout
    private lateinit var selectionCountTextView: TextView
    private lateinit var selectAllToggleButton: Button
    private lateinit var confirmSelectionButton: Button
    // ----------------------------------------->

    // Click guard timestamp to prevent triggering two actions on a single tap
    private var lastClickAt: Long = 0L

    private val OPEN_FILE = 1

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private var currentFileForMenu: File? = null
    // Show older items first by default
    private var sortAscending = true

    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1
    private var gestureDetector: GestureDetector? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var spanCount = 4 // default value
    private val MIN_SPAN_COUNT = 2
    private val MAX_SPAN_COUNT = 6

    private enum class SortMode { DATE_ASC, DATE_DESC, NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC }
    private var sortMode: SortMode = SortMode.DATE_ASC

    private enum class SwipeSelectionMode { NONE, ADD, REMOVE }
    private var swipeSelectionMode = SwipeSelectionMode.NONE

    companion object {
        private const val KEY_LAST_TRASH_AUTO_CLEAR = "last_trash_auto_clear"
        private const val KEY_SORT_MODE = "sort_mode"
        private val AUTO_TRASH_CLEAR_INTERVAL_MS = TimeUnit.DAYS.toMillis(30)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        settingsManager = FileManagerSettingsManager(applicationContext)
        initializeViews()
        restoreSortMode() // Восстанавливаем сохраненный режим сортировки
        restoreSpanCount() // Восстанавливаем количество колонок
        setupButtons()
        setupRecyclerView()
        setupGestureDetector()
        setupSizeSeekbar()
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
        seekbarWrapper = findViewById(R.id.seekbar_wrapper)
        sizeSeekBar = findViewById(R.id.progressBar)

        selectionTopToolbar = findViewById(R.id.selection_top_toolbar)
        selectionCountTextView = findViewById(R.id.selection_count_text)
        selectAllToggleButton = findViewById(R.id.select_all_toggle_button)
        confirmSelectionButton = findViewById(R.id.confirm_selection_button)
    }

    private fun setupButtons() {
        // Guard to prevent accidental double-actions when user taps once
        fun clickAllowed(): Boolean {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastClickAt < 600L) return false
            lastClickAt = now
            return true
        }

        val sortButton: ImageButton = findViewById(R.id.sort_button)
        val uploadButton = findViewById<View>(R.id.upload_button)

        // Tap: open sort menu with options
        sortButton.setOnClickListener { showSortMenu(it) }
        openTrashButton.setOnClickListener { openTrashDirectory() }
        clearTrashButton.setOnClickListener { showClearTrashConfirmation() }

        uploadButton.setOnClickListener {
            if (!clickAllowed()) return@setOnClickListener
            val options = arrayOf(
                getString(R.string.add_from_gallery),
                getString(R.string.add_from_files)
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.add_files_from)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> launchGalleryPicker()
                        1 -> launchFileManagerPicker()
                    }
                }
                .show()
        }

        captureButton.setOnClickListener {
            if (!clickAllowed()) return@setOnClickListener
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

    private fun launchGalleryPicker() {
        // Prefer native gallery via ACTION_PICK, allow multiple selection when supported
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooserTitle = try { getString(R.string.select_images_from_gallery) } catch (_: Exception) { "Выберите из галереи" }
        try {
            startActivityForResult(Intent.createChooser(galleryIntent, chooserTitle), OPEN_FILE)
        } catch (_: ActivityNotFoundException) {
            // Fallback to system picker with images and videos
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            try {
                startActivityForResult(Intent.createChooser(fallback, chooserTitle), OPEN_FILE)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.gallery_app_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchFileManagerPicker() {
        val mimeTypes = arrayOf(
            "image/*",
            "video/*",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooserTitle = try { getString(R.string.select_images_from_gallery) } catch (_: Exception) { "Select files" }
        try {
            startActivityForResult(Intent.createChooser(intent, chooserTitle), OPEN_FILE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.gallery_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var scaleFactor = 1.0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            swipeRefreshLayout.isEnabled = false
            showSizeSeekbar()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 2.0f))

            if (scaleFactor > 1.2f && spanCount > MIN_SPAN_COUNT) {
                spanCount--
                updateGridLayout()
                updateSeekbarFromSpan()
                scaleFactor = 1.0f
            } else if (scaleFactor < 0.8f && spanCount < MAX_SPAN_COUNT) {
                spanCount++
                updateGridLayout()
                updateSeekbarFromSpan()
                scaleFactor = 1.0f
            }
            showSizeSeekbar()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            swipeRefreshLayout.isEnabled = true
            scheduleHideSizeSeekbar()
        }
    }

    private fun setupRecyclerView() {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredItemWidthDp = 120
        // spanCount будет восстановлен из DataStore в restoreSpanCount()
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

    private fun setupSizeSeekbar() {
        updateSeekbarFromSpan()
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val max = seekBar?.max ?: 100
                val fraction = progress.toFloat() / max.toFloat()
                val mapped = (MAX_SPAN_COUNT - Math.round(fraction * (MAX_SPAN_COUNT - MIN_SPAN_COUNT)))
                    .coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)
                if (mapped != spanCount) {
                    spanCount = mapped
                    updateGridLayout()
                }
                showSizeSeekbar()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                showSizeSeekbar()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideSizeSeekbar()
            }
        })
    }

    private fun updateSeekbarFromSpan() {
        val max = sizeSeekBar.max.takeIf { it > 0 } ?: 100
        val denom = (MAX_SPAN_COUNT - MIN_SPAN_COUNT).takeIf { it != 0 } ?: 1
        val fraction = (MAX_SPAN_COUNT - spanCount).toFloat() / denom.toFloat()
        val progress = (fraction * max).toInt().coerceIn(0, max)
        sizeSeekBar.progress = progress
    }

    private fun showSizeSeekbar() {
        uiHandler.removeCallbacks(hideSeekbarRunnable)
        if (seekbarWrapper.visibility != View.VISIBLE) {
            seekbarWrapper.visibility = View.VISIBLE
        }
    }

    private fun scheduleHideSizeSeekbar(delayMs: Long = 1500L) {
        uiHandler.removeCallbacks(hideSeekbarRunnable)
        uiHandler.postDelayed(hideSeekbarRunnable, delayMs)
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
                        val isMedia = !file.isDirectory && (fileAdapter.isImage(file) || fileAdapter.isVideo(file))
                        if (isMedia) {
                            startSelectionMode(file, position)
                            startSwipeSelection(position, SwipeSelectionMode.ADD)
                        }
                        // If not media (e.g., folder), do not start selection here.
                    }
                }
            }
        })
    }

    private fun updateGridLayout() {
        (recyclerView.layoutManager as GridLayoutManager).spanCount = spanCount
        saveSpanCount() // Используем новый метод с DataStore
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

    /**
     * Восстанавливает сохраненный режим сортировки из DataStore
     */
    private fun restoreSortMode() {
        lifecycleScope.launch {
            try {
                val savedMode = settingsManager.getSortMode().first()
                sortMode = when (savedMode) {
                    FileManagerSettingsManager.SortMode.DATE_ASC -> SortMode.DATE_ASC
                    FileManagerSettingsManager.SortMode.DATE_DESC -> SortMode.DATE_DESC
                    FileManagerSettingsManager.SortMode.NAME_ASC -> SortMode.NAME_ASC
                    FileManagerSettingsManager.SortMode.NAME_DESC -> SortMode.NAME_DESC
                    FileManagerSettingsManager.SortMode.SIZE_ASC -> SortMode.SIZE_ASC
                    FileManagerSettingsManager.SortMode.SIZE_DESC -> SortMode.SIZE_DESC
                }

                // Обновляем иконку сортировки
                val asc = (sortMode == SortMode.DATE_ASC || sortMode == SortMode.NAME_ASC || sortMode == SortMode.SIZE_ASC)
                findViewById<ImageView>(R.id.sort_button).scaleY = if (asc) -1f else 1f

                Log.d("FileManagerActivity", "Восстановлен режим сортировки: $sortMode")
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Ошибка восстановления режима сортировки", e)
                sortMode = SortMode.DATE_ASC // Значение по умолчанию
            }
        }
    }

    /**
     * Сохраняет текущий режим сортировки в DataStore
     */
    private fun saveSortMode() {
        lifecycleScope.launch {
            try {
                val settingsMode = when (sortMode) {
                    SortMode.DATE_ASC -> FileManagerSettingsManager.SortMode.DATE_ASC
                    SortMode.DATE_DESC -> FileManagerSettingsManager.SortMode.DATE_DESC
                    SortMode.NAME_ASC -> FileManagerSettingsManager.SortMode.NAME_ASC
                    SortMode.NAME_DESC -> FileManagerSettingsManager.SortMode.NAME_DESC
                    SortMode.SIZE_ASC -> FileManagerSettingsManager.SortMode.SIZE_ASC
                    SortMode.SIZE_DESC -> FileManagerSettingsManager.SortMode.SIZE_DESC
                }
                settingsManager.setSortMode(settingsMode)
                Log.d("FileManagerActivity", "Сохранен режим сортировки: $sortMode")
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Ошибка сохранения режима сортировки", e)
            }
        }
    }

    /**
     * Восстанавливает сохраненное количество колонок из DataStore
     */
    private fun restoreSpanCount() {
        lifecycleScope.launch {
            try {
                spanCount = settingsManager.getSpanCount().first()
                // Обновим layoutManager если RecyclerView уже инициализирован
                if (::recyclerView.isInitialized) {
                    (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
                }
                Log.d("FileManagerActivity", "Восстановлено количество колонок: $spanCount")
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Ошибка восстановления spanCount", e)
                spanCount = FileManagerSettingsManager.DEFAULT_SPAN_COUNT
            }
        }
    }

    /**
     * Сохраняет количество колонок в DataStore
     */
    private fun saveSpanCount() {
        lifecycleScope.launch {
            settingsManager.setSpanCount(spanCount)
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
            FileManagerUtils.importUrisToDirectoryModern(this, directory, uris)
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
            val menuRes = if (file.isDirectory) R.menu.file_context_menu else R.menu.file_actions_menu
            menuInflater.inflate(menuRes, menu)

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
            R.id.action_share_single -> { shareFile(file); true }
            R.id.action_rename -> { showRenameDialog(file); true }
            
            R.id.action_select_multiple -> { startSelectionMode(file); true }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun getCurrentDirectory(): File = directoryStack.lastOrNull() ?: appDirectory

    private fun categoryRank(file: File): Int = when {
        file.isDirectory -> 0
        FileManagerUtils.isVideoFile(file) -> 1
        FileManagerUtils.isImageFile(file) -> 2
        else -> 3
    }

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        thread {
            val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()
            // Фильтруем служебные файлы (.nomedia и другие скрытые файлы)
            val visibleFiles = if (directory == appDirectory) {
                filesAndDirs.filterNot { it == trashDirectory || it.name.startsWith(".") || it.isHidden }
            } else {
                filesAndDirs.filterNot { it.name.startsWith(".") || it.isHidden }
            }
            // Group order: Folders (0) → Videos (1) → Photos (2) → Others (3)
            // Inside each group, apply selected sort mode
            val sortedVisibleFiles = visibleFiles.sortedWith { a, b ->
                val cr = categoryRank(a).compareTo(categoryRank(b))
                if (cr != 0) return@sortedWith cr

                fun cmpDate(x: File, y: File): Int =
                    FileManagerUtils.getCreationTimeMillis(x).compareTo(FileManagerUtils.getCreationTimeMillis(y))
                fun cmpName(x: File, y: File): Int =
                    x.name.lowercase(Locale.ROOT).compareTo(y.name.lowercase(Locale.ROOT))
                fun cmpSize(x: File, y: File): Int =
                    x.length().compareTo(y.length())

                val cmp = when (sortMode) {
                    SortMode.DATE_ASC -> cmpDate(a, b)
                    SortMode.DATE_DESC -> -cmpDate(a, b)
                    SortMode.NAME_ASC -> cmpName(a, b)
                    SortMode.NAME_DESC -> -cmpName(a, b)
                    SortMode.SIZE_ASC -> cmpSize(a, b)
                    SortMode.SIZE_DESC -> -cmpSize(a, b)
                }
                cmp
            }
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

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.sort_mode_menu, popup.menu)

        // Reflect current selection with a checkmark
        val checkedId = when (sortMode) {
            SortMode.DATE_ASC -> R.id.sort_date_asc
            SortMode.DATE_DESC -> R.id.sort_date_desc
            SortMode.NAME_ASC -> R.id.sort_name_asc
            SortMode.NAME_DESC -> R.id.sort_name_desc
            SortMode.SIZE_ASC -> R.id.sort_size_asc
            SortMode.SIZE_DESC -> R.id.sort_size_desc
        }
        popup.menu.setGroupCheckable(R.id.sort_mode_group, true, true)
        popup.menu.findItem(checkedId)?.isChecked = true

        popup.setOnMenuItemClickListener { item ->
            val newMode = when (item.itemId) {
                R.id.sort_date_asc -> SortMode.DATE_ASC
                R.id.sort_date_desc -> SortMode.DATE_DESC
                R.id.sort_name_asc -> SortMode.NAME_ASC
                R.id.sort_name_desc -> SortMode.NAME_DESC
                R.id.sort_size_asc -> SortMode.SIZE_ASC
                R.id.sort_size_desc -> SortMode.SIZE_DESC
                else -> null
            }
            if (newMode != null) {
                sortMode = newMode
                item.isChecked = true
                // Update icon orientation for visual hint: asc → upside-down
                val asc = (sortMode == SortMode.DATE_ASC || sortMode == SortMode.NAME_ASC || sortMode == SortMode.SIZE_ASC)
                findViewById<ImageView>(R.id.sort_button).scaleY = if (asc) -1f else 1f
                saveSortMode()
                loadDirectoryContent(getCurrentDirectory())
                true
            } else false
        }
        popup.show()
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
        } else {
            openFile(file)
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

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val mime = try {
            val ext = file.extension.lowercase(Locale.getDefault())
            if (ext.isNotEmpty()) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
        } catch (_: Exception) { null } ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Нет приложений для открытия данного файла", Toast.LENGTH_SHORT).show()
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

            val zipFileName = if (filesToShare.size == 1 && filesToShare.first().isDirectory) {
                val folderName = filesToShare.first().name.removeSuffix(".zip")
                "$folderName.zip"
            } else {
                "archive-${System.currentTimeMillis()}.zip"
            }
            val zipFile = File(sharedZipsDir, zipFileName)

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

        MoveFilesBottomSheet.newInstance(
            filePaths,
            sourceFolderPath,
            rootFolderPath,
            trashDirectory.absolutePath
        )
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
