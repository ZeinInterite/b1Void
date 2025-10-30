package com.example.b1void.camera.focus

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal class CameraXFocusEngine(
    private val previewView: PreviewView,
    private val camera: Camera,
    private val mainExecutor: Executor,
    private val tag: String = "FocusEngine"
) : FocusCoordinator.FocusEngine {

    override fun isSupportedAt(x: Float, y: Float, includeAeAwb: Boolean): Boolean {
        // Используем систему координат превью, ориентированную по поверхности
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )
        val af = factory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(af, FocusMeteringAction.FLAG_AF)
        if (includeAeAwb) {
            builder.addPoint(af, FocusMeteringAction.FLAG_AE)
            try { builder.addPoint(af, FocusMeteringAction.FLAG_AWB) } catch (_: Throwable) {}
        }
        val action = builder.build()
        val supported = camera.cameraInfo.isFocusMeteringSupported(action)
        android.util.Log.d("AEAF_EV", "FocusEngine.isSupportedAt(x=" + x + ", y=" + y + ", aeAwb=" + includeAeAwb + ") -> " + supported)
        return supported
    }

    override fun startAt(
        x: Float,
        y: Float,
        includeAeAwb: Boolean,
        autoCancelSeconds: Int,
        onResult: (Boolean) -> Unit
    ) {
        // Используем SurfaceOrientedMeteringPointFactory для точного соответствия
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )
        val af = factory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(af, FocusMeteringAction.FLAG_AF)
        if (includeAeAwb) {
            builder.addPoint(af, FocusMeteringAction.FLAG_AE)
            try { builder.addPoint(af, FocusMeteringAction.FLAG_AWB) } catch (_: Throwable) {}
        }
        if (autoCancelSeconds <= 0) builder.disableAutoCancel()
        else builder.setAutoCancelDuration(autoCancelSeconds.toLong(), TimeUnit.SECONDS)
        val action = builder.build()
        android.util.Log.d("AEAF_EV", "FocusEngine.startAt(x=" + x + ", y=" + y + ", aeAwb=" + includeAeAwb + ", autoCancel=" + autoCancelSeconds + ")")
        val future = camera.cameraControl.startFocusAndMetering(action)
        val resolved = java.util.concurrent.atomic.AtomicBoolean(false)
        // Timeout fallback ~3s
        previewView.postDelayed({
            if (resolved.compareAndSet(false, true)) {
                Log.d(tag, "focus timeout -> fail")
                onResult(false)
            }
        }, 3000)
        future.addListener({
            try {
                val result = future.get()
                if (resolved.compareAndSet(false, true)) {
                    android.util.Log.d("AEAF_EV", "FocusEngine.startAt.result -> success=" + result.isFocusSuccessful)
                    onResult(result.isFocusSuccessful)
                }
            } catch (t: Throwable) {
                Log.w(tag, "startAt result error", t)
                if (resolved.compareAndSet(false, true)) onResult(false)
            }
        }, mainExecutor)
    }

    override fun startCenter(
        includeAeAwb: Boolean,
        autoCancelSeconds: Int,
        onResult: (Boolean) -> Unit
    ) {
        previewView.post {
            val w = previewView.width
            val h = previewView.height
            if (w <= 0 || h <= 0) {
                onResult(false)
                return@post
            }
            startAt(w / 2f, h / 2f, includeAeAwb, autoCancelSeconds, onResult)
        }
    }

    override fun cancel() {
        camera.cameraControl.cancelFocusAndMetering()
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    override fun setAeLock(locked: Boolean) {
        try {
            val control = Camera2CameraControl.from(camera.cameraControl)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
                .build()
            control.setCaptureRequestOptions(options)
            android.util.Log.d("AEAF_EV", "FocusEngine.setAeLock(" + locked + ")")
        } catch (_: Throwable) {
            // best effort; ignore if not supported
        }
    }
}
