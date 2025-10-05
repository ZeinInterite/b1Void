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
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.data.CameraSettingsManager
import com.example.b1void.ui.CameraSettingsDialogFragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    private lateinit var modeSwitchButton: ImageButton
    private lateinit var flipCameraButton: ImageButton
    private lateinit var recordingTimer: Chronometer
    private lateinit var thumbnailPreview: ImageView
    private lateinit var settingsButton: ImageButton
    private lateinit var torchButton: ImageButton
    private lateinit var zoomSlider: SeekBar
    private lateinit var focusIndicator: View
    private lateinit var captureAnimationView: ImageView
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var bottomControlsContainer: ConstraintLayout
    private lateinit var topControlsSpacer: View

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

    // Settings
    private lateinit var settingsManager: CameraSettingsManager
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var selectedResolution: Size? = DEFAULT_PHOTO_RESOLUTION
    private var supportedResolutions: List<Size> = emptyList()

    // State variables
    private var currentMode = CaptureMode.PHOTO
    private var isRecording = false
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var lastSavedFile: File? = null
    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f
    private var isZoomGesture = false
    private val hideFocusIndicatorRunnable = Runnable {
        focusIndicator.animate().cancel()
        focusIndicator.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        settingsManager = CameraSettingsManager(this)
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
        captureButton.setOnClickListener {
            if (currentMode == CaptureMode.PHOTO) {
                takePhoto()
            } else {
                toggleVideoRecording()
            }
        }

        modeSwitchButton.setOnClickListener {
            currentMode = if (currentMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
            updateCameraUI()
            startCamera()
        }

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
                    it.cameraControl.enableTorch(!isTorchOn)
                }
            }
        }

        previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isZoomGesture = false
                    showControlsOnInteraction()
                }
                MotionEvent.ACTION_POINTER_DOWN -> isZoomGesture = true
                MotionEvent.ACTION_CANCEL -> isZoomGesture = false
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    if (!isZoomGesture && !scaleGestureDetector.isInProgress && event.pointerCount == 1) {
                        focusAtPoint(event.x, event.y)
                    }
                }
            }
            true
        }

        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cam = camera ?: return
                if (zoomSlider.max == 0) return
                val zoomRange = maxZoomRatio - minZoomRatio
                if (zoomRange <= 0f) return
                val fraction = progress.toFloat() / zoomSlider.max
                val newZoomRatio = minZoomRatio + fraction * zoomRange
                cam.cameraControl.setZoomRatio(newZoomRatio)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }
        })
    }

    private fun initializeViews() {
        rootLayout = findViewById(R.id.main_container)
        topControlsContainer = findViewById(R.id.topControls)
        bottomControlsContainer = findViewById(R.id.bottomControls)
        topControlsSpacer = findViewById(R.id.topControlsSpacer)
        previewView = findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        captureButton = findViewById(R.id.shutterButton)
        modeSwitchButton = findViewById(R.id.mode_switch_button)
        flipCameraButton = findViewById(R.id.switchCameraButton)
        recordingTimer = findViewById(R.id.recording_timer)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        thumbnailPreview.scaleType = ImageView.ScaleType.FIT_CENTER
        thumbnailPreview.adjustViewBounds = true
        settingsButton = findViewById(R.id.settingsButton)
        torchButton = findViewById(R.id.torchButton)
        zoomSlider = findViewById(R.id.zoomSlider)
        focusIndicator = findViewById(R.id.focusIndicator)
        captureAnimationView = findViewById(R.id.captureAnimationView)
        zoomSlider.isEnabled = false

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
            if (currentMode == CaptureMode.VIDEO) {
                modeSwitchButton.setImageResource(R.drawable.ic_switch_to_photo)
                if (!isRecording) {
                    captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
                    captureButton.setImageResource(R.drawable.ic_videocam)
                }
            } else {
                modeSwitchButton.setImageResource(R.drawable.ic_switch_to_video)
                captureButton.setBackgroundResource(R.drawable.bg_capture_button_photo)
                captureButton.setImageResource(R.drawable.camera)
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
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

            if (currentMode == CaptureMode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                val newVideoCapture = VideoCapture.withOutput(recorder).apply {
                    targetRotation = currentTargetRotation
                }
                videoCapture = newVideoCapture
                useCaseGroupBuilder.addUseCase(newVideoCapture)
            } else {
                videoCapture = null
            }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, useCaseGroupBuilder.build()
                )
                applyCamera2Defaults()
                setupCameraStateObserver()
                setupTorchObserver()
                setupZoomObserver()
                val resolutionConfirmed = verifyBoundCaptureResolution(captureResolution)
                if (!resolutionConfirmed) {
                    return@addListener
                }
                focusAtCenter()
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
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyCamera2Defaults(imageCaptureBuilder: ImageCapture.Builder) {
        val extender = Camera2Interop.Extender(imageCaptureBuilder)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
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
            startFocusMeteringAt(width / 2f, height / 2f, showIndicator = false)
        }
    }


    private fun setupTorchObserver() {
        camera?.cameraInfo?.torchState?.observe(this) { state ->
            if (state == TorchState.ON) {
                torchButton.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
            } else {
                torchButton.clearColorFilter()
            }
        }
    }

    private fun setupZoomObserver() {
        val cam = camera ?: run {
            zoomSlider.visibility = View.GONE
            zoomSlider.isEnabled = false
            return
        }
        val zoomStateLiveData = cam.cameraInfo.zoomState
        zoomStateLiveData.removeObservers(this)
        zoomStateLiveData.observe(this) { state ->
            minZoomRatio = state.minZoomRatio
            maxZoomRatio = state.maxZoomRatio
            val zoomRange = maxZoomRatio - minZoomRatio
            val shouldShowZoom = zoomRange > 0.01f
            zoomSlider.visibility = if (shouldShowZoom) View.VISIBLE else View.GONE
            zoomSlider.isEnabled = shouldShowZoom
            if (!shouldShowZoom) {
                zoomSlider.progress = 0
                return@observe
            }

            val fraction = if (zoomRange <= 0f) 0f else (state.zoomRatio - minZoomRatio) / zoomRange
            val newProgress = (fraction.coerceIn(0f, 1f) * zoomSlider.max).roundToInt()
            if (zoomSlider.progress != newProgress) {
                zoomSlider.progress = newProgress
            }
        }
    }

    private fun focusAtPoint(x: Float, y: Float) {
        startFocusMeteringAt(x, y, showIndicator = true)
    }

    private fun startFocusMeteringAt(x: Float, y: Float, showIndicator: Boolean) {
        val cam = camera ?: return
        val factory = previewView.meteringPointFactory
        val afPoint = factory.createPoint(x, y)
        val aePoint = factory.createPoint(x, y)

        val action = FocusMeteringAction.Builder(afPoint, FocusMeteringAction.FLAG_AF)
            .addPoint(aePoint, FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        if (showIndicator) {
            showFocusIndicator(x, y)
        }

        if (!cam.cameraInfo.isFocusMeteringSupported(action)) {
            if (showIndicator) {
                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 600)
            }
            return
        }

        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            try {
                val result = future.get()
                if (showIndicator) {
                    val delay = if (result.isFocusSuccessful) 600L else 200L
                    focusIndicator.postDelayed(hideFocusIndicatorRunnable, delay)
                }
            } catch (e: Exception) {
                if (showIndicator) {
                    focusIndicator.post(hideFocusIndicatorRunnable)
                }
            }
        }, ContextCompat.getMainExecutor(this))
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
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = clampedX
            translationY = clampedY
            animate().cancel()
            animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(120)
                .withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                .start()
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

                val displayRotation = getDisplayRotation()
                updateLayoutForRotation(displayRotation)
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


        rootSet.constrainWidth(R.id.zoomSlider, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.constrainHeight(R.id.zoomSlider, dpToPx(200))
        rootSet.clear(R.id.zoomSlider, ConstraintSet.START)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.END)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.BOTTOM)
        rootSet.connect(
            R.id.zoomSlider,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START,
            edgeMargin
        )
        rootSet.connect(R.id.zoomSlider, ConstraintSet.TOP, R.id.topControls, ConstraintSet.BOTTOM, verticalSpacing)

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

        zoomSlider.rotation = if (flipped) 90f else -90f

        val bottomSet = ConstraintSet().apply { clone(bottomControlsContainer) }
        bottomSet.clear(R.id.shutterButton, ConstraintSet.BOTTOM)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.START)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.END)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.BOTTOM)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.TOP, R.id.shutterButton, ConstraintSet.BOTTOM, verticalSpacing)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.applyTo(bottomControlsContainer)

        captureButton.scaleX = 1f
        captureButton.scaleY = 1f

        if (animate) {
            topControlsContainer.animate().alpha(1f).setDuration(180).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(180).start()
        } else {
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
        }

        showControlsOnInteraction()
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

        rootSet.clear(R.id.zoomSlider, ConstraintSet.START)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.END)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.TOP)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.BOTTOM)


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

            rootSet.constrainWidth(R.id.zoomSlider, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.zoomSlider, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, startMargin)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.TOP, R.id.bottomControls, ConstraintSet.BOTTOM, dpToPx(12))
            rootSet.connect(R.id.zoomSlider, ConstraintSet.BOTTOM, R.id.topControls, ConstraintSet.TOP, dpToPx(12))


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

            rootSet.constrainWidth(R.id.zoomSlider, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            rootSet.constrainHeight(R.id.zoomSlider, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, startMargin)
            rootSet.connect(R.id.zoomSlider, ConstraintSet.BOTTOM, R.id.bottomControls, ConstraintSet.TOP, dpToPx(12))


            rootSet.connect(R.id.recording_timer, ConstraintSet.TOP, R.id.topControls, ConstraintSet.BOTTOM, dpToPx(8))
            rootSet.connect(R.id.recording_timer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            rootSet.connect(R.id.recording_timer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }

        rootSet.applyTo(rootLayout)

        zoomSlider.rotation = 0f

        val bottomSet = ConstraintSet().apply { clone(bottomControlsContainer) }
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.setHorizontalBias(R.id.shutterButton, 0.5f)

        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.START)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.END)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.TOP)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.BOTTOM)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.START, R.id.shutterButton, ConstraintSet.END, dpToPx(16))
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.TOP, R.id.shutterButton, ConstraintSet.TOP)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.BOTTOM, R.id.shutterButton, ConstraintSet.BOTTOM)
        bottomSet.applyTo(bottomControlsContainer)

        if (animate) {
            topControlsContainer.animate().alpha(1f).setDuration(200).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(200).start()
        } else {
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
        }

        captureButton.scaleX = 1f
        captureButton.scaleY = 1f
    }


    private fun showControlsOnInteraction() {
        if (!layoutOrientationInitialized) return
        val orientation = currentUiOrientation
        if (orientation != UiOrientation.LANDSCAPE_LEFT && orientation != UiOrientation.LANDSCAPE_RIGHT) return
        topControlsContainer.animate().alpha(1f).setDuration(150).start()
        bottomControlsContainer.animate().alpha(1f).setDuration(150).start()
        clearControlsAutoHide()
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        val orientation = currentUiOrientation
        if (orientation != UiOrientation.LANDSCAPE_LEFT && orientation != UiOrientation.LANDSCAPE_RIGHT) {
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutForRotation(getDisplayRotation())
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun applyTargetRotations(rotation: Int) {
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
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
            modeSwitchButton.isEnabled = false
            flipCameraButton.isEnabled = false
        }
    }

    private fun stopRecordingIndicator() {
        runOnUiThread {
            captureButton.clearAnimation()
            captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
            recordingTimer.stop()
            recordingTimer.visibility = View.GONE
            modeSwitchButton.isEnabled = true
            flipCameraButton.isEnabled = true
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
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
        recording?.stop()
        recording = null
        orientationEventListener?.disable()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clearControlsAutoHide()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateLayoutForRotation(getDisplayRotation(), animate = false)
        loadLatestPhotoThumbnail()
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
