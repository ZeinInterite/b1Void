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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
            scaleType = PreviewView.ScaleType.FILL_CENTER
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
            val cameraPreview = Preview.Builder().build().also { p ->
                p.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder().build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                cameraPreview,
                imageCapture
            )
            camera?.let { vm.bindCamera(it) }
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
