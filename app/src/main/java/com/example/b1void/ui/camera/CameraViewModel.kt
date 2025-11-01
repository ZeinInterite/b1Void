package com.example.b1void.ui.camera

import android.content.Context
import android.os.Build
import androidx.camera.core.Camera
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.ZoomState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.b1void.data.CameraSettingsManager
import com.example.b1void.camera.ev.ExposureInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val exposureInteractor: ExposureInteractor,
    private val settingsManager: CameraSettingsManager
) : ViewModel() {
    private var camera: Camera? = null
    private var zoomCollectJob: Job? = null
    private var evCollectJob: Job? = null
    private var currentCameraKey: String? = null

    companion object {
        private const val TAG = "TONEMAP_DEBUG"
    }

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

    // EV is bound after we know concrete camera (per-camera key)

    fun bindCamera(camera: Camera) {
        android.util.Log.d(TAG, "=== bindCamera START ===")
        this.camera = camera

        // Build per-camera persistence key
        val info2 = Camera2CameraInfo.from(camera.cameraInfo)
        val cameraId = info2.cameraId
        val facingInt = info2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            ?: android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        val facing = if (facingInt == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) "front" else "back"
        val deviceKey = "${Build.MANUFACTURER}_${Build.MODEL}_${cameraId}_$facing"
            .replace("\\s+".toRegex(), "_")
            .lowercase()
        currentCameraKey = deviceKey
        android.util.Log.d(TAG, "Camera passport key=$deviceKey")

        // Log initial exposure state
        val initialExposure = camera.cameraInfo.exposureState
        android.util.Log.d(TAG, "Initial exposure state: index=${initialExposure.exposureCompensationIndex}, " +
                "step=${initialExposure.exposureCompensationStep}, " +
                "range=[${initialExposure.exposureCompensationRange.lower}..${initialExposure.exposureCompensationRange.upper}]")

        zoomCollectJob?.cancel()
        zoomCollectJob = viewModelScope.launch {
            camera.cameraInfo.zoomState.asFlow().collectLatest { state ->
                updateFromZoomState(state)
            }
        }
        // Restore last user zoom ratio after rebind to preserve user's zoom setting
        // This ensures zoom persists across camera configuration changes
        if (lastUserZoomRatio != 1.0f) {
            android.util.Log.d(TAG, "Restoring zoom ratio: $lastUserZoomRatio")
            camera.cameraControl.setZoomRatio(lastUserZoomRatio)
        }

        // Initialize EV range from camera capabilities
        android.util.Log.d(TAG, "Binding exposureInteractor to camera")
        exposureInteractor.bind(camera)
        initializeEvRange()
        android.util.Log.d(TAG, "Initialized EV range: ${_evRange.value}")

        // Observe per-camera EV and apply with clamp
        evCollectJob?.cancel()
        val key = deviceKey
        evCollectJob = viewModelScope.launch {
            settingsManager.getEvCompensationFor(key).collectLatest { savedEv ->
                val clamped = savedEv.coerceIn(_evRange.value)
                _evCompensation.value = clamped
                android.util.Log.d(TAG, "Apply saved EV (key=$key): $savedEv -> $clamped")
                applyEvCompensationToCamera(clamped)
            }
        }
        android.util.Log.d(TAG, "=== bindCamera END ===")
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
        android.util.Log.d(TAG, "=== setExposureCompensation CALLED with evValue=$evValue ===")
        android.util.Log.d(TAG, "Current state: _evCompensation=${_evCompensation.value}, _evRange=${_evRange.value}")

        // CRITICAL: Validate input to prevent NaN/Infinity from crashing camera HAL
        if (!evValue.isFinite()) {
            android.util.Log.e(TAG, "❌ REJECTED: EV value $evValue is not finite")
            return
        }

        val clampedEv = evValue.coerceIn(_evRange.value)
        android.util.Log.d(TAG, "After clamping: $evValue -> $clampedEv (range=${_evRange.value})")

        // Additional safety: ensure clamped value is still finite
        if (!clampedEv.isFinite()) {
            android.util.Log.e(TAG, "❌ REJECTED: Clamped EV $clampedEv is not finite")
            return
        }

        _evCompensation.value = clampedEv
        android.util.Log.d(TAG, "✅ State updated to $clampedEv, calling applyEvCompensationToCamera")
        applyEvCompensationToCamera(clampedEv)
        viewModelScope.launch {
            val key = currentCameraKey
            if (key != null) {
                settingsManager.setEvCompensationFor(key, clampedEv)
                android.util.Log.d(TAG, "Saved EV $clampedEv for $key")
            } else {
                settingsManager.setEvCompensation(clampedEv)
                android.util.Log.d(TAG, "Saved EV $clampedEv (global, no camera key)")
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
    private fun initializeEvRange() {
        runCatching { _evRange.value = exposureInteractor.evRange() }
            .onFailure { _evRange.value = -2.0f..2.0f }
    }

    /**
     * Apply EV compensation to camera hardware
     */
    private fun applyEvCompensationToCamera(evValue: Float) {
        android.util.Log.d(TAG, "applyEvCompensationToCamera: calling exposureInteractor.setEv($evValue)")
        runCatching { exposureInteractor.setEv(evValue) }
            .onSuccess { android.util.Log.d(TAG, "✅ exposureInteractor.setEv succeeded") }
            .onFailure {
                android.util.Log.e(TAG, "❌ exposureInteractor.setEv FAILED", it)
                it.printStackTrace()
            }
    }
}
