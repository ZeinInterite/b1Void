package com.example.b1void.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.viewmodels.CameraEvent
import com.example.b1void.viewmodels.CameraViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.appcompat.widget.PopupMenu
import java.text.SimpleDateFormat
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private enum class CaptureMode {
        PHOTO,
        VIDEO
    }

    private lateinit var viewModel: CameraViewModel
    private lateinit var resolutionAdapter: ResolutionAdapter
    
    // Views
    private lateinit var resolutionSelectorButton: ImageButton
    private lateinit var shutterButton: ImageButton
    private lateinit var thumbnailPreview: ImageView
    private lateinit var resolutionListContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var switchCameraButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var torchButton: ImageButton
    private lateinit var modeSelector: MaterialButtonToggleGroup
    private lateinit var recordingTimer: TextView

    // CameraX components
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isTorchOn = false
    private var currentMode = CaptureMode.PHOTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        
        // Find views
        resolutionSelectorButton = findViewById(R.id.resolutionSelectorButton)
        resolutionListContainer = findViewById(R.id.resolutionListContainer)
        val resolutionRecyclerView = findViewById<RecyclerView>(R.id.resolutionRecyclerView)
        progressBar = findViewById(R.id.resolutionChangeProgressBar)
        shutterButton = findViewById(R.id.shutterButton)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        settingsButton = findViewById(R.id.settingsButton)
        torchButton = findViewById(R.id.torchButton)
        modeSelector = findViewById(R.id.modeSelector)
        recordingTimer = findViewById(R.id.recordingTimer)

        // Setup RecyclerView
        resolutionAdapter = ResolutionAdapter { size ->
            viewModel.onResolutionSelected(size)
            rebindCameraUseCases()
            resolutionListContainer.visibility = View.GONE
        }
        resolutionRecyclerView.layoutManager = LinearLayoutManager(this)
        resolutionRecyclerView.adapter = resolutionAdapter

        // Setup Listeners
        resolutionSelectorButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            resolutionListContainer.visibility = if (resolutionListContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        shutterButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (currentMode) {
                CaptureMode.PHOTO -> {
                    val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
                    viewModel.onTakePicture(imageCapture, savePath)
                }
                CaptureMode.VIDEO -> captureVideo()
            }
        }
        switchCameraButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            rebindCameraUseCases()
        }
        torchButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isTorchOn = !isTorchOn
            camera?.cameraControl?.enableTorch(isTorchOn)
            torchButton.alpha = if (isTorchOn) 1.0f else 0.5f
        }
        settingsButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showSettingsMenu(it)
        }
        modeSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.videoModeButton -> CaptureMode.VIDEO
                    else -> CaptureMode.PHOTO
                }
                rebindCameraUseCases()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
        
        observeUiState()
        observeCameraEvents()
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val uiState = viewModel.uiState.value

        // Add menu items programmatically
        val flashTitle = if (uiState.flashMode == ImageCapture.FLASH_MODE_ON) "Вспышка: Вкл" else "Вспышка: Выкл"
        val stampTitle = if (uiState.isWatermarkEnabled) "Штамп: Вкл" else "Штамп: Выкл"

        popup.menu.add(flashTitle)
        popup.menu.add(stampTitle)

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                flashTitle -> viewModel.cycleFlashMode() // Toggles between ON and OFF
                stampTitle -> viewModel.onWatermarkToggled()
            }
            true
        }
        popup.show()
    }
    
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update resolution list and pass the current resolution
                    resolutionAdapter.submitList(state.availableResolutions, state.currentResolution)
                    
                    // Show/hide progress bar
                    progressBar.visibility = if (state.isBinding) View.VISIBLE else View.GONE

                    // Update thumbnail
                    state.lastThumbnail?.let { thumbnail ->
                        thumbnailPreview.setImageBitmap(thumbnail)
                    }
                }
            }
        }
    }

    private fun observeCameraEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is CameraEvent.Error -> {
                            Toast.makeText(this@CameraActivity, "Error: ${event.message}", Toast.LENGTH_LONG).show()
                        }
                        is CameraEvent.PictureSaved -> {
                            // Photo saved, do nothing (toast removed as per request)
                        }
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            this.cameraProvider = cameraProviderFuture.get()
            rebindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun rebindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return
        val uiState = viewModel.uiState.value

        val preview = Preview.Builder()
            .build()
            .also {
                val previewView = findViewById<PreviewView>(R.id.previewView)
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        try {
            cameraProvider.unbindAll()

            when (currentMode) {
                CaptureMode.PHOTO -> {
                    val captureMode = if (uiState.isQualityPriority) {
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    } else {
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }

                    val imageCaptureBuilder = ImageCapture.Builder()
                        .setCaptureMode(captureMode)
                        .setFlashMode(uiState.flashMode)
                    
                    uiState.currentResolution?.let {
                        imageCaptureBuilder.setTargetResolution(it)
                    }
                    this.imageCapture = imageCaptureBuilder.build()
                    this.videoCapture = null // Ensure video capture is null

                    this.camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, this.imageCapture)
                    updateUiForMode()
                }
                CaptureMode.VIDEO -> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build()
                    this.videoCapture = VideoCapture.withOutput(recorder)
                    this.imageCapture = null // Ensure image capture is null

                    this.camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, this.videoCapture)
                    updateUiForMode()
                }
            }
            
            this.camera?.cameraInfo?.let { viewModel.onCameraBound(it) }
            
            lifecycleScope.launch {
                delay(300)
                viewModel.onRebindComplete()
            }

        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Failed to rebind camera.", Toast.LENGTH_SHORT).show()
            viewModel.onRebindComplete()
        }
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val name = "VID_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (savePath != null) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, savePath)
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this), videoRecordingListener)
    }

    private val videoRecordingListener = Consumer<VideoRecordEvent> {
        when (it) {
            is VideoRecordEvent.Start -> {
                updateUiForRecording(true)
            }
            is VideoRecordEvent.Finalize -> {
                if (!it.hasError()) {
                    val msg = "Video saved to: ${it.outputResults.outputUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                } else {
                    recording?.close()
                    recording = null
                    Log.e(TAG, "Video recording failed with error: ${it.error}")
                }
                updateUiForRecording(false)
            }
            is VideoRecordEvent.Status -> {
                val minutes = java.util.concurrent.TimeUnit.NANOSECONDS.toMinutes(it.recordingStats.recordedDurationNanos)
                val seconds = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(it.recordingStats.recordedDurationNanos) % 60
                recordingTimer.text = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private fun updateUiForMode() {
        when (currentMode) {
            CaptureMode.PHOTO -> {
                shutterButton.setImageResource(android.R.drawable.ic_menu_camera)
                resolutionSelectorButton.visibility = View.VISIBLE
            }
            CaptureMode.VIDEO -> {
                shutterButton.setImageResource(android.R.drawable.ic_media_play)
                resolutionSelectorButton.visibility = View.GONE
                resolutionListContainer.visibility = View.GONE
            }
        }
    }

    private fun updateUiForRecording(isRecording: Boolean) {
        if (isRecording) {
            shutterButton.setImageResource(android.R.drawable.ic_media_pause)
            recordingTimer.visibility = View.VISIBLE
            // Disable other controls during recording
            switchCameraButton.isEnabled = false
            modeSelector.isEnabled = false
            settingsButton.isEnabled = false
        } else {
            shutterButton.setImageResource(android.R.drawable.ic_media_play)
            recordingTimer.visibility = View.GONE
            // Re-enable controls
            switchCameraButton.isEnabled = true
            modeSelector.isEnabled = true
            settingsButton.isEnabled = true
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop recording if it's in progress
        recording?.stop()
        recording = null
    }

    // --- Inner Adapter Class ---
    class ResolutionAdapter(private val onSizeSelected: (Size) -> Unit) : RecyclerView.Adapter<ResolutionAdapter.ResolutionViewHolder>() {

        private var resolutions: List<Size> = emptyList()
        private var selectedResolution: Size? = null

        fun submitList(newList: List<Size>, selected: Size?) {
            resolutions = newList
            selectedResolution = selected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResolutionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_resolution, parent, false)
            return ResolutionViewHolder(view)
        }

        override fun onBindViewHolder(holder: ResolutionViewHolder, position: Int) {
            val size = resolutions[position]
            holder.bind(size, onSizeSelected)
            if (size == selectedResolution) {
                holder.checkmark.visibility = View.VISIBLE
            } else {
                holder.checkmark.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = resolutions.size

        class ResolutionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(R.id.resolution_text)
            val checkmark: ImageView = itemView.findViewById(R.id.checkmark_image)

            fun bind(size: Size, onSizeSelected: (Size) -> Unit) {
                textView.text = "${size.width} x ${size.height}"
                itemView.setOnClickListener { onSizeSelected(size) }
            }
        }
    }
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        const val EXTRA_SAVE_PATH = "extra_save_path"
    }
}