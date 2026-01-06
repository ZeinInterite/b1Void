package com.example.b1void.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.b1void.core.camera.CameraControllerProvider
import com.example.b1void.core.ui.theme.B1VoidTheme
import com.example.b1void.feature.camera.presentation.screen.CameraScreen
import com.example.b1void.feature.camera.presentation.viewmodel.CameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@AndroidEntryPoint
class CameraActivity : ComponentActivity() {

    @Inject
    lateinit var cameraControllerProvider: CameraControllerProvider

    private lateinit var cameraExecutor: ExecutorService
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        } else {
            startCameraUI()
        }
    }

    private fun startCameraUI() {
        setContent {
            B1VoidTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val cameraViewModel: CameraViewModel = hiltViewModel()

                val previewView = remember { PreviewView(context) }

                LaunchedEffect(Unit) {
                    val cameraProvider = context.getCameraProvider()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview
                        )
                        val controller = cameraControllerProvider.get(
                            previewView = previewView,
                            camera = camera,
                            mainExecutor = ContextCompat.getMainExecutor(context)
                        )
                        cameraViewModel.setCameraController(controller)
                    } catch (e: Exception) {
                        Log.e("CameraActivity", "Use case binding failed", e)
                    }
                }

                CameraScreen(viewModel = cameraViewModel)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCameraUI()
            } else {
                finish() // If permissions are not granted, close the app
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener({
            continuation.resume(future.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

