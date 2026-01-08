package com.example.b1void.core.camera

import android.app.Application
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import com.example.b1void.core.camera.quirks.DeviceQuirksManager
import com.example.b1void.core.domain.camera.CameraController
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraXControllerProvider @Inject constructor(
    private val quirksManager: DeviceQuirksManager
) : CameraControllerProvider {
    override fun get(
        previewView: PreviewView,
        camera: Camera,
        mainExecutor: Executor
    ): CameraController {
        val application = previewView.context.applicationContext as Application
        return CameraXController(
            application = application,
            previewView = previewView,
            camera = camera,
            mainExecutor = mainExecutor,
            deviceQuirksManager = quirksManager
        )
    }
}
