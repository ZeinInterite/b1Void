package com.example.b1void.camera.ev

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ExposureState
import kotlin.math.abs
import kotlin.math.roundToInt

class EvController(
    private val onOverlayVisibility: (visible: Boolean) -> Unit,
    private val onOverlayValue: (ev: Float) -> Unit
) {
    private val logTag = "AEAF_EV"

    private var camera: Camera? = null
    private var baseIndex: Int = 0
    private var stepEv: Float = 0.3333f
    private var minIndex: Int = 0
    private var maxIndex: Int = 0
    private var active = false

    // Handler для периодического обновления UI на основе реального состояния камеры
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportedIndex: Int = 0
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (active) {
                syncUiWithCamera()
                handler.postDelayed(this, 100) // poll at 10Hz to reduce UI thread load
            }
        }
    }

    fun attach(camera: Camera) {
        this.camera = camera
        val st: ExposureState = camera.cameraInfo.exposureState
        baseIndex = st.exposureCompensationIndex
        stepEv = st.exposureCompensationStep.toFloat()
        minIndex = st.exposureCompensationRange.lower
        maxIndex = st.exposureCompensationRange.upper
        val minEv = minIndex * stepEv
        val maxEv = maxIndex * stepEv
        Log.i(logTag, "EvController.attach: stepEv=" + stepEv + ", baseIndex=" + baseIndex + ", range=[" + st.exposureCompensationRange.lower + ".." + st.exposureCompensationRange.upper + "] -> EV [" + minEv + ".." + maxEv + "]")
        if (stepEv <= 0f || (minIndex == 0 && maxIndex == 0)) {
            Log.w(logTag, "EvController.attach: EV compensation NOT supported on this camera")
        }
    }

    fun begin() {
        active = true
        Log.d(logTag, "EvController.begin: show EV overlay")
        onOverlayVisibility(true)
        // Запускаем периодическое обновление UI на основе реального состояния камеры
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    fun end() {
        active = false
        // Останавливаем периодическое обновление
        handler.removeCallbacks(updateRunnable)
        Log.d(logTag, "EvController.end: stop EV tracking (overlay stays)")
    }

    /**
     * Синхронизирует UI с фактическим состоянием экспозиции камеры
     * Вызывается периодически для плавного обновления иконки
     */
    private fun syncUiWithCamera() {
        val cam = camera ?: return
        val st: ExposureState = cam.cameraInfo.exposureState
        val currentIndex = st.exposureCompensationIndex

        // Обновляем UI только если значение изменилось
        if (currentIndex != lastReportedIndex) {
            val currentEv = (currentIndex - baseIndex) * stepEv
            val clampedEv = currentEv.coerceIn(-2f, 2f)
            onOverlayValue(clampedEv)
            lastReportedIndex = currentIndex
            Log.v(logTag, "syncUiWithCamera: index $currentIndex -> EV $clampedEv")
        }
    }

    fun adjustByDrag(dy: Float) {
        val cam = camera ?: return
        if (!active) return
        // Map pixels to EV: simple gain factor; ~150px -> 1 EV
        val deltaEv = (-dy / 150f)
        val st: ExposureState = cam.cameraInfo.exposureState
        val current = st.exposureCompensationIndex
        val targetEv = (current - baseIndex) * stepEv + deltaEv
        val clampedEv = targetEv.coerceIn(-2f, 2f) // clamp visual overlay to +/-2 EV
        val targetIndex = (clampedEv / stepEv).roundToInt().coerceIn(minIndex, maxIndex)
        Log.d(logTag, "EvController.adjustByDrag: dy=" + dy + " -> deltaEv=" + deltaEv + ", currentIdx=" + current + ", targetEv=" + targetEv + ", clampedEv=" + clampedEv + " -> idx=" + targetIndex + " (range [" + minIndex + ".." + maxIndex + "], step=" + stepEv + ")")

        // НЕ вызываем onOverlayValue здесь - пусть syncUiWithCamera отслеживает реальное состояние камеры
        // Это обеспечит синхронизацию движения UI с фактической скоростью изменения экспозиции

        if (targetIndex != current) {
            Log.d(logTag, "EvController: setExposureCompensationIndex(" + targetIndex + ")")
            cam.cameraControl.setExposureCompensationIndex(targetIndex)
            // syncUiWithCamera будет вызван автоматически через 50ms и обновит UI
        } else {
            Log.v(logTag, "EvController.adjustByDrag: no index change (current=" + current + ")")
        }
    }
}
