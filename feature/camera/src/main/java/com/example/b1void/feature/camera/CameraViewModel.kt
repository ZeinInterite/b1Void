package com.example.b1void.feature.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CameraUiEvent {
    data object CapturePhoto : CameraUiEvent
}

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _uiEvents = Channel<CameraUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun capturePhoto() {
        viewModelScope.launch {
            _uiEvents.send(CameraUiEvent.CapturePhoto)
        }
    }
}
