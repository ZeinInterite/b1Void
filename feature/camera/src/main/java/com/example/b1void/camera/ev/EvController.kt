package com.example.b1void.camera.ev

import androidx.camera.core.Camera
import androidx.camera.core.ExposureState
import kotlin.math.roundToInt

class EvController(
    private val onOverlayVisibility: (visible: Boolean) -> Unit,
    private val onOverlayValue: (ev: Float) -> Unit
) {
    private var camera: Camera? = null
    private var baseIndex: Int = 0
    private var stepEv: Float = 0.3333f
    private var minIndex: Int = 0
    private var maxIndex: Int = 0
    private var active = false

    fun attach(camera: Camera) {
        this.camera = camera
        val st: ExposureState = camera.cameraInfo.exposureState
        baseIndex = st.exposureCompensationIndex
        stepEv = st.exposureCompensationStep.toFloat()
        minIndex = st.exposureCompensationRange.lower
        maxIndex = st.exposureCompensationRange.upper
    }

    fun begin() {
        active = true
        onOverlayVisibility(true)
    }

    fun end() {
        active = false
        onOverlayVisibility(false)
    }

    fun adjustByDrag(dy: Float) {
        val cam = camera ?: return
        if (!active) return
        // Map pixels to EV: simple gain factor; 150px ≈ 1 EV
        val deltaEv = (-dy / 150f)
        val st: ExposureState = cam.cameraInfo.exposureState
        val current = st.exposureCompensationIndex
        val targetEv = (current - baseIndex) * stepEv + deltaEv
        val clampedEv = targetEv.coerceIn(-2f, 2f) // ±2 EV
        onOverlayValue(clampedEv)
        val targetIndex = (clampedEv / stepEv).roundToInt().coerceIn(minIndex, maxIndex)
        if (targetIndex != current) {
            cam.cameraControl.setExposureCompensationIndex(targetIndex)
        }
    }
}

