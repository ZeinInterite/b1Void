package com.example.b1void.ui.camera

import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * CameraViewModel holds zoom state and exposes actions to manipulate CameraX zoom.
 * Identifiers and comments are in English, production-leaning style.
 */
class CameraViewModel : ViewModel() {

    private var camera: Camera? = null
    private var zoomCollectJob: Job? = null

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _minZoomRatio = MutableStateFlow(0.5f)
    val minZoomRatio: StateFlow<Float> = _minZoomRatio.asStateFlow()

    private val _maxZoomRatio = MutableStateFlow(10f)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio.asStateFlow()

    // Presets: 0.5x, 1x, 2x, 3x. Filtered by available range at runtime.
    private val allPresets = listOf(0.5f, 1f, 2f, 3f)
    private val _availablePresets = MutableStateFlow<List<Float>>(allPresets)
    val availablePresets: StateFlow<List<Float>> = _availablePresets.asStateFlow()

    // In-session persistence of the last used ratio.
    private var lastUserZoomRatio: Float = 1.0f

    fun bindCamera(camera: Camera) {
        this.camera = camera
        zoomCollectJob?.cancel()
        zoomCollectJob = viewModelScope.launch {
            camera.cameraInfo.zoomState.asFlow().collectLatest { state ->
                updateFromZoomState(state)
            }
        }
        // Restore last user zoom ratio after rebind to preserve user's zoom setting
        // This ensures zoom persists across camera configuration changes
        if (lastUserZoomRatio != 1.0f) {
            camera.cameraControl.setZoomRatio(lastUserZoomRatio)
        }
    }

    private fun updateFromZoomState(state: ZoomState) {
        val minR = state.minZoomRatio
        val maxR = state.maxZoomRatio
        _minZoomRatio.value = minR
        _maxZoomRatio.value = maxR
        val clamped = state.zoomRatio.coerceIn(minR, maxR)
        _zoomRatio.value = clamped
        lastUserZoomRatio = clamped
        // Filter presets by availability
        _availablePresets.value = allPresets.filter { it in minR..maxR }
    }

    /**
     * Apply an absolute zoom ratio. Clamped to camera range and propagated to CameraX.
     */
    fun applyZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val minR = _minZoomRatio.value
        val maxR = _maxZoomRatio.value
        val clamped = ratio.coerceIn(minR, maxR)
        _zoomRatio.value = clamped
        lastUserZoomRatio = clamped
        cam.cameraControl.setZoomRatio(clamped)
    }

    /**
     * Smoothly animate to a preset (UI handles visual animation; we just set ratio).
     */
    fun animateToPreset(preset: Float) {
        applyZoomRatio(preset)
    }

    /**
     * Handle pinch gesture by scaling current ratio using a multiplicative delta.
     * The delta should be >0; 1.0 = no change.
     */
    fun onPinch(delta: Float) {
        val base = lastUserZoomRatio
        val scaled = if (delta.isFinite() && delta > 0f) base * delta else base
        applyZoomRatio(scaled)
    }

    /**
     * Cycle through main presets similar to iOS: 1 → 2 → 3 → 1 ... skipping unavailable.
     */
    fun onDoubleTap() {
        val presets = _availablePresets.value
        if (presets.isEmpty()) return
        val ordered = presets.sorted()
        // Find next higher or wrap to first.
        val current = _zoomRatio.value
        val next = ordered.firstOrNull { it > current + 0.01f } ?: ordered.first()
        applyZoomRatio(next)
    }

    /** Increase ratio by a small step for DPAD/keyboard. */
    fun nudgeUp(step: Float = 0.05f) {
        applyZoomRatio(_zoomRatio.value + step * max(1f, _zoomRatio.value))
    }

    /** Decrease ratio by a small step for DPAD/keyboard. */
    fun nudgeDown(step: Float = 0.05f) {
        applyZoomRatio(_zoomRatio.value - step * max(1f, _zoomRatio.value))
    }
}
