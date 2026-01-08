package com.example.b1void.feature.camera.presentation.screen

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.b1void.core.camera.CameraXController
import com.example.b1void.feature.camera.presentation.viewmodel.CameraViewModel
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val mainExecutor = ContextCompat.getMainExecutor(context)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                previewView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = { viewModel.capturePhoto() },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(text = "Capture Photo")
        }
    }

    DisposableEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases(
            cameraProvider,
            previewView,
            imageCapture,
            lifecycleOwner,
            mainExecutor,
            viewModel,
            context
        )

        onDispose {
            cameraProvider.unbindAll()
        }
    }
}

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    lifecycleOwner: LifecycleOwner,
    mainExecutor: Executor,
    viewModel: CameraViewModel,
    context: Context
) {
    val preview = Preview.Builder()
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    try {
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, preview, imageCapture
        )
        val entryPoint =
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.b1void.feature.camera.di.CameraProviderEntryPoint::class.java
            )
        val controller = entryPoint.cameraXControllerProvider().get(
            previewView = previewView,
            camera = camera,
            mainExecutor = mainExecutor
        )
        controller.initialize(imageCapture)
        viewModel.setCameraController(controller)
    } catch (exc: Exception) {
        // Log error
    }
}