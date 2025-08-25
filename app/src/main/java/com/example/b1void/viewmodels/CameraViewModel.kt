package com.example.b1void.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class CameraUiState(
    val currentResolution: Size? = null,
    val availableResolutions: List<Size> = emptyList(),
    @ImageCapture.FlashMode val flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val isWatermarkEnabled: Boolean = true,
    val isTorchOn: Boolean = false,
    val lastThumbnail: Bitmap? = null,
    val isBinding: Boolean = false,
    val isQualityPriority: Boolean = true // Default to quality
)

sealed class CameraEvent {
    data class Error(val message: String) : CameraEvent()
    object PictureSaved : CameraEvent()
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CameraEvent>()
    val event: SharedFlow<CameraEvent> = _event.asSharedFlow()

    fun cycleFlashMode() {
        val currentFlashMode = _uiState.value.flashMode
        val isTorchOn = _uiState.value.isTorchOn

        if (isTorchOn) {
            // Torch -> Auto
            _uiState.update { it.copy(isTorchOn = false, flashMode = ImageCapture.FLASH_MODE_AUTO) }
        } else {
            when (currentFlashMode) {
                ImageCapture.FLASH_MODE_AUTO -> {
                    // Auto -> On
                    _uiState.update { it.copy(flashMode = ImageCapture.FLASH_MODE_ON) }
                }
                ImageCapture.FLASH_MODE_ON -> {
                    // On -> Off
                    _uiState.update { it.copy(flashMode = ImageCapture.FLASH_MODE_OFF) }
                }
                ImageCapture.FLASH_MODE_OFF -> {
                    // Off -> Torch
                    _uiState.update { it.copy(isTorchOn = true, flashMode = ImageCapture.FLASH_MODE_OFF) }
                }
            }
        }
    }

    fun onResolutionSelected(resolution: Size) {
        _uiState.update { it.copy(isBinding = true, currentResolution = resolution) }
    }

    fun onWatermarkToggled() {
        _uiState.update { it.copy(isWatermarkEnabled = !it.isWatermarkEnabled) }
    }

    fun toggleQualityPriority() {
        _uiState.update { it.copy(isQualityPriority = !it.isQualityPriority) }
    }

    fun onTakePicture(imageCapture: ImageCapture?, savePath: String?) {
        val imageCapture = imageCapture ?: run {
            viewModelScope.launch { _event.emit(CameraEvent.Error("Camera is not ready.")) }
            return
        }

        val outputDirectory = if (savePath != null) {
            File(savePath)
        } else {
            val mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            File(mediaDir, "InspectorApp").apply { mkdirs() }
        }

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(getApplication()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraViewModel", "Photo capture failed: ${exc.message}", exc)
                    viewModelScope.launch {
                        _event.emit(CameraEvent.Error("Photo capture failed: ${exc.message}"))
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    Log.d("CameraViewModel", "Photo capture succeeded: $savedUri")
                    
                    viewModelScope.launch {
                        applyWatermark(savedUri)
                        createThumbnail(savedUri)
                        _event.emit(CameraEvent.PictureSaved)
                    }
                }
            }
        )
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private suspend fun applyWatermark(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (fileBytes == null) {
                    Log.e("CameraViewModel", "Failed to read file bytes from Uri")
                    return@withContext
                }

                var rotationAngle = 0f
                ByteArrayInputStream(fileBytes).use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    rotationAngle = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }

                val originalBitmap = BitmapFactory.decodeStream(ByteArrayInputStream(fileBytes))
                    ?: throw Exception("Failed to decode bitmap")

                val rotatedBitmap = if (rotationAngle != 0f) {
                    rotateBitmap(originalBitmap, rotationAngle)
                } else {
                    originalBitmap
                }

                val finalBitmap = if (_uiState.value.isWatermarkEnabled) {
                    val watermarkedBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(watermarkedBitmap)
                    val paint = Paint().apply {
                        color = Color.WHITE
                        textSize = 64f
                        isAntiAlias = true
                        setShadowLayer(5f, 2f, 2f, Color.BLACK)
                    }
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    canvas.drawText(date, 50f, 100f, paint)
                    watermarkedBitmap
                } else {
                    rotatedBitmap
                }

                val outputStream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                val byteArray = outputStream.toByteArray()

                context.contentResolver.openOutputStream(uri, "w")?.use { fileOutputStream ->
                    fileOutputStream.write(byteArray)
                }

                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exifInterface = ExifInterface(pfd.fileDescriptor)
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    exifInterface.saveAttributes()
                }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to process image", e)
                viewModelScope.launch {
                    _event.emit(CameraEvent.Error("Failed to process image: ${e.message}"))
                }
            }
        }
    }

    private suspend fun createThumbnail(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (fileBytes == null) {
                    Log.e("CameraViewModel", "Failed to read file bytes from Uri for thumbnail")
                    return@withContext
                }

                var rotationAngle = 0f
                ByteArrayInputStream(fileBytes).use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                    rotationAngle = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }

                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                val thumbnail = BitmapFactory.decodeStream(ByteArrayInputStream(fileBytes), null, options)

                thumbnail?.let {
                    val rotatedThumbnail = rotateBitmap(it, rotationAngle)
                    _uiState.update { state -> state.copy(lastThumbnail = rotatedThumbnail) }
                }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to create thumbnail", e)
            }
        }
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    fun onCameraBound(cameraInfo: CameraInfo) {
        val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
        val streamConfigurationMap = camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        
        val resolutions = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
        _uiState.update { it.copy(availableResolutions = resolutions) }
    }
    
    fun onRebindComplete() {
        _uiState.update { it.copy(isBinding = false) }
    }
}