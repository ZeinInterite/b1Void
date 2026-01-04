package com.example.b1void.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import com.example.b1void.camera.ui.camera.FocusOverlayView
import com.example.b1void.camera.ui.camera.FocusOverlayView.Mode
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.Alignment
import android.view.HapticFeedbackConstants
import java.util.concurrent.TimeUnit

private const val TAG = "TONEMAP_DEBUG"

/**
 * CameraScreen shows CameraX Preview and the iPhone-like ZoomControl, with pinch/double-tap hooks.
 * This is a sample screen for integration and tests.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    leftHanded: Boolean = false,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // Create ViewModel with context for settings management
    val vm: CameraViewModel = hiltViewModel()

    // Ask for camera and audio just in case, gracefully no-op if granted.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { /* ignore */ }
    )

    LaunchedEffect(Unit) {
        val missing = arrayOf(
            Manifest.permission.CAMERA
        ).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // CameraX setup
    var camera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            val isXiaomi = com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Xiaomi/Redmi: use COMPATIBLE (TextureView) to avoid black preview / HAL quirks
            implementationMode = if (isXiaomi) {
                PreviewView.ImplementationMode.COMPATIBLE
            } else {
                PreviewView.ImplementationMode.PERFORMANCE
            }
            // FIT_CENTER shows full image without cropping, matching what will be captured
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    var focusOverlayRef by remember { mutableStateOf<FocusOverlayView?>(null) }
    // Attach ring overlay directly to PreviewView overlay to ensure it renders above camera surface
    DisposableEffect(previewView) {
        val sizePx = (72 * context.resources.displayMetrics.density).toInt()
        val overlay = FocusOverlayView(context).apply {
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            visibility = android.view.View.GONE
        }
        previewView.overlay.add(overlay)
        focusOverlayRef = overlay
        onDispose {
            previewView.overlay.remove(overlay)
            focusOverlayRef = null
        }
    }

    LaunchedEffect(cameraSelector) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        try {
            provider.unbindAll()

            val isXiaomi = com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()

            val cameraPreview = Preview.Builder()
                .build()

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // UseCaseGroup: on Xiaomi we intentionally skip ViewPort to avoid black preview bug
            val useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(cameraPreview)
                .addUseCase(imageCapture)

            if (!isXiaomi) {
                // Non-Xiaomi: keep shared ViewPort so preview matches capture crop
                val viewPort = ViewPort.Builder(
                    android.util.Rational(4, 3), // Use 4:3 aspect ratio (sensor native)
                    previewView.display.rotation
                ).build()
                useCaseGroupBuilder.setViewPort(viewPort)
            } else {
                Log.d("CameraScreen", "XIAOMI WORKAROUND: Skipping ViewPort to avoid black preview")
            }

            val useCaseGroup = useCaseGroupBuilder.build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                useCaseGroup
            )

            // Set surface provider AFTER binding to ensure proper initialization
            cameraPreview.setSurfaceProvider(previewView.surfaceProvider)

            camera?.let {
                vm.bindCamera(it)
                // ViewModel will restore last user zoom ratio automatically

                // DEBUG: Log configuration to verify synchronization
                Log.d("CameraScreen", "=== CameraX Configuration ===")
                Log.d("CameraScreen", "ViewPort: 4:3 aspect ratio")
                Log.d("CameraScreen", "PreviewView ScaleType: ${previewView.scaleType}")
                cameraPreview.resolutionInfo?.let { resInfo ->
                    val res = resInfo.resolution
                    Log.d("CameraScreen", "Preview resolved: ${res.width}x${res.height}, aspect: ${res.width.toFloat() / res.height}")
                }
                imageCapture.resolutionInfo?.let { resInfo ->
                    val res = resInfo.resolution
                    Log.d("CameraScreen", "ImageCapture resolved: ${res.width}x${res.height}, aspect: ${res.width.toFloat() / res.height}")
                }
                it.cameraInfo.zoomState.value?.let { zoom ->
                    Log.d("CameraScreen", "Zoom: ${zoom.zoomRatio} (min: ${zoom.minZoomRatio}, max: ${zoom.maxZoomRatio})")
                }
            }
        } catch (t: Throwable) {
            Log.e("CameraScreen", "Binding failed", t)
        }
    }

    val zoom by vm.zoomRatio.collectAsState()
    val minZoom by vm.minZoomRatio.collectAsState()
    val maxZoom by vm.maxZoomRatio.collectAsState()
    val presets by vm.availablePresets.collectAsState()

    // Exposure compensation state
    val evValue by vm.evCompensation.collectAsState()
    val evRange by vm.evRange.collectAsState()

    // Preview gestures: pinch to zoom and double tap to cycle presets.

    // Track composable size to map tap -> PreviewView coordinates safely
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun tapToFocusAt(offset: Offset) {
        // CRITICAL: Validate offset to prevent NaN/Infinity from reaching camera HAL
        if (!offset.x.isFinite() || !offset.y.isFinite()) {
            android.util.Log.e(TAG, "Invalid tap offset: ($offset), ignoring")
            return
        }

        val cam = camera ?: return
        try { cam.cameraControl.cancelFocusAndMetering() } catch (_: Throwable) {}

        val compW = previewSize.width.coerceAtLeast(1)
        val compH = previewSize.height.coerceAtLeast(1)

        // Additional safety: ensure preview dimensions are valid
        if (previewView.width <= 0 || previewView.height <= 0) {
            android.util.Log.e(TAG, "Invalid previewView dimensions: ${previewView.width}x${previewView.height}")
            return
        }

        val pxX = (offset.x * previewView.width / compW)
        val pxY = (offset.y * previewView.height / compH)

        // Validate calculated coordinates
        if (!pxX.isFinite() || !pxY.isFinite()) {
            android.util.Log.e(TAG, "Coordinate calculation produced invalid values: ($pxX, $pxY)")
            return
        }
        val factory = previewView.meteringPointFactory
        val af = factory.createPoint(pxX, pxY, 0.15f)
        val ae = factory.createPoint(pxX, pxY, 0.35f)
        val awb = factory.createPoint(pxX, pxY, 0.25f)
        val action = FocusMeteringAction.Builder(af, FocusMeteringAction.FLAG_AF)
            .addPoint(ae, FocusMeteringAction.FLAG_AE)
            .addPoint(awb, FocusMeteringAction.FLAG_AWB)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        focusOverlayRef?.apply {
            visibility = android.view.View.VISIBLE
            showFocusAt(pxX, pxY, Mode.Focusing)
        }
        if (!cam.cameraInfo.isFocusMeteringSupported(action)) {
            focusOverlayRef?.apply {
                showFocusAt(pxX, pxY, Mode.Fail)
                postDelayed({ visibility = android.view.View.GONE }, 300)
            }
            return
        }
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            try {
                val res = future.get()
                if (res.isFocusSuccessful) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                focusOverlayRef?.apply {
                    showFocusAt(pxX, pxY, if (res.isFocusSuccessful) Mode.Success else Mode.Fail)
                    postDelayed({ visibility = android.view.View.GONE }, if (res.isFocusSuccessful) 600 else 300)
                }
            } catch (_: Throwable) {}
        }, ContextCompat.getMainExecutor(context))
    }

    // Long-press EV adjust state
    var isAdjustingEv by remember { mutableStateOf(false) }
    var evStart by remember { mutableStateOf(0f) }

    val previewGestures = Modifier
        .pointerInput(evRange) {
            // Drag after long press: show focus ring + EV slider and adjust EV with vertical swipe
            detectDragGesturesAfterLongPress(
                onDragStart = onDragStart@ { offset ->
                    // CRITICAL: Validate offset to prevent NaN/Infinity
                    if (!offset.x.isFinite() || !offset.y.isFinite()) {
                        android.util.Log.e(TAG, "Invalid drag offset: $offset")
                        return@onDragStart
                    }

                    val compW = previewSize.width.coerceAtLeast(1)
                    val compH = previewSize.height.coerceAtLeast(1)

                    if (previewView.width <= 0 || previewView.height <= 0) {
                        android.util.Log.e(TAG, "Invalid preview dimensions")
                        return@onDragStart
                    }

                    val pxX = (offset.x * previewView.width / compW)
                    val pxY = (offset.y * previewView.height / compH)

                    if (!pxX.isFinite() || !pxY.isFinite()) {
                        android.util.Log.e(TAG, "Invalid calculated coordinates: ($pxX, $pxY)")
                        return@onDragStart
                    }

                    isAdjustingEv = true
                    evStart = evValue

                    // Focus lock visualization and show EV slider near ring
                    focusOverlayRef?.apply {
                        visibility = android.view.View.VISIBLE
                        showFocusAt(pxX, pxY, Mode.Locked)
                        evMin = evRange.start
                        evMax = evRange.endInclusive
                        showEvSlider(pxX, pxY, evStart)
                    }
                    // Haptic feedback for long press
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                },
                onDragEnd = {
                    isAdjustingEv = false
                    // Hide EV slider after a short delay
                    focusOverlayRef?.postDelayed({ focusOverlayRef?.hideEvSlider() }, 1200)
                },
                onDragCancel = {
                    isAdjustingEv = false
                    focusOverlayRef?.hideEvSlider()
                }
            ) { change, dragAmount ->
                change.consume()
                if (!isAdjustingEv) return@detectDragGesturesAfterLongPress

                // CRITICAL: Validate dragAmount to prevent NaN/Infinity from gesture bugs
                if (!dragAmount.y.isFinite()) {
                    android.util.Log.e(TAG, "Invalid dragAmount.y: ${dragAmount.y}, ignoring gesture")
                    return@detectDragGesturesAfterLongPress
                }

                // Positive drag up -> increase EV, down -> decrease EV
                val sensitivityPxPerEv = 150f
                val rawEv = evStart - dragAmount.y / sensitivityPxPerEv

                // Validate calculated EV before clamping
                if (!rawEv.isFinite()) {
                    android.util.Log.e(TAG, "EV calculation invalid: $rawEv from evStart=$evStart, drag=${dragAmount.y}")
                    return@detectDragGesturesAfterLongPress
                }

                val newEv = rawEv.coerceIn(evRange)
                vm.setExposureCompensation(newEv)
                focusOverlayRef?.apply {
                    this.evValue = newEv
                    invalidate()
                }
            }
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, _, zoomChange, _ ->
                if (zoomChange.isFinite()) vm.onPinch(zoomChange)
            }
        }
        .pointerInteropFilter { ev ->
            // Consume multi-touch so it doesn't bubble to AndroidView/PreviewView
            if (ev.pointerCount >= 2) return@pointerInteropFilter true
            false
        }
        .pointerInput(Unit) {
            // Tap gestures: tap for focus, double-tap for zoom, long-press shows EV slider
            detectTapGestures(
                onTap = { offset ->
                    focusOverlayRef?.apply {
                        visibility = android.view.View.VISIBLE
                        showFocusAt(offset.x, offset.y, Mode.Focusing)
                    }
                    tapToFocusAt(offset)
                },
                onDoubleTap = { vm.onDoubleTap() },
                onLongPress = onLongPress@ { offset ->
                    // CRITICAL: Validate offset to prevent NaN/Infinity
                    if (!offset.x.isFinite() || !offset.y.isFinite()) {
                        android.util.Log.e(TAG, "Invalid long-press offset: $offset")
                        return@onLongPress
                    }

                    // Map composable offset -> PreviewView px
                    val compW = previewSize.width.coerceAtLeast(1)
                    val compH = previewSize.height.coerceAtLeast(1)

                    if (previewView.width <= 0 || previewView.height <= 0) {
                        android.util.Log.e(TAG, "Invalid preview dimensions")
                        return@onLongPress
                    }

                    val pxX = (offset.x * previewView.width / compW)
                    val pxY = (offset.y * previewView.height / compH)

                    if (!pxX.isFinite() || !pxY.isFinite()) {
                        android.util.Log.e(TAG, "Invalid coordinates: ($pxX, $pxY)")
                        return@onLongPress
                    }
                    focusOverlayRef?.apply {
                        visibility = android.view.View.VISIBLE
                        showFocusAt(pxX, pxY, Mode.Locked)
                        evMin = evRange.start
                        evMax = evRange.endInclusive
                        showEvSlider(pxX, pxY, evValue)
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            )
        }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("PreviewView")
                    .then(previewGestures)
                    .onSizeChanged { size -> previewSize = size },
                factory = { previewView }
            )

            // Exposure slider is rendered by FocusOverlayView overlay during long-press drag

            // Zoom control anchored near bottom insets; keep ≥12dp gap from other UI (caller ensures other controls gaps).
            ZoomControl(
                zoomRatio = zoom,
                minZoom = minZoom,
                maxZoom = maxZoom,
                availablePresets = presets,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .align(Alignment.BottomCenter),
                leftHanded = leftHanded,
                onZoomChanged = { ratio -> vm.applyZoomRatio(ratio) },
                onPresetSelected = { vm.animateToPreset(it) }
            )
        }
    }
}

@Composable
@ComposePreview(showBackground = true)
private fun CameraScreenPreview() {
    // Preview shows only the layout scaffold (no real camera in previews)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Exposure overlay preview omitted

        // Zoom control preview
        ZoomControl(
            zoomRatio = 1f,
            minZoom = 0.5f,
            maxZoom = 4f,
            availablePresets = listOf(0.5f, 1f, 2f, 3f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            onZoomChanged = {},
            onPresetSelected = {}
        )
    }
}
