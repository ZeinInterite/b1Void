package com.example.b1void.ui.camera

import android.content.Context
import android.os.Build
import androidx.camera.core.Camera
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.TorchState
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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
    private var zoomPersistJob: Job? = null
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

    /**
     * XIAOMI BUG FIX #4: Get current zoom ratio for applying to capture
     */
    fun getCurrentZoomRatio(): Float = lastUserZoomRatio

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

    // Torch state
    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)
    val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

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

        // Check flash unit availability
        _hasFlashUnit.value = camera.cameraInfo.hasFlashUnit()
        android.util.Log.d(TAG, "Flash unit available: ${_hasFlashUnit.value}")

        // Restore torch state from persistent storage
        viewModelScope.launch {
            try {
                val savedTorchState = settingsManager.getTorchEnabled().first()
                android.util.Log.d(TAG, "Loaded torch state from DataStore: $savedTorchState")

                if (savedTorchState && camera.cameraInfo.hasFlashUnit()) {
                    android.util.Log.d(TAG, "Restoring torch state: ON")
                    camera.cameraControl.enableTorch(true)
                    _torchEnabled.value = true
                } else {
                    android.util.Log.d(TAG, "Torch state is OFF or no flash unit")
                    _torchEnabled.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to restore torch state", e)
                _torchEnabled.value = false
            }
        }

        // 1) Observe live ZoomState from CameraX and mirror into VM state.
        zoomCollectJob?.cancel()
        zoomCollectJob = viewModelScope.launch {
            camera.cameraInfo.zoomState.asFlow().collectLatest { state ->
                updateFromZoomState(state)
            }
        }

        // 2) Restore persisted zoom for this concrete camera (per-device/camera key),
        // but only after ZoomState has reported min/max so we can clamp safely.
        viewModelScope.launch {
            try {
                val saved = settingsManager.getZoomRatioFor(deviceKey).first()
                val zState = camera.cameraInfo.zoomState.value
                if (zState != null) {
                    val clamped = saved.coerceIn(zState.minZoomRatio, zState.maxZoomRatio)
                    if (kotlin.math.abs(clamped - zState.zoomRatio) > 0.01f) {
                        android.util.Log.d(TAG, "Restoring persisted zoom for $deviceKey: $saved -> $clamped")
                        lastUserZoomRatio = clamped
                        camera.cameraControl.setZoomRatio(clamped)
                        _zoomRatio.value = clamped
                    }
                } else {
                    // Fallback: apply after first ZoomState arrives
                    android.util.Log.d(TAG, "ZoomState not ready; will apply persisted zoom on first emission")
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Failed to restore zoom from DataStore", t)
            }
        }

        // 3) Persist user zoom changes with debounce to avoid DataStore thrash and write races.
        zoomPersistJob?.cancel()
        zoomPersistJob = viewModelScope.launch {
            // Mirror of _zoomRatio changes; debounce to 250ms to minimize writes.
            // Note: We intentionally persist VM state, not raw ZoomState, to store user intent.
            zoomRatio
                .debounce(250)
                .collectLatest { z ->
                    val key = currentCameraKey
                    if (key != null) {
                        runCatching { settingsManager.setZoomRatioFor(key, z) }
                            .onSuccess { android.util.Log.d(TAG, "Persisted zoom $z for $key") }
                            .onFailure { android.util.Log.e(TAG, "Failed to persist zoom for $key", it) }
                    }
                }
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
     * === CRITICAL SAFETY for Xiaomi: Logs show "Invalid exposure parameters: gain: 0.000000, exposureTime: 0" ===
     * HOTFIX: NEVER send 0.0 or invalid values - they cause HAL crash with "gain: 0.0"
     */
    private fun applyEvCompensationToCamera(evValue: Float) {
        android.util.Log.d(TAG, "applyEvCompensationToCamera: input=$evValue")

        // === CRITICAL VALIDATION ===
        // NEVER send invalid values to camera HAL (causes crash on Xiaomi)
        if (!evValue.isFinite() || evValue.isNaN()) {
            android.util.Log.e(TAG, "❌ CRITICAL: Invalid EV value detected: $evValue - SKIP setting EV")
            // DO NOT send any value to camera - keep current exposure
            return
        }

        // Additional safety check for Xiaomi: clamp to safe range
        val safeValue = if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice()) {
            evValue.coerceIn(-1.0f, 2.0f).also {
                if (it != evValue) {
                    android.util.Log.w(TAG, "XIAOMI: EV clamped from $evValue to $it")
                }
            }
        } else {
            evValue.coerceIn(-2.0f, 2.0f)
        }

        // === XIAOMI HOTFIX: Never send exactly 0.0 - use small offset ===
        val finalValue = if (com.example.b1void.utils.ManufacturerCompatibility.isXiaomiDevice() &&
                             kotlin.math.abs(safeValue) < 0.01f) {
            0.1f.also {
                android.util.Log.w(TAG, "XIAOMI HOTFIX: Replacing near-zero EV $safeValue with minimal positive 0.1f")
            }
        } else {
            safeValue
        }

        android.util.Log.d(TAG, "applyEvCompensationToCamera: final value=$finalValue")

        // Check camera is bound before attempting EV change
        val cam = camera
        if (cam == null) {
            android.util.Log.e(TAG, "❌ Camera not bound, cannot set EV")
            return
        }

        runCatching { exposureInteractor.setEv(finalValue) }
            .onSuccess { android.util.Log.d(TAG, "✅ exposureInteractor.setEv($finalValue) succeeded") }
            .onFailure {
                android.util.Log.e(TAG, "❌ exposureInteractor.setEv FAILED - camera may be in invalid state", it)
                it.printStackTrace()
                // DO NOT attempt fallback - let camera maintain current exposure
            }
    }

    // ==================== Torch Control ====================

    /**
     * Toggle torch on/off and save state to persistent storage
     */
    fun toggleTorch() {
        val cam = camera ?: run {
            android.util.Log.w(TAG, "Cannot toggle torch: camera is null")
            return
        }

        if (!cam.cameraInfo.hasFlashUnit()) {
            android.util.Log.w(TAG, "Cannot toggle torch: no flash unit available")
            return
        }

        val newState = !_torchEnabled.value
        android.util.Log.d(TAG, "Toggling torch: ${_torchEnabled.value} -> $newState")

        try {
            cam.cameraControl.enableTorch(newState)
            _torchEnabled.value = newState

            // Save to persistent storage
            viewModelScope.launch {
                try {
                    settingsManager.setTorchEnabled(newState)
                    android.util.Log.d(TAG, "Torch state saved to DataStore: $newState")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to save torch state to DataStore", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to enable torch", e)
        }
    }
}
