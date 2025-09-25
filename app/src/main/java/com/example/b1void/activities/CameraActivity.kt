package com.example.b1void.activities

import android.Manifest
import android.annotation.SuppressLint
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
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.adapters.ResolutionAdapter
import com.example.b1void.data.CameraSettingsManager
import com.example.b1void.ui.CameraSettingsDialogFragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
    private lateinit var resolutionSelectorButton: ImageButton
    private lateinit var resolutionListContainer: CardView
    private lateinit var resolutionRecyclerView: RecyclerView
    private lateinit var zoomSlider: SeekBar
    private lateinit var focusIndicator: View
    private lateinit var captureAnimationView: ImageView
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var bottomControlsContainer: ConstraintLayout

    // CameraX components
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var orientationEventListener: OrientationEventListener? = null
    private var currentTargetRotation = Surface.ROTATION_0
    private var cameraRestartJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isLandscapeUi: Boolean = false
    private var layoutOrientationInitialized = false
    private val controlsHideDelayMs = 2500L
    private val controlsAutoHideRunnable = Runnable {
        if (isLandscapeUi) {
            fadeControlsForLandscape()
        }
    }

    // Gesture detector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Settings
    private lateinit var settingsManager: CameraSettingsManager
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timestampEnabled = true
    private var selectedResolution: Size? = DEFAULT_PHOTO_RESOLUTION

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

        initializeViews()
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
            CameraSettingsDialogFragment().show(supportFragmentManager, "CameraSettingsDialog")
        }

        resolutionSelectorButton.setOnClickListener {
            resolutionListContainer.visibility = if (resolutionListContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.shutterButton)
        modeSwitchButton = findViewById(R.id.mode_switch_button)
        flipCameraButton = findViewById(R.id.switchCameraButton)
        recordingTimer = findViewById(R.id.recording_timer)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        settingsButton = findViewById(R.id.settingsButton)
        torchButton = findViewById(R.id.torchButton)
        resolutionSelectorButton = findViewById(R.id.resolutionSelectorButton)
        resolutionListContainer = findViewById(R.id.resolutionListContainer)
        resolutionRecyclerView = findViewById(R.id.resolutionRecyclerView)
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
            settingsManager.isTimestampEnabled().collect { isEnabled ->
                timestampEnabled = isEnabled
            }
        }
        lifecycleScope.launch {
            settingsManager.getResolution().collect { resString ->
                val parsedResolution = resString?.let { parseResolution(it) }
                val newResolution = parsedResolution ?: DEFAULT_PHOTO_RESOLUTION

                if (!isValidResolution(parsedResolution)) {
                    selectedResolution = DEFAULT_PHOTO_RESOLUTION
                    settingsManager.setResolution("${DEFAULT_PHOTO_RESOLUTION.width}x${DEFAULT_PHOTO_RESOLUTION.height}")
                    startCamera()
                    return@collect
                }

                if (selectedResolution != newResolution) {
                    selectedResolution = newResolution
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
                Toast.makeText(this, "???? ????????????? ??????>???\u0014?????? ???>?? ???'????<?'??? ?\"?????>??", Toast.LENGTH_SHORT).show()
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

            val previewBuilder = Preview.Builder()
            applyCamera2Defaults(previewBuilder)

            val preview = previewBuilder
                .setTargetRotation(currentTargetRotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            previewUseCase = preview

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val newVideoCapture = VideoCapture.withOutput(recorder).apply {
                targetRotation = currentTargetRotation
            }
            videoCapture = newVideoCapture

            val imageCaptureBuilder = ImageCapture.Builder()
            applyCamera2Defaults(imageCaptureBuilder)
            imageCaptureBuilder
                .setFlashMode(flashMode)
                .setTargetRotation(currentTargetRotation)
            
            selectedResolution?.takeIf { isValidResolution(it) }?.let {
                imageCaptureBuilder.setTargetResolution(it)
            }

            val newImageCapture = imageCaptureBuilder.build()
            imageCapture = newImageCapture
            applyTargetRotations(currentTargetRotation)

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, newImageCapture, newVideoCapture
                )
                applyCamera2Defaults()
                setupCameraStateObserver()
                setupTorchObserver()
                setupResolutionList()
                setupZoomObserver()
                focusAtCenter()
            } catch (exc: CameraAccessException) {
                Log.e(TAG, "Camera access error while binding use cases", exc)
                scheduleCameraRestart("CameraAccessException: ${exc.reason}")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
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
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
            .build()
        camera2Control.setCaptureRequestOptions(options)
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

    private fun restartCameraSession() {
        if (isFinishing || isDestroyed) return
        try {
            cameraProvider?.unbindAll()
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to unbind camera before restart", exc)
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

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupResolutionList() {
        camera?.let { cam ->
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(Camera2CameraInfo.from(cam.cameraInfo).cameraId)
            val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()

            resolutionRecyclerView.layoutManager = LinearLayoutManager(this)
            resolutionRecyclerView.adapter = ResolutionAdapter(resolutions.reversed(), selectedResolution) { size ->
                lifecycleScope.launch {
                    settingsManager.setResolution("${size.width}x${size.height}")
                }
                resolutionListContainer.visibility = View.GONE
            }
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
                    updateLayoutForRotation(rotation)
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
        val landscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val shouldAnimate = animate && layoutOrientationInitialized

        if (!layoutOrientationInitialized || landscape != isLandscapeUi) {
            isLandscapeUi = landscape
            layoutOrientationInitialized = true

            if (shouldAnimate) {
                val transition = AutoTransition().apply {
                    duration = 160
                    interpolator = AccelerateDecelerateInterpolator()
                }
                TransitionManager.beginDelayedTransition(rootLayout, transition)
            } else {
                captureButton.animate().cancel()
                topControlsContainer.animate().cancel()
                bottomControlsContainer.animate().cancel()
            }

            if (landscape) {
                applyLandscapeLayout(shouldAnimate)
            } else {
                applyPortraitLayout(shouldAnimate)
            }
        } else if (landscape) {
            scheduleControlsAutoHide()
        }
    }

    private fun applyLandscapeLayout(animate: Boolean) {
        val edgeMargin = dpToPx(24)

        val rootSet = ConstraintSet().apply { clone(rootLayout) }
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.TOP)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.END)
        rootSet.connect(R.id.thumbnailPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, edgeMargin)
        rootSet.connect(R.id.thumbnailPreview, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, edgeMargin)

        rootSet.clear(R.id.zoomSlider, ConstraintSet.START)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.END)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.TOP)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.BOTTOM)
        rootSet.constrainWidth(R.id.zoomSlider, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.constrainHeight(R.id.zoomSlider, dpToPx(180))
        rootSet.connect(R.id.zoomSlider, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, edgeMargin)
        rootSet.connect(R.id.zoomSlider, ConstraintSet.BOTTOM, R.id.bottomControls, ConstraintSet.TOP, dpToPx(8))
        rootSet.applyTo(rootLayout)

        zoomSlider.rotation = -90f

        val bottomSet = ConstraintSet().apply { clone(bottomControlsContainer) }
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dpToPx(12))
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, edgeMargin)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.setHorizontalBias(R.id.shutterButton, 1f)

        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.START)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.END)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.END, R.id.shutterButton, ConstraintSet.START, dpToPx(16))
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.TOP, R.id.shutterButton, ConstraintSet.TOP)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.BOTTOM, R.id.shutterButton, ConstraintSet.BOTTOM)
        bottomSet.applyTo(bottomControlsContainer)

        val targetScale = 1.18f
        if (animate) {
            captureButton.animate().scaleX(targetScale).scaleY(targetScale).setDuration(220).start()
        } else {
            captureButton.scaleX = targetScale
            captureButton.scaleY = targetScale
        }

        if (animate) {
            topControlsContainer.animate().alpha(1f).setDuration(180).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(180).start()
        } else {
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
        }

        showControlsOnInteraction()
    }

    private fun applyPortraitLayout(animate: Boolean) {
        clearControlsAutoHide()

        val startMargin = dpToPx(24)
        val bottomMargin = dpToPx(32)

        val rootSet = ConstraintSet()
        rootSet.clone(rootLayout)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.TOP)
        rootSet.clear(R.id.thumbnailPreview, ConstraintSet.END)
        rootSet.connect(R.id.thumbnailPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
        rootSet.connect(R.id.thumbnailPreview, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, bottomMargin)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.TOP)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.END)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.BOTTOM)
        rootSet.clear(R.id.zoomSlider, ConstraintSet.START)
        rootSet.constrainWidth(R.id.zoomSlider, ConstraintSet.MATCH_CONSTRAINT)
        rootSet.constrainHeight(R.id.zoomSlider, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        rootSet.connect(R.id.zoomSlider, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
        rootSet.connect(R.id.zoomSlider, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, startMargin)
        rootSet.connect(R.id.zoomSlider, ConstraintSet.BOTTOM, R.id.bottomControls, ConstraintSet.TOP, dpToPx(12))
        rootSet.applyTo(rootLayout)

        zoomSlider.rotation = 0f

        val bottomSet = ConstraintSet()
        bottomSet.clone(bottomControlsContainer)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(R.id.shutterButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.setHorizontalBias(R.id.shutterButton, 0.5f)

        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.START)
        bottomSet.clear(R.id.mode_switch_button, ConstraintSet.END)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.START, R.id.shutterButton, ConstraintSet.END, dpToPx(16))
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.TOP, R.id.shutterButton, ConstraintSet.TOP)
        bottomSet.connect(R.id.mode_switch_button, ConstraintSet.BOTTOM, R.id.shutterButton, ConstraintSet.BOTTOM)
        bottomSet.applyTo(bottomControlsContainer)

        if (animate) {
            captureButton.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            topControlsContainer.animate().alpha(1f).setDuration(200).start()
            bottomControlsContainer.animate().alpha(1f).setDuration(200).start()
        } else {
            captureButton.scaleX = 1f
            captureButton.scaleY = 1f
            topControlsContainer.alpha = 1f
            bottomControlsContainer.alpha = 1f
        }
    }

    private fun showControlsOnInteraction() {
        if (!layoutOrientationInitialized || !isLandscapeUi) return
        topControlsContainer.animate().alpha(1f).setDuration(150).start()
        bottomControlsContainer.animate().alpha(1f).setDuration(150).start()
        clearControlsAutoHide()
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        if (!isLandscapeUi) {
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
            runOnUiThread { thumbnailPreview.setImageResource(R.drawable.gray_square) }
        }
    }

    private fun takePhoto() {
        val imageCapture = this.imageCapture ?: return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                val bitmap = imageProxyToBitmap(image)
                val rotatedBitmap = if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
                image.close()

                val finalBitmap = if (timestampEnabled) {
                    addTimestampToBitmap(rotatedBitmap)
                } else {
                    rotatedBitmap
                }

                val savedFile = saveBitmapToFile(finalBitmap)
                lastSavedFile = savedFile

                runOnUiThread {
                    val fileUri = Uri.fromFile(savedFile)
                    updateThumbnail(fileUri)
                    playCaptureAnimation(fileUri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        })
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
            color = Color.RED
            this.textSize = textSize
            isAntiAlias = true
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        val fontMetrics = paint.fontMetrics
        paint.textAlign = Paint.Align.RIGHT
        val x = newBitmap.width - padding
        val y = newBitmap.height - padding - fontMetrics.bottom

        canvas.drawText(timestamp, x, y, paint)
        return newBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: ""
        val file = File(savePath, "IMG_${System.currentTimeMillis()}.jpg")
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
                            val msg = "Видео сохранено: ${recordEvent.outputResults.outputUri}"
                            updateThumbnail(recordEvent.outputResults.outputUri)
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
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(thumbnailPreview)
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
        private val DEFAULT_PHOTO_RESOLUTION = Size(720, 960)
    }
}
