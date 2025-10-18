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
import kotlinx.coroutines.flow.first
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
import com.example.b1void.data.CameraSettingsManager
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
    val isQualityPriority: Boolean = true, // Default to quality
    // New camera settings
    val isoValue: Int = 100,
    val shutterSpeed: Long = 1000000L, // 1/1000 sec in nanoseconds
    val focusMode: String = "auto",
    val isOisEnabled: Boolean = false,
    val isEisEnabled: Boolean = false,
    val photoQuality: Int = 95
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

    private val settingsManager = CameraSettingsManager(application.applicationContext)

    init {
        // Load saved settings on initialization
        loadSettings()
    }

    /**
     * Загружает сохраненные настройки из DataStore и применяет их к UI state
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // Загружаем все настройки параллельно
                val torchEnabled = settingsManager.getTorchEnabled().first()
                val flashMode = settingsManager.getFlashMode().first()
                val isoValue = settingsManager.getIsoValue().first()
                val shutterSpeed = settingsManager.getShutterSpeed().first()
                val focusMode = settingsManager.getFocusMode().first()
                val oisEnabled = settingsManager.getOisEnabled().first()
                val eisEnabled = settingsManager.getEisEnabled().first()
                val photoQuality = settingsManager.getPhotoQuality().first()

                _uiState.update {
                    it.copy(
                        isTorchOn = torchEnabled,
                        flashMode = flashMode,
                        isoValue = isoValue,
                        shutterSpeed = shutterSpeed,
                        focusMode = focusMode,
                        isOisEnabled = oisEnabled,
                        isEisEnabled = eisEnabled,
                        photoQuality = photoQuality
                    )
                }
                Log.d("CameraViewModel", "Settings loaded: torch=$torchEnabled, flash=$flashMode, iso=$isoValue")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to load settings", e)
            }
        }
    }

    fun cycleFlashMode() {
        val currentFlashMode = _uiState.value.flashMode
        val isTorchOn = _uiState.value.isTorchOn

        if (isTorchOn) {
            // Torch -> Auto
            _uiState.update { it.copy(isTorchOn = false, flashMode = ImageCapture.FLASH_MODE_AUTO) }
            saveFlashSettings(false, ImageCapture.FLASH_MODE_AUTO)
        } else {
            when (currentFlashMode) {
                ImageCapture.FLASH_MODE_AUTO -> {
                    // Auto -> On
                    _uiState.update { it.copy(flashMode = ImageCapture.FLASH_MODE_ON) }
                    saveFlashSettings(false, ImageCapture.FLASH_MODE_ON)
                }
                ImageCapture.FLASH_MODE_ON -> {
                    // On -> Off
                    _uiState.update { it.copy(flashMode = ImageCapture.FLASH_MODE_OFF) }
                    saveFlashSettings(false, ImageCapture.FLASH_MODE_OFF)
                }
                ImageCapture.FLASH_MODE_OFF -> {
                    // Off -> Torch
                    _uiState.update { it.copy(isTorchOn = true, flashMode = ImageCapture.FLASH_MODE_OFF) }
                    saveFlashSettings(true, ImageCapture.FLASH_MODE_OFF)
                }
            }
        }
    }

    private fun saveFlashSettings(torchEnabled: Boolean, flashMode: Int) {
        viewModelScope.launch {
            try {
                settingsManager.setTorchEnabled(torchEnabled)
                settingsManager.setFlashMode(flashMode)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to save flash settings", e)
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

                // If watermarking is enabled, decode, modify, and overwrite the image.
                // This is a resource-intensive operation.
                if (_uiState.value.isWatermarkEnabled) {
                    val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (fileBytes == null) {
                        Log.e("CameraViewModel", "Failed to read file bytes for watermarking.")
                        return@withContext
                    }

                    val originalBitmap = BitmapFactory.decodeStream(ByteArrayInputStream(fileBytes))
                        ?: throw Exception("Failed to decode bitmap for watermarking")

                    val watermarkedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(watermarkedBitmap)
                    val paint = Paint().apply {
                        color = Color.RED
                        textSize = watermarkedBitmap.height / 20f // "Large" font size relative to image height
                        isAntiAlias = true
                        textAlign = Paint.Align.RIGHT // Align text to the right for easier positioning
                    }
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    
                    // Position text in the bottom-right corner with padding
                    val padding = watermarkedBitmap.height / 25f
                    val x = watermarkedBitmap.width - padding
                    val y = watermarkedBitmap.height - padding
                    canvas.drawText(date, x, y, paint)

                    // Overwrite the original file with the watermarked version
                    context.contentResolver.openOutputStream(uri, "w")?.use { fileOutputStream ->
                        watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fileOutputStream)
                    }
                    originalBitmap.recycle()
                    watermarkedBitmap.recycle()
                }

                // --- Force EXIF Orientation to Landscape (Normal) ---
                // This block runs regardless of whether the watermark is enabled.
                // It directly modifies the EXIF data of the saved file.
                setOrientationToNormal(context, uri)

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to process image", e)
                viewModelScope.launch {
                    _event.emit(CameraEvent.Error("Failed to process image: ${e.message}"))
                }
            }
        }
    }

    /**
     * Modifies the EXIF metadata of an image to force a standard orientation.
     * It sets the orientation tag to ORIENTATION_NORMAL (1), which is typically
     * interpreted as a landscape image that requires no rotation.
     *
     * @param context The application context to access the content resolver.
     * @param uri The URI of the JPEG file to modify.
     */
    private fun setOrientationToNormal(context: android.content.Context, uri: Uri) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exifInterface = ExifInterface(pfd.fileDescriptor)
                // Set orientation to NORMAL (landscape, no rotation needed)
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exifInterface.saveAttributes()
                Log.d("CameraViewModel", "Successfully set orientation for $uri")
            }
        } catch (e: Exception) {
            // Log error if we can't modify the EXIF data
            Log.e("CameraViewModel", "Failed to set EXIF orientation for $uri", e)
        }
    }

    private suspend fun createThumbnail(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                
                val thumbnail = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                thumbnail?.let {
                    // The image orientation is now always NORMAL, so no rotation is needed.
                    // The thumbnail will correctly appear as a landscape image.
                    _uiState.update { state -> state.copy(lastThumbnail = it) }
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

    // ==================== New Settings Methods ====================

    /**
     * Обновляет значение ISO и сохраняет в DataStore
     */
    fun setIsoValue(iso: Int) {
        _uiState.update { it.copy(isoValue = iso) }
        viewModelScope.launch {
            settingsManager.setIsoValue(iso)
        }
    }

    /**
     * Обновляет выдержку и сохраняет в DataStore
     */
    fun setShutterSpeed(shutterSpeed: Long) {
        _uiState.update { it.copy(shutterSpeed = shutterSpeed) }
        viewModelScope.launch {
            settingsManager.setShutterSpeed(shutterSpeed)
        }
    }

    /**
     * Обновляет режим фокусировки и сохраняет в DataStore
     */
    fun setFocusMode(mode: String) {
        _uiState.update { it.copy(focusMode = mode) }
        viewModelScope.launch {
            settingsManager.setFocusMode(mode)
        }
    }

    /**
     * Переключает OIS (оптическую стабилизацию) и сохраняет в DataStore
     */
    fun toggleOis() {
        val newState = !_uiState.value.isOisEnabled
        _uiState.update { it.copy(isOisEnabled = newState) }
        viewModelScope.launch {
            settingsManager.setOisEnabled(newState)
        }
    }

    /**
     * Переключает EIS (электронную стабилизацию) и сохраняет в DataStore
     */
    fun toggleEis() {
        val newState = !_uiState.value.isEisEnabled
        _uiState.update { it.copy(isEisEnabled = newState) }
        viewModelScope.launch {
            settingsManager.setEisEnabled(newState)
        }
    }

    /**
     * Обновляет качество фото и сохраняет в DataStore
     */
    fun setPhotoQuality(quality: Int) {
        _uiState.update { it.copy(photoQuality = quality) }
        viewModelScope.launch {
            settingsManager.setPhotoQuality(quality)
        }
    }
}