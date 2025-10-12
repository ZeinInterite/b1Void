package com.example.b1void.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.exifinterface.media.ExifInterface
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.WindowManager
import android.widget.LinearLayout
import android.webkit.MimeTypeMap
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.data.CameraSettingsManager
import com.example.b1void.ui.CameraSettingsDialogFragment
import com.example.b1void.ui.camera.CameraViewModel as ComposeCameraViewModel
import com.example.b1void.ui.camera.ZoomControl
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.example.b1void.utils.VideoStampProcessor
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.abs

class CameraActivity : AppCompatActivity() {

    private enum class CaptureMode {
        PHOTO,
        VIDEO
    }

    // View references
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var flipCameraButton: ImageButton
    private lateinit var recordingTimer: Chronometer
    private lateinit var thumbnailPreview: ImageView
    private lateinit var settingsButton: ImageButton
    private lateinit var torchButton: ImageButton
    private var autofocusButton: ImageButton? = null
    // Legacy zoom SeekBars removed; using Compose ZoomControl instead
    private lateinit var focusIndicator: View
    private lateinit var lockIcon: ImageView
    private lateinit var evOverlay: View
    private lateinit var captureAnimationView: ImageView
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var bottomControlsContainer: ConstraintLayout
    private lateinit var topControlsSpacer: View
    private lateinit var zoomCompose: ComposeView

    private enum class UiOrientation {
        PORTRAIT,
        PORTRAIT_REVERSE,
        LANDSCAPE_LEFT,
        LANDSCAPE_RIGHT
    }

    private var currentUiOrientation: UiOrientation? = null


    // CameraX components
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var activeCameraId: String? = null
    private var currentCameraCharacteristics: CameraCharacteristics? = null
    private var availableCaptureResolutions: List<Size> = emptyList()
    private var availablePreviewResolutions: List<Size> = emptyList()
    private var orientationEventListener: OrientationEventListener? = null
    private var selectableCaptureResolutions: List<Size> = emptyList()
    private val invalidCaptureResolutions = mutableSetOf<Size>()
    private var currentTargetRotation = Surface.ROTATION_0
    private var cameraRestartJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isLandscapeUi: Boolean = false
    private var layoutOrientationInitialized = false
    private var lastLayoutRotation: Int = Surface.ROTATION_0
    private val controlsHideDelayMs = 2500L
    private val controlsAutoHideRunnable = Runnable {
        val orientation = currentUiOrientation
        if (orientation == UiOrientation.LANDSCAPE_LEFT || orientation == UiOrientation.LANDSCAPE_RIGHT) {
            fadeControlsForLandscape()
        }
    }

    // Gesture detector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var tapGestureDetector: GestureDetector

    // Settings
    private lateinit var settingsManager: CameraSettingsManager
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var selectedResolution: Size? = DEFAULT_PHOTO_RESOLUTION
    private var supportedResolutions: List<Size> = emptyList()
    
    // Wake lock for screen management

    // State variables
    private var currentMode = CaptureMode.PHOTO
    private var isRecording = false
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var lastSavedFile: File? = null
    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f
    private var isZoomGesture = false
    private var shouldRestoreTorchState = false
    private var savedTorchState = false
    private val useComposeZoom = true
    // Hold-to-record state
    private var isHoldRecordingActive = false
    private var holdStartRunnable: Runnable? = null
    private val holdToRecordDelayMs = 200L
    private var stopHoldRunnable: Runnable? = null
    private val stopHoldDelayMs = 500L
    private var pressDownUptime: Long = 0L
    private val quickTapThresholdMs = 150L
    private val hideFocusIndicatorRunnable = Runnable {
        focusIndicator.animate().cancel()
        focusIndicator.visibility = View.GONE
    }
    private var focusLastX: Float? = null
    private var focusLastY: Float? = null
    private var evHideRunnable: Runnable? = null
    private var evController: com.example.b1void.camera.ev.EvController? = null

    // Focus coordination
    private var focusCoordinator: com.example.b1void.camera.focus.FocusCoordinator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        settingsManager = CameraSettingsManager(this)
        // Request camera permissions on first launch
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                settingsManager.setResolution("${DEFAULT_PHOTO_RESOLUTION.width}x${DEFAULT_PHOTO_RESOLUTION.height}")
            }
        }

        initializeViews()
        lastLayoutRotation = getDisplayRotation()
        updateLayoutForRotation(lastLayoutRotation, animate = false)
        setupListeners()
        observeSettings()

        if (allPermissionsGranted()) {
            startCamera()
            loadLatestPhotoThumbnail()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        scaleGestureDetector = ScaleGestureDetector(this, ScaleGestureListener())
        currentTargetRotation = getDisplayRotation()
        setupOrientationListener()
        
        // Initialize wake lock
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val camera = camera ?: return true
            val zoomState = camera.cameraInfo.zoomState.value ?: return true
            val currentZoomRatio = zoomState.zoomRatio
            val newZoomRatio = currentZoomRatio * detector.scaleFactor
            camera.cameraControl.setZoomRatio(newZoomRatio)
            return true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Click is used for VIDEO mode toggle. PHOTO mode tap is handled in onTouch.
        captureButton.setOnClickListener {
            if (currentMode == CaptureMode.VIDEO) {
                toggleVideoRecording()
            }
        }

        captureButton.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (currentMode == CaptureMode.PHOTO) {
                        v.isPressed = true
                        pressDownUptime = SystemClock.uptimeMillis()
                        // If a delayed stop is pending (grace period), cancel it to continue recording seamlessly
                        cancelScheduledStopVideoRecordingForHold()
                        // If already recording due to prior hold, keep going; otherwise schedule start
                        if (!isHoldRecordingActive && !isRecording) {
                            scheduleHoldRecordingStart()
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentMode == CaptureMode.PHOTO) {
                        v.isPressed = false
                        cancelHoldRecordingStartIfPending()
                        val elapsed = SystemClock.uptimeMillis() - pressDownUptime
                        val isQuickTap = elapsed <= quickTapThresholdMs
                        if (isQuickTap && !isHoldRecordingActive && !isRecording) {
                            // Only quick taps produce photos
                            takePhoto()
                        } else {
                            // Consider this a video gesture: ensure recording, then schedule delayed stop
                            if (!isRecording) {
                                startVideoRecordingForHold()
                            }
                            scheduleStopVideoRecordingForHold()
                        }
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        // Removed mode switch button; mode remains PHOTO with hold-to-record.

        flipCameraButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        thumbnailPreview.setOnClickListener {
            lastSavedFile?.let {
                if (it.exists()) {
                    onThumbnailClicked(it)
                }
            }
        }

        settingsButton.setOnClickListener {
            val settingsDialog = CameraSettingsDialogFragment()
            settingsDialog.setSupportedResolutions(supportedResolutions) { newResolution ->
                selectedResolution = newResolution
                // Restart camera with new resolution
                startCamera()
            }
            settingsDialog.show(supportFragmentManager, "CameraSettingsDialog")
        }

        torchButton.setOnClickListener {
            camera?.let {
                if (it.cameraInfo.hasFlashUnit()) {
                    val isTorchOn = it.cameraInfo.torchState.value == TorchState.ON
                    val newTorchState = !isTorchOn
                    it.cameraControl.enableTorch(newTorchState)
                    // Save torch state to persistent storage
                    lifecycleScope.launch {
                        settingsManager.setTorchEnabled(newTorchState)
                    }
                }
            }
        }

        autofocusButton?.setOnClickListener { triggerManualAutofocus() }

        tapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showControlsOnInteraction()
                focusCoordinator?.onSingleTap(e.x, e.y)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                focusCoordinator?.onLongPress(e.x, e.y)
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // EV: vertical swipe near last focus point
                if (focusLastX != null && focusLastY != null) {
                    val dx = e2.x - focusLastX!!
                    val dy = e2.y - focusLastY!!
                    val near = kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= 160.0
                    if (near) {
                        ensureEvController()
                        evController?.begin()
                        evController?.adjustByDrag(distanceY)
                        scheduleHideEvOverlay()
                        return true
                    }
                }
                return false
            }
        }).apply {
            setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    focusCoordinator?.resetToCenter()
                    return true
                }
                override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false
            })
        }

        previewView.setOnTouchListener { view, event ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return@setOnTouchListener false
            }
            scaleGestureDetector.onTouchEvent(event)
            val handled = tapGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isZoomGesture = false
                    showControlsOnInteraction()
                }
                MotionEvent.ACTION_POINTER_DOWN -> isZoomGesture = true
                MotionEvent.ACTION_CANCEL -> isZoomGesture = false
                MotionEvent.ACTION_UP -> view.performClick()
            }
            handled || true
        }

        // Legacy SeekBar zoom listeners removed
    }

    private fun initializeViews() {
        rootLayout = findViewById(R.id.main_container)
        topControlsContainer = findViewById(R.id.topControls)
        bottomControlsContainer = findViewById(R.id.bottomControls)
        topControlsSpacer = findViewById(R.id.topControlsSpacer)
        previewView = findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        captureButton = findViewById(R.id.shutterButton)
        // modeSwitchButton removed from layout
        flipCameraButton = findViewById(R.id.switchCameraButton)
        recordingTimer = findViewById(R.id.recording_timer)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        thumbnailPreview.scaleType = ImageView.ScaleType.FIT_CENTER
        thumbnailPreview.adjustViewBounds = true
        settingsButton = findViewById(R.id.settingsButton)
        torchButton = findViewById(R.id.torchButton)
        // No autofocus button in layout anymore
        // Legacy zoom sliders removed from layouts
        zoomCompose = findViewById(R.id.zoomCompose)
        focusIndicator = findViewById(R.id.focusIndicator)
        lockIcon = findViewById(R.id.lockIcon)
        evOverlay = findViewById(R.id.evOverlay)
        captureAnimationView = findViewById(R.id.captureAnimationView)
        // Hide legacy zoom sliders when using Compose zoom
        if (useComposeZoom) {
            // Legacy sliders are not present in layout anymore
        }
        
        // Set initial properties for vertical slider (legacy)
        // No legacy slider init

        // Set Compose zoom content if enabled
        if (useComposeZoom) {
            val vm = ViewModelProvider(this)[ComposeCameraViewModel::class.java]
            zoomCompose.setContent {
                val zoom by vm.zoomRatio.collectAsState()
                val minZoom by vm.minZoomRatio.collectAsState()
                val maxZoom by vm.maxZoomRatio.collectAsState()
                val presets by vm.availablePresets.collectAsState()
                MaterialTheme {
                    ZoomControl(
                        zoomRatio = zoom,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        availablePresets = presets,
                        modifier = Modifier,
                        leftHanded = false,
                        onZoomChanged = { ratio -> vm.applyZoomRatio(ratio) },
                        onPresetSelected = { preset -> vm.animateToPreset(preset) }
                    )
                }
            }
        }

        updateLayoutForRotation(getDisplayRotation(), animate = false)
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsManager.getFlashMode().collect { mode ->
                val newFlashMode = when(mode) {
                    0 -> ImageCapture.FLASH_MODE_OFF
                    1 -> ImageCapture.FLASH_MODE_ON
                    2 -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                if (newFlashMode != flashMode) {
                    flashMode = newFlashMode
                    startCamera()
                }
            }
        }
        lifecycleScope.launch {
            settingsManager.getResolution().collect { resString ->
                val parsedResolution = resString?.let { parseResolution(it) }
                if (selectedResolution != parsedResolution) {
                    selectedResolution = parsedResolution ?: DEFAULT_PHOTO_RESOLUTION
                    startCamera()
                }
            }
        }
        lifecycleScope.launch {
            settingsManager.getTorchEnabled().collect { enabled ->
                savedTorchState = enabled
            }
        }
    }

    private fun onThumbnailClicked(file: File) {
        if (isImageFile(file)) {
            val images = getSavedImages()
            if (images.isEmpty()) return
            val imagePaths = ArrayList(images.map { it.absolutePath })
            val currentIndex = images.indexOfFirst { it.absolutePath == file.absolutePath }.let { if (it >= 0) it else 0 }
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putStringArrayListExtra("image_paths", imagePaths)
                putExtra("current_image_index", currentIndex)
            }
            startActivity(intent)
        } else {
            val authority = "${applicationContext.packageName}.provider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Невозможно открыть файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCameraUI() {
        runOnUiThread {
            // Mode switch removed; default UI reflects PHOTO mode.
            captureButton.setBackgroundResource(R.drawable.bg_capture_button_photo)
            captureButton.setImageResource(R.drawable.camera)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        // Prevent re-binding camera while recording video to avoid stopping the session
        if (isRecording) {
            Log.d(TAG, "startCamera() ignored: recording in progress")
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val resolvedCameraId = resolveCameraId()
            if (resolvedCameraId != null) {
                activeCameraId = resolvedCameraId
                refreshCameraCharacteristics(resolvedCameraId)
                updateSelectableCaptureResolutions()
                // Update supported resolutions with real camera data
                supportedResolutions = com.example.b1void.utils.CameraOptimizer.getSupportedResolutions(this, resolvedCameraId)
            } else {
                availableCaptureResolutions = emptyList()
                availablePreviewResolutions = emptyList()
                selectableCaptureResolutions = emptyList()
                // Use fallback resolutions if no camera ID
                supportedResolutions = com.example.b1void.utils.CameraOptimizer.getSupportedResolutions(this)
            }

            val captureResolution = selectedResolution?.let {
                when {
                    selectableCaptureResolutions.contains(it) -> it
                    availableCaptureResolutions.contains(it) -> it
                    else -> null
                }
            }
                ?: selectableCaptureResolutions.firstOrNull()
                ?: availableCaptureResolutions.firstOrNull()
                ?: DEFAULT_PHOTO_RESOLUTION

            val previewResolution = findBestPreviewResolutionFor(captureResolution)

            if (selectedResolution != captureResolution) {
                lifecycleScope.launch {
                    settingsManager.setResolution("${captureResolution.width}x${captureResolution.height}")
                }
            }
            selectedResolution = captureResolution

            val imageCaptureSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        captureResolution,
                        ResolutionStrategy.FALLBACK_RULE_NONE
                    )
                )
                .build()

            val useCaseGroupBuilder = UseCaseGroup.Builder()

            val previewBuilder = Preview.Builder()
                .setTargetRotation(currentTargetRotation)

            val previewSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        previewResolution ?: captureResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            previewBuilder.setResolutionSelector(previewSelector)

            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            previewUseCase = preview
            useCaseGroupBuilder.addUseCase(preview)

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .setTargetRotation(currentTargetRotation)

            imageCaptureBuilder.setResolutionSelector(imageCaptureSelector)

            val newImageCapture = imageCaptureBuilder.build()
            imageCapture = newImageCapture
            useCaseGroupBuilder.addUseCase(newImageCapture)

            // Always bind VideoCapture to support hold-to-record in PHOTO mode
            run {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                        )
                    )
                    .build()
                val newVideoCapture = VideoCapture.withOutput(recorder).apply {
                    targetRotation = currentTargetRotation
                }
                videoCapture = newVideoCapture
                useCaseGroupBuilder.addUseCase(newVideoCapture)
            }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, useCaseGroupBuilder.build()
                )
                // Bind Compose zoom VM to CameraX for live zoom state
                if (useComposeZoom) {
                    try {
                        val vm = ViewModelProvider(this)[ComposeCameraViewModel::class.java]
                        camera?.let { vm.bindCamera(it) }
                    } catch (_: Exception) { }
                }
                applyCamera2Defaults()
                setupCameraStateObserver()
                setupTorchObserver()
                // Compose zoom used; no legacy zoom observer
                val resolutionConfirmed = verifyBoundCaptureResolution(captureResolution)
                if (!resolutionConfirmed) {
                    return@addListener
                }
                // Initialize modern focus coordinator via feature module provider
                camera?.let { cam ->
                    focusCoordinator = com.example.b1void.camera.focus.FocusProvider.create(
                        previewView = previewView,
                        camera = cam,
                        mainExecutor = ContextCompat.getMainExecutor(this),
                        callbacks = object : com.example.b1void.camera.focus.FocusCoordinator.Callbacks {
                            override fun showIndicator(x: Float, y: Float) { showFocusIndicator(x, y); focusLastX = x; focusLastY = y }
                            override fun hideIndicator() { focusIndicator.post(hideFocusIndicatorRunnable) }
                            override fun onFocusResult(success: Boolean) {
                                val delay = if (success) 600L else 300L
                                focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
                                focusIndicator.postDelayed(hideFocusIndicatorRunnable, delay)
                                if (success) previewView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            override fun onLockChanged(locked: Boolean) {
                                if (!locked) focusIndicator.post(hideFocusIndicatorRunnable)
                                toggleLockIcon(locked)
                            }
                        }
                    )
                    ensureEvController()
                }
                focusAtCenter()

                // Restore torch state if needed (with slight delay to ensure camera is ready)
                Log.d(TAG, "Camera binding complete - shouldRestoreTorchState=$shouldRestoreTorchState, savedTorchState=$savedTorchState")
                if (shouldRestoreTorchState) {
                    lifecycleScope.launch {
                        Log.d(TAG, "Waiting 100ms before restoring torch state...")
                        delay(100) // Small delay to ensure camera is fully initialized
                        restoreTorchState()
                        shouldRestoreTorchState = false
                        Log.d(TAG, "Torch state restoration complete, shouldRestoreTorchState set to false")
                    }
                } else {
                    Log.d(TAG, "Skipping torch restoration - shouldRestoreTorchState is false")
                }
            } catch (exc: IllegalArgumentException) {
                Log.w(TAG, "Binding failed for resolution ${captureResolution.width}x${captureResolution.height}", exc)
                handleUnsupportedCaptureResolution(captureResolution)
                return@addListener
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                scheduleCameraRestart("Use case binding failed")
                return@addListener
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun setupCameraStateObserver() {
        val cam = camera ?: return
        val cameraStateLiveData = cam.cameraInfo.cameraState
        cameraStateLiveData.removeObservers(this)
        cameraStateLiveData.observe(this) { state ->
            val error = state.error ?: return@observe
            when (error.code) {
                CameraState.ERROR_CAMERA_IN_USE,
                CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                    Log.w(TAG, "Camera state error (${error.code}), scheduling restart")
                    scheduleCameraRestart("CameraState error ${error.code}")
                }
                CameraState.ERROR_CAMERA_DISABLED,
                CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                    Log.e(TAG, "Non-recoverable camera error (${error.code})")
                }
                else -> {
                    Log.w(TAG, "Camera error (${error.code})")
                }
            }
        }
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyCamera2Defaults(previewBuilder: Preview.Builder) {
        val extender = Camera2Interop.Extender(previewBuilder)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
    }

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyCamera2Defaults(imageCaptureBuilder: ImageCapture.Builder) {
        val extender = Camera2Interop.Extender(imageCaptureBuilder)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyCamera2Defaults() {
        val cam = camera ?: return
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)
        camera2Control.clearCaptureRequestOptions()
            val optionsBuilder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
            
        // Enhanced autofocus settings
        try {
            // Add face detection for better autofocus
            optionsBuilder.setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
            
            // Enable lens stabilization if available
            optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            
            // Set autofocus trigger for better responsiveness
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            
            // Enable scene detection for better autofocus
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            
        } catch (e: Exception) {
            Log.d(TAG, "Some enhanced autofocus features not supported: ${e.message}")
        }

        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    private fun scheduleCameraRestart(reason: String? = null) {
        if (cameraRestartJob?.isActive == true) {
            Log.d(TAG, "Camera restart already scheduled")
            return
        }
        cameraRestartJob = lifecycleScope.launch {
            Log.i(TAG, "Scheduling camera restart${reason?.let { ": $it" } ?: ""}")
            delay(500)
            restartCameraSession()
        }
    }

    private fun resolveCameraId(): String? {
        val provider = cameraProvider ?: return null
        val cameraInfos = provider.availableCameraInfos
        return try {
            cameraSelector.filter(cameraInfos).firstOrNull()
                ?.let { Camera2CameraInfo.from(it).cameraId }
        } catch (exc: Exception) {
            Log.w(TAG, "Failed to resolve camera id", exc)
            null
        }
    }

    private fun refreshCameraCharacteristics(cameraId: String) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        currentCameraCharacteristics = characteristics
        val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        availableCaptureResolutions = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)
            ?.toList()
            ?.let { sortResolutionsDescending(it) }
            ?: emptyList()
        availablePreviewResolutions = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)
            ?.toList()
            ?.let { sortResolutionsDescending(it) }
            ?: emptyList()
        invalidCaptureResolutions.retainAll(availableCaptureResolutions.toSet())
    }

    private fun sortResolutionsDescending(sizes: List<Size>): List<Size> {
        return sizes.distinctBy { it.width to it.height }
            .sortedWith(compareByDescending<Size> { it.width.toLong() * it.height }
                .thenByDescending { it.width })
    }

    private fun updateSelectableCaptureResolutions() {
        if (availablePreviewResolutions.isEmpty()) {
            selectableCaptureResolutions = availableCaptureResolutions
            return
        }

        val filtered = availableCaptureResolutions.filter { captureSize ->
            val targetRatio = aspectRatio(captureSize)
            availablePreviewResolutions.any { matchesAspectRatio(it, targetRatio) }
        }

        selectableCaptureResolutions = (if (filtered.isNotEmpty()) filtered else availableCaptureResolutions)
            .filterNot { invalidCaptureResolutions.contains(it) }
    }

    private fun findBestPreviewResolutionFor(captureSize: Size): Size? {
        if (availablePreviewResolutions.isEmpty()) return null
        val targetRatio = aspectRatio(captureSize)
        val candidates = availablePreviewResolutions.filter { matchesAspectRatio(it, targetRatio) }
        return candidates.maxByOrNull { it.width.toLong() * it.height }
    }

    private fun matchesAspectRatio(size: Size, targetRatio: Float): Boolean {
        return abs(aspectRatio(size) - targetRatio) <= ASPECT_RATIO_TOLERANCE
    }

    private fun aspectRatio(size: Size): Float = size.width.toFloat() / size.height

    private fun restartCameraSession() {
        if (isFinishing || isDestroyed) return
        try {
            cameraProvider?.unbindAll()
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to unbind camera before restart", exc)
        }
        mainHandler.post { startCamera() }
    }

    private fun verifyBoundCaptureResolution(requestedResolution: Size): Boolean {
        val actualResolution = imageCapture?.resolutionInfo?.resolution ?: return true
        if (actualResolution == requestedResolution) return true

        val isSwappedOrientationMatch = actualResolution.width == requestedResolution.height &&
                actualResolution.height == requestedResolution.width
        if (isSwappedOrientationMatch) return true

        Log.w(
            TAG,
            "Requested capture resolution ${requestedResolution.width}x${requestedResolution.height} but camera reported ${actualResolution.width}x${actualResolution.height}"
        )
        handleUnsupportedCaptureResolution(requestedResolution)
        return false
    }

    private fun handleUnsupportedCaptureResolution(failedResolution: Size) {
        if (!invalidCaptureResolutions.add(failedResolution)) {
            Log.w(TAG, "Resolution ${failedResolution.width}x${failedResolution.height} already marked invalid")
        } else {
            Log.w(TAG, "Resolution ${failedResolution.width}x${failedResolution.height} is not supported by camera")
        }

        selectableCaptureResolutions = selectableCaptureResolutions.filterNot { it == failedResolution }
        availableCaptureResolutions = availableCaptureResolutions.filterNot { it == failedResolution }

        val fallback = selectableCaptureResolutions.firstOrNull()
            ?: availableCaptureResolutions.firstOrNull()
            ?: DEFAULT_PHOTO_RESOLUTION

        if (fallback != failedResolution) {
            if (selectedResolution != fallback) {
                selectedResolution = fallback
                lifecycleScope.launch {
                    settingsManager.setResolution("${fallback.width}x${fallback.height}")
                }
            }
        } else {
            selectedResolution = DEFAULT_PHOTO_RESOLUTION
            lifecycleScope.launch {
                settingsManager.setResolution("${DEFAULT_PHOTO_RESOLUTION.width}x${DEFAULT_PHOTO_RESOLUTION.height}")
            }
        }

        mainHandler.post { startCamera() }
    }

    private fun focusAtCenter() {
        previewView.post {
            val width = previewView.width
            val height = previewView.height
            if (width <= 0 || height <= 0) return@post
            focusLastX = width / 2f
            focusLastY = height / 2f
            focusCoordinator?.resetToCenter()
        }
    }

    private fun triggerManualAutofocus() {
        // Provide haptic feedback
        previewView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        
        // Visual feedback for autofocus button
        autofocusButton?.animate()
            ?.scaleX(1.2f)
            ?.scaleY(1.2f)
            ?.setDuration(100)
            ?.withEndAction {
                autofocusButton?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(100)
                    ?.start()
            }
            ?.start()
            
        // Use coordinator: focus at center with visual feedback
        focusCoordinator?.resetToCenter()
    }


    private fun setupTorchObserver() {
        camera?.cameraInfo?.torchState?.observe(this) { state ->
            if (state == TorchState.ON) {
                torchButton.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
            } else {
                torchButton.clearColorFilter()
            }

            // Save torch state when it changes (to handle system changes)
            val isTorchOn = state == TorchState.ON
            if (isTorchOn != savedTorchState) {
                lifecycleScope.launch {
                    settingsManager.setTorchEnabled(isTorchOn)
                }
            }
        }
    }

    private fun restoreTorchState() {
        Log.d(TAG, "restoreTorchState() called - savedTorchState=$savedTorchState, camera=$camera")
        val cam = camera ?: run {
            Log.w(TAG, "Cannot restore torch state: camera is null")
            return
        }

        // Only restore torch state if camera has flash unit and is back camera
        if (!cam.cameraInfo.hasFlashUnit()) {
            Log.d(TAG, "Cannot restore torch state: no flash unit available")
            return
        }

        // Restore regardless of lens facing if flash unit exists

        // Restore the saved torch state
        if (savedTorchState) {
            Log.d(TAG, "Restoring torch state: ON - calling enableTorch(true)")
            cam.cameraControl.enableTorch(true)
            Log.d(TAG, "Torch enableTorch(true) called successfully")
        } else {
            Log.d(TAG, "Torch state is OFF, no restoration needed")
        }
    }

    // Legacy zoom slider observers removed (Compose ZoomControl is used)

    private fun focusAtPoint(x: Float, y: Float) {
        focusLastX = x
        focusLastY = y
        startFocusMeteringAt(x, y, showIndicator = true)
    }

    private fun enhancedAutofocus() {
        val cam = camera ?: return
        
        // Cancel any ongoing focus operation
        cam.cameraControl.cancelFocusAndMetering()
        
        // Use enhanced focus metering with multiple areas
        previewView.post {
            val width = previewView.width
            val height = previewView.height
            if (width <= 0 || height <= 0) return@post
            
            val factory = previewView.meteringPointFactory
            val centerPoint = factory.createPoint(width / 2f, height / 2f)
            
            // Create multiple focus points for better coverage
            val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5, TimeUnit.SECONDS) // Longer duration for manual focus
                .build()
            
            showFocusIndicator(width / 2f, height / 2f)
            
            if (!cam.cameraInfo.isFocusMeteringSupported(action)) {
                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 800)
                return@post
            }
            
            val future = cam.cameraControl.startFocusAndMetering(action)
            future.addListener({
                try {
                    val result = future.get()
                    runOnUiThread {
                        // Update autofocus button color based on focus success
                        if (result.isFocusSuccessful) {
                            autofocusButton?.setColorFilter(ContextCompat.getColor(this@CameraActivity, android.R.color.holo_green_light))
                            focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1000)
                        } else {
                            autofocusButton?.setColorFilter(ContextCompat.getColor(this@CameraActivity, android.R.color.holo_red_light))
                            focusIndicator.postDelayed(hideFocusIndicatorRunnable, 500)
                        }
                        
                        // Clear button color after delay
                        autofocusButton?.postDelayed({
                            autofocusButton?.clearColorFilter()
                        }, 1500)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        autofocusButton?.setColorFilter(ContextCompat.getColor(this@CameraActivity, android.R.color.holo_orange_light))
                        focusIndicator.post(hideFocusIndicatorRunnable)
                        autofocusButton?.postDelayed({
                            autofocusButton?.clearColorFilter()
                        }, 1000)
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun startFocusMeteringAt(x: Float, y: Float, showIndicator: Boolean) {
        // Backward-compat wrapper: delegate to coordinator
        if (showIndicator) showFocusIndicator(x, y)
        focusCoordinator?.onSingleTap(x, y)
    }

    private fun toggleLockIcon(locked: Boolean) {
        lockIcon.animate().cancel()
        if (locked) {
            lockIcon.alpha = 0f
            lockIcon.visibility = View.VISIBLE
            lockIcon.animate().alpha(1f).setDuration(120).start()
        } else {
            lockIcon.animate().alpha(0f).setDuration(120).withEndAction {
                lockIcon.visibility = View.GONE
            }.start()
        }
    }

    private fun ensureEvController() {
        if (evController == null) {
            camera?.let { cam ->
                evController = com.example.b1void.camera.ev.EvController(
                    onOverlayVisibility = { visible -> evOverlay.visibility = if (visible) View.VISIBLE else View.GONE },
                    onOverlayValue = { /* future: draw gradation on overlay or bubble */ }
                ).also { it.attach(cam) }
            }
        }
    }

    private fun scheduleHideEvOverlay() {
        evHideRunnable?.let { evOverlay.removeCallbacks(it) }
        val r = Runnable { evOverlay.visibility = View.GONE }
        evHideRunnable = r
        evOverlay.postDelayed(r, 1500)
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        val indicatorWidth = focusIndicator.width.takeIf { it > 0 }
            ?: focusIndicator.layoutParams.width.takeIf { it > 0 }
            ?: 0
        val indicatorHeight = focusIndicator.height.takeIf { it > 0 }
            ?: focusIndicator.layoutParams.height.takeIf { it > 0 }
            ?: 0

        val parentLeft = previewView.left.toFloat()
        val parentTop = previewView.top.toFloat()
        val parentRight = previewView.right.toFloat()
        val parentBottom = previewView.bottom.toFloat()

        val centeredX = parentLeft + x - indicatorWidth / 2f
        val centeredY = parentTop + y - indicatorHeight / 2f

        val clampedX = centeredX.coerceIn(parentLeft, parentRight - indicatorWidth)
        val clampedY = centeredY.coerceIn(parentTop, parentBottom - indicatorHeight)

        focusIndicator.apply {
            removeCallbacks(hideFocusIndicatorRunnable)
            visibility = View.VISIBLE
            alpha = 0.8f
            scaleX = 1.5f
            scaleY = 1.5f
            translationX = clampedX
            translationY = clampedY
            animate().cancel()
            
            // Enhanced focus animation with scale and fade
            animate()
                .alpha(1f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .withEndAction {
                            // Pulse animation to show focusing is active
                            animate()
                                .alpha(0.6f)
                                .setDuration(300)
                                .withEndAction {
                                    animate()
                                        .alpha(1f)
                                        .setDuration(300)
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }
        // Move lock icon next to ring
        lockIcon.apply {
            translationX = clampedX + indicatorWidth + 8f
            translationY = clampedY - 8f
            elevation = focusIndicator.elevation + 1f
            visibility = if (focusCoordinator?.isLocked() == true) View.VISIBLE else View.GONE
        }
        // Place EV overlay alongside
        evOverlay.apply {
            translationX = clampedX - 16f
            translationY = (clampedY - height / 2f).coerceAtLeast(0f)
        }
    }

    private fun playCaptureAnimation(imageUri: Uri) {
        captureAnimationView.animate().cancel()

        Glide.with(this)
            .load(imageUri)
            .centerCrop()
            .into(captureAnimationView)

        captureAnimationView.visibility = View.VISIBLE
        captureAnimationView.alpha = 0f
        captureAnimationView.scaleX = 0.6f
        captureAnimationView.scaleY = 0.6f
        captureAnimationView.translationX = 0f
        captureAnimationView.translationY = 0f

        captureAnimationView.post {
            if (!isFinishing && !isDestroyed) {
                val startLocation = IntArray(2)
                val endLocation = IntArray(2)
                captureAnimationView.getLocationOnScreen(startLocation)
                thumbnailPreview.getLocationOnScreen(endLocation)

                val deltaX = endLocation[0] - startLocation[0]
                val deltaY = endLocation[1] - startLocation[1]

                captureAnimationView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        captureAnimationView.animate()
                            .translationX(deltaX.toFloat())
                            .translationY(deltaY.toFloat())
                            .alpha(0f)
                            .scaleX(0.3f)
                            .scaleY(0.3f)
                            .setDuration(280)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction {
                                captureAnimationView.visibility = View.GONE
                                captureAnimationView.translationX = 0f
                                captureAnimationView.translationY = 0f
                                captureAnimationView.scaleX = 1f
                                captureAnimationView.scaleY = 1f
                                captureAnimationView.alpha = 1f
                            }
                            .start()
                    }
                    .start()
            }
        }
    }

    

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientationDegrees: Int) {
                if (orientationDegrees == ORIENTATION_UNKNOWN) return
                val rotation = when {
                    orientationDegrees in 45..134 -> Surface.ROTATION_270
                    orientationDegrees in 135..224 -> Surface.ROTATION_180
                    orientationDegrees in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                if (rotation != currentTargetRotation) {
                    currentTargetRotation = rotation
                    applyTargetRotations(rotation)
                }

                // Only update layout when display rotation actually changes
                val displayRotation = getDisplayRotation()
                if (displayRotation != lastLayoutRotation) {
                    updateLayoutForRotation(displayRotation)
                }
            }
        }

        orientationEventListener?.let { listener ->
            if (listener.canDetectOrientation()) {
                listener.enable()
            } else {
                listener.disable()
            }
        }
    }

    private fun updateLayoutForRotation(rotation: Int, animate: Boolean = true) {
        if (!::rootLayout.isInitialized) return
        if (layoutOrientationInitialized && rotation == lastLayoutRotation) {
            currentUiOrientation?.let { handleAutoHideForOrientation(it) }
            return
        }

        val targetOrientation = when (rotation) {
            Surface.ROTATION_0 -> UiOrientation.PORTRAIT
            Surface.ROTATION_180 -> UiOrientation.PORTRAIT_REVERSE
            Surface.ROTATION_90 -> UiOrientation.LANDSCAPE_RIGHT
            Surface.ROTATION_270 -> UiOrientation.LANDSCAPE_LEFT
            else -> UiOrientation.PORTRAIT
        }

        val orientationChanged = currentUiOrientation != targetOrientation || !layoutOrientationInitialized
        val shouldAnimate = animate && layoutOrientationInitialized && orientationChanged

        if (orientationChanged) {
            if (shouldAnimate) {
                val transition = AutoTransition().apply {
                    duration = 160
                    interpolator = AccelerateDecelerateInterpolator()
                }
                transition.excludeTarget(previewView, true)
                TransitionManager.beginDelayedTransition(rootLayout, transition)
            } else {
                captureButton.animate().cancel()
                topControlsContainer.animate().cancel()
                bottomControlsContainer.animate().cancel()
            }

            when (targetOrientation) {
                UiOrientation.PORTRAIT -> applyPortraitLayout(shouldAnimate, reversed = false)
                UiOrientation.PORTRAIT_REVERSE -> applyPortraitLayout(shouldAnimate, reversed = true)
                UiOrientation.LANDSCAPE_LEFT -> applyLandscapeLayout(shouldAnimate, flipped = false)
                UiOrientation.LANDSCAPE_RIGHT -> applyLandscapeLayout(shouldAnimate, flipped = true)
            }

            currentUiOrientation = targetOrientation
            isLandscapeUi = targetOrientation == UiOrientation.LANDSCAPE_LEFT || targetOrientation == UiOrientation.LANDSCAPE_RIGHT
            layoutOrientationInitialized = true
        }

        currentUiOrientation?.let { handleAutoHideForOrientation(it) }
        lastLayoutRotation = rotation
    }

    private fun handleAutoHideForOrientation(orientation: UiOrientation) {
        if (orientation == UiOrientation.LANDSCAPE_LEFT || orientation == UiOrientation.LANDSCAPE_RIGHT) {
            scheduleControlsAutoHide()
        } else {
            clearControlsAutoHide()
        }
    }

    private fun applyLandscapeLayout(animate: Boolean, flipped: Boolean) {
        val edgeMargin = dpToPx(24)
        val verticalSpacing = dpToPx(16)
        val columnPadding = dpToPx(12)

        topControlsContainer.orientation = LinearLayout.VERTICAL
        topControlsContainer.setPadding(columnPadding, dpToPx(20), columnPadding, dpToPx(20))
        topControlsContainer.setBackgroundColor(Color.TRANSPARENT)
        topControlsSpacer.visibility = View.GONE

        bottomControlsContainer.setPadding(columnPadding, columnPadding, columnPadding, columnPadding)
        bottomControlsContainer.setBackgroundColor(Color.TRANSPARENT)

        (settingsButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = 0
            params.topMargin = 0
            settingsButton.layoutParams = params
        }
        (torchButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = 0
            params.topMargin = verticalSpacing / 2
            torchButton.layoutParams = params
        }
        (flipCameraButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = 0
            params.topMargin = verticalSpacing / 2
            flipCameraButton.layoutParams = params
        }

        val rootSet = ConstraintSet().apply { clone(rootLayout) }

        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.START)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.END)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.BOTTOM)
        rootSet.connect(
            R.id.thumbnailPreview,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            edgeMargin
        )
        rootSet.connect(R.id.thumbnailPreview, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, edgeMargin)

        rootSet.constrainWidth(R.id.topControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.constrainHeight(R.id.topControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.clear(R.id.topControls, ConstraintSet.START)
        rootSet.clear(R.id.topControls, ConstraintSet.END)
        rootSet.clear(R.id.topControls, ConstraintSet.TOP)
        rootSet.connect(
            R.id.topControls,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            edgeMargin
        )
        rootSet.connect(R.id.topControls, ConstraintSet.TOP, R.id.thumbnailPreview, ConstraintSet.BOTTOM, verticalSpacing)
        // Legacy horizontal zoom slider removed

        rootSet.constrainWidth(R.id.bottomControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.constrainHeight(R.id.bottomControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.clear(R.id.bottomControls, ConstraintSet.START)
        rootSet.clear(R.id.bottomControls, ConstraintSet.END)
        rootSet.clear(R.id.bottomControls, ConstraintSet.BOTTOM)
        rootSet.connect(
            R.id.bottomControls,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END,
            edgeMargin
        )
        rootSet.connect(R.id.bottomControls, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, edgeMargin)

        rootSet.clear(R.id.recording_timer, ConstraintSet.START)
        rootSet.clear(R.id.recording_timer, ConstraintSet.END)
        rootSet.clear(R.id.recording_timer, ConstraintSet.BOTTOM)
        rootSet.connect(
            R.id.recording_timer,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END,
            edgeMargin
        )
        rootSet.connect(R.id.recording_timer, ConstraintSet.TOP, R.id.bottomControls, ConstraintSet.BOTTOM, verticalSpacing)

        rootSet.applyTo(rootLayout)

        // Position Compose zoom control at side center (replacement for legacy vertical slider)
        val rootSetZoom = ConstraintSet().apply { clone(rootLayout) }
        rootSetZoom.constrainWidth(R.id.zoomCompose, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSetZoom.constrainHeight(R.id.zoomCompose, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSetZoom.clear(R.id.zoomCompose, ConstraintSet.START)
        rootSetZoom.clear(R.id.zoomCompose, ConstraintSet.END)
        rootSetZoom.clear(R.id.zoomCompose, ConstraintSet.TOP)
        rootSetZoom.clear(R.id.zoomCompose, ConstraintSet.BOTTOM)
        // Place zoom just to the left of the shutter column (bottomControls) by ~0.3dp
        rootSetZoom.connect(
            R.id.zoomCompose,
            ConstraintSet.END,
            R.id.bottomControls,
            ConstraintSet.START,
            dpToPxF(0.3f)
        )
        rootSetZoom.connect(R.id.zoomCompose, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        rootSetZoom.connect(R.id.zoomCompose, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        rootSetZoom.applyTo(rootLayout)

        val bottomSet = ConstraintSet().apply { clone(bottomControlsContainer) }
        bottomSet.clear(R.id.shutterButton, ConstraintSet.BOTTOM)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        // Mode switch removed; no constraints needed.
        bottomSet.applyTo(bottomControlsContainer)

        captureButton.scaleX = 1f
        captureButton.scaleY = 1f

        if (animate) {
            topControlsContainer.animate().alpha(1f).setDuration(180).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(180).start()
            zoomCompose.animate().alpha(1f).setDuration(180).start()
        } else {
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
            zoomCompose.alpha = 1f
        }

        showControlsOnInteraction()
        
        // No legacy sliders when Compose zoom is enabled
    }

    private fun applyPortraitLayout(animate: Boolean, reversed: Boolean = false) {
        clearControlsAutoHide()

        topControlsContainer.orientation = LinearLayout.HORIZONTAL
        topControlsContainer.setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(12))
        topControlsContainer.setBackgroundColor(Color.TRANSPARENT)
        topControlsSpacer.visibility = View.VISIBLE

        bottomControlsContainer.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
        bottomControlsContainer.setBackgroundColor(Color.TRANSPARENT)

        (settingsButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = 0
            params.topMargin = 0
            settingsButton.layoutParams = params
        }
        (torchButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = dpToPx(16)
            params.topMargin = 0
            torchButton.layoutParams = params
        }
        (flipCameraButton.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = 0
            params.topMargin = 0
            flipCameraButton.layoutParams = params
        }

        val startMargin = dpToPx(24)
        val bottomMargin = dpToPx(32)

        val rootSet = ConstraintSet().apply { clone(rootLayout) }

        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.START)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.END)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.TOP)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.BOTTOM)

        rootSet.clear(R.id.topControls, ConstraintSet.START)
        rootSet.clear(R.id.topControls, ConstraintSet.END)
        rootSet.clear(R.id.topControls, ConstraintSet.TOP)
        rootSet.clear(R.id.topControls, ConstraintSet.BOTTOM)

        rootSet.clear(R.id.bottomControls, ConstraintSet.START)
        rootSet.clear(R.id.bottomControls, ConstraintSet.END)
        rootSet.clear(R.id.bottomControls, ConstraintSet.TOP)
        rootSet.clear(R.id.bottomControls, ConstraintSet.BOTTOM)

        // Place Compose zoom control centered above bottom controls
        rootSet.constrainWidth(R.id.zoomCompose, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
        rootSet.constrainHeight(R.id.zoomCompose, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.clear(R.id.zoomCompose, ConstraintSet.START)
        rootSet.clear(R.id.zoomCompose, ConstraintSet.END)
        rootSet.clear(R.id.zoomCompose, ConstraintSet.TOP)
        rootSet.clear(R.id.zoomCompose, ConstraintSet.BOTTOM)
        rootSet.connect(R.id.zoomCompose, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dpToPx(24))
        rootSet.connect(R.id.zoomCompose, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dpToPx(24))
        // Position just above bottom controls; to move visually lower by ~0.7cm we allow slight overlap
        // 11.5dp (previous) - 44.1dp = -32.6dp
        rootSet.connect(
            R.id.zoomCompose,
            ConstraintSet.BOTTOM,
            R.id.bottomControls,
            ConstraintSet.TOP,
            dpToPxF(-32.6f)
        )


        rootSet.clear(R.id.recording_timer, ConstraintSet.START)
        rootSet.clear(R.id.recording_timer, ConstraintSet.END)
        rootSet.clear(R.id.recording_timer, ConstraintSet.TOP)
        rootSet.clear(R.id.recording_timer, ConstraintSet.BOTTOM)

        if (reversed) {
            rootSet.constrainWidth(R.id.topControls, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.topControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.topControls, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.topControls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            rootSet.connect(R.id.topControls, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            rootSet.constrainWidth(R.id.bottomControls, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.bottomControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.bottomControls, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.bottomControls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            rootSet.connect(R.id.bottomControls, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            rootSet.setVerticalBias(R.id.bottomControls, 0f)

            rootSet.connect(R.id.thumbnailPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
            rootSet.connect(R.id.thumbnailPreview, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, startMargin)

            // Legacy zoomSlider constraints removed


            rootSet.connect(R.id.recording_timer, ConstraintSet.BOTTOM, R.id.topControls, ConstraintSet.TOP, dpToPx(8))
            rootSet.connect(R.id.recording_timer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.recording_timer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        } else {
            rootSet.constrainWidth(R.id.topControls, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.topControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.topControls, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.topControls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            rootSet.connect(R.id.topControls, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)

            rootSet.constrainWidth(R.id.bottomControls, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.bottomControls, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.bottomControls, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.bottomControls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            rootSet.connect(R.id.bottomControls, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            rootSet.setVerticalBias(R.id.bottomControls, 1f)

            rootSet.connect(R.id.thumbnailPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
            rootSet.connect(R.id.thumbnailPreview, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, bottomMargin)

            // Legacy zoomSlider constraints removed


            rootSet.connect(R.id.recording_timer, ConstraintSet.TOP, R.id.topControls, ConstraintSet.BOTTOM, dpToPx(8))
            rootSet.connect(R.id.recording_timer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.recording_timer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }

        rootSet.applyTo(rootLayout)

        // Legacy zoomSlider removed

        val bottomSet = ConstraintSet().apply { clone(bottomControlsContainer) }
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.setHorizontalBias(R.id.shutterButton, 0.5f)

        // Mode switch removed; no constraints needed.
        bottomSet.applyTo(bottomControlsContainer)

        if (animate) {
            topControlsContainer.animate().alpha(1f).setDuration(200).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(200).start()
            // Compose zoom handled separately
        } else {
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
            zoomCompose.alpha = 1f
        }

        captureButton.scaleX = 1f
        captureButton.scaleY = 1f
        
        // No legacy sliders when Compose zoom is enabled
    }


    private fun showControlsOnInteraction() {
        if (!layoutOrientationInitialized) return
        val orientation = currentUiOrientation
        if (orientation != UiOrientation.LANDSCAPE_RIGHT) return
        topControlsContainer.animate().alpha(1f).setDuration(150).start()
        bottomControlsContainer.animate().alpha(1f).setDuration(150).start()
        // Compose zoom has its own visibility
        clearControlsAutoHide()
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        val orientation = currentUiOrientation
        if (orientation != UiOrientation.LANDSCAPE_RIGHT) {
            clearControlsAutoHide()
            return
        }
        mainHandler.removeCallbacks(controlsAutoHideRunnable)
        mainHandler.postDelayed(controlsAutoHideRunnable, controlsHideDelayMs)
    }

    private fun clearControlsAutoHide() {
        mainHandler.removeCallbacks(controlsAutoHideRunnable)
    }

    private fun fadeControlsForLandscape() {
        topControlsContainer.animate().alpha(0.55f).setDuration(250).start()
        bottomControlsContainer.animate().alpha(0.8f).setDuration(250).start()
        // Compose zoom has its own visibility
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutForRotation(getDisplayRotation())
        // No legacy zoom sliders to update
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()
    private fun dpToPxF(dp: Float): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun applyTargetRotations(rotation: Int) {
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        // Avoid reconfiguring video capture while recording to prevent unintended stops
        if (!isRecording) {
            videoCapture?.targetRotation = rotation
        }
    }

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private fun getSaveDirectory(): File? {
        val path = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath
        if (path.isNullOrBlank()) return null
        val dir = File(path)
        return if (dir.exists() || dir.mkdirs()) dir else null
    }

    private fun getSavedImages(): List<File> {
        val directory = getSaveDirectory() ?: return emptyList()
        return directory.listFiles { file -> file.isFile && isImageFile(file) }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.ROOT)
        return extension == "jpg" || extension == "jpeg" || extension == "png"
    }

    private fun loadLatestPhotoThumbnail() {
        val latestImage = getSavedImages().firstOrNull()
        if (latestImage != null) {
            lastSavedFile = latestImage
            updateThumbnail(Uri.fromFile(latestImage))
        } else {
            lastSavedFile = null
            runOnUiThread {
                val size = resources.getDimensionPixelSize(R.dimen.thumbnail_max_size)
                val params = thumbnailPreview.layoutParams
                if (params.width != size || params.height != size) {
                    params.width = size
                    params.height = size
                    thumbnailPreview.layoutParams = params
                }
                thumbnailPreview.setImageResource(R.drawable.gray_square)
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = this.imageCapture ?: return

        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: ""
        val photoFile = File(savePath, "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lastSavedFile = photoFile
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)

                    try {
                        val bitmap = getCorrectlyOrientedBitmap(photoFile)
                        val timestampedBitmap = addTimestampToBitmap(bitmap)
                        saveBitmapToFile(timestampedBitmap, photoFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding timestamp", e)
                    }

                    runOnUiThread {
                        updateThumbnail(savedUri)
                        playCaptureAnimation(savedUri)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun getCorrectlyOrientedBitmap(photoFile: File): Bitmap {
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, options)
        val exifInterface = androidx.exifinterface.media.ExifInterface(photoFile.absolutePath)
        val orientation = exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun addTimestampToBitmap(originalBitmap: Bitmap): Bitmap {
        val newBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(newBitmap)
        val textSize = originalBitmap.width / 35f
        val padding = originalBitmap.width / 45f
        val paint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        paint.textAlign = Paint.Align.LEFT
        val x = padding
        val fontMetrics = paint.fontMetrics
        val timestampY = newBitmap.height - padding - fontMetrics.bottom
        val companyY = timestampY - paint.textSize - padding * 0.3f

        canvas.drawText("DOCUMENT LLC", x, companyY, paint)
        canvas.drawText(timestamp, x, timestampY, paint)

        return newBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: ""
        val file = File(savePath, "IMG_${System.currentTimeMillis()}.jpg")
        return saveBitmapToFile(bitmap, file)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): File {
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        return file
    }

    private fun toggleVideoRecording() {
        val videoCapture = this.videoCapture ?: return

        if (isRecording) {
            recording?.stop()
            recording = null
            return
        }

        isRecording = true
        startRecordingIndicator()

        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: return
        val videoFile = File(savePath, "VID_${System.currentTimeMillis()}.mp4")
        lastSavedFile = videoFile
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {}
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        stopRecordingIndicator()
                        if (!recordEvent.hasError()) {
                            processVideoStamp(videoFile)
                        } else {
                            Log.e(TAG, "Video capture error: ${recordEvent.error}")
                            videoFile.delete()
                        }
                    }
                }
            }
    }

    private fun scheduleHoldRecordingStart() {
        // Cancel any pending start
        holdStartRunnable?.let { mainHandler.removeCallbacks(it) }
        holdStartRunnable = Runnable {
            holdStartRunnable = null
            startVideoRecordingForHold()
            isHoldRecordingActive = true
        }
        mainHandler.postDelayed(holdStartRunnable!!, holdToRecordDelayMs)
    }

    private fun cancelHoldRecordingStartIfPending() {
        holdStartRunnable?.let {
            mainHandler.removeCallbacks(it)
            holdStartRunnable = null
        }
    }

    private fun startVideoRecordingForHold() {
        val vc = this.videoCapture ?: return
        if (isRecording) return
        isRecording = true
        isHoldRecordingActive = true
        startRecordingIndicator()

        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: return
        val videoFile = File(savePath, "VID_${System.currentTimeMillis()}.mp4")
        lastSavedFile = videoFile
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {}
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        stopRecordingIndicator()
                        if (!recordEvent.hasError()) {
                            processVideoStamp(videoFile)
                        } else {
                            Log.e(TAG, "Video capture error: ${recordEvent.error}")
                            videoFile.delete()
                        }
                    }
                }
            }
    }

    private fun stopVideoRecordingForHoldNow() {
        if (!isRecording) return
        isHoldRecordingActive = false
        recording?.stop()
        recording = null
    }

    private fun scheduleStopVideoRecordingForHold() {
        stopHoldRunnable?.let { mainHandler.removeCallbacks(it) }
        stopHoldRunnable = Runnable {
            stopHoldRunnable = null
            stopVideoRecordingForHoldNow()
        }
        mainHandler.postDelayed(stopHoldRunnable!!, stopHoldDelayMs)
    }

    private fun cancelScheduledStopVideoRecordingForHold() {
        stopHoldRunnable?.let {
            mainHandler.removeCallbacks(it)
            stopHoldRunnable = null
        }
    }

    private fun updateThumbnail(uri: Uri) {
        runOnUiThread {
            adjustThumbnailSize(uri)
            Glide.with(this)
                .load(uri)
                .fitCenter()
                .into(thumbnailPreview)
        }
    }

    private fun adjustThumbnailSize(uri: Uri) {
        val path = uri.path
        val isImage = when {
            path != null -> isImageFile(File(path))
            else -> {
                val type = contentResolver.getType(uri)
                type != null && type.startsWith("image/")
            }
        }
        if (!isImage) return

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (_: Exception) {
            return
        }

        var width = options.outWidth
        var height = options.outHeight
        val rotationDegrees = resolveImageRotation(uri)
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            val tmp = width
            width = height
            height = tmp
        }
        if (width <= 0 || height <= 0) return

        val maxSize = resources.getDimensionPixelSize(R.dimen.thumbnail_max_size)
        val (targetWidth, targetHeight) = if (width >= height) {
            maxSize to (maxSize.toFloat() * height / width).roundToInt().coerceAtLeast(1)
        } else {
            (maxSize.toFloat() * width / height).roundToInt().coerceAtLeast(1) to maxSize
        }

        val params = thumbnailPreview.layoutParams
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            thumbnailPreview.layoutParams = params
        }
    }

    private fun resolveImageRotation(uri: Uri): Int {
        return try {
            val orientation = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_UNDEFINED
            } else {
                val path = uri.path
                if (!path.isNullOrEmpty()) {
                    ExifInterface(path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } else {
                    ExifInterface.ORIENTATION_UNDEFINED
                }
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun startRecordingIndicator() {
        runOnUiThread {
            captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
            val anim = AlphaAnimation(0.5f, 1.0f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            captureButton.startAnimation(anim)
            recordingTimer.visibility = View.VISIBLE
            recordingTimer.base = SystemClock.elapsedRealtime()
            recordingTimer.start()
            // modeSwitchButton removed
            flipCameraButton.isEnabled = false
        }
    }

    private fun stopRecordingIndicator() {
        runOnUiThread {
            captureButton.clearAnimation()
            if (currentMode == CaptureMode.VIDEO) {
                captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
            } else {
                captureButton.setBackgroundResource(R.drawable.bg_capture_button_photo)
                captureButton.setImageResource(R.drawable.camera)
            }
            recordingTimer.stop()
            recordingTimer.visibility = View.GONE
            // modeSwitchButton removed
            flipCameraButton.isEnabled = true
        }
    }

    private fun parseResolution(resString: String): Size? {
        return try {
            val parts = resString.split("x")
            val width = parts[0].toInt()
            val height = parts[1].toInt()
            if (width > 0 && height > 0) Size(width, height) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidResolution(size: Size?): Boolean = size?.let { it.width > 0 && it.height > 0 } ?: false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                loadLatestPhotoThumbnail()
            } else {
                Toast.makeText(this, "Разрешения не предоставлены.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun processVideoStamp(videoFile: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val processor = VideoStampProcessor(this@CameraActivity)
            val stampTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val success = try {
                processor.applyStamp(videoFile, "DOCUMENT LLC", stampTimestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stamp video", e)
                false
            }
            if (!success) {
                Log.w(TAG, "Video stamp processor reported failure for ${videoFile.name}")
            }

            val uri = Uri.fromFile(videoFile)
            withContext(Dispatchers.Main) {
                updateThumbnail(uri)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cancelHoldRecordingStartIfPending()
        cancelScheduledStopVideoRecordingForHold()
        recording?.stop()
        recording = null
        orientationEventListener?.disable()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        clearControlsAutoHide()

        // Save current torch state before pausing
        camera?.let { cam ->
            val currentTorchState = cam.cameraInfo.torchState.value == TorchState.ON
            savedTorchState = currentTorchState
            lifecycleScope.launch {
                settingsManager.setTorchEnabled(currentTorchState)
            }
            Log.d(TAG, "Saved torch state on pause: $currentTorchState")
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        updateLayoutForRotation(getDisplayRotation(), animate = false)
        loadLatestPhotoThumbnail()

        // Load and restore torch state after camera initializes
        lifecycleScope.launch {
            val enabled = settingsManager.getTorchEnabled().first()
            savedTorchState = enabled
            shouldRestoreTorchState = true
            Log.d(TAG, "onResume: Loaded torch state from DataStore: $savedTorchState, will restore after camera initialization")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraRestartJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        orientationEventListener?.disable()
        orientationEventListener = null
        focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
        captureAnimationView.animate().cancel()
        
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        const val EXTRA_SAVE_PATH = "extra_save_path"
        private val DEFAULT_PHOTO_RESOLUTION = Size(960, 720)
        private const val ASPECT_RATIO_TOLERANCE = 0.02f
    }
}
