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