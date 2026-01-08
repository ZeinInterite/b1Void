package com.example.b1void.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.b1void.core.camera.CameraControllerProvider
import com.example.b1void.core.domain.camera.CameraController
import com.example.b1void.core.ui.theme.B1VoidTheme
import com.example.b1void.feature.camera.CameraUiEvent
import com.example.b1void.feature.camera.CameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class CameraActivity : ComponentActivity() {

    @Inject
    lateinit var cameraControllerProvider: CameraControllerProvider

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCameraUI()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraUI()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraUI() {
        setContent {
            B1VoidTheme {
                val cameraViewModel: CameraViewModel = hiltViewModel()
                CameraScreenContent(
                    provider = cameraControllerProvider,
                    viewModel = cameraViewModel
                )
            }
        }
    }
}

@Composable
fun CameraScreenContent(
    provider: CameraControllerProvider,
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    // The controller is now created and remembered within the composable's lifecycle
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }

    // Collect UI events from the ViewModel
    LaunchedEffect(viewModel, cameraController) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is CameraUiEvent.CapturePhoto -> {
                    cameraController?.let { controller ->
                        scope.launch {
                            val filePath = controller.takePicture()
                            if (filePath != null) {
                                Log.d("CameraActivity", "Photo saved at: $filePath")
                                // We could call another viewModel method here to save the path
                                // viewModel.onPhotoCaptured(filePath)
                            } else {
                                Log.e("CameraActivity", "Failed to save photo")
                            }
                        }
                    }
                }
            }
        }
    }

    // Initialize the camera
    LaunchedEffect(provider) {
        try {
            val cameraProvider = context.getCameraProvider()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            
            // Create the controller using the provider now that we have the camera object
            val mainExecutor = ContextCompat.getMainExecutor(context)
            cameraController = provider.get(previewView, camera, mainExecutor).also {
                // Noticed the controller has an initialize method, let's call it.
                // This is a bit redundant if the controller is created with everything it needs,
                // but following the existing pattern.
                (it as? com.example.b1void.core.camera.CameraXController)?.initialize(imageCapture)
            }
            isCameraReady = true

        } catch (e: Exception) {
            Log.e("CameraActivity", "Camera init failed", e)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            if (!isCameraReady) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Button(
                onClick = { viewModel.capturePhoto() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(72.dp),
                enabled = isCameraReady
            ) {
                Text("📷", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    return ProcessCameraProvider.getInstance(this).await()
}
