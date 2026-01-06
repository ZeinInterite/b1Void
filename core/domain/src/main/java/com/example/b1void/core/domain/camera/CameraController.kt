package com.example.b1void.core.domain.camera

interface CameraController {
    suspend fun takePicture(): String?
    fun setZoom(zoomRatio: Float)
    fun toggleFlash(enable: Boolean)
    fun setExposure(value: Float)
    // Дополнительные методы могут быть добавлены позже
}
