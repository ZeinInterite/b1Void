package com.example.b1void.camera.data.camera.camerax

import android.graphics.RectF
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.data.camera.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CameraXFocusController(
    private val previewView: PreviewView,
    private val camera: Camera,
    private val mainExecutor: Executor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val logTag: String = "CameraXFocusCtrl",
    private val telemetry: com.example.b1void.camera.core.telemetry.TelemetryLogger = com.example.b1void.camera.core.telemetry.TelemetryLogger.NOOP
) : FocusRepository {

    private val _state = MutableStateFlow<FocusState>(FocusState.Idle())
    override val state = _state.asStateFlow()

    private val _ev = MutableStateFlow(0f)
    override val evValue = _ev.asStateFlow()
    private val _step = MutableStateFlow(0.3333f)
    override val exposureStep = _step.asStateFlow()

    private var torchAssist = false
    private var macroEnabled = false
    private var trackingJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastTrackRect: RectF? = null
    @Volatile private var lastTrackPoint: Pair<Float, Float>? = null
    private var lastTrackEmitTs: Long = 0L
    private val trackIntervalMs = 100L // <=10 Hz

    override suspend fun tapToFocus(x: Float, y: Float) {
        telemetry.log("tap_focus", mapOf("x" to x, "y" to y))
        performFocus(x, y, lock = false, retryOnce = true)
    }

    override suspend fun longPressLock(x: Float, y: Float) {
        // toggle: if already locked -> unlock
        val locked = state.value is FocusState.Locked
        if (locked) {
            unlock()
            return
        }
        telemetry.log("ae_lock_toggle", mapOf("x" to x, "y" to y))
        performFocus(x, y, lock = true, retryOnce = false)
    }

    override suspend fun unlock() {
        try {
            camera.cameraControl.cancelFocusAndMetering()
        } catch (_: Throwable) {}
        try {
            val control = Camera2CameraControl.from(camera.cameraControl)
            control.setCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    .build()
            )
        } catch (_: Throwable) {}
        _state.update { FocusState.Idle() }
        telemetry.log("ae_unlock")
    }

    override suspend fun startTracking(rect: RectF?, x: Float?, y: Float?) {
        lastTrackRect = rect
        lastTrackPoint = if (x != null && y != null) x to y else null
        _state.update { FocusState.Tracking(rect = rect, x = x, y = y) }
        telemetry.log("tracking_start", mapOf("rect" to rect?.toShortString(), "x" to x, "y" to y))
        if (trackingJob?.isActive != true) {
            trackingJob = scope.launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    if (now - lastTrackEmitTs >= trackIntervalMs) {
                        lastTrackEmitTs = now
                        val center = lastTrackPoint ?: lastTrackRect?.let { (it.centerX() to it.centerY()) }
                        if (center != null) {
                            // Coalesce updates and apply metering without lock
                            performFocus(center.first, center.second, lock = false, retryOnce = false)
                        }
                    }
                    kotlinx.coroutines.delay(16L)
                }
            }
        }
    }

    override suspend fun stopTracking() {
        trackingJob?.cancel()
        lastTrackRect = null
        lastTrackPoint = null
        _state.update { FocusState.Idle() }
        telemetry.log("tracking_stop")
    }

    override suspend fun setEv(delta: Float) {
        val st = camera.cameraInfo.exposureState
        val step = st.exposureCompensationStep.toFloat().takeIf { it > 0 } ?: 0.3333f
        _step.value = step
        val targetEv = (_ev.value + delta).coerceIn(-2f, 2f)
        val raw = targetEv / step
        val rounded = kotlin.math.round(raw)
        val idx = rounded.toInt().coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)
        camera.cameraControl.setExposureCompensationIndex(idx)
        _ev.value = idx * step
        telemetry.log("ev_change", mapOf("value" to _ev.value))
    }

    override suspend fun enableMacro(enabled: Boolean) {
        macroEnabled = enabled
        // TODO: switch AF macro if supported via interop/extensions
    }

    override suspend fun enableTorchAssist(enabled: Boolean) {
        torchAssist = enabled
        // Keep torch off until needed; this flag used by performFocus if low light
    }

    private fun performFocus(x: Float, y: Float, lock: Boolean, retryOnce: Boolean) {
        // Cancel any previous metering before starting a new one
        try { camera.cameraControl.cancelFocusAndMetering() } catch (_: Throwable) {}
        _state.value = if (lock) FocusState.Locked(x, y) else FocusState.Metering(x, y)

        // Optional torch assist (simple heuristic placeholder; real implementation would check brightness)
        if (torchAssist && lock) {
            try { camera.cameraControl.enableTorch(true) } catch (_: Throwable) {}
        }

        val factory = previewView.meteringPointFactory
        // Different region sizes to emulate weights: AF small, AE larger, AWB medium
        val af = factory.createPoint(x, y, /*sizeFraction=*/0.15f)
        val ae = factory.createPoint(x, y, /*sizeFraction=*/0.35f)
        val awb = factory.createPoint(x, y, /*sizeFraction=*/0.25f)
        val builder = FocusMeteringAction.Builder(af, FocusMeteringAction.FLAG_AF)
            .addPoint(ae, FocusMeteringAction.FLAG_AE)
            .addPoint(awb, FocusMeteringAction.FLAG_AWB)
        if (lock) builder.disableAutoCancel() else builder.setAutoCancelDuration(2, TimeUnit.SECONDS)
        val action = builder.build()

        val supported = camera.cameraInfo.isFocusMeteringSupported(action)
        if (!supported) {
            _state.update { FocusState.Failed("not_supported") }
            telemetry.log("af_fail", mapOf("reason" to "not_supported"))
            if (torchAssist && lock) { try { camera.cameraControl.enableTorch(false) } catch (_: Throwable) {} }
            return
        }

        if (lock) {
            try {
                val control = Camera2CameraControl.from(camera.cameraControl)
                control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                        // Lock AF to AUTO mode while locked, with trigger idle
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                        .build()
                )
            } catch (_: Throwable) {}
        }

        val future = camera.cameraControl.startFocusAndMetering(action)
        val resolved = AtomicBoolean(false)

        previewView.postDelayed({
            if (resolved.compareAndSet(false, true)) {
                _state.update { FocusState.Failed("timeout") }
                telemetry.log("af_fail", mapOf("reason" to "timeout"))
                if (!lock && retryOnce) {
                    scope.launch { performFocus(x, y, lock = false, retryOnce = false) }
                }
                if (torchAssist && lock) { try { camera.cameraControl.enableTorch(false) } catch (_: Throwable) {} }
            }
        }, 3000)

        future.addListener({
            try {
                val r = future.get()
                if (resolved.compareAndSet(false, true)) {
                    if (r.isFocusSuccessful) {
                        if (lock) _state.update { FocusState.Locked(x, y) } else _state.update { FocusState.Idle() }
                        telemetry.log("af_success")
                    } else {
                        _state.update { FocusState.Failed("af_fail") }
                        telemetry.log("af_fail")
                        if (!lock && retryOnce) {
                            scope.launch { performFocus(x, y, lock = false, retryOnce = false) }
                        }
                    }
                    if (torchAssist && lock) { try { camera.cameraControl.enableTorch(false) } catch (_: Throwable) {} }
                }
            } catch (t: Throwable) {
                Log.w(logTag, "focus metering error", t)
                if (resolved.compareAndSet(false, true)) {
                    _state.update { FocusState.Failed(t.message) }
                    telemetry.log("af_fail", mapOf("reason" to (t.message ?: "error")))
                    if (!lock && retryOnce) {
                        scope.launch { performFocus(x, y, lock = false, retryOnce = false) }
                    }
                    if (torchAssist && lock) { try { camera.cameraControl.enableTorch(false) } catch (_: Throwable) {} }
                }
            }
        }, mainExecutor)
    }
}
