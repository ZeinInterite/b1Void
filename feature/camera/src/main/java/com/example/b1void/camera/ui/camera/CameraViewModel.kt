package com.example.b1void.camera.ui.camera

import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.domain.camera.FocusInteractor
import kotlinx.coroutines.flow.StateFlow

/**
 * CameraViewModel
 *
 * State diagram (simplified):
 * Idle -> (tap) -> Metering -> (success/timeout) -> Idle
 * Idle -> (longPress) -> Locked -> (longPress/unlock) -> Idle
 * Any -> (doubleTap/startTracking) -> Tracking -> (stopTracking/lost) -> Idle
 */
class CameraViewModel(
    private val focusInteractor: FocusInteractor
): ViewModel() {

    val focusState: StateFlow<FocusState> = focusInteractor.state
    val evValue: StateFlow<Float> = focusInteractor.evValue

    suspend fun onTap(x: Float, y: Float) = focusInteractor.tapToFocus(x, y)
    suspend fun onLongPress(x: Float, y: Float) = focusInteractor.longPressLock(x, y)
    suspend fun onDoubleTap(target: android.graphics.RectF) = focusInteractor.startTracking(target)
    suspend fun onStopTracking() = focusInteractor.stopTracking()
    suspend fun onUnlock() = focusInteractor.unlock()
    fun onVerticalSwipe(evDelta: Int) = focusInteractor.adjustEv(evDelta)
    fun enableMacro(enabled: Boolean) = focusInteractor.enableMacro(enabled)
    fun enableTorchAssist(enabled: Boolean) = focusInteractor.enableTorchAssist(enabled)
}
