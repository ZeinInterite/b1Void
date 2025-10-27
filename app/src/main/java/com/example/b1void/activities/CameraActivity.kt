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
import android.widget.TextView
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
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import android.content.res.Configuration as AndroidConfiguration
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import android.view.TouchDelegate
import android.graphics.Rect
import com.example.b1void.utils.dpToPx

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
    private lateinit var evSun: android.widget.TextView
    private lateinit var captureAnimationView: ImageView
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var bottomControlsContainer: ConstraintLayout
    private var topControlsSpacer: View? = null
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
    private var preferredVideoQuality: Int = 720 // 2160,1080,720,480
    // Hold-to-record state
    // Возврат автофокуса в центр через 5 секунд после ручного тапа
    private val refocusToCenterRunnable = Runnable { focusCoordinator?.resetToCenter() }
    private var isHoldRecordingActive = false
    private var holdStartRunnable: Runnable? = null
    private var holdToRecordDelayMs = 3000L  // Загружается из настроек, по умолчанию 0.8 сек
    private var pressDownUptime: Long = 0L
    private val quickTapThresholdMs = 150L
    private var waitingForStopTap = false  // Флаг: палец отпущен после старта записи, ждем следующий тап для остановки
    private val hideFocusIndicatorRunnable = Runnable {
        focusIndicator.animate().cancel()
        focusIndicator.visibility = View.GONE
        hideEvUi()
        Log.d(AEAF_TAG, "hideFocusIndicatorRunnable: ring + EV UI hidden")
    }
    private var focusLastX: Float? = null
    private var focusLastY: Float? = null
    private var evHideRunnable: Runnable? = null
    private var evController: com.example.b1void.camera.ev.EvController? = null
    private var previewEvLastY: Float? = null

    // Focus coordination
    private var focusCoordinator: com.example.b1void.camera.focus.FocusCoordinator? = null

    // Debounce rebind on orientation change to keep transition smooth
    private var rebindAfterRotationRunnable: Runnable? = null
    private val rebindDebounceMs = 160L
    private var isRebinding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        settingsManager = CameraSettingsManager(this)
        // Initialize camera settings from app-wide cache (restored at app startup)
        runCatching {
            val cache = com.example.b1void.data.AppSettingsCache
            flashMode = when (cache.flashMode) {
                1 -> ImageCapture.FLASH_MODE_ON
                2 -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            cache.resolution?.let { resStr ->
                selectedResolution = parseResolution(resStr) ?: selectedResolution
            }
            preferredVideoQuality = cache.videoQuality
            holdToRecordDelayMs = cache.videoRecordDelayMs.toLong()
            savedTorchState = cache.torchEnabled
            shouldRestoreTorchState = true
        }
        // Request camera permissions on first launch
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                // Set a default resolution only if not previously chosen
                if (com.example.b1void.data.AppSettingsCache.resolution.isNullOrEmpty()) {
                    settingsManager.setResolution("${DEFAULT_PHOTO_RESOLUTION.width}x${DEFAULT_PHOTO_RESOLUTION.height}")
                }
            }
        }

        initializeViews()
        lastLayoutRotation = getDisplayRotation()
        updateLayoutForRotation(lastLayoutRotation, animate = false)
        setupListeners()
        observeSettings()

        if (allPermissionsGranted()) {
            // Запускаем камеру после разметки PreviewView, чтобы корректно собрать ViewPort
            previewView.post { startCamera() }
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

                        if (waitingForStopTap) {
                            // Запись идет, пользователь нажал кнопку для остановки - ничего не делаем в ACTION_DOWN
                            // Остановка произойдет в ACTION_UP
                        } else if (!isHoldRecordingActive && !isRecording) {
                            // Запись не идет - начинаем отсчет 0.8 сек
                            imageCapture?.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            takePhoto()
                            scheduleHoldRecordingStart()
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentMode == CaptureMode.PHOTO) {
                    if (currentMode == CaptureMode.PHOTO) {
                        v.isPressed = false

                        if (waitingForStopTap) {
                            // Stop recording on the next tap release
                            stopVideoRecordingForHoldNow()
                            waitingForStopTap = false
                        } else if (isRecording || isHoldRecordingActive) {
                            // Do not stop on release; wait for next tap
                            waitingForStopTap = true
                        } else {
                            // Released before hold threshold — cancel scheduled video start
                            cancelHoldRecordingStartIfPending()
                        }
                        return@setOnTouchListener true
                    }
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
            val resForSettings = when {
                selectableCaptureResolutions.isNotEmpty() -> selectableCaptureResolutions
                availableCaptureResolutions.isNotEmpty() -> availableCaptureResolutions
                else -> supportedResolutions
            }
            settingsDialog.setSupportedResolutions(resForSettings) { newResolution ->
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
                        runCatching { settingsManager.setTorchEnabled(newTorchState) }
                            .onFailure { Log.e(TAG, "Failed to persist torch state", it) }
                    }
                }
            }
        }

        autofocusButton?.setOnClickListener { triggerManualAutofocus() }

        tapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Легкий тап обрабатывается в ACTION_UP, здесь только показываем UI
                showControlsOnInteraction()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                Log.d(AEAF_TAG, "onLongPress: x=" + e.x + ", y=" + e.y)
                focusCoordinator?.onLongPress(e.x, e.y)
                ensureEvController()
                evController?.begin()
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val locked = focusCoordinator?.isLocked() == true
                return if (locked && !isZoomGesture) {
                    ensureEvController()
                    val dy = -distanceY
                    evController?.adjustByDrag(dy)
                    scheduleHideEvOverlay()
                    Log.v(AEAF_TAG, "onScroll handled for EV: dy=" + dy)
                    true
                } else {
                    Log.d(AEAF_TAG, "onScroll: dx=" + distanceX + ", dy=" + distanceY + " (skipped; locked=" + locked + ", isZoomGesture=" + isZoomGesture + ")")
                    false
                }
            }
        }).apply {
            setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
                override fun onDoubleTap(e: MotionEvent): Boolean = false
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
                    Log.d(AEAF_TAG, "touch ACTION_DOWN: x=" + event.x + ", y=" + event.y + ", pointers=" + event.pointerCount)
                    // If finger lands near the EV bar, begin EV tracking from the preview
                    try {
                        val pv = IntArray(2)
                        val ov = IntArray(2)
                        previewView.getLocationOnScreen(pv)
                        evOverlay.getLocationOnScreen(ov)
                        val sx = pv[0] + event.x.toInt()
                        val sy = pv[1] + event.y.toInt()
                        val within = sx >= ov[0] - 20 && sx <= ov[0] + evOverlay.width + 20 && sy >= ov[1] - 32 && sy <= ov[1] + evOverlay.height + 32
                        if (within) {
                            ensureEvController()
                            evController?.begin()
                            showEvUi()
                            previewEvLastY = event.y
                            rootLayout.requestDisallowInterceptTouchEvent(true)
                            Log.d(AEAF_TAG, "preview EV begin (near bar) y=" + event.y)
                        }
                    } catch (_: Throwable) { }
                    showControlsOnInteraction()
                }
                MotionEvent.ACTION_POINTER_DOWN -> { isZoomGesture = true; Log.d(AEAF_TAG, "touch POINTER_DOWN -> isZoomGesture=true (pointers=" + event.pointerCount + ")") }
                MotionEvent.ACTION_MOVE -> {
                    if (!isZoomGesture) {
                        // If preview-based EV drag session is active, adjust globally
                        val activeLast = previewEvLastY
                        if (activeLast != null) {
                            ensureEvController()
                            val dy = event.y - activeLast
                            previewEvLastY = event.y
                            evController?.adjustByDrag(dy)
                            scheduleHideEvOverlay()
                            Log.v(AEAF_TAG, "preview EV MOVE active: dy=" + dy)
                        }
                        val pv = IntArray(2)
                        val ov = IntArray(2)
                        previewView.getLocationOnScreen(pv)
                        evOverlay.getLocationOnScreen(ov)
                        val sx = pv[0] + event.x.toInt()
                        val sy = pv[1] + event.y.toInt()
                        val within = sx >= ov[0] - 20 && sx <= ov[0] + evOverlay.width + 20 && sy >= ov[1] - 32 && sy <= ov[1] + evOverlay.height + 32
                        Log.d(AEAF_TAG, "touch ACTION_MOVE: x=" + event.x + ", y=" + event.y + " (EV drag not handled) nearBar=" + within + " screenX=" + sx + ", screenY=" + sy)
                        if (within && activeLast == null) {
                            ensureEvController()
                            val last = previewEvLastY
                            if (last == null) {
                                previewEvLastY = event.y
                            } else {
                                val dy = event.y - last
                                previewEvLastY = event.y
                                evController?.adjustByDrag(dy)
                                scheduleHideEvOverlay()
                                Log.v(AEAF_TAG, "preview EV MOVE nearBar: dy=" + dy)
                            }
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> { isZoomGesture = false; Log.d(AEAF_TAG, "touch ACTION_CANCEL") }
                MotionEvent.ACTION_UP -> {
                    // Лёгкое нажатие (короткий тап) – фокусируемся в точке отпускания
                    if (!isZoomGesture) {
                        startFocusMeteringAt(event.x, event.y, showIndicator = true)
                        Log.d(AEAF_TAG, "touch ACTION_UP: startFocusMeteringAt at x=" + event.x + ", y=" + event.y)
                        // Через 5 секунд возвращаемся в автофокус по центру
                        
                        
                        if (focusCoordinator?.isLocked() == true) scheduleHideEvOverlay()
                        if (previewEvLastY != null) {
                            scheduleHideEvOverlay()
                            previewEvLastY = null
                            rootLayout.requestDisallowInterceptTouchEvent(false)
                            Log.d(AEAF_TAG, "preview EV end on ACTION_UP")
                        }
                    }
                    view.performClick()
                }
            }
            handled || true
        }

        // Legacy SeekBar zoom listeners removed
    }

    private fun initializeViews() {
        rootLayout = findViewById(R.id.main_container)
        topControlsContainer = findViewById(R.id.topControls)
        bottomControlsContainer = findViewById(R.id.bottomControls)
        // Present only in portrait layout; absent in landscape override
        topControlsSpacer = findViewById(R.id.topControlsSpacer)
        previewView = findViewById(R.id.previewView)
        // Default preview scaling. В портретной ориентации избегаем кропа (FIT_CENTER),
        // в альбомной — заполняем экран без чёрных полос (FILL_CENTER).
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
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
        evSun = findViewById(R.id.evSun)
        captureAnimationView = findViewById(R.id.captureAnimationView)
        // Hide legacy zoom sliders when using Compose zoom
        if (useComposeZoom) {
            // Legacy sliders are not present in layout anymore
        }

        // Make EV overlay draggable to adjust exposure
        var evTouchLastY: Float? = null
        evOverlay.isClickable = true
        evOverlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    evTouchLastY = event.y
                    ensureEvController()
                    evController?.begin()
                    showEvUi()
                    rootLayout.requestDisallowInterceptTouchEvent(true)
                    Log.d(AEAF_TAG, "evOverlay DOWN: y=" + event.y)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val last = evTouchLastY
                    if (last != null) {
                        val dy = event.y - last
                        evTouchLastY = event.y
                        ensureEvController()
                        evController?.adjustByDrag(dy)
                        scheduleHideEvOverlay()
                        Log.v(AEAF_TAG, "evOverlay MOVE: dy=" + dy + ", y=" + event.y)
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val act = if (event.actionMasked == MotionEvent.ACTION_UP) "UP" else "CANCEL"
                    Log.d(AEAF_TAG, "evOverlay " + act)
                    evTouchLastY = null
                    // Stop EV tracking but keep overlay; schedule auto-hide
                    ensureEvController()
                    // Do not call end() here to avoid immediate hide; let auto-hide handle it
                    scheduleHideEvOverlay()
                    rootLayout.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        // Increase touch area around the shutter button for better accessibility
        // and easier tapping without changing visual shape beyond layout size.
        (captureButton.parent as? View)?.post {
            try {
                val extra = 24.dpToPx(this)
                val rect = Rect()
                captureButton.getHitRect(rect)
                rect.inset(-extra, -extra)
                (captureButton.parent as View).touchDelegate = TouchDelegate(rect, captureButton)
            } catch (_: Throwable) { /* no-op if parent not available */ }
        }

        // Log EV overlay bounds after first layout
        evOverlay.post { logEvOverlayBounds("after-init") }

        // Set initial properties for vertical slider (legacy)
        // No legacy slider init

        // Expand touch area for the narrow EV overlay so it's easier to drag
        (evOverlay.parent as? View)?.post {
            try {
                val extraH = 20.dpToPx(this)
                val extraV = 32.dpToPx(this)
                val hit = Rect()
                evOverlay.getHitRect(hit)
                hit.left -= extraH
                hit.right += extraH
                hit.top -= extraV
                hit.bottom += extraV
                (evOverlay.parent as View).touchDelegate = TouchDelegate(hit, evOverlay)
                Log.d(AEAF_TAG, "expanded evOverlay touch area: rect=" + hit.toShortString())
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to expand EV overlay touch area", t)
            }
        }

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
        lifecycleScope.launch {
            settingsManager.getVideoQuality().collect { q ->
                if (q != preferredVideoQuality) {
                    preferredVideoQuality = q
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
        // Ensure PreviewView is measured to avoid building ViewPort with wrong size
        val pvW = previewView.width.takeIf { it > 0 } ?: previewView.measuredWidth
        val pvH = previewView.height.takeIf { it > 0 } ?: previewView.measuredHeight
        if (pvW <= 0 || pvH <= 0) {
            previewView.post { startCamera() }
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Получаем информацию о производителе и модели
            val manufacturer = com.example.b1void.utils.DeviceInfo.manufacturer
            val model = com.example.b1void.utils.DeviceInfo.model
            Log.d(TAG, "Device: $manufacturer $model")

            // Логирование настроек камеры для отладки (особенно для Xiaomi устройств)
            if (com.example.b1void.utils.DeviceInfo.isXiaomi()) {
                Log.d("CAMERA_DEBUG_REDMI", com.example.b1void.utils.DeviceInfo.logCameraDebugInfo())
            }

            val resolvedCameraId = resolveCameraId()
            if (resolvedCameraId != null) {
                activeCameraId = resolvedCameraId
                refreshCameraCharacteristics(resolvedCameraId)
                updateSelectableCaptureResolutions()
                // Update supported resolutions with manufacturer-specific filtering
                supportedResolutions = getSupportedResolutionsForDevice(resolvedCameraId, manufacturer)
            } else {
                availableCaptureResolutions = emptyList()
                availablePreviewResolutions = emptyList()
                selectableCaptureResolutions = emptyList()
                supportedResolutions = getDefaultResolutions()
            }

            // === КРИТИЧЕСКАЯ ДИАГНОСТИКА: НАЧАЛО ===
            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ НАСТРОЙКИ ПОЛЬЗОВАТЕЛЯ")
            Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ selectedResolution (из настроек): ${selectedResolution?.width}x${selectedResolution?.height}")
            Log.e("CAMERA_DEBUG", "║ Производитель устройства: $manufacturer")
            Log.e("CAMERA_DEBUG", "║ Модель устройства: $model")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            // Выбираем разрешение с учетом особенностей устройства
            val captureResolution = selectBestResolution(
                selectedResolution,
                manufacturer,
                model
            )

            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ ПОСЛЕ selectBestResolution()")
            Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ captureResolution: ${captureResolution.width}x${captureResolution.height}")
            Log.e("CAMERA_DEBUG", "║ Изменилось? ${selectedResolution != captureResolution}")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            // Use the same resolution for both Preview and ImageCapture to keep crop/viewport in sync
            val previewResolution: Size? = captureResolution

            Log.d(TAG, "=== Resolution Configuration ===")
            Log.d(TAG, "Capture resolution: ${captureResolution.width}x${captureResolution.height}")
            Log.d(TAG, "Preview resolution: ${previewResolution?.let { "${it.width}x${it.height}" } ?: "same as capture"}")

            if (selectedResolution != captureResolution) {
                lifecycleScope.launch {
                    settingsManager.setResolution("${captureResolution.width}x${captureResolution.height}")
                }
            }
            selectedResolution = captureResolution

            // Derive rotation directly from PreviewView display to keep use cases aligned
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            currentTargetRotation = rotation

            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ ОРИЕНТАЦИЯ И ROTATION")
            Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ Display rotation: $rotation")
            val rotationDegrees = when(rotation) {
                Surface.ROTATION_0 -> "0° (Portrait)"
                Surface.ROTATION_90 -> "90° (Landscape)"
                Surface.ROTATION_180 -> "180°"
                Surface.ROTATION_270 -> "270° (Landscape reverse)"
                else -> "Unknown"
            }
            Log.e("CAMERA_DEBUG", "║ Rotation в градусах: $rotationDegrees")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            // ViewPort должен соответствовать aspect ratio целевого разрешения камеры (4:3)
            // чтобы избежать нежелательного cropping изображения
            val isLandscape = (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)

            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ isLandscape = $isLandscape")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            // Используем aspect ratio целевого разрешения для ViewPort
            // Это гарантирует, что захваченное изображение будет точно соответствовать captureResolution
            val viewPortWidth: Int
            val viewPortHeight: Int

            if (isLandscape) {
                // В landscape режиме ширина больше высоты
                viewPortWidth = captureResolution.width
                viewPortHeight = captureResolution.height
            } else {
                // В portrait режиме высота больше ширины (меняем местами)
                viewPortWidth = captureResolution.height
                viewPortHeight = captureResolution.width
            }

            val viewPortScaleType = when (previewView.scaleType) {
                PreviewView.ScaleType.FILL_CENTER -> 1 // ViewPort.FILL
                else -> 0 // ViewPort.FIT
            }
            val viewPort = ViewPort.Builder(
                android.util.Rational(viewPortWidth, viewPortHeight),
                rotation
            )
                // Используем FIT чтобы показать всё изображение без обрезки
                // FIT гарантирует, что весь контент будет виден в preview
                .setScaleType(0) // ViewPort.FIT - избегаем cropping
                .build()

            // КРИТИЧНО: ImageCapture должен использовать ТОЧНОЕ разрешение, выбранное пользователем
            // НЕ используем AspectRatioStrategy для ImageCapture - это переопределяет точное разрешение!
            // Если пользователь выбрал 960x720, фото ДОЛЖНО быть 960x720, а не "ближайшее с соотношением 4:3"
            val imageCaptureSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        captureResolution,  // ТОЧНОЕ разрешение от пользователя (например, 960x720)
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            // Для Preview можем использовать AspectRatioStrategy - это влияет только на отображение
            val targetAspect = CameraSettingsManager.CAMERA_ASPECT_RATIO
            val previewAspectRatioStrategy = AspectRatioStrategy(
                targetAspect,
                AspectRatioStrategy.FALLBACK_RULE_AUTO
            )

            // КРИТИЧНО: НЕ используем ViewPort для UseCaseGroup!
            // ViewPort переопределяет разрешение ImageCapture и приводит к 720x540 вместо 960x720
            // Preview будет работать без ViewPort, используя ResolutionSelector
            val useCaseGroupBuilder = UseCaseGroup.Builder()
                // .setViewPort(viewPort)  // УДАЛЕНО - это причина бага!

            val previewBuilder = Preview.Builder()
                .setTargetRotation(rotation)

            // Preview использует AspectRatioStrategy для красивого отображения
            val previewSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(previewAspectRatioStrategy)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        previewResolution ?: captureResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            previewBuilder.setResolutionSelector(previewSelector)

            val preview = previewBuilder.build()
            previewUseCase = preview
            useCaseGroupBuilder.addUseCase(preview)

            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ СОЗДАНИЕ ImageCapture")
            Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ Передаем captureResolution в ResolutionSelector:")
            Log.e("CAMERA_DEBUG", "║   ${captureResolution.width}x${captureResolution.height}")
            Log.e("CAMERA_DEBUG", "║ targetRotation: $rotation")
            Log.e("CAMERA_DEBUG", "║ flashMode: $flashMode")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .setTargetRotation(rotation)

            imageCaptureBuilder.setResolutionSelector(imageCaptureSelector)

            val newImageCapture = imageCaptureBuilder.build()
            imageCapture = newImageCapture

            Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ ImageCapture СОЗДАН")
            Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
            Log.e("CAMERA_DEBUG", "║ ImageCapture.targetRotation: ${newImageCapture.targetRotation}")
            Log.e("CAMERA_DEBUG", "║ ImageCapture.flashMode: ${newImageCapture.flashMode}")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")
            useCaseGroupBuilder.addUseCase(newImageCapture)

            // DEBUG: Log preview and capture configuration
            Log.d(TAG, "=== CameraX Configuration ===")
            Log.d(TAG, "ViewPort: ${viewPortWidth}x${viewPortHeight} (based on capture resolution), ScaleType=FIT, rotation=$rotation")
            Log.d(
                TAG,
                "Preview target resolution: " + (
                    previewResolution?.let { "${it.width}x${it.height}" }
                        ?: "${captureResolution.width}x${captureResolution.height}"
                    )
            )
            Log.d(TAG, "ImageCapture target resolution: ${captureResolution.width}x${captureResolution.height} (EXACT - no aspect ratio override)")
            Log.d(TAG, "SYNC CHECK: Preview and Capture using SAME resolution = ${previewResolution == captureResolution}")
            Log.d(TAG, "PreviewView ScaleType: ${previewView.scaleType}")
            Log.d(TAG, "PreviewView ImplementationMode: ${previewView.implementationMode}")
            Log.d(TAG, "Preview AspectRatioStrategy: ${if (targetAspect == androidx.camera.core.AspectRatio.RATIO_16_9) "RATIO_16_9" else "RATIO_4_3"} (for display only)")
            Log.d(TAG, "ImageCapture: NO AspectRatioStrategy - using exact resolution")
            Log.d(TAG, "TargetRotation (Preview/ImageCapture): $rotation / $rotation")

            // Always bind VideoCapture to support hold-to-record in PHOTO mode
            run {
                val targetQuality = mapPreferredVideoQuality(preferredVideoQuality)
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            targetQuality,
                            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                        )
                    )
                    .build()

                // Создаем VideoCapture с применением стабилизации видео
                val videoCaptureBuilder = VideoCapture.Builder(recorder)
                    .setTargetRotation(rotation)

                // Применяем стабилизацию для видео через Camera2 Interop
                try {
                    val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()
                    if (!quirks.hasEisIssues()) {
                        val videoExtender = Camera2Interop.Extender(videoCaptureBuilder)
                        videoExtender.setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
                        Log.d(TAG, "Video stabilization enabled for VideoCapture")
                    } else {
                        Log.d(TAG, "Video stabilization disabled for VideoCapture due to quirks")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable video stabilization for VideoCapture: ${e.message}")
                }

                val newVideoCapture = videoCaptureBuilder.build()
                videoCapture = newVideoCapture
                useCaseGroupBuilder.addUseCase(newVideoCapture)
            }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, useCaseGroupBuilder.build()
                )

                // Set surface provider after binding to ensure proper initialization
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // After bind, switch ImplementationMode back to COMPATIBLE to maximize fidelity
                previewView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }, 250)

                // Zoom is managed by CameraViewModel which preserves user's zoom setting across rebinds

                // DEBUG: Log actual resolved dimensions and zoom state after binding
                camera?.cameraInfo?.let { info ->
                    var previewResActual: Size? = null
                    var captureResActual: Size? = null

                    preview.resolutionInfo?.let { resInfo ->
                        previewResActual = resInfo.resolution
                        Log.d(TAG, "Preview resolved resolution: ${previewResActual!!.width}x${previewResActual!!.height}, " +
                                "aspect ratio: ${previewResActual!!.width.toFloat() / previewResActual!!.height}")
                    }
                    newImageCapture.resolutionInfo?.let { resInfo ->
                        captureResActual = resInfo.resolution
                        Log.d(TAG, "ImageCapture resolved resolution: ${captureResActual!!.width}x${captureResActual!!.height}, " +
                                "aspect ratio: ${captureResActual!!.width.toFloat() / captureResActual!!.height}")
                    }

                    // CRITICAL CHECK: Verify preview and capture use same resolution
                    if (previewResActual != null && captureResActual != null) {
                        val isMatching = previewResActual == captureResActual
                        Log.d(TAG, "=== RESOLUTION SYNC STATUS: ${if (isMatching) "✓ MATCHED" else "✗ MISMATCH"} ===")
                        if (!isMatching) {
                            Log.w(TAG, "WARNING: Preview and Capture resolutions don't match!")
                            Log.w(TAG, "This will cause preview to show different area than captured photo")
                        }
                    }

                    info.zoomState.value?.let { zoom ->
                        Log.d(TAG, "Initial zoom ratio: ${zoom.zoomRatio} (min: ${zoom.minZoomRatio}, max: ${zoom.maxZoomRatio})")
                    }
                }

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
                            override fun showIndicator(x: Float, y: Float) {
                                Log.d(AEAF_TAG, "callbacks.showIndicator(x=" + x + ", y=" + y + ")")
                                showFocusIndicator(x, y); focusLastX = x; focusLastY = y
                            }
                            override fun hideIndicator() {
                                Log.d(AEAF_TAG, "callbacks.hideIndicator() -> schedule ring hide 1500ms")
                                focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
                                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
                            }
                            override fun onFocusResult(success: Boolean) {
                                Log.d(AEAF_TAG, "callbacks.onFocusResult(success=" + success + ")")
                                focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
                                Log.d(AEAF_TAG, "callbacks.onFocusResult(success=" + success + ") -> schedule ring hide 1500ms")
                                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
                                if (success) previewView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            override fun onLockChanged(locked: Boolean) {
                                Log.d(AEAF_TAG, "callbacks.onLockChanged(locked=" + locked + ")")
                                if (!locked) focusIndicator.post(hideFocusIndicatorRunnable)
                                toggleLockIcon(locked)
                            }
                        },
                        // Автоотмена ручного фокуса через 5 секунд
                        config = com.example.b1void.camera.focus.FocusCoordinator.Config(
                            tapAutoCancelSeconds = 0,
                            showIndicatorOnCenter = false,
                            focusTimeoutMs = 3000L,
                            cafReturnDelayMs = 1800L
                        )
                    )
                    
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

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyCamera2Defaults() {
        val cam = camera ?: return

        try {
            val camera2Control = Camera2CameraControl.from(cam.cameraControl)
            val camera2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val characteristics = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )

            // Получаем quirks для текущего производителя
            val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()

            // Проверяем доступность базовых возможностей
            val hasManualSensor = characteristics?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ) == true

            val hasManualPostProcessing = characteristics?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
            ) == true

            camera2Control.clearCaptureRequestOptions()
            val optionsBuilder = CaptureRequestOptions.Builder()

            // Базовые настройки, поддерживаемые всеми устройствами
            try {
                optionsBuilder
                    .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Basic camera controls not fully supported", e)
            }

            // Настройка стабилизации изображения
            applyImageStabilization(camera2Info, optionsBuilder, quirks)

            // Опциональные улучшенные настройки - Face detection уже был, оставляем как есть

            try {
                // Проверяем поддержку распознавания лиц
                val maxFaceCount = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT
                )
                if (maxFaceCount != null && maxFaceCount > 0) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
                    )
                    Log.d(TAG, "Face detection enabled (max faces: $maxFaceCount)")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Face detection not supported: ${e.message}")
            }

            try {
                // Проверяем поддержку режимов сцены
                val availableSceneModes = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES
                )
                if (availableSceneModes?.contains(CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED) == true) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Scene modes not configurable: ${e.message}")
            }

            try {
                // Autofocus trigger
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                )
            } catch (e: Exception) {
                Log.d(TAG, "AF trigger not supported: ${e.message}")
            }

            // Применяем только успешно сконфигурированные опции
            camera2Control.setCaptureRequestOptions(optionsBuilder.build())

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Camera2 defaults, using CameraX defaults", e)
            // Откат к стандартным настройкам CameraX
        }
    }

    /**
     * Применяет настройки стабилизации изображения (OIS и EIS) с учетом особенностей устройства
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyImageStabilization(
        camera2Info: Camera2CameraInfo,
        optionsBuilder: CaptureRequestOptions.Builder,
        quirks: com.example.b1void.utils.ManufacturerCompatibility.CameraQuirks
    ) {
        var oisApplied = false
        var eisApplied = false

        // 1. Проверяем и применяем оптическую стабилизацию (OIS)
        if (!quirks.hasOisIssues()) {
            try {
                val availableOIS = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
                )

                val oisSupported = availableOIS?.contains(
                    CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON
                ) == true

                if (oisSupported && !quirks.preferEisOverOis()) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                    oisApplied = true
                    Log.d(TAG, "Stabilization: OIS enabled (hardware: ${com.example.b1void.utils.DeviceInfo.manufacturer})")
                } else if (!oisSupported) {
                    Log.d(TAG, "Stabilization: OIS not available on this device")
                } else {
                    Log.d(TAG, "Stabilization: OIS available but preferring EIS due to quirks")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stabilization: Failed to check/enable OIS: ${e.message}")
            }
        } else {
            Log.d(TAG, "Stabilization: OIS disabled due to manufacturer quirks (${com.example.b1void.utils.DeviceInfo.manufacturer})")
        }

        // 2. Проверяем и применяем электронную стабилизацию изображения (EIS)
        // EIS применяется через VIDEO_STABILIZATION_MODE для фото
        if (!quirks.hasEisIssues()) {
            try {
                val availableVideoStabilization = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
                )

                val eisSupported = availableVideoStabilization?.contains(
                    CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON
                ) == true

                // Применяем EIS если:
                // - устройство поддерживает
                // - и (OIS недоступна ИЛИ производитель предпочитает EIS)
                val shouldApplyEis = eisSupported && (!oisApplied || quirks.preferEisOverOis())

                if (shouldApplyEis) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                    eisApplied = true
                    Log.d(TAG, "Stabilization: EIS enabled (software-based)")
                } else if (!eisSupported) {
                    Log.d(TAG, "Stabilization: EIS not available on this device")
                } else if (oisApplied) {
                    Log.d(TAG, "Stabilization: EIS available but using OIS instead")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stabilization: Failed to check/enable EIS: ${e.message}")
            }
        } else {
            Log.d(TAG, "Stabilization: EIS disabled due to manufacturer quirks (${com.example.b1void.utils.DeviceInfo.manufacturer})")
        }

        // 3. Логирование итогового состояния стабилизации
        val stabilizationStatus = when {
            oisApplied && eisApplied -> "HYBRID (OIS + EIS)"
            oisApplied -> "OIS only"
            eisApplied -> "EIS only"
            else -> "NONE (not available or disabled)"
        }
        Log.i(TAG, "=== Image Stabilization Status: $stabilizationStatus ===")
    }

    /**
     * Выбирает оптимальное разрешение для текущего устройства
     */
    private fun selectBestResolution(
        requested: Size?,
        manufacturer: String,
        model: String
    ): Size {
        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ selectBestResolution() ВЫЗВАНА")
        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ requested: ${requested?.width}x${requested?.height}")
        Log.e("CAMERA_DEBUG", "║ manufacturer: $manufacturer")
        Log.e("CAMERA_DEBUG", "║ model: $model")
        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

        // Если запрошенное разрешение поддерживается, используем его
        if (requested != null &&
            (selectableCaptureResolutions.contains(requested) ||
                    availableCaptureResolutions.contains(requested))) {
            Log.e("CAMERA_DEBUG", "║ ✅ requested разрешение ПОДДЕРЖИВАЕТСЯ")
            Log.e("CAMERA_DEBUG", "║ ВОЗВРАЩАЕМ: ${requested.width}x${requested.height}")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")
            return requested
        }

        Log.e("CAMERA_DEBUG", "║ ❌ requested разрешение НЕ поддерживается")

        // Получаем список доступных разрешений
        val available = selectableCaptureResolutions.ifEmpty {
            availableCaptureResolutions.ifEmpty { supportedResolutions }
        }

        Log.e("CAMERA_DEBUG", "║")
        Log.e("CAMERA_DEBUG", "║ ДОСТУПНЫЕ РАЗРЕШЕНИЯ:")
        available.take(10).forEach {
            Log.e("CAMERA_DEBUG", "║   - ${it.width}x${it.height}")
        }
        if (available.size > 10) {
            Log.e("CAMERA_DEBUG", "║   ... и еще ${available.size - 10}")
        }
        Log.e("CAMERA_DEBUG", "║")

        // ПРИОРИТЕТ: Если DEFAULT_PHOTO_RESOLUTION (960x720) поддерживается камерой, используем его
        if (available.contains(DEFAULT_PHOTO_RESOLUTION)) {
            Log.e("CAMERA_DEBUG", "║ ✅ DEFAULT_PHOTO_RESOLUTION (960x720) ПОДДЕРЖИВАЕТСЯ")
            Log.e("CAMERA_DEBUG", "║ ВОЗВРАЩАЕМ: 960x720")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")
            Log.d(TAG, "Using DEFAULT_PHOTO_RESOLUTION (960x720) as it's supported by camera")
            return DEFAULT_PHOTO_RESOLUTION
        }

        // Если DEFAULT_PHOTO_RESOLUTION недоступно, выбираем наилучшее для данного устройства
        Log.e("CAMERA_DEBUG", "║ ❌ DEFAULT_PHOTO_RESOLUTION (960x720) НЕ поддерживается")
        Log.e("CAMERA_DEBUG", "║ Используем manufacturer-specific logic...")
        Log.d(TAG, "DEFAULT_PHOTO_RESOLUTION not available, using manufacturer-specific logic")

        // Получаем рекомендации для производителя
        val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()
        val maxRecommended = quirks.getMaxRecommendedResolution()

        return when {
            manufacturer == "samsung" && model.contains("galaxy s") -> {
                // Флагманские Samsung - можем использовать высокое разрешение
                available.firstOrNull { it.width >= 1920 } ?: available.firstOrNull() ?: DEFAULT_PHOTO_RESOLUTION
            }
            manufacturer == "xiaomi" && model.contains("redmi") -> {
                // Бюджетные Xiaomi - консервативный выбор
                available.firstOrNull { it.width in 1280..1920 } ?: available.firstOrNull() ?: Size(1280, 720)
            }
            manufacturer == "huawei" -> {
                // Huawei - предпочитаем 4:3
                available.firstOrNull {
                    val ratio = it.width.toFloat() / it.height
                    kotlin.math.abs(ratio - 1.333f) < 0.1f
                } ?: available.firstOrNull() ?: DEFAULT_PHOTO_RESOLUTION
            }
            maxRecommended != null -> {
                // Используем рекомендацию производителя
                available.firstOrNull {
                    it.width <= maxRecommended.width && it.height <= maxRecommended.height
                } ?: available.firstOrNull() ?: DEFAULT_PHOTO_RESOLUTION
            }
            else -> {
                // Безопасное разрешение для всех устройств
                available.firstOrNull { it.width == 1920 && it.height == 1080 }
                    ?: available.firstOrNull { it.width == 1280 && it.height == 720 }
                    ?: available.firstOrNull()
                    ?: Size(1280, 720) // Fallback на 720p
            }
        }
    }

    /**
     * Получает поддерживаемые разрешения для конкретного устройства
     */
    private fun getSupportedResolutionsForDevice(cameraId: String, manufacturer: String): List<Size> {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputFormats = map?.outputFormats ?: return getDefaultResolutions()

            // Получаем все поддерживаемые разрешения
            val allSizes = outputFormats.flatMap { format ->
                map.getOutputSizes(format)?.toList() ?: emptyList()
            }.distinct().sortedByDescending { it.width * it.height }

            // Фильтруем с учетом производителя
            return when (manufacturer) {
                "samsung" -> {
                    // Samsung поддерживает широкий диапазон, но иногда имеет проблемы с 4K
                    allSizes.filter { size ->
                        size.width <= 3840 && size.height <= 2160 && // Max 4K
                                (size.width >= 640 && size.height >= 480)    // Min VGA
                    }
                }
                "xiaomi", "redmi" -> {
                    // Xiaomi бюджетные модели иногда имеют проблемы с высокими разрешениями
                    allSizes.filter { size ->
                        size.width <= 1920 && size.height <= 1080 // Max FHD для стабильности
                    }
                }
                "huawei", "honor" -> {
                    // Huawei имеют собственные оптимизации
                    allSizes.filter { size ->
                        // Предпочитаем стандартные aspect ratios
                        val aspectRatio = size.width.toFloat() / size.height
                        aspectRatio in 1.3f..1.8f // 4:3 до 16:9
                    }
                }
                "oneplus" -> {
                    // OnePlus обычно хорошо поддерживают высокие разрешения
                    allSizes
                }
                "motorola", "lenovo" -> {
                    // Motorola/Lenovo могут иметь ограничения
                    allSizes.filter { size ->
                        size.width <= 2560 && size.height <= 1440
                    }
                }
                else -> {
                    // Для неизвестных производителей используем консервативный подход
                    allSizes.filter { size ->
                        size.width <= 1920 && size.height <= 1080
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting supported resolutions", e)
            return getDefaultResolutions()
        }
    }

    private fun getDefaultResolutions(): List<Size> {
        return listOf(
            Size(1920, 1080), // FHD
            Size(1280, 720),  // HD
            Size(640, 480)    // VGA
        )
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
        if (isFinishing || isDestroyed || isRebinding) return
        isRebinding = true
        try {
            cameraProvider?.unbindAll()
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to unbind camera before restart", exc)
        }
        // Дождёмся корректной разметки превью, чтобы собрать ViewPort по реальным размерам
        previewView.post {
            val w = previewView.width
            val h = previewView.height
            if (w <= 0 || h <= 0) {
                // если размеры ещё не готовы — отложим на следующий кадр
                previewView.post { startCamera(); isRebinding = false }
            } else {
                startCamera(); isRebinding = false
            }
        }
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
                    runCatching { settingsManager.setTorchEnabled(isTorchOn) }
                        .onFailure { Log.e(TAG, "Failed to persist torch state from observer", it) }
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

    private fun mapPreferredVideoQuality(pref: Int): Quality {
        return when (pref) {
            2160 -> Quality.UHD
            1080 -> Quality.FHD
            720 -> Quality.HD
            480 -> Quality.SD
            else -> Quality.HD
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
        // Плановый возврат в автофокус по центру через 5 секунд
        
        
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
                    onOverlayVisibility = { visible ->
                        Log.d(AEAF_TAG, "overlay visibility -> " + (if (visible) "VISIBLE" else "GONE"))
                        if (visible) showEvUi() else hideEvUi()
                    },
                    onOverlayValue = { ev ->
                        Log.v(AEAF_TAG, "overlay value EV=" + ev)
                        val st = cam.cameraInfo.exposureState
                        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0 } ?: 0.3333f
                        val minEv = st.exposureCompensationRange.lower * step
                        val maxEv = st.exposureCompensationRange.upper * step
                        updateSunPosition(ev, minEv, maxEv)
                    }
                ).also {
                    it.attach(cam)
                    Log.i(AEAF_TAG, "ensureEvController: created and attached")
                    // Sync initial EV knob
                    syncSunToCurrentEv()
                }
            }
        }
    }

    private fun scheduleHideEvOverlay() {
        evHideRunnable?.let { evOverlay.removeCallbacks(it) }
        val r = Runnable {
            Log.d(AEAF_TAG, "auto-hide EV overlay now")
            hideEvUi()
        }
        evHideRunnable = r
        Log.d(AEAF_TAG, "scheduleHideEvOverlay: 1500ms")
        evOverlay.postDelayed(r, 1500)
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        val indicatorWidth = focusIndicator.width.takeIf { it > 0 }
            ?: focusIndicator.layoutParams.width.takeIf { it > 0 }
            ?: 0
        val indicatorHeight = focusIndicator.height.takeIf { it > 0 }
            ?: focusIndicator.layoutParams.height.takeIf { it > 0 }
            ?: 0

        // Calculate a physical gap of 0.3 cm in pixels using device xdpi
        val gapPx = (0.3f * (resources.displayMetrics.xdpi / 2.54f))
        val evBarWidthPx = (evOverlay.width.takeIf { it > 0 }
            ?: evOverlay.layoutParams.width.takeIf { it > 0 }
            ?: 0).toFloat()

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
        // Place EV overlay vertically centered to the focus square,
        // and 0.3 cm to the left from the focus square's left edge
        evOverlay.apply {
            translationX = (clampedX - gapPx - evBarWidthPx).coerceAtLeast(0f)
            translationY = (clampedY + indicatorHeight / 2f - height / 2f).coerceAtLeast(0f)
            bringToFront()
        }
        // Place sun icon centered over the EV bar horizontally; sync to current EV
        evSun.apply {
            translationX = evOverlay.translationX + evOverlay.width / 2f - width / 2f
            translationY = evOverlay.translationY + evOverlay.height / 2f
            visibility = View.VISIBLE
            bringToFront()
        }
        showEvUi()
        syncSunToCurrentEv()
        logEvOverlayBounds("after-showFocusIndicator")
        Log.d(AEAF_TAG, "showFocusIndicator: touch(x=" + x + ", y=" + y + ") -> ring(tx=" + clampedX + ", ty=" + clampedY + "); lockVisible=" + (focusCoordinator?.isLocked() == true) + "; evOverlay(tx=" + evOverlay.translationX + ", ty=" + evOverlay.translationY + ", vis=" + (evOverlay.visibility == View.VISIBLE) + ")")
    }

    private fun logEvOverlayBounds(label: String) {
        try {
            val pos = IntArray(2)
            val pv = IntArray(2)
            evOverlay.getLocationOnScreen(pos)
            previewView.getLocationOnScreen(pv)
            Log.d(
                AEAF_TAG,
                "evOverlay bounds [" + pos[0] + "," + pos[1] + ", w=" + evOverlay.width + ", h=" + evOverlay.height + "]; preview@[" + pv[0] + "," + pv[1] + "] label=" + label
            )
        } catch (_: Throwable) {}
    }

    private fun showEvUi() {
        evOverlay.visibility = View.VISIBLE
        evSun.visibility = View.VISIBLE
    }

    private fun hideEvUi() {
        evOverlay.visibility = View.GONE
        evSun.visibility = View.GONE
    }

    private fun syncSunToCurrentEv() {
        val cam = camera ?: return
        val st = cam.cameraInfo.exposureState
        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0 } ?: 0.3333f
        val minEv = st.exposureCompensationRange.lower * step
        val maxEv = st.exposureCompensationRange.upper * step
        val currentEv = st.exposureCompensationIndex * step
        updateSunPosition(currentEv, minEv, maxEv)
    }

    private fun updateSunPosition(currentEv: Float, minEv: Float, maxEv: Float) {
        val range = (maxEv - minEv).takeIf { it != 0f } ?: 1f
        val t = ((currentEv - minEv) / range).coerceIn(0f, 1f)
        evOverlay.post {
            val barH = evOverlay.height.takeIf { it > 0 } ?: return@post
            val top = evOverlay.translationY
            val knobY = top + (1f - t) * barH
            // Center horizontally to the EV bar
            val centerX = evOverlay.translationX + evOverlay.width / 2f - evSun.width / 2f
            evSun.translationY = knobY - evSun.height / 2f
            evSun.translationX = centerX
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
                // Не ребиндим камеру по наклону — только обновляем UI при реальном повороте экрана
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
        topControlsSpacer?.visibility = View.GONE

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
        topControlsSpacer?.visibility = View.VISIBLE

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
        if (!isLandscapeUi) return
        topControlsContainer.animate().alpha(1f).setDuration(150).start()
        bottomControlsContainer.animate().alpha(1f).setDuration(150).start()
        // Compose zoom has its own visibility
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
        // Compose zoom has its own visibility
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // fix: rebind camera on orientation change to remove black bars
        // Обновить UI-раскладку под новый rotation
        val displayRotation = getDisplayRotation()
        // Disable UI fade/slide during orientation changes to prevent "floating" feel
        updateLayoutForRotation(displayRotation, animate = false)

        // Переключить режим масштабирования превью: в landscape заполняем экран без полос
        previewView.scaleType = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
            PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER

        // Обновить targetRotation и перебиндить use-cases с актуальным ViewPort
        currentTargetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        // Для плавности используем SurfaceView-режим
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        // Единоразовый быстрый перебинд с актуальным ViewPort
        // Debounce rebind slightly so layout can settle and PreviewView gets final size
        rebindAfterRotationRunnable?.let { previewView.removeCallbacks(it) }
        rebindAfterRotationRunnable = Runnable {
            // fix: rebind camera on orientation change to remove black bars
            restartCameraSession()
        }
        previewView.postDelayed(rebindAfterRotationRunnable!!, rebindDebounceMs)
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

        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ НАЧАЛО СЪЕМКИ ФОТО")
        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ ImageCapture.targetRotation: ${imageCapture.targetRotation}")
        Log.e("CAMERA_DEBUG", "║ ImageCapture.flashMode: ${imageCapture.flashMode}")
        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

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

                    // === КРИТИЧЕСКАЯ ДИАГНОСТИКА: РАЗМЕР СОХРАНЕННОГО ФОТО ===
                    Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
                    Log.e("CAMERA_DEBUG", "║ ФОТО СОХРАНЕНО (ДО обработки timestamp)")
                    Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
                    val optionsBeforeTimestamp = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, optionsBeforeTimestamp)
                    Log.e("CAMERA_DEBUG", "║ Размер ДО timestamp: ${optionsBeforeTimestamp.outWidth}x${optionsBeforeTimestamp.outHeight}")
                    Log.e("CAMERA_DEBUG", "║ Путь: ${photoFile.absolutePath}")
                    Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

                    try {
                        val bitmap = getCorrectlyOrientedBitmap(photoFile)
                        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ ПОСЛЕ getCorrectlyOrientedBitmap")
                        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ Bitmap размер: ${bitmap.width}x${bitmap.height}")
                        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

                        val timestampedBitmap = addTimestampToBitmap(bitmap)
                        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ ПОСЛЕ addTimestampToBitmap")
                        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ Timestamped Bitmap: ${timestampedBitmap.width}x${timestampedBitmap.height}")
                        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

                        saveBitmapToFile(timestampedBitmap, photoFile)

                        // ФИНАЛЬНАЯ проверка размера
                        val optionsFinal = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, optionsFinal)
                        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ ФИНАЛЬНЫЙ РАЗМЕР ФОТО")
                        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
                        Log.e("CAMERA_DEBUG", "║ ИТОГОВЫЙ размер: ${optionsFinal.outWidth}x${optionsFinal.outHeight}")
                        Log.e("CAMERA_DEBUG", "║ Файл: ${photoFile.name}")
                        Log.e("CAMERA_DEBUG", "║ Размер файла: ${photoFile.length() / 1024} KB")
                        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding timestamp", e)
                        Log.e("CAMERA_DEBUG", "ОШИБКА при обработке: ${e.message}")
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
                        isHoldRecordingActive = false
                        waitingForStopTap = false
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
        waitingForStopTap = false
        recording?.stop()
        recording = null
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
        waitingForStopTap = false
        isHoldRecordingActive = false
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
                runCatching { settingsManager.setTorchEnabled(currentTorchState) }
                    .onFailure { Log.e(TAG, "Failed to persist torch state onPause", it) }
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
            runCatching {
                val enabled = settingsManager.getTorchEnabled().first()
                savedTorchState = enabled
                shouldRestoreTorchState = true
                Log.d(TAG, "onResume: Loaded torch state from DataStore: $savedTorchState, will restore after camera initialization")
            }.onFailure { Log.e(TAG, "Failed to load torch state", it) }

            runCatching {
                holdToRecordDelayMs = settingsManager.getVideoRecordDelay().first().toLong()
                Log.d(TAG, "onResume: Loaded video record delay: ${holdToRecordDelayMs}ms")
            }.onFailure { Log.e(TAG, "Failed to load video delay", it) }

            runCatching {
                preferredVideoQuality = settingsManager.getVideoQuality().first()
                Log.d(TAG, "onResume: Loaded preferred video quality: $preferredVideoQuality")
            }.onFailure { Log.e(TAG, "Failed to load video quality", it) }
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
        private const val AEAF_TAG = "AEAF_EV"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        const val EXTRA_SAVE_PATH = "extra_save_path"
        private val DEFAULT_PHOTO_RESOLUTION = Size(960, 720)
        private const val ASPECT_RATIO_TOLERANCE = 0.02f
    }
}




