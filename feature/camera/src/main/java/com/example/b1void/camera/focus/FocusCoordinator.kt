package com.example.b1void.camera.focus

import java.util.concurrent.atomic.AtomicBoolean

class FocusCoordinator(
    private val engine: FocusEngine,
    private val callbacks: Callbacks,
    private val config: Config = Config(),
    private val telemetry: TelemetryLogger = TelemetryLogger.NOOP
) {
    private val logTag = "AEAF_EV"
    data class Config(
        // Автоотмена ручного тапа: по умолчанию 5 сек
        val tapAutoCancelSeconds: Int = 5,
        val showIndicatorOnCenter: Boolean = false,
        val focusTimeoutMs: Long = 3000L,
        val cafReturnDelayMs: Long = 1800L
    )

    interface FocusEngine {
        fun isSupportedAt(x: Float, y: Float, includeAeAwb: Boolean = true): Boolean
        fun startAt(
            x: Float,
            y: Float,
            includeAeAwb: Boolean = true,
            autoCancelSeconds: Int = 5,
            onResult: (success: Boolean) -> Unit
        )
        fun startCenter(
            includeAeAwb: Boolean = true,
            autoCancelSeconds: Int = 3,
            onResult: (success: Boolean) -> Unit
        )
        fun cancel()
        fun setAeLock(locked: Boolean)
    }

    interface Callbacks {
        fun showIndicator(x: Float, y: Float)
        fun hideIndicator()
        fun onFocusResult(success: Boolean)
        fun onLockChanged(locked: Boolean)
    }

    interface TelemetryLogger {
        fun log(event: String, extras: Map<String, Any?> = emptyMap())
        object NOOP : TelemetryLogger { override fun log(event: String, extras: Map<String, Any?>) {} }
    }

    private val locked = AtomicBoolean(false)
    private var lastPoint: Pair<Float, Float>? = null

    fun isLocked(): Boolean = locked.get()

    fun onSingleTap(x: Float, y: Float) {
        android.util.Log.d(logTag, "FocusCoordinator.onSingleTap(x=" + x + ", y=" + y + ") locked=" + locked.get())
        if (locked.get()) return
        telemetry.log("tap_focus", mapOf("x" to x, "y" to y))
        lastPoint = x to y
        callbacks.showIndicator(x, y)
        if (!engine.isSupportedAt(x, y, includeAeAwb = true)) {
            android.util.Log.w(logTag, "FocusCoordinator: metering not supported at point")
            callbacks.onFocusResult(false)
            callbacks.hideIndicator()
            telemetry.log("af_fail", mapOf("reason" to "not_supported"))
            return
        }
        engine.cancel()
        engine.startAt(
            x = x,
            y = y,
            includeAeAwb = true,
            autoCancelSeconds = config.tapAutoCancelSeconds
        ) { success ->
            callbacks.onFocusResult(success)
            telemetry.log(if (success) "af_success" else "af_fail")
        }
    }

    fun onLongPress(x: Float, y: Float) {
        android.util.Log.d(logTag, "FocusCoordinator.onLongPress(x=" + x + ", y=" + y + ") currentLocked=" + locked.get())
        if (locked.get()) {
            cancelAndUnlock()
            return
        }
        lastPoint = x to y
        engine.cancel()
        locked.set(true)
        callbacks.onLockChanged(true)
        callbacks.showIndicator(x, y)
        engine.setAeLock(true)
        engine.startAt(
            x = x,
            y = y,
            includeAeAwb = true,
            autoCancelSeconds = 0
        ) { success ->
            callbacks.onFocusResult(success)
            telemetry.log("ae_lock", mapOf("locked" to true, "success" to success))
        }
    }

    fun resetToCenter() {
        android.util.Log.d(logTag, "FocusCoordinator.resetToCenter()")
        engine.cancel()
        if (locked.getAndSet(false)) {
            engine.setAeLock(false)
            callbacks.onLockChanged(false)
            telemetry.log("ae_unlock")
        }
        engine.startCenter(includeAeAwb = true, autoCancelSeconds = 3) { success ->
            callbacks.onFocusResult(success)
            callbacks.hideIndicator()
        }
    }

    fun cancelAndUnlock() {
        android.util.Log.d(logTag, "FocusCoordinator.cancelAndUnlock()")
        engine.cancel()
        if (locked.getAndSet(false)) {
            engine.setAeLock(false)
            callbacks.onLockChanged(false)
        }
        callbacks.hideIndicator()
    }
}
