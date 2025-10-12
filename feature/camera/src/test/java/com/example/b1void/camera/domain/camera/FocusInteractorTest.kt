package com.example.b1void.camera.domain.camera

import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.data.camera.FocusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepo : FocusRepository {
    private val _state = MutableStateFlow<FocusState>(FocusState.Idle())
    override val state: Flow<FocusState> = _state.asStateFlow()
    private val _ev = MutableStateFlow(0f)
    override val evValue: Flow<Float> = _ev.asStateFlow()

    override suspend fun tapToFocus(x: Float, y: Float) { _state.value = FocusState.Metering(x,y) }
    override suspend fun longPressLock(x: Float, y: Float) { _state.value = FocusState.Locked(x,y) }
    override suspend fun unlock() { _state.value = FocusState.Idle() }
    override suspend fun startTracking(rect: android.graphics.RectF?, x: Float?, y: Float?) { _state.value = FocusState.Tracking(rect,x,y) }
    override suspend fun stopTracking() { _state.value = FocusState.Idle() }
    override suspend fun setEv(delta: Float) { _ev.value += delta }
    override suspend fun enableMacro(enabled: Boolean) {}
    override suspend fun enableTorchAssist(enabled: Boolean) {}
}

class FocusInteractorTest {
    @Test
    fun tap_movesToMetering() = runBlocking {
        val repo = FakeRepo()
        val interactor = FocusInteractor(repo, this)
        interactor.tapToFocus(10f, 20f)
        assertTrue(interactor.state.value is FocusState.Metering)
    }
}

