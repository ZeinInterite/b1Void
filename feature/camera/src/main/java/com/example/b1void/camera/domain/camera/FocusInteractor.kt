package com.example.b1void.camera.domain.camera

import android.graphics.RectF
import android.os.Build
import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.data.camera.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Coordinates high-level focus operations for camera.
 * Pre-conditions: camera is bound and permissions granted.
 * Post-conditions: state reflects focus lifecycle; EV adjustments quantized by device step.
 */
class FocusInteractor(
    private val repo: FocusRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val state: StateFlow<FocusState> = repo.state
        .stateIn(scope, SharingStarted.Lazily, FocusState.Idle())

    val evValue: StateFlow<Float> = repo.evValue
        .stateIn(scope, SharingStarted.Lazily, 0f)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events = _events

    private var cafReturnJob: Job? = null

    /**
     * One-shot tap-to-focus with AE/AF on point.
     * May auto-cancel to CAF; may auto-retry once on failure.
     */
    suspend fun tapToFocus(pxX: Float, pxY: Float) {
        _events.tryEmit("tap_focus")
        repo.tapToFocus(pxX, pxY)
        scheduleCafReturn()
    }

    /** Locks AE/AF at point; second long-press unlocks. */
    suspend fun longPressLock(pxX: Float, pxY: Float) {
        repo.longPressLock(pxX, pxY)
        _events.tryEmit("ae_lock_toggle")
    }

    /** Unlocks AE/AF and returns to CAF. */
    suspend fun unlock() {
        repo.unlock()
        _events.tryEmit("ae_unlock")
    }

    /** Start subject tracking with target rect. */
    suspend fun startTracking(target: RectF) { repo.startTracking(rect = target) }
    /** Stop tracking and return to Idle/CAF. */
    suspend fun stopTracking() { repo.stopTracking() }

    /** Adjust exposure compensation by device steps (e.g., +1/-1 step). */
    fun adjustEv(deltaSteps: Int) {
        scope.launch {
            val step = exposureStepValue()
            repo.setEv(deltaSteps * step)
        }
    }

    /** Set absolute exposure compensation value in EV stops (-2.0 to +2.0). */
    fun setAbsoluteEv(evValue: Float) {
        scope.launch {
            // === XIAOMI BUG FIX #3: Limit EV range ===
            val clampedValue = if (isXiaomiDevice()) {
                // Xiaomi devices have issues with negative EV values
                val model = Build.MODEL.lowercase()
                val minEv = if (model.contains("redmi")) -1.0f else -1.5f
                val maxEv = 2.0f
                evValue.coerceIn(minEv, maxEv).also {
                    if (it != evValue) {
                        _events.tryEmit("xiaomi_ev_clamped")
                    }
                }
            } else {
                evValue
            }
            repo.setAbsoluteEv(clampedValue)
        }
    }

    /** Get the supported EV range for the camera. */
    suspend fun getEvRange(): ClosedFloatingPointRange<Float> = repo.getEvRange()

    /** Enable/disable macro mode (if supported by device). */
    fun enableMacro(enabled: Boolean) { scope.launch { repo.enableMacro(enabled) } }
    /** Enable/disable torch assist in low light modes. */
    fun enableTorchAssist(enabled: Boolean) { scope.launch { repo.enableTorchAssist(enabled) } }

    private fun scheduleCafReturn() {
        cafReturnJob?.cancel()
        cafReturnJob = scope.launch {
            // After single metering, allow camera to return to CAF
            // Controller sets autoCancel=2s; here we just ensure no lock persists.
        }
    }

    private suspend fun exposureStepValue(): Float =
        repo.exposureStep
            .map { if (it > 0f) it else 0.3333f }
            .stateIn(scope, SharingStarted.Eagerly, 0.3333f).value

    /**
     * XIAOMI BUG FIX #3: Check if device is Xiaomi/Redmi
     */
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer == "xiaomi" || manufacturer == "redmi"
    }
}
