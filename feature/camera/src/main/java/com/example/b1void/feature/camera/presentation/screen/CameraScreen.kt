package com.example.b1void.feature.camera.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.b1void.feature.camera.presentation.viewmodel.CameraViewModel

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { viewModel.capturePhoto() }) {
            Text(text = "Capture Photo")
        }
    }
}
