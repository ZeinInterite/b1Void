package com.example.b1void.camera.core.camera

import android.graphics.RectF

/**
 * FocusState
 *
 * State diagram:
 * Idle -> (tap) -> Metering -> (af_success|timeout|fail) -> Idle
 * Idle -> (longPress) -> Locked -> (longPress|unlock) -> Idle
 * Any  -> (startTracking) -> Tracking -> (stopTracking|lost) -> Idle
 */
sealed interface FocusState {
    val timestamp: Long

    data class Idle(override val timestamp: Long = System.currentTimeMillis()) : FocusState

    data class Metering(
        val x: Float,
        val y: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : FocusState

    data class Locked(
        val x: Float,
        val y: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : FocusState

    data class Tracking(
        val rect: RectF?,
        val x: Float?,
        val y: Float?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : FocusState

    data class Failed(
        val cause: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : FocusState
}
