package com.example.b1void.ui.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.b1void.data.CameraSettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * CameraViewModel holds zoom and exposure state and exposes actions to manipulate CameraX.
 * Identifiers and comments are in English, production-leaning style.
 */
class CameraViewModel(
    private val context: Context? = null
) : ViewModel() {

    private var camera: Camera? = null
    private var zoomCollectJob: Job? = null
    private val settingsManager: CameraSettingsManager? = context?.let { CameraSettingsManager(it) }

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

    // Exposure compensation state
    private val _evCompensation = MutableStateFlow(0.0f)
    val evCompensation: StateFlow<Float> = _evCompensation.asStateFlow()

    private val _evRange = MutableStateFlow(-2.0f..2.0f)
    val evRange: StateFlow<ClosedFloatingPointRange<Float>> = _evRange.asStateFlow()

    init {
        // Load saved EV compensation from settings
        settingsManager?.let { manager ->
            viewModelScope.launch {
                manager.getEvCompensation().collectLatest { savedEv ->
                    _evCompensation.value = savedEv
                    // Apply to camera if already bound
                    camera?.let { applyEvCompensationToCamera(savedEv) }
                }
            }
        }
    }

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

        // Initialize EV range from camera capabilities
        initializeEvRange(camera)

        // Apply saved EV compensation
        applyEvCompensationToCamera(_evCompensation.value)
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

    // ==================== Exposure Compensation ====================

    /**
     * Set exposure compensation value and save to settings
     * @param evValue EV value from -2.0 to +2.0
     */
    fun setExposureCompensation(evValue: Float) {
        val clampedEv = evValue.coerceIn(_evRange.value)
        _evCompensation.value = clampedEv
        applyEvCompensationToCamera(clampedEv)

        // Save to settings
        settingsManager?.let { manager ->
            viewModelScope.launch {
                manager.setEvCompensation(clampedEv)
            }
        }
    }

    /**
     * Reset exposure compensation to 0.0
     */
    fun resetExposureCompensation() {
        setExposureCompensation(0.0f)
    }

    /**
     * Initialize EV range from camera capabilities
     */
    private fun initializeEvRange(camera: Camera) {
        try {
            val exposureState = camera.cameraInfo.exposureState
            val step = exposureState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.3333f
            val minEv = exposureState.exposureCompensationRange.lower * step
            val maxEv = exposureState.exposureCompensationRange.upper * step
            _evRange.value = minEv.coerceIn(-2f, 2f)..maxEv.coerceIn(-2f, 2f)
        } catch (e: Exception) {
            // Use default range on error
            _evRange.value = -2.0f..2.0f
        }
    }

    /**
     * Apply EV compensation to camera hardware
     */
    private fun applyEvCompensationToCamera(evValue: Float) {
        val cam = camera ?: return
        try {
            val exposureState = cam.cameraInfo.exposureState
            val step = exposureState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.3333f
            val index = (evValue / step).toInt().coerceIn(
                exposureState.exposureCompensationRange.lower,
                exposureState.exposureCompensationRange.upper
            )
            cam.cameraControl.setExposureCompensationIndex(index)
        } catch (e: Exception) {
            android.util.Log.e("CameraViewModel", "Failed to apply EV compensation", e)
        }
    }
}
