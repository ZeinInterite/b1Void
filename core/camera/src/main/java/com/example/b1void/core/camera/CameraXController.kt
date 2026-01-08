package com.example.b1void.core.camera

import android.app.Application
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import com.example.b1void.core.camera.quirks.DeviceQuirksManager
import com.example.b1void.core.domain.camera.CameraController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraXController(
    private val application: Application,
    private val previewView: PreviewView,
    private val camera: Camera,
    private val mainExecutor: Executor,
    private val deviceQuirksManager: DeviceQuirksManager
) : CameraController {

    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor: Executor = mainExecutor

    override fun initialize(imageCapture: ImageCapture) {
        this.imageCapture = imageCapture
    }

    override suspend fun takePicture(): String? = suspendCoroutine { continuation ->
        val outputDirectory = getOutputDirectory()
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: photoFile.toURI()
                    Log.d(TAG, "Photo capture succeeded: $savedUri")
                    continuation.resume(savedUri.toString())
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    continuation.resume(null)
                }
            }
        )
    }

    override fun setZoom(zoomRatio: Float) {
        camera.cameraControl.setZoomRatio(zoomRatio)
    }

    override fun toggleFlash(enable: Boolean) {
        camera.cameraControl.enableTorch(enable)
    }

    override fun setExposure(value: Float) {
        // Not implemented
    }

    private fun getOutputDirectory(): File {
        val mediaDir = application.externalMediaDirs.firstOrNull()?.let {
            File(it, "b1void").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else application.filesDir
    }

    companion object {
        private const val TAG = "CameraXController"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"

        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder,
                SimpleDateFormat(format, Locale.US).format(System.currentTimeMillis()) + extension
            )

        fun getOutputDirectory(application: Application): File {
            val mediaDir = application.externalMediaDirs.firstOrNull()?.let {
                File(it, "b1void").apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else application.filesDir
        }
    }
}
