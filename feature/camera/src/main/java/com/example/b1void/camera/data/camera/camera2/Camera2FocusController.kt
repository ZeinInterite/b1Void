package com.example.b1void.camera.data.camera.camera2

import android.graphics.RectF
import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.data.camera.FocusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Camera2FocusController : FocusRepository {
    private val _state = MutableStateFlow<FocusState>(FocusState.Idle())
    override val state: Flow<FocusState> = _state.asStateFlow()
    private val _ev = MutableStateFlow(0f)
    override val evValue: Flow<Float> = _ev.asStateFlow()
    private val _step = MutableStateFlow(0.3333f)
    override val exposureStep: Flow<Float> = _step.asStateFlow()

    override suspend fun tapToFocus(x: Float, y: Float) {
        // TODO: implement direct Camera2 regions + capture session
        _state.value = FocusState.Failed("camera2_not_implemented")
    }

    override suspend fun longPressLock(x: Float, y: Float) {
        _state.value = FocusState.Failed("camera2_not_implemented")
    }

    override suspend fun unlock() { _state.value = FocusState.Idle() }
    override suspend fun startTracking(rect: RectF?, x: Float?, y: Float?) { _state.value = FocusState.Failed("camera2_not_implemented") }
    override suspend fun stopTracking() { _state.value = FocusState.Idle() }
    override suspend fun setEv(delta: Float) { _ev.value += delta }
    override suspend fun setAbsoluteEv(evValue: Float) { _ev.value = evValue }
    override suspend fun getEvRange(): ClosedFloatingPointRange<Float> = -2.0f..2.0f
    override suspend fun enableMacro(enabled: Boolean) { /* noop */ }
    override suspend fun enableTorchAssist(enabled: Boolean) { /* noop */ }
}
