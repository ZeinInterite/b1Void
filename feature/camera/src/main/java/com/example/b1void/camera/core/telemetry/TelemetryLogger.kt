package com.example.b1void.camera.core.telemetry

/** Simple telemetry logger for camera focus events. */
interface TelemetryLogger {
    fun log(event: String, extras: Map<String, Any?> = emptyMap())

    object NOOP : TelemetryLogger {
        override fun log(event: String, extras: Map<String, Any?>) { /* no-op */ }
    }
}

