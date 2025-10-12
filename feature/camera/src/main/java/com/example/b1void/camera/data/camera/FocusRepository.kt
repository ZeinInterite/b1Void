package com.example.b1void.camera.data.camera

import android.graphics.RectF
import com.example.b1void.camera.core.camera.FocusState
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over CameraX/Camera2 focus and exposure.
 * Pre-conditions: camera bound, permissions granted.
 * Post-conditions: state emits transitions; EV values are quantized per device step.
 */
interface FocusRepository {
    /** Continuous stream of focus states. */
    val state: Flow<FocusState>
    /** Current exposure compensation in EV (stops). */
    val evValue: Flow<Float>
    /** Device exposure step in EV (e.g., 1/3). */
    val exposureStep: Flow<Float>

    /** One-shot AF/AE/AWB metering on the given pixel point. */
    suspend fun tapToFocus(x: Float, y: Float)
    /** Toggle AE/AF lock at the given pixel point. */
    suspend fun longPressLock(x: Float, y: Float)
    /** Unlock AE/AF and return to CAF. */
    suspend fun unlock()

    /** Start subject tracking with optional rect or point in pixels. */
    suspend fun startTracking(rect: RectF? = null, x: Float? = null, y: Float? = null)
    /** Stop tracking. */
    suspend fun stopTracking()

    /** Adjust EV by delta in stops (positive or negative). */
    suspend fun setEv(delta: Float)
    /** Enable/disable macro mode if supported. */
    suspend fun enableMacro(enabled: Boolean)
    /** Enable/disable torch assist for low-light focusing. */
    suspend fun enableTorchAssist(enabled: Boolean)
}
