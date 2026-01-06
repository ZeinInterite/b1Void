package com.example.b1void.core.camera

import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import com.example.b1void.core.camera.FocusState
import com.example.b1void.core.domain.camera.CameraController
import com.example.b1void.core.camera.quirks.DeviceQuirksManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class CameraXController(
    private val previewView: PreviewView,
    private val camera: Camera,
    private val mainExecutor: Executor,
    private val deviceQuirksManager: DeviceQuirksManager,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logTag: String = "CameraXController",
) : CameraController {

    private val scope = CoroutineScope(ioDispatcher)

    private val _state = MutableStateFlow<FocusState>(FocusState.Idle())
    val state = _state.asStateFlow()

    override suspend fun takePicture(): String? {
        // TODO: Implement takePicture logic that returns a file path string
        return null
    }

    override fun setZoom(zoomRatio: Float) {
        camera.cameraControl.setZoomRatio(zoomRatio)
    }

    override fun toggleFlash(enable: Boolean) {
        camera.cameraControl.enableTorch(enable)
    }

    override fun setExposure(value: Float) {
        // TODO: Implement exposure logic
    }
}