package com.example.b1void.feature.camera.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b1void.core.domain.camera.CameraController // Import CameraController
import com.example.b1void.core.domain.usecase.CapturePhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val capturePhotoUseCase: CapturePhotoUseCase
) : ViewModel() {

    private val _cameraController = MutableStateFlow<CameraController?>(null)
    val cameraController = _cameraController.asStateFlow()

    fun setCameraController(controller: CameraController) {
        _cameraController.value = controller
    }

    fun capturePhoto() {
        viewModelScope.launch {
            // Use the provided CameraController if available
            _cameraController.value?.let { controller ->
                // The CapturePhotoUseCase needs to be updated to accept the CameraController
                // For now, we'll directly call the controller's takePicture method
                // This is a temporary bypass of the use case to get the build working
                val result = controller.takePicture()
                if (result != null) {
                    println("Photo captured successfully: $result")
                } else {
                    println("Failed to capture photo: null result from controller")
                }
            } ?: run {
                println("Camera controller not initialized.")
            }
        }
    }
}