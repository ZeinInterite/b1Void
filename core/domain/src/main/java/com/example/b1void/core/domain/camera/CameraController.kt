package com.example.b1void.core.domain.camera

import androidx.camera.core.ImageCapture

interface CameraController {
    fun initialize(imageCapture: ImageCapture)
    suspend fun takePicture(): String?
    fun setZoom(zoomRatio: Float)
    fun toggleFlash(enable: Boolean)
    fun setExposure(value: Float)
    // Дополнительные методы могут быть добавлены позже
}
