package com.example.b1void.camera.ev

import androidx.camera.core.Camera
import androidx.camera.core.ExposureState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
open class ExposureInteractor @Inject constructor() {
    private var camera: Camera? = null

    companion object {
        private const val TAG = "TONEMAP_DEBUG"
    }

    open fun bind(camera: Camera) {
        this.camera = camera
    }

    open fun evRange(): ClosedFloatingPointRange<Float> {
        val st = camera?.cameraInfo?.exposureState ?: return -2.0f..2.0f
        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.3333f
        val min = st.exposureCompensationRange.lower * step
        val max = st.exposureCompensationRange.upper * step
        return min.coerceIn(-2f, 2f)..max.coerceIn(-2f, 2f)
    }

    open fun currentEv(): Float {
        val st = camera?.cameraInfo?.exposureState ?: return 0f
        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.3333f
        return st.exposureCompensationIndex * step
    }

    open fun setEv(ev: Float) {
        android.util.Log.d(TAG, "=== setEv CALLED with ev=$ev ===")

        val cam = camera ?: run {
            android.util.Log.e(TAG, "setEv: camera is null")
            return
        }

        // CRITICAL: Validate input to prevent NaN/Infinity from reaching camera HAL
        // Invalid values cause crashes in tonemap processing at hardware level
        if (!ev.isFinite()) {
            android.util.Log.e(TAG, "❌ REJECTED: EV value $ev is not finite (NaN or Infinity)")
            return
        }

        val st: ExposureState = cam.cameraInfo.exposureState
        android.util.Log.d(TAG, "ExposureState: current index=${st.exposureCompensationIndex}, " +
                "range=[${st.exposureCompensationRange.lower}..${st.exposureCompensationRange.upper}], " +
                "step=${st.exposureCompensationStep}")

        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.3333f

        // Additional validation: ensure step is finite
        if (!step.isFinite() || step <= 0f) {
            android.util.Log.e(TAG, "❌ REJECTED: Invalid step value: $step")
            return
        }

        val rawIdx = ev / step
        android.util.Log.d(TAG, "Calculation: $ev / $step = $rawIdx")

        // Validate division result before rounding
        if (!rawIdx.isFinite()) {
            android.util.Log.e(TAG, "❌ REJECTED: Division produced invalid value: $rawIdx")
            return
        }

        val rounded = rawIdx.roundToInt()
        val targetIdx = rounded.coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)

        android.util.Log.d(TAG, "✅ APPLYING: ev=$ev -> rawIdx=$rawIdx -> rounded=$rounded -> targetIdx=$targetIdx")

        try {
            cam.cameraControl.setExposureCompensationIndex(targetIdx)
            android.util.Log.d(TAG, "✅ SUCCESS: setExposureCompensationIndex($targetIdx) completed")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ FAILED: setExposureCompensationIndex threw exception", e)
        }
    }
}
