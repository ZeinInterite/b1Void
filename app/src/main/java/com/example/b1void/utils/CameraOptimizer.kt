package com.example.b1void.utils

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import com.example.b1void.B1VoidApplication
import com.otaliastudios.cameraview.size.SizeSelector
import com.otaliastudios.cameraview.size.SizeSelectors

object CameraOptimizer {

    private const val TAG = "CameraOptimizer"

    /**
     * Получает оптимальное разрешение для слабых устройств
     */
    fun getOptimalResolution(context: Context): Size {
        val isLowEnd = MemoryManager.isLowEndDevice(context)

        return if (isLowEnd) {
            Size(1280, 720) // 720p для слабых устройств
        } else {
            Size(1920, 1080) // 1080p для обычных устройств
        }
    }

    /**
     * Получает список поддерживаемых разрешений камеры
     */
    fun getSupportedResolutions(context: Context, cameraId: String? = null): List<Size> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val actualCameraId = cameraId ?: cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (actualCameraId != null) {
                val characteristics = cameraManager.getCameraCharacteristics(actualCameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                
                if (map != null) {
                    val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
                    
                    // Фильтруем и сортируем разрешения
                    val filteredSizes = jpegSizes
                        .filter { it.width >= 640 && it.height >= 480 } // Минимальное разрешение
                        .filter { it.width <= 4096 && it.height <= 4096 } // Максимальное разрешение
                        .sortedByDescending { it.width * it.height } // Сортируем по площади (от большего к меньшему)
                    
                    if (filteredSizes.isNotEmpty()) {
                        return filteredSizes
                    }
                }
            }
            
            // Fallback к предустановленным разрешениям если не удалось получить от камеры
            getFallbackResolutions(context)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения разрешений камеры", e)
            getFallbackResolutions(context)
        }
    }

    /**
     * Получает резервные разрешения для слабых устройств
     */
    private fun getFallbackResolutions(context: Context): List<Size> {
        val isLowEnd = MemoryManager.isLowEndDevice(context)
        
        return if (isLowEnd) {
            listOf(
                Size(1920, 1080), // 1080p
                Size(1280, 720),  // 720p
                Size(640, 480)    // 480p
            )
        } else {
            listOf(
                Size(3840, 2160), // 4K
                Size(2560, 1440), // 1440p
                Size(1920, 1080), // 1080p
                Size(1280, 720)   // 720p
            )
        }
    }

    /**
     * Создает оптимизированный селектор размера
     */
    fun createOptimizedSizeSelector(context: Context): SizeSelector {
        val isLowEnd = MemoryManager.isLowEndDevice(context)
        val maxSize = if (isLowEnd) 1280 else 1920

        // Используем правильные методы комбинирования селекторов из CameraView
        // Объединяем через логическое "И" - размер должен удовлетворять обоим условиям
        return SizeSelectors.and(
            SizeSelectors.maxWidth(maxSize),
            SizeSelectors.maxHeight(maxSize)
        )
    }

    /**
     * Получает оптимальные настройки качества изображения
     */
    fun getOptimalImageQuality(context: Context): Int {
        val isLowEnd = MemoryManager.isLowEndDevice(context)
        return if (isLowEnd) {
            B1VoidApplication.IMAGE_COMPRESSION_QUALITY - 10 // Снижаем качество на 10%
        } else {
            B1VoidApplication.IMAGE_COMPRESSION_QUALITY
        }
    }

    /**
     * Проверяет поддержку камеры
     */
    fun isCameraSupported(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки поддержки камеры", e)
            false
        }
    }

    /**
     * Получает информацию о камере
     */
    fun getCameraInfo(context: Context): Map<String, Any> {
        val info = mutableMapOf<String, Any>()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList

            info["camera_count"] = cameraIds.size
            info["supported_cameras"] = cameraIds.toList()

            // Получаем характеристики основной камеры
            if (cameraIds.isNotEmpty()) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraIds[0])

                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                info["sensor_orientation"] = sensorOrientation ?: 0

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                info["lens_facing"] = facing ?: CameraCharacteristics.LENS_FACING_BACK
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения информации о камере", e)
        }

        return info
    }

    /**
     * Оптимизирует настройки камеры для слабых устройств
     */
    fun getOptimizedCameraSettings(context: Context): Map<String, Any> {
        val isLowEnd = MemoryManager.isLowEndDevice(context)

        return mapOf(
            "resolution" to getOptimalResolution(context),
            "image_quality" to getOptimalImageQuality(context),
            "enable_hdr" to !isLowEnd,
            "enable_auto_focus" to true,
            "enable_flash" to true,
            "enable_stabilization" to !isLowEnd,
            "max_zoom" to if (isLowEnd) 2.0f else 4.0f,
            "enable_face_detection" to !isLowEnd,
            "enable_burst_mode" to !isLowEnd,
            "enable_raw_capture" to false // Отключаем RAW для экономии памяти
        )
    }

    /**
     * Проверяет, достаточно ли памяти для съемки
     */
    fun hasEnoughMemoryForCapture(context: Context): Boolean {
        val requiredMemory = 50 * 1024 * 1024L // 50MB для съемки
        return MemoryManager.hasEnoughMemory(context, requiredMemory)
    }

    /**
     * Получает рекомендуемые настройки для слабых устройств
     */
    fun getLowEndDeviceRecommendations(): List<String> {
        return listOf(
            "Используйте разрешение 720p вместо 1080p",
            "Отключите HDR для экономии ресурсов",
            "Используйте JPEG вместо RAW",
            "Ограничьте зум до 2x",
            "Отключите стабилизацию изображения",
            "Используйте автоматический режим фокусировки",
            "Ограничьте размер кэша изображений"
        )
    }

    /**
     * Оптимизирует обработку изображений
     */
    fun optimizeImageProcessing(context: Context): Map<String, Any> {
        val isLowEnd = MemoryManager.isLowEndDevice(context)

        return mapOf(
            "max_image_size" to if (isLowEnd) 800 else B1VoidApplication.MAX_IMAGE_SIZE,
            "compression_quality" to getOptimalImageQuality(context),
            "enable_thumbnail_generation" to !isLowEnd,
            "enable_metadata_extraction" to !isLowEnd,
            "enable_face_detection" to !isLowEnd,
            "enable_scene_detection" to !isLowEnd,
            "enable_auto_enhancement" to !isLowEnd
        )
    }
}