package com.example.b1void.camera.focus

import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import java.util.concurrent.Executor

/**
 * Simple factory to create FocusCoordinator wired for CameraX.
 * Allows usage without app-wide DI.
 */
object FocusProvider {
    fun create(
        previewView: PreviewView,
        camera: Camera,
        mainExecutor: Executor,
        callbacks: FocusCoordinator.Callbacks,
        config: FocusCoordinator.Config = FocusCoordinator.Config()
    ): FocusCoordinator {
        val engine = CameraXFocusEngine(previewView, camera, mainExecutor)
        return FocusCoordinator(engine, callbacks, config)
    }
}

