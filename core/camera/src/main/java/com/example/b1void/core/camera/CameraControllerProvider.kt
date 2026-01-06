package com.example.b1void.core.camera

import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import com.example.b1void.core.domain.camera.CameraController
import java.util.concurrent.Executor

interface CameraControllerProvider {
    fun get(
        previewView: PreviewView,
        camera: Camera,
        mainExecutor: Executor
    ): CameraController
}