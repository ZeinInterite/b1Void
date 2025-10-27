package com.example.b1void.camera.ui.camera

import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.domain.camera.FocusInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // EV Range state - получаем из camera после инициализации
    private val _evRange = MutableStateFlow(-2.0f..2.0f)
    val evRange: StateFlow<ClosedFloatingPointRange<Float>> = _evRange.asStateFlow()

    suspend fun onTap(x: Float, y: Float) = focusInteractor.tapToFocus(x, y)
    suspend fun onLongPress(x: Float, y: Float) = focusInteractor.longPressLock(x, y)
    suspend fun onDoubleTap(target: android.graphics.RectF) = focusInteractor.startTracking(target)
    suspend fun onStopTracking() = focusInteractor.stopTracking()
    suspend fun onUnlock() = focusInteractor.unlock()
    fun onVerticalSwipe(evDelta: Int) = focusInteractor.adjustEv(evDelta)
    fun enableMacro(enabled: Boolean) = focusInteractor.enableMacro(enabled)
    fun enableTorchAssist(enabled: Boolean) = focusInteractor.enableTorchAssist(enabled)

    /**
     * Устанавливает абсолютное значение экспозиции
     * @param evValue значение EV от -2.0 до +2.0
     */
    fun setExposureCompensation(evValue: Float) {
        focusInteractor.setAbsoluteEv(evValue)
    }

    /**
     * Сбрасывает экспозицию на 0.0
     */
    fun resetExposureCompensation() {
        focusInteractor.setAbsoluteEv(0.0f)
    }

    /**
     * Инициализирует диапазон EV для данной камеры
     * Должно вызываться после привязки камеры
     */
    fun initializeEvRange() {
        viewModelScope.launch {
            try {
                val range = focusInteractor.getEvRange()
                _evRange.value = range
            } catch (e: Exception) {
                // В случае ошибки используем значения по умолчанию
                _evRange.value = -2.0f..2.0f
            }
        }
    }
}
