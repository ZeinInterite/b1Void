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
import android.view.ViewConfiguration
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.min
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import dagger.hilt.android.AndroidEntryPoint
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
// EvControlPanel removed (legacy)

@AndroidEntryPoint
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
    private lateinit var exposureButton: ImageButton
    private var xiaomiBrightnessButton: ImageButton? = null
    private var autofocusButton: ImageButton? = null
    // Legacy zoom SeekBars removed; using Compose ZoomControl instead
    private lateinit var focusIndicator: View
    private lateinit var lockIcon: ImageView
    // Dummy placeholders for legacy EV references to keep touch logic intact
    private lateinit var evOverlay: View
    private lateinit var evSun: TextView
    // Legacy EV state placeholders (no longer driving UI)
    private var evHideRunnable: Runnable? = null
    // Legacy EV controller removed
    private var evController: Any? = null
    private var isEvAdjustmentActive: Boolean = false
    private var evInitialTouchX: Float? = null
    private var evInitialTouchY: Float? = null
    private var evTouchDownTime: Long = 0L
    private var evLongPressRunnable: Runnable? = null
    private val ZOOM_EXCLUSION_MARGIN = 48f
    private lateinit var captureAnimationView: ImageView
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var bottomControlsContainer: ConstraintLayout
    private var topControlsSpacer: View? = null
    private lateinit var zoomCompose: ComposeView
    private lateinit var evComposePanel: ComposeView

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

    // === XIAOMI BUG FIX #5: Preview watchdog for black screen detection ===
    private var previewStartTime = 0L
    private val previewTimeoutHandler = Handler(Looper.getMainLooper())
    private val PREVIEW_TIMEOUT_MS = 5000L  // 5 seconds timeout
    // When watchdog fires on Xiaomi, we first try switching PreviewView implementation mode
    // from TEXTURE_VIEW (COMPATIBLE) to SURFACE_VIEW (PERFORMANCE). Some MIUI builds
    // fail to render camera frames into TextureView which results in a permanent black
    // preview. Toggling implementation mode and rebinding the camera fixes this.
    private var xiaomiPreviewTriedPerformanceMode = false
    private val previewTimeoutRunnable = Runnable {
        if (System.currentTimeMillis() - previewStartTime > PREVIEW_TIMEOUT_MS) {
            Log.w(TAG, "XIAOMI FIX: Preview timeout detected (black screen)")

            if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()
                && !xiaomiPreviewTriedPerformanceMode
            ) {
                xiaomiPreviewTriedPerformanceMode = true
                Log.w(
                    TAG,
                    "XIAOMI FIX: Switching PreviewView.implementationMode to PERFORMANCE and restarting camera"
                )
                previewView.post {
                    // PERFORMANCE -> SurfaceView-based preview, avoids TextureView black screen
                    previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    restartCamera()
                }
            } else {
                restartCamera()
            }
        }
    }

    // === XIAOMI BUG FIX: Graceful cleanup job ===
    private var gracefulCleanupJob: Job? = null
    private val GRACEFUL_CLEANUP_DELAY_MS = 500L  // 500ms delay to finish pending captures

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
    // Throttling для pinch-to-zoom для предотвращения задержек
    private var lastZoomUpdateTime = 0L
    private var pendingZoomRatio: Float? = null
    private val ZOOM_UPDATE_INTERVAL_MS = 16L  // ~60 FPS максимум
    // While finger touches the screen, keep focus/EV UI visible
    private var userTouchActive = false
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
    private val quickTapThresholdMs = 100L
    private var waitingForStopTap = false  // Флаг: палец отпущен после старта записи, ждем следующий тап для остановки
    private val hideFocusIndicatorRunnable = object : Runnable {
        override fun run() {
            if (userTouchActive) {
                // Defer hiding while user still touching the screen
                focusIndicator.removeCallbacks(this)
                focusIndicator.postDelayed(this, 500)
                Log.v(AEAF_TAG, "defer hide (userTouchActive)")
                return
            }
            focusIndicator.animate().cancel()
            focusIndicator.visibility = View.GONE
            Log.d(AEAF_TAG, "hideFocusIndicatorRunnable: ring hidden")
        }
    }
    private var focusLastX: Float? = null
    private var focusLastY: Float? = null
    // EV overlay/controller removed

    // Legacy EV adjustment state removed

    // General touch tracking for focus/tap detection
    private var previewDownX: Float? = null
    private var previewDownY: Float? = null
    private var previewDownUptime: Long = 0L

    // Focus coordination
    private var focusCoordinator: com.example.b1void.camera.focus.FocusCoordinator? = null

    // Debounce rebind on orientation change to keep transition smooth
    private var rebindAfterRotationRunnable: Runnable? = null
    private val rebindDebounceMs = 160L
    private var isRebinding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        try {
            // Keep screen on; optionally boost brightness on Xiaomi/Redmi
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val maker = android.os.Build.MANUFACTURER.lowercase()
            val mgr = com.example.b1void.data.CameraSettingsManager(this)
            lifecycleScope.launchWhenCreated {
                val boost = runCatching { mgr.getXiaomiBrightnessBoostEnabled().first() }
                    .getOrElse { maker.contains("xiaomi") }
                if (boost || maker.contains("xiaomi") || maker.contains("redmi")) {
                    val lp = window.attributes
                    lp.screenBrightness = 1f
                    window.attributes = lp
                    Log.d(TONEMAP_TAG, "Applied brightness boost (maker=$maker)")
                }
            }
        } catch (_: Throwable) { }

        settingsManager = CameraSettingsManager(this)
        // Apply orientation preference (default: lock to landscape)
        lifecycleScope.launch {
            try {
                val lock = settingsManager.isOrientationLockEnabled().first()
                requestedOrientation = if (lock) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            } catch (_: Throwable) { /* ignore */ }
        }
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
        }

        // Load torch state from DataStore (persistent storage)
        lifecycleScope.launch {
            try {
                savedTorchState = settingsManager.getTorchEnabled().first()
                shouldRestoreTorchState = true
                Log.d(TAG, "Loaded torch state from DataStore: $savedTorchState")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load torch state from DataStore", e)
                savedTorchState = false
                shouldRestoreTorchState = false
            }
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
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Устанавливаем флаг СРАЗУ при начале pinch gesture
            isZoomGesture = true
            lastZoomUpdateTime = SystemClock.elapsedRealtime()
            pendingZoomRatio = null
            Log.d(TAG, "Pinch-to-zoom начался (scaleFactor=${detector.scaleFactor})")
            return true // Возвращаем true чтобы получать дальнейшие события onScale
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val camera = camera ?: return true
            val zoomState = camera.cameraInfo.zoomState.value ?: return true
            val currentZoomRatio = zoomState.zoomRatio
            val minR = zoomState.minZoomRatio
            val maxR = zoomState.maxZoomRatio
            val newZoomRatio = (currentZoomRatio * detector.scaleFactor).coerceIn(minR, maxR)

            // Throttling: обновляем зум не чаще чем раз в ZOOM_UPDATE_INTERVAL_MS
            val now = SystemClock.elapsedRealtime()
            val timeSinceLastUpdate = now - lastZoomUpdateTime

            if (timeSinceLastUpdate >= ZOOM_UPDATE_INTERVAL_MS) {
                // Достаточно времени прошло - применяем зум сразу
                try { camera.cameraControl.setZoomRatio(newZoomRatio) } catch (_: Throwable) {}
                lastZoomUpdateTime = now
                pendingZoomRatio = null
                Log.v(TAG, "Pinch-to-zoom APPLIED: ${currentZoomRatio} -> ${newZoomRatio}")
            } else {
                // Слишком рано для обновления - сохраняем для применения позже
                pendingZoomRatio = newZoomRatio
                Log.v(TAG, "Pinch-to-zoom PENDING: ${currentZoomRatio} -> ${newZoomRatio} (wait ${ZOOM_UPDATE_INTERVAL_MS - timeSinceLastUpdate}ms)")
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // Применяем отложенный зум если есть
            pendingZoomRatio?.let { ratio ->
                camera?.cameraControl?.setZoomRatio(ratio)
                Log.d(TAG, "Pinch-to-zoom завершен, применен отложенный зум: $ratio")
            }

            // Сбрасываем флаг после завершения pinch gesture
            isZoomGesture = false
            pendingZoomRatio = null
            Log.d(TAG, "Pinch-to-zoom завершен")
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

        // Diagnostic long-press: reset EV & Zoom
        settingsButton.setOnLongClickListener {
            val cam = camera
            if (cam != null) {
                try { cam.cameraControl.setExposureCompensationIndex(0) } catch (_: Throwable) {}
                try { cam.cameraControl.setZoomRatio(1.0f) } catch (_: Throwable) {}
                Toast.makeText(this, "EV & Zoom reset", Toast.LENGTH_SHORT).show()
                true
            } else false
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
                            // Start hold-to-record timer; photo will be decided on release
                            scheduleHoldRecordingStart()
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentMode == CaptureMode.PHOTO) {
                        v.isPressed = false

                        if (waitingForStopTap) {
                            // Stop recording on the next tap release
                            stopVideoRecordingForHoldNow()
                            waitingForStopTap = false
                            return@setOnTouchListener true
                        } else if (isRecording || isHoldRecordingActive) {
                            // Do not stop on release; wait for next tap
                            waitingForStopTap = true
                            return@setOnTouchListener true
                        }

                        // Released without recording: decide tap vs. long press by duration
                        val dt = SystemClock.uptimeMillis() - pressDownUptime
                        if (dt <= quickTapThresholdMs) {
                            imageCapture?.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            takePhoto()
                        }
                        // Cancel any pending hold start (if not yet fired)
                        cancelHoldRecordingStartIfPending()
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
                Log.d(TONEMAP_TAG, "=== onLongPress START ===")
                Log.d(TONEMAP_TAG, "Touch coordinates: x=${e.x}, y=${e.y}")
                Log.d(TONEMAP_TAG, "Preview dimensions: width=${previewView.width}, height=${previewView.height}")

                // CRITICAL: Validate coordinates to prevent NaN/Infinity
                if (!e.x.isFinite() || !e.y.isFinite()) {
                    Log.e(TONEMAP_TAG, "❌ REJECTED: Invalid coordinates (x=${e.x}, y=${e.y})")
                    return
                }

                if (previewView.width <= 0 || previewView.height <= 0) {
                    Log.e(TONEMAP_TAG, "❌ REJECTED: Invalid preview dimensions")
                    return
                }

                focusCoordinator?.onLongPress(e.x, e.y)
                Log.d(TONEMAP_TAG, "=== onLongPress END ===")
                // EV long-press path removed – use Compose EV panel
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val locked = focusCoordinator?.isLocked() == true
                return if (locked && !isZoomGesture) {
                    // EV scroll handling removed – use EV panel
                    Log.v(AEAF_TAG, "onScroll (EV path disabled)")
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

            // КРИТИЧНО: Обработать zoom ПЕРВЫМ и сохранить результат
            val scaleHandled = scaleGestureDetector.onTouchEvent(event)

            // Если zoom активен (два пальца), НЕ обрабатывать tap gesture для предотвращения конфликта
            val tapHandled = if (scaleGestureDetector.isInProgress || isZoomGesture) {
                Log.v(AEAF_TAG, "Пропускаем tap detection: zoom активен (isInProgress=${scaleGestureDetector.isInProgress}, isZoomGesture=$isZoomGesture)")
                false // Пропускаем tap detection во время zoom
            } else {
                tapGestureDetector.onTouchEvent(event)
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TONEMAP_TAG, "=== ACTION_DOWN START ===")
                    Log.d(TONEMAP_TAG, "Touch coordinates: x=${event.x}, y=${event.y}")
                    Log.d(TONEMAP_TAG, "Pointer count: ${event.pointerCount}")
                    Log.d(TONEMAP_TAG, "Preview dimensions: ${previewView.width}x${previewView.height}")

                    // CRITICAL: Validate coordinates to prevent NaN/Infinity from propagating
                    if (!event.x.isFinite() || !event.y.isFinite()) {
                        Log.e(TONEMAP_TAG, "❌ REJECTED: Invalid touch coordinates (x=${event.x}, y=${event.y})")
                        return@setOnTouchListener false
                    }

                    if (previewView.width <= 0 || previewView.height <= 0) {
                        Log.e(TONEMAP_TAG, "❌ REJECTED: Invalid preview dimensions")
                        return@setOnTouchListener false
                    }

                    userTouchActive = true
                    // НЕ устанавливаем isZoomGesture здесь - это делается в onScaleBegin

                    // Record touch for tap vs hold detection
                    previewDownX = event.x
                    previewDownY = event.y
                    previewDownUptime = SystemClock.uptimeMillis()
                    Log.d(TONEMAP_TAG, "Recorded touch position for tap detection")
                    Log.d(TONEMAP_TAG, "=== ACTION_DOWN END ===")

                    // Reset EV adjustment state
                    isEvAdjustmentActive = false
                    evInitialTouchX = event.x
                    evInitialTouchY = event.y
                    evTouchDownTime = SystemClock.uptimeMillis()

                    // EV long-press removed – no timer to cancel

                    // EV long-press logic removed – EV handled by Compose panel

                    showControlsOnInteraction()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Second finger down - this is a pinch gesture
                    // Cancel EV adjustment long press timer and deactivate EV mode
                    evLongPressRunnable?.let {
                        evOverlay.removeCallbacks(it)
                        Log.d(AEAF_TAG, "EV long press timer CANCELLED (pinch detected)")
                    }
                    isEvAdjustmentActive = false
                    // isZoomGesture теперь устанавливается в onScaleBegin, не нужно здесь
                    Log.d(AEAF_TAG, "touch POINTER_DOWN -> pinch detected (pointers=" + event.pointerCount + "), EV adjustment disabled")
                }
                MotionEvent.ACTION_MOVE -> {
                    // Skip processing if zoom is active
                    if (scaleGestureDetector.isInProgress || isZoomGesture) {
                        Log.v(AEAF_TAG, "touch ACTION_MOVE: skipped (zoom in progress)")
                        return@setOnTouchListener true
                    }

                    // Process EV adjustment only if it was activated by long press
                    if (isEvAdjustmentActive) {
                        val lastY = evInitialTouchY
                        if (lastY != null) {
                            // Calculate incremental delta from last position
                            val dy = event.y - lastY

                            // Apply smoothing and damping to reduce jitter
                            // Lower multiplier = smoother, less sensitive movement
                            val dampingFactor = 0.7f
                            val smoothedDy = dy * dampingFactor

                            // Clamp maximum change per frame to avoid jumps
                            val maxDeltaPerFrame = 8f  // pixels
                            val clampedDy = smoothedDy.coerceIn(-maxDeltaPerFrame, maxDeltaPerFrame)

                            // Only adjust if movement is significant enough
                            if (kotlin.math.abs(clampedDy) > 0.5f) {
                                // EV drag handling removed – use EV panel instead
                                evInitialTouchY = event.y
                                Log.v(AEAF_TAG, "EV adjustment path disabled")
                            }
                        }
                    } else {
                        // EV adjustment not active - just log for debugging
                        Log.v(AEAF_TAG, "touch ACTION_MOVE: x=" + event.x + ", y=" + event.y + " (EV not active)")
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Cancel - clean up all state
                    isZoomGesture = false
                    userTouchActive = false
                    previewDownX = null
                    previewDownY = null

                    // EV long-press removed – no EV state to clear
                    Log.d(AEAF_TAG, "touch ACTION_CANCEL")
                }
                MotionEvent.ACTION_UP -> {
                    Log.d(TONEMAP_TAG, "=== ACTION_UP START ===")
                    Log.d(TONEMAP_TAG, "Touch coordinates: x=${event.x}, y=${event.y}")
                    Log.d(TONEMAP_TAG, "isZoomGesture: $isZoomGesture")

                    // CRITICAL: Validate coordinates before processing
                    if (!event.x.isFinite() || !event.y.isFinite()) {
                        Log.e(TONEMAP_TAG, "❌ REJECTED: Invalid coordinates in ACTION_UP (x=${event.x}, y=${event.y})")
                        userTouchActive = false
                        return@setOnTouchListener false
                    }

                    // Лёгкое нажатие (короткий тап) – фокусируемся в точке отпускания
                    if (!isZoomGesture) {
                        val dt = SystemClock.uptimeMillis() - previewDownUptime
                        val dx = event.x - (previewDownX ?: event.x)
                        val dy = event.y - (previewDownY ?: event.y)
                        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
                        val moved = (dx * dx + dy * dy) > (slop * slop)
                        val isTap = dt <= quickTapThresholdMs && !moved

                        Log.d(TONEMAP_TAG, "Tap detection: dt=${dt}ms, dx=$dx, dy=$dy, slop=$slop, moved=$moved, isTap=$isTap")

                        if (isTap) {
                            Log.d(TONEMAP_TAG, "✅ Confirmed TAP - triggering focus/metering")
                            // Move and trigger focus only on confirmed TAP
                            startFocusMeteringAt(event.x, event.y, showIndicator = true)
                            Log.d(TONEMAP_TAG, "startFocusMeteringAt called with x=${event.x}, y=${event.y}")
                            // Ensure CameraX focus triggers at tap point (in addition to coordinator)
                            run {
                                val cam = camera
                                if (cam != null) {
                                    val width = previewView.width
                                    val height = previewView.height
                                    Log.d(TONEMAP_TAG, "CameraX focus: preview size=${width}x${height}")

                                    if (width > 0 && height > 0) {
                                        val factory = previewView.meteringPointFactory
                                        val afPoint = factory.createPoint(event.x, event.y)
                                        val aePoint = factory.createPoint(event.x, event.y)
                                        Log.d(TONEMAP_TAG, "Created metering points: AF=${afPoint}, AE=${aePoint}")

                                        val action = FocusMeteringAction.Builder(
                                            afPoint,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                        )
                                            .addPoint(aePoint, FocusMeteringAction.FLAG_AE)
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                            .build()

                                        if (cam.cameraInfo.isFocusMeteringSupported(action)) {
                                            Log.d(TONEMAP_TAG, "✅ Focus/metering is supported, triggering...")
                                            try { cam.cameraControl.cancelFocusAndMetering() } catch (_: Throwable) { }
                                            val future = cam.cameraControl.startFocusAndMetering(action)
                                            Log.d(TONEMAP_TAG, "startFocusAndMetering() called, waiting for result...")
                                            future.addListener({
                                                try {
                                                    val result = future.get()
                                                    val success = result.isFocusSuccessful
                                                    Log.d(TONEMAP_TAG, "✅ Focus/metering completed: success=$success")
                                                    runOnUiThread {
                                                        focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
                                                        focusIndicator.postDelayed(
                                                            hideFocusIndicatorRunnable,
                                                            1500  // Одинаковое время показа для успешного и неудачного фокуса
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TONEMAP_TAG, "❌ Focus/metering failed with exception", e)
                                                    runOnUiThread { focusIndicator.post(hideFocusIndicatorRunnable) }
                                                }
                                            }, ContextCompat.getMainExecutor(this))
                                        } else {
                                            focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
                                            Log.w(AEAF_TAG, "Focus/metering not supported at this point (fallback)")
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d(AEAF_TAG, "touch ACTION_UP: HOLD/DRAG -> skip refocus")
                        }
                        // Через 5 секунд возвращаемся в автофокус по центру

                        // EV overlay removed
                    }

                    // Clean up EV adjustment state on finger up
                    evLongPressRunnable?.let {
                        evOverlay.removeCallbacks(it)
                        Log.d(AEAF_TAG, "EV long press timer CANCELLED (ACTION_UP)")
                    }

                    if (isEvAdjustmentActive) {
                        Log.d(AEAF_TAG, "EV adjustment DEACTIVATED on ACTION_UP (no-op)")
                    }

                    isEvAdjustmentActive = false
                    evInitialTouchX = null
                    evInitialTouchY = null
                    userTouchActive = false

                    view.performClick()
                    Log.d(TONEMAP_TAG, "=== ACTION_UP END ===")
                }
            }
            // Возвращаем true если хотя бы один детектор обработал событие
            scaleHandled || tapHandled || true
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
        exposureButton = findViewById(R.id.exposureButton)
        xiaomiBrightnessButton = findViewById(R.id.xiaomiBrightnessButton)
        // No autofocus button in layout anymore
        // Legacy zoom sliders removed from layouts
        zoomCompose = findViewById(R.id.zoomCompose)
        focusIndicator = findViewById(R.id.focusIndicator)
        lockIcon = findViewById(R.id.lockIcon)
        evComposePanel = findViewById(R.id.evComposePanel)
        // Initialize dummy views for legacy EV references (overlay removed from layout)
        evOverlay = View(this)
        evSun = TextView(this)
        captureAnimationView = findViewById(R.id.captureAnimationView)
        // Hide legacy zoom sliders when using Compose zoom
        if (useComposeZoom) {
            // Legacy sliders are not present in layout anymore
        }

        // EV overlay removed – Compose panel configured below

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

        // No legacy EV overlay touch expansion

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
            // Compose EV panel content
            // Centered EV overlay; visibility controlled by exposureButton
            var evVisibleState: androidx.compose.runtime.MutableState<Boolean>? = null
            exposureButton.setOnClickListener {
                evVisibleState?.let { st -> st.value = !st.value }
            }
            evComposePanel.setContent {
                val ev by vm.evCompensation.collectAsState()
                val range by vm.evRange.collectAsState()
                val visible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                evVisibleState = visible
                val cfg = LocalConfiguration.current
                val isLandscape = cfg.orientation == AndroidConfiguration.ORIENTATION_LANDSCAPE
                val baseHeight = if (isLandscape) 340.dp else 220.dp
                val extraHeight = 190.dp // +3 cm
                val shrinkBy = 13.dp    // ~0.2 cm ≈ 12.6dp
                val desired = (baseHeight + extraHeight) - shrinkBy
                val maxAllowed = (cfg.screenHeightDp - 96).coerceAtLeast(160).dp // keep ~48dp top/bottom margins
                val sliderHeight = if (desired > maxAllowed) maxAllowed else desired
                MaterialTheme {
                    if (visible.value) {
                        // Background scrim with tap-to-dismiss
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(ComposeColor.Black.copy(alpha = 0.25f))
                                .pointerInput(Unit) { detectTapGestures(onTap = { visible.value = false }) }
                        )
                        // Foreground centered slider (panel shifted UP by ~1 cm in landscape)
                        Box(Modifier.fillMaxSize()) {
                            androidx.compose.ui.platform.LocalView.current // ensure composition
                            // Raise panel by ~0.5 cm in landscape (~19dp up)
                            val yOffset = if (isLandscape) (-19).dp else 0.dp
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .offset(y = yOffset)
                            ) {
                                com.example.b1void.ui.camera.ExposureControlV(
                                    currentEv = ev,
                                    evRange = range,
                                    onEvChanged = { value -> vm.setExposureCompensation(value) },
                                    onResetEv = { vm.resetExposureCompensation() },
                                    sliderHeight = sliderHeight
                                )
                            }
                        }
                    }
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

    private fun selectFixedStandardPreviewResolution(supportedSizes: List<Size>): Size {
        val preferredResolutions = listOf(
            Size(1920, 1080), // FHD
            Size(1280, 720)   // HD
        )

        // Ищем первое предпочтительное разрешение из списка, которое поддерживается устройством
        for (preferred in preferredResolutions) {
            if (supportedSizes.contains(preferred)) {
                Log.d(TAG, "FIXED PREVIEW: Found supported standard resolution: ${preferred.width}x${preferred.height}")
                return preferred
            }
        }

        // Если ни одно из предпочтительных не найдено, выбираем лучшее из доступных, но не выше 1080p
        val fallback = supportedSizes
            .filter { it.width <= 1920 && it.height <= 1080 && (abs(it.width.toFloat() / it.height.toFloat() - 1.0f) > 0.1f) }
            .maxByOrNull { it.width.toLong() * it.height }

        if (fallback != null) {
            Log.w(TAG, "FIXED PREVIEW: Preferred resolutions not found. Falling back to best available: ${fallback.width}x${fallback.height}")
            return fallback
        }

        // Абсолютно последний вариант, если ничего не подошло
        Log.e(TAG, "FIXED PREVIEW: No suitable resolution found. Defaulting to 640x480.")
        return Size(640, 480)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        // === XIAOMI BUG FIX #1: Proactively set compatible mode & add readiness checks ===
        // Ensure the view is attached to a window and has been laid out.
        if (!ViewCompat.isAttachedToWindow(previewView)) {
            previewView.post { startCamera() }
            return
        }
        
        if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            Log.d(TAG, "XIAOMI FIX: Proactively set PreviewView implementation mode to COMPATIBLE.")
        }

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
            Log.e("CAMERA_DEBUG", "║ selectedResolution (для фото): ${selectedResolution?.width}x${selectedResolution?.height}")
            Log.e("CAMERA_DEBUG", "║ Производитель устройства: $manufacturer")
            Log.e("CAMERA_DEBUG", "║ Модель устройства: $model")
            Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

            // Выбираем разрешение для ФОТО
            val captureResolution = selectBestResolution(
                selectedResolution,
                manufacturer,
                model
            )

            // === ИСПРАВЛЕНИЕ: Выбираем отдельное, безопасное разрешение для ПРЕВЬЮ ===
            val previewResolution = selectFixedStandardPreviewResolution(availablePreviewResolutions)

            Log.d(TAG, "=== Resolution Configuration (FIXED) ===")
            Log.d(TAG, "Capture (фото) resolution: ${captureResolution.width}x${captureResolution.height}")
            Log.d(TAG, "Preview (просмотр) resolution: ${previewResolution.width}x${previewResolution.height}")

            if (selectedResolution != captureResolution) {
                lifecycleScope.launch {
                    settingsManager.setResolution("${captureResolution.width}x${captureResolution.height}")
                }
            }
            selectedResolution = captureResolution

            // Derive rotation directly from PreviewView display to keep use cases aligned
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            currentTargetRotation = rotation

            val isLandscape = (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)

            val viewPort = ViewPort.Builder(
                android.util.Rational(previewResolution.width, previewResolution.height),
                rotation
            ).setScaleType(ViewPort.FIT).build()
            
            val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()

            val imageCaptureSelector = ResolutionSelector.Builder().apply {
                if (quirks.useHardcodedAspectRatio()) {
                    setAspectRatioStrategy(
                        AspectRatioStrategy(
                            androidx.camera.core.AspectRatio.RATIO_4_3,
                            AspectRatioStrategy.FALLBACK_RULE_AUTO
                        )
                    )
                }
                setResolutionStrategy(
                    ResolutionStrategy(
                        captureResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
            }.build()

            val previewSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        previewResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                ).build()

            val useCaseGroupBuilder = UseCaseGroup.Builder()

            val previewBuilder = Preview.Builder()
                .setTargetRotation(rotation)
                .setResolutionSelector(previewSelector)

            val preview = previewBuilder.build()
            previewUseCase = preview
            useCaseGroupBuilder.addUseCase(preview)

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .setTargetRotation(rotation)
                .setResolutionSelector(imageCaptureSelector)

            val newImageCapture = imageCaptureBuilder.build()
            imageCapture = newImageCapture
            useCaseGroupBuilder.addUseCase(newImageCapture)
            
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

                val videoCaptureBuilder = VideoCapture.Builder(recorder)
                    .setTargetRotation(rotation)

                try {
                    if (!quirks.hasEisIssues()) {
                        val videoExtender = Camera2Interop.Extender(videoCaptureBuilder)
                        videoExtender.setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
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

                try {
                    camera?.cameraInfo?.cameraState?.observe(this) { state ->
                        val err = state?.error
                        if (err != null) {
                            Log.e(TAG, "CameraState error detected: code=${err.code}, type=${state?.type}")

                            if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
                                when (err.code) {
                                    CameraState.ERROR_CAMERA_IN_USE,
                                    CameraState.ERROR_MAX_CAMERAS_IN_USE,
                                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR,
                                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                                        Log.e(TAG, "❌ XIAOMI: Recoverable camera error (${err.code}), attempting restart.")
                                        if (!isRebinding) {
                                            isRebinding = true
                                            previewView.postDelayed({ restartCameraSession(); isRebinding = false }, 1000)
                                        }
                                    }
                                    CameraState.ERROR_CAMERA_DISABLED -> {
                                        Log.e(TAG, "❌ XIAOMI: Camera disabled by device policy.")
                                        runOnUiThread {
                                            Toast.makeText(this, "Камера отключена системой.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    else -> {
                                         if (!isRebinding) {
                                            isRebinding = true
                                            previewView.postDelayed({ restartCameraSession(); isRebinding = false }, 1000)
                                        }
                                    }
                                }
                            } else {
                                if (!isRebinding) {
                                    isRebinding = true
                                    previewView.postDelayed({ restartCameraSession(); isRebinding = false }, 600)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to observe camera state", e)
                }

                preview.setSurfaceProvider(previewView.surfaceProvider)

                if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
                    startPreviewWatchdog()
                }

                previewView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }, 250)
                
                if (useComposeZoom) {
                    try {
                        val vm = ViewModelProvider(this)[ComposeCameraViewModel::class.java]
                        camera?.let { vm.bindCamera(it) }
                    } catch (_: Exception) { }
                }
                applyCamera2Defaults()
                setupCameraStateObserver()
                setupTorchObserver()
                setupXiaomiBrightnessBoost()
                
                camera?.let { cam ->
                    focusCoordinator = com.example.b1void.camera.focus.FocusProvider.create(
                        previewView = previewView,
                        camera = cam,
                        mainExecutor = ContextCompat.getMainExecutor(this),
                        callbacks = object : com.example.b1void.camera.focus.FocusCoordinator.Callbacks {
                            override fun showIndicator(x: Float, y: Float) { showFocusIndicator(x, y); focusLastX = x; focusLastY = y }
                            override fun hideIndicator() { focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500) }
                            override fun onFocusResult(success: Boolean) { 
                                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
                                if (success) previewView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            override fun onLockChanged(locked: Boolean) {
                                if (!locked) focusIndicator.post(hideFocusIndicatorRunnable)
                                toggleLockIcon(locked)
                            }
                        },
                        config = com.example.b1void.camera.focus.FocusCoordinator.Config(
                            tapAutoCancelSeconds = 0,
                            showIndicatorOnCenter = false,
                            focusTimeoutMs = 3000L,
                            cafReturnDelayMs = 1800L
                        )
                    )
                }
                focusAtCenter()

                if (shouldRestoreTorchState) {
                    lifecycleScope.launch {
                        delay(100)
                        restoreTorchState()
                        shouldRestoreTorchState = false
                    }
                }
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
        Log.d(TONEMAP_TAG, "=== applyCamera2Defaults START ===")
        val cam = camera ?: run {
            Log.e(TONEMAP_TAG, "applyCamera2Defaults: camera is null, aborting")
            return
        }

        try {
            // Log current exposure state BEFORE applying defaults
            val exposureState = cam.cameraInfo.exposureState
            Log.d(TONEMAP_TAG, "BEFORE defaults - EV index: ${exposureState.exposureCompensationIndex}, " +
                    "step: ${exposureState.exposureCompensationStep}, " +
                    "range: [${exposureState.exposureCompensationRange.lower}..${exposureState.exposureCompensationRange.upper}]")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)
            val camera2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val characteristics = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )

            // Получаем quirks для текущего производителя
            val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()
            Log.d(TONEMAP_TAG, "Device quirks: EV boost=${quirks.getEvCompensationBoost()}, ISO=${quirks.getPreferredIsoSensitivity()}")

            // Проверяем доступность базовых возможностей
            val hasManualSensor = characteristics?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ) == true

            val hasManualPostProcessing = characteristics?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
            ) == true

            // CRITICAL: Do NOT call clearCaptureRequestOptions() as it temporarily leaves camera
            // in undefined state with gain=0, exposureTime=0 which causes NaN/Infinity in tonemap
            // processing and crashes the camera HAL. Instead, just set new options.
            // camera2Control.clearCaptureRequestOptions()  // REMOVED - causes tonemap crash

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

                Log.d(TONEMAP_TAG, "Applying Camera2 defaults (without clearing to avoid gain=0 crash)")
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
            val options = optionsBuilder.build()
            Log.d(TONEMAP_TAG, "Applying Camera2 options (count: ${options.listOptions().size})")
            camera2Control.setCaptureRequestOptions(options)

            // Log exposure state AFTER applying defaults
            val exposureStateAfter = cam.cameraInfo.exposureState
            Log.d(TONEMAP_TAG, "AFTER defaults - EV index: ${exposureStateAfter.exposureCompensationIndex}, " +
                    "step: ${exposureStateAfter.exposureCompensationStep}")
            Log.d(TONEMAP_TAG, "=== applyCamera2Defaults END ===")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Camera2 defaults, using CameraX defaults", e)
            e.printStackTrace()
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

    /**
     * Настройка кнопки управления яркостью для Xiaomi/Redmi устройств
     * Показывает кнопку только на проблемных устройствах и позволяет вручную увеличить яркость
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun setupXiaomiBrightnessBoost() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        // Показываем кнопку только для Xiaomi/Redmi/Poco устройств
        if (manufacturer !in listOf("xiaomi", "redmi", "poco")) {
            xiaomiBrightnessButton?.visibility = View.GONE
            return
        }

        val cam = camera ?: run {
            Log.w(TAG, "setupXiaomiBrightnessBoost: camera is null")
            return
        }

        // Показываем кнопку для Xiaomi устройств
        xiaomiBrightnessButton?.visibility = View.VISIBLE

        xiaomiBrightnessButton?.setOnClickListener {
            try {
                val exposureState = cam.cameraInfo.exposureState
                val currentEvIndex = exposureState.exposureCompensationIndex
                val maxEv = exposureState.exposureCompensationRange.upper
                val step = exposureState.exposureCompensationStep.toFloat()

                if (currentEvIndex < maxEv) {
                    // Увеличиваем EV на 2 шага (примерно +0.67 EV)
                    val newEvIndex = (currentEvIndex + 2).coerceAtMost(maxEv)

                    cam.cameraControl.setExposureCompensationIndex(newEvIndex).addListener({
                        val newEvValue = newEvIndex * step
                        Toast.makeText(
                            this,
                            "Яркость увеличена до +${String.format("%.2f", newEvValue)} EV",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.d("XIAOMI_BRIGHTNESS", "Manual brightness boost: EV index $currentEvIndex -> $newEvIndex (+${String.format("%.2f", newEvValue)} EV)")

                        // Подсвечиваем кнопку, если применен boost
                        if (newEvIndex > 0) {
                            xiaomiBrightnessButton?.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    // Достигнут максимум - сбрасываем EV до автоматического значения
                    val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()
                    val autoEvBoost = quirks.getEvCompensationBoost()
                    val autoEvIndex = (autoEvBoost / step).toInt().coerceIn(
                        exposureState.exposureCompensationRange.lower,
                        exposureState.exposureCompensationRange.upper
                    )

                    cam.cameraControl.setExposureCompensationIndex(autoEvIndex).addListener({
                        Toast.makeText(
                            this,
                            "Яркость сброшена до авто (+${String.format("%.2f", autoEvBoost)} EV)",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.d("XIAOMI_BRIGHTNESS", "Brightness reset to auto: EV index $autoEvIndex (+${String.format("%.2f", autoEvBoost)} EV)")

                        // Убираем подсветку кнопки при автоматическом значении
                        if (autoEvIndex <= (autoEvBoost / step).toInt()) {
                            xiaomiBrightnessButton?.clearColorFilter()
                        }
                    }, ContextCompat.getMainExecutor(this))
                }
            } catch (e: Exception) {
                Log.e("XIAOMI_BRIGHTNESS", "Failed to adjust brightness", e)
                Toast.makeText(this, "Ошибка настройки яркости", Toast.LENGTH_SHORT).show()
            }
        }

        // Установим начальную подсветку кнопки, если уже применен EV boost
        try {
            val exposureState = cam.cameraInfo.exposureState
            val currentEvIndex = exposureState.exposureCompensationIndex
            if (currentEvIndex > 0) {
                xiaomiBrightnessButton?.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
            }
        } catch (e: Exception) {
            Log.w("XIAOMI_BRIGHTNESS", "Failed to check initial EV state", e)
        }

        Log.i("XIAOMI_BRIGHTNESS", "Brightness boost button configured for $manufacturer $model")
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
                focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
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
                            focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
                        } else {
                            autofocusButton?.setColorFilter(ContextCompat.getColor(this@CameraActivity, android.R.color.holo_red_light))
                            focusIndicator.postDelayed(hideFocusIndicatorRunnable, 1500)
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

    private fun ensureEvController() { /* removed */ }

    /**
     * Check if touch coordinates are within the zoom control exclusion zone.
     * Returns true if the touch should NOT activate EV adjustment (too close to zoom slider).
     *
     * @param touchX Touch X coordinate relative to previewView
     * @param touchY Touch Y coordinate relative to previewView
     * @return true if touch is in exclusion zone, false otherwise
     */
    private fun isInZoomExclusionZone(touchX: Float, touchY: Float): Boolean {
        return try {
            val previewLoc = IntArray(2)
            val zoomLoc = IntArray(2)
            previewView.getLocationOnScreen(previewLoc)
            zoomCompose.getLocationOnScreen(zoomLoc)

            // Convert touch coordinates to screen coordinates
            val screenX = previewLoc[0] + touchX.toInt()
            val screenY = previewLoc[1] + touchY.toInt()

            // Calculate exclusion zone with margin
            val marginPx = (ZOOM_EXCLUSION_MARGIN * resources.displayMetrics.density).toInt()
            val zoneLeft = zoomLoc[0] - marginPx
            val zoneRight = zoomLoc[0] + zoomCompose.width + marginPx
            val zoneTop = zoomLoc[1] - marginPx
            val zoneBottom = zoomLoc[1] + zoomCompose.height + marginPx

            val isInZone = screenX >= zoneLeft && screenX <= zoneRight &&
                          screenY >= zoneTop && screenY <= zoneBottom

            if (isInZone) {
                Log.d(AEAF_TAG, "Touch in zoom exclusion zone: screenX=$screenX, screenY=$screenY")
            }

            isInZone
        } catch (e: Throwable) {
            Log.w(AEAF_TAG, "Failed to check zoom exclusion zone", e)
            false  // If check fails, allow EV adjustment
        }
    }

    private fun scheduleHideEvOverlay() { /* removed */ }

    private fun showFocusIndicator(x: Float, y: Float) {
        if (!::focusIndicator.isInitialized) return
        if (focusIndicator.parent == null || previewView.width <= 0 || previewView.height <= 0) {
            previewView.post { showFocusIndicator(x, y) }
            return
        }
        val indicatorWidth = focusIndicator.width.takeIf { it > 0 }
            ?: focusIndicator.layoutParams?.width?.takeIf { it > 0 }
            ?: 0
        val indicatorHeight = focusIndicator.height.takeIf { it > 0 }
            ?: focusIndicator.layoutParams?.height?.takeIf { it > 0 }
            ?: 0

        // Calculate a physical gap of 0.3 cm in pixels using device xdpi
        val gapPx = (0.3f * (resources.displayMetrics.xdpi / 2.54f))
        // evOverlay is a dummy view without layoutParams - always use 0
        val evBarWidthPx = evOverlay.width.takeIf { it > 0 }?.toFloat() ?: 0f

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
            // Умеренный начальный размер вместо слишком большого 1.5f
            scaleX = 1.2f
            scaleY = 1.2f
            translationX = clampedX
            translationY = clampedY
            animate().cancel()

            // Enhanced focus animation with scale and fade
            animate()
                .alpha(1f)
                .scaleX(0.9f)
                .scaleY(0.9f)
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
            // Ограничиваем X только слева (не выходим за край экрана)
            translationX = (clampedX - gapPx - evBarWidthPx).coerceAtLeast(0f)

            // Для Y используем правильное ограничение с учетом размеров экрана
            // Центрируем относительно квадрата фокуса
            val centeredEvY = clampedY + indicatorHeight / 2f - height / 2f
            // Ограничиваем сверху и снизу, чтобы evOverlay всегда был виден
            val minEvY = 0f
            val maxEvY = (parentBottom - parentTop - height).coerceAtLeast(0f)
            translationY = centeredEvY.coerceIn(minEvY, maxEvY)

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

    private fun logEvOverlayBounds(label: String) { /* removed */ }

    private fun showEvUi() { /* removed */ }

    private fun hideEvUi() { /* removed */ }

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

            // Используем плавную анимацию вместо мгновенного перемещения
            // Длительность ~200ms соответствует скорости изменения экспозиции на камере
            evSun.animate()
                .translationY(knobY - evSun.height / 2f)
                .translationX(centerX)
                .setDuration(200) // Синхронизировано со скоростью изменения камеры
                .setInterpolator(DecelerateInterpolator())
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
        // Avoid PERFORMANCE mode on Xiaomi/Redmi due to black preview/brightness quirks
        val maker = android.os.Build.MANUFACTURER.lowercase()
        previewView.implementationMode = if (maker.contains("xiaomi") || maker.contains("redmi"))
            PreviewView.ImplementationMode.COMPATIBLE else PreviewView.ImplementationMode.PERFORMANCE
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
        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "❌ ImageCapture is null, cannot take photo")
            Toast.makeText(this, "Камера не инициализирована", Toast.LENGTH_SHORT).show()
            return
        }
        val currentCamera = this.camera ?: run {
            Log.e(TAG, "❌ Camera is null, cannot take photo")
            Toast.makeText(this, "Камера не инициализирована", Toast.LENGTH_SHORT).show()
            return
        }

        // === XIAOMI SAFETY CHECK: Validate camera state ===
        if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            val cameraState = currentCamera.cameraInfo.cameraState.value
            if (cameraState?.type != CameraState.Type.OPEN) {
                Log.e(TAG, "❌ XIAOMI: Camera not in OPEN state: ${cameraState?.type}")
                Toast.makeText(this, "Камера не готова. Повторите попытку.", Toast.LENGTH_SHORT).show()
                return
            }

            // Validate zoom state (logs show invalid zoom can crash camera HAL)
            val zoomState = currentCamera.cameraInfo.zoomState.value
            if (zoomState == null || zoomState.zoomRatio.isNaN() || !zoomState.zoomRatio.isFinite()) {
                Log.e(TAG, "❌ XIAOMI: Invalid zoom state: $zoomState, resetting to 1.0")
                currentCamera.cameraControl.setZoomRatio(1.0f)
                Toast.makeText(this, "Сброс зума, повторите съёмку", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d(TAG, "✅ XIAOMI: Pre-capture validation passed (state=${cameraState.type}, zoom=${zoomState.zoomRatio})")
        }

        Log.e("CAMERA_DEBUG", "╔════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ НАЧАЛО СЪЕМКИ ФОТО")
        Log.e("CAMERA_DEBUG", "╠════════════════════════════════════════════════")
        Log.e("CAMERA_DEBUG", "║ ImageCapture.targetRotation: ${imageCapture.targetRotation}")
        Log.e("CAMERA_DEBUG", "║ ImageCapture.flashMode: ${imageCapture.flashMode}")
        Log.e("CAMERA_DEBUG", "╚════════════════════════════════════════════════")

        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: ""
        val photoFile = File(savePath, "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // === XIAOMI BUG FIX #4: Apply zoom before capture ===
        val currentZoomRatio = currentCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1.0f
        val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()

        if (quirks.requiresZoomStabilizationDelay() && currentZoomRatio > 1.0f) {
            // Re-apply zoom to ensure it's applied to capture request on Xiaomi
            Log.d(TAG, "XIAOMI FIX: Re-applying zoom ($currentZoomRatio) before capture")
            currentCamera.cameraControl.setZoomRatio(currentZoomRatio)

            // Wait for zoom stabilization before capture
            val delayMs = quirks.getZoomStabilizationDelayMs()
            Handler(Looper.getMainLooper()).postDelayed({
                performCapture(imageCapture, outputOptions, photoFile, currentZoomRatio)
            }, delayMs)
        } else {
            // No delay needed for non-Xiaomi devices
            performCapture(imageCapture, outputOptions, photoFile, currentZoomRatio)
        }
    }

    /**
     * XIAOMI BUG FIX #4: Separate method for actual capture
     */
    private fun performCapture(
        imageCapture: ImageCapture,
        outputOptions: ImageCapture.OutputFileOptions,
        photoFile: File,
        zoomRatio: Float
    ) {
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lastSavedFile = photoFile
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)

                    // Update UI immediately for perceived speed; heavy processing continues in background
                    runOnUiThread {
                        updateThumbnail(savedUri)
                        playCaptureAnimation(savedUri)
                    }

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

                        // === XIAOMI BUG FIX #2: Post-capture aspect ratio correction ===
                        correctAspectRatioIfNeeded(photoFile.absolutePath)

                        // === XIAOMI BUG FIX #4: Post-capture zoom crop (fallback) ===
                        applyZoomPostCapture(photoFile.absolutePath, zoomRatio)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding timestamp", e)
                        Log.e("CAMERA_DEBUG", "ОШИБКА при обработке: ${e.message}")
                    }

                    // UI already updated above
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

        // === XIAOMI BUG FIX: Refactored Lifecycle Handling ===
        // Always release camera onPause to ensure a clean state.
        // For Xiaomi, use a delayed release to prevent race conditions with the camera HAL.
        gracefulCleanupJob?.cancel() // Cancel any previous cleanup job
        if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            Log.d(TAG, "XIAOMI FIX: Scheduling graceful camera cleanup in ${GRACEFUL_CLEANUP_DELAY_MS}ms")
            gracefulCleanupJob = lifecycleScope.launch {
                delay(GRACEFUL_CLEANUP_DELAY_MS)
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    Log.d(TAG, "XIAOMI FIX: Activity resumed before cleanup, skipping release")
                } else {
                    Log.d(TAG, "XIAOMI FIX: Executing delayed camera cleanup")
                    releaseCamera()
                }
            }
        } else {
            // For other devices, release immediately.
            releaseCamera()
        }
        cancelPreviewWatchdog()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // === XIAOMI BUG FIX: Refactored Lifecycle Handling ===
        // Always restart the camera onResume as it's always released onPause.
        gracefulCleanupJob?.cancel()
        Log.d(TAG, "onResume: Restarting camera.")
        // Post the camera start to allow the view to be measured.
        previewView.post { startCamera() }

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
        gracefulCleanupJob?.cancel()  // === XIAOMI FIX: Cancel pending cleanup ===
        mainHandler.removeCallbacksAndMessages(null)
        orientationEventListener?.disable()
        orientationEventListener = null
        focusIndicator.removeCallbacks(hideFocusIndicatorRunnable)
        captureAnimationView.animate().cancel()

        // === XIAOMI BUG FIX #5: Cancel preview watchdog ===
        cancelPreviewWatchdog()

        // Release camera resources
        releaseCamera()

        cameraExecutor.shutdown()
    }

    /**
     * XIAOMI BUG FIX #5: Release camera resources to prevent memory leaks
     */
    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageCapture = null
            previewUseCase = null
            Log.d(TAG, "Camera resources released.")

            // Force GC on Xiaomi devices to reclaim memory
            if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
                System.gc()
                Log.d(TAG, "XIAOMI FIX: Forced garbage collection after camera release")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
    }

    /**
     * XIAOMI BUG FIX #5: Start preview watchdog to detect black screen
     */
    private fun startPreviewWatchdog() {
        previewStartTime = System.currentTimeMillis()
        previewTimeoutHandler.removeCallbacks(previewTimeoutRunnable)
        previewTimeoutHandler.postDelayed(previewTimeoutRunnable, PREVIEW_TIMEOUT_MS)
        Log.d(TAG, "XIAOMI FIX: Preview watchdog started")
    }

    /**
     * XIAOMI BUG FIX #5: Cancel preview watchdog (called when preview successfully starts)
     */
    private fun cancelPreviewWatchdog() {
        previewTimeoutHandler.removeCallbacks(previewTimeoutRunnable)
        Log.d(TAG, "XIAOMI FIX: Preview watchdog cancelled (preview OK)")
    }

    /**
     * XIAOMI BUG FIX #5: Restart camera (used for black screen recovery)
     */
    private fun restartCamera() {
        Log.w(TAG, "XIAOMI FIX: Restarting camera")
        releaseCamera()
        Handler(Looper.getMainLooper()).postDelayed({
            startCamera()
        }, 100)
    }

    /**
     * XIAOMI BUG FIX #2: Post-capture aspect ratio correction
     * Проверяет и корректирует aspect ratio сохраненного изображения, если оно отличается от 4:3
     */
    private fun correctAspectRatioIfNeeded(imagePath: String) {
        // Применяем только для Xiaomi устройств
        if (!com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            return
        }

        try {
            // Загружаем информацию о размере изображения
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            val width = options.outWidth
            val height = options.outHeight
            val ratio = width.toFloat() / height.toFloat()
            val targetRatio = 4f / 3f  // 1.333...

            Log.d(TAG, "XIAOMI FIX: Checking aspect ratio - ${width}x${height}, ratio=$ratio, target=$targetRatio")

            // Если ratio отличается от 4:3 более чем на 10%, корректируем
            if (kotlin.math.abs(ratio - targetRatio) > 0.1f) {
                Log.w(TAG, "XIAOMI FIX: Incorrect aspect ratio detected! Correcting image...")
                correctImageAspectRatio(imagePath, 4, 3)
            } else {
                Log.d(TAG, "XIAOMI FIX: Aspect ratio OK, no correction needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "XIAOMI FIX: Error checking aspect ratio", e)
        }
    }

    /**
     * Корректирует aspect ratio изображения, обрезая его до заданного соотношения
     */
    private fun correctImageAspectRatio(imagePath: String, aspectWidth: Int, aspectHeight: Int) {
        try {
            // Загружаем полное изображение
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e(TAG, "XIAOMI FIX: Failed to load bitmap for correction")
                return
            }

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            val targetRatio = aspectWidth.toFloat() / aspectHeight.toFloat()
            val currentRatio = originalWidth.toFloat() / originalHeight.toFloat()

            // Вычисляем новые размеры с сохранением максимальной площади
            val newWidth: Int
            val newHeight: Int

            if (currentRatio > targetRatio) {
                // Изображение слишком широкое - обрезаем по ширине
                newHeight = originalHeight
                newWidth = (originalHeight * targetRatio).toInt()
            } else {
                // Изображение слишком высокое - обрезаем по высоте
                newWidth = originalWidth
                newHeight = (originalWidth / targetRatio).toInt()
            }

            // Вычисляем offset для центрального crop
            val offsetX = (originalWidth - newWidth) / 2
            val offsetY = (originalHeight - newHeight) / 2

            Log.d(TAG, "XIAOMI FIX: Cropping image from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight}")

            // Создаем обрезанное изображение
            val croppedBitmap = Bitmap.createBitmap(bitmap, offsetX, offsetY, newWidth, newHeight)
            bitmap.recycle()

            // Сохраняем обрезанное изображение обратно
            saveBitmapToFile(croppedBitmap, File(imagePath))
            croppedBitmap.recycle()

            Log.d(TAG, "XIAOMI FIX: Aspect ratio correction completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "XIAOMI FIX: Error correcting aspect ratio", e)
        }
    }

    /**
     * XIAOMI BUG FIX #4: Post-capture zoom crop (fallback method)
     * Applies zoom by cropping the captured image if zoom was not applied during capture
     */
    private fun applyZoomPostCapture(imagePath: String, zoomRatio: Float) {
        // Apply only for Xiaomi devices and when zoom > 1.0
        if (!com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            return
        }

        if (zoomRatio <= 1.0f) {
            Log.d(TAG, "XIAOMI FIX: No zoom to apply (ratio=$zoomRatio)")
            return
        }

        // Only apply if manual zoom workaround is enabled
        val quirks = com.example.b1void.utils.ManufacturerCompatibility.getCameraQuirks()
        if (!quirks.useManualZoom()) {
            return
        }

        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e(TAG, "XIAOMI FIX: Failed to load bitmap for zoom crop")
                return
            }

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Calculate crop rectangle based on zoom ratio
            val cropWidth = (originalWidth / zoomRatio).toInt()
            val cropHeight = (originalHeight / zoomRatio).toInt()
            val x = (originalWidth - cropWidth) / 2
            val y = (originalHeight - cropHeight) / 2

            Log.d(TAG, "XIAOMI FIX: Applying zoom $zoomRatio via crop - ${originalWidth}x${originalHeight} -> ${cropWidth}x${cropHeight}")

            // Create cropped bitmap (center crop)
            val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
            bitmap.recycle()

            // Scale back to original resolution for consistent output size
            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, originalWidth, originalHeight, true)
            croppedBitmap.recycle()

            // Save the zoomed image
            saveBitmapToFile(scaledBitmap, File(imagePath))
            scaledBitmap.recycle()

            Log.d(TAG, "XIAOMI FIX: Zoom crop applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "XIAOMI FIX: Error applying zoom post-capture", e)
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val AEAF_TAG = "AEAF_EV"
        private const val TONEMAP_TAG = "TONEMAP_DEBUG"  // Unified tag for diagnostic logs
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        const val EXTRA_SAVE_PATH = "extra_save_path"
        private val DEFAULT_PHOTO_RESOLUTION = Size(960, 720)
        private const val ASPECT_RATIO_TOLERANCE = 0.02f
    }
}








