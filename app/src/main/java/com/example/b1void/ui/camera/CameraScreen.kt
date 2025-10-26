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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.Alignment
import android.view.HapticFeedbackConstants
import java.util.concurrent.TimeUnit

/**
 * CameraScreen shows CameraX Preview and the iPhone-like ZoomControl, with pinch/double-tap hooks.
 * This is a sample screen for integration and tests.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(
    leftHanded: Boolean = false,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    vm: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

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
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
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

            // Create shared ViewPort to ensure Preview and ImageCapture use the same crop region
            // This is THE key to making preview match captured photo exactly
            val viewPort = ViewPort.Builder(
                android.util.Rational(4, 3), // Use 4:3 aspect ratio (sensor native)
                previewView.display.rotation
            ).build()

            val cameraPreview = Preview.Builder()
                .build()

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // UseCaseGroup with shared ViewPort ensures both use cases see the same area
            val useCaseGroup = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(cameraPreview)
                .addUseCase(imageCapture)
                .build()

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

    // Preview gestures: pinch to zoom and double tap to cycle presets.

    // Track composable size to map tap -> PreviewView coordinates safely
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun tapToFocusAt(offset: Offset) {
        val cam = camera ?: return
        try { cam.cameraControl.cancelFocusAndMetering() } catch (_: Throwable) {}
        val compW = previewSize.width.coerceAtLeast(1)
        val compH = previewSize.height.coerceAtLeast(1)
        val pxX = (offset.x * previewView.width / compW)
        val pxY = (offset.y * previewView.height / compH)
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

    val previewGestures = Modifier
        .pointerInput(Unit) {
            // Short tap triggers focus immediately
            detectTapGestures(
                onTap = { offset ->
                    focusOverlayRef?.apply {
                        visibility = android.view.View.VISIBLE
                        showFocusAt(offset.x, offset.y, Mode.Focusing)
                    }
                    tapToFocusAt(offset)
                },
                onDoubleTap = { vm.onDoubleTap() }
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, _, zoomChange, _ ->
                if (zoomChange.isFinite()) vm.onPinch(zoomChange)
            }
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
    val fakeVm = CameraViewModel()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Just render the zoom control in bottom for preview purposes
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
