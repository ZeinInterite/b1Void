package com.example.b1void.utils

import android.os.Build
import android.util.Size

/**
 * Управление особенностями камеры для различных производителей устройств
 */
object ManufacturerCompatibility {

    fun getCameraQuirks(): CameraQuirks {
        val manufacturer = DeviceInfo.manufacturer
        val model = DeviceInfo.model

        return when (manufacturer) {
            "samsung" -> SamsungCameraQuirks(model)
            "xiaomi", "redmi" -> XiaomiCameraQuirks(model)
            "huawei", "honor" -> HuaweiCameraQuirks(model)
            "oneplus" -> OnePlusCameraQuirks(model)
            "oppo", "realme" -> OppoCameraQuirks(model)
            "vivo" -> VivoCameraQuirks(model)
            "motorola", "lenovo" -> MotorolaCameraQuirks(model)
            else -> DefaultCameraQuirks()
        }
    }

    /**
     * Проверка, является ли устройство Xiaomi/Redmi
     */
    fun isXiaomiDevice(): Boolean {
        return DeviceInfo.isXiaomi()
    }

    /**
     * Получение версии MIUI из system properties
     * Возвращает null, если не удалось определить версию
     */
    fun getMiuiVersion(): Int? {
        return try {
            val miuiVersionName = getSystemProperty("ro.miui.ui.version.name")
            if (miuiVersionName.isNullOrEmpty()) {
                null
            } else {
                // Парсим версию из строки типа "V12", "V13", "V14"
                miuiVersionName.removePrefix("V").toIntOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Проверка, требуются ли Xiaomi-specific workarounds
     * Применяются для MIUI 12+ и всех Redmi устройств
     */
    fun requiresXiaomiWorkarounds(): Boolean {
        if (!isXiaomiDevice()) return false

        // Все Redmi устройства требуют workarounds
        if (DeviceInfo.model.contains("redmi")) return true

        // Для Mi устройств проверяем версию MIUI
        val miuiVersion = getMiuiVersion()
        return miuiVersion != null && miuiVersion >= 12
    }

    /**
     * Получение Xiaomi-specific camera quirks
     */
    fun getXiaomiCameraQuirks(): CameraQuirks {
        return if (isXiaomiDevice()) {
            XiaomiCameraQuirks(DeviceInfo.model)
        } else {
            DefaultCameraQuirks()
        }
    }

    /**
     * Чтение system property через reflection (Android internal API)
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            get.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }

    interface CameraQuirks {
        fun needsExtraDelay(): Boolean
        fun getInitDelayMs(): Long
        fun supportsFullHardwareLevel(): Boolean
        fun hasAutofocusIssues(): Boolean
        fun needsManualExposureWorkaround(): Boolean
        fun getRecommendedPreviewSize(): Size?
        fun getMaxRecommendedResolution(): Size?
        fun hasOisIssues(): Boolean
        fun hasEisIssues(): Boolean
        fun preferEisOverOis(): Boolean
        // Новые параметры для коррекции яркости на проблемных устройствах
        fun getEvCompensationBoost(): Float
        fun getPreferredIsoSensitivity(): Int?
        fun getMinIsoSensitivity(): Int?
        fun shouldDisableSceneModes(): Boolean

        // === Xiaomi Bug Fixes ===

        // Bug #2: Stretched photos - использовать фиксированный aspect ratio 4:3
        fun useHardcodedAspectRatio(): Boolean

        // Bug #5: Camera freeze - отключить определение hardware level
        fun disableHardwareLevel(): Boolean

        // Bug #4: Zoom not applied - использовать manual zoom с post-processing
        fun useManualZoom(): Boolean

        // Bug #3: Exposure control - ограничить EV range
        fun limitEvRange(): Boolean
        fun getEvRangeMin(): Float
        fun getEvRangeMax(): Float

        // Принудительное использование Legacy camera API
        fun forceLegacyCamera(): Boolean

        // Максимальное значение zoom ratio (для ограничения на проблемных устройствах)
        fun getMaxZoomRatio(): Float?

        // Bug #4: Задержка стабилизации zoom перед capture
        fun requiresZoomStabilizationDelay(): Boolean
        fun getZoomStabilizationDelayMs(): Long
    }

    class SamsungCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = model.contains("galaxy j") || model.contains("galaxy a10")
        override fun getInitDelayMs() = if (needsExtraDelay()) 300L else 100L
        override fun supportsFullHardwareLevel() = !model.contains("galaxy j")
        override fun hasAutofocusIssues() = model.contains("galaxy a10")
        override fun needsManualExposureWorkaround() = false
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(3840, 2160) // Max 4K
        // Samsung обычно хорошо поддерживает OIS на флагманах
        override fun hasOisIssues() = model.contains("galaxy j") || model.contains("galaxy a")
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = false
        // Samsung обычно не требует коррекции яркости
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        // Samsung не требует Xiaomi workarounds
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class XiaomiCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = model.contains("redmi")
        override fun getInitDelayMs() = when {
            model.contains("redmi 9") -> 500L
            model.contains("redmi 10") -> 400L   // Redmi 10 требует больше времени
            model.contains("redmi") -> 300L
            else -> 200L
        }
        override fun supportsFullHardwareLevel() = !model.contains("redmi")
        override fun hasAutofocusIssues() = model.contains("redmi note 8")
        override fun needsManualExposureWorkaround() = true
        override fun getRecommendedPreviewSize() = Size(1280, 720) // Консервативно для Xiaomi
        override fun getMaxRecommendedResolution() = if (model.contains("redmi")) {
            Size(1920, 1080) // FHD для бюджетных моделей
        } else {
            Size(3840, 2160) // 4K для флагманов
        }
        // Xiaomi/Redmi часто имеют проблемы с OIS на бюджетных моделях
        override fun hasOisIssues() = model.contains("redmi")
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = model.contains("redmi") // Для бюджетных лучше EIS

        // Коррекция яркости для Xiaomi/Redmi устройств
        // Многие устройства Xiaomi имеют консервативные настройки AE, что приводит к темным фото
        override fun getEvCompensationBoost() = when {
            model.contains("redmi note 10") -> 1.0f  // Redmi Note 10 особенно темный - увеличен boost
            model.contains("redmi note") -> 0.7f     // Другие модели Note - также увеличен
            model.contains("redmi") -> 0.5f          // Все бюджетные Redmi
            else -> 0.3f                             // Флагманы Mi менее проблемные
        }

        override fun getPreferredIsoSensitivity() = when {
            model.contains("redmi note 10") -> 600   // Повышенный ISO для Note 10 (было 400)
            model.contains("redmi") -> 300           // Умеренный ISO для других Redmi
            else -> null                             // Auto ISO для флагманов
        }

        override fun getMinIsoSensitivity() = when {
            model.contains("redmi") -> 200           // Минимум ISO 200 для бюджетных
            else -> null
        }

        // Отключаем сценарные режимы, которые могут принудительно затемнять
        override fun shouldDisableSceneModes() = model.contains("redmi")

        // === Xiaomi Bug Fixes Implementation ===

        // Bug #2: Stretched photos - принудительно использовать 4:3 aspect ratio
        override fun useHardcodedAspectRatio() = true  // Все Xiaomi устройства

        // Bug #5: Camera freeze - отключить hardware level detection для Redmi
        override fun disableHardwareLevel() = model.contains("redmi")

        // Bug #4: Zoom not applied - использовать manual zoom processing
        override fun useManualZoom() = true  // Критично для всех Xiaomi

        // Bug #3: Exposure control - ограничить EV range
        override fun limitEvRange() = true
        override fun getEvRangeMin() = if (model.contains("redmi")) -1.0f else -1.5f
        override fun getEvRangeMax() = 2.0f  // Положительные значения работают лучше

        // Использовать Legacy API только для очень старых устройств
        override fun forceLegacyCamera() = model.contains("redmi 8") || model.contains("redmi 9a")

        // Ограничение максимального zoom для стабильности
        override fun getMaxZoomRatio() = when {
            model.contains("redmi 10") -> 8.0f   // Redmi 10 имеет проблемы выше 8x
            model.contains("redmi") -> 6.0f      // Другие Redmi ограничены 6x
            else -> 10.0f                        // Mi устройства поддерживают 10x
        }

        // Bug #4: Задержка стабилизации zoom перед capture
        override fun requiresZoomStabilizationDelay() = true
        override fun getZoomStabilizationDelayMs() = when {
            model.contains("redmi 10") -> 100L   // Redmi 10 требует больше времени
            model.contains("redmi") -> 50L       // Другие Redmi
            else -> 30L                          // Mi устройства быстрее
        }
    }

    class HuaweiCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = true
        override fun getInitDelayMs() = 400L
        override fun supportsFullHardwareLevel() = model.contains("p30") || model.contains("mate")
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = true
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(3840, 2160)
        // Huawei имеет собственную реализацию, могут быть проблемы с CameraX API
        override fun hasOisIssues() = true
        override fun hasEisIssues() = true
        override fun preferEisOverOis() = false
        // Huawei обычно не требует коррекции яркости
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class OnePlusCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = false
        override fun getInitDelayMs() = 100L
        override fun supportsFullHardwareLevel() = true
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = false
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(3840, 2160)
        // OnePlus обычно хорошо работает с обоими видами стабилизации
        override fun hasOisIssues() = false
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = false
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class OppoCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = model.contains("a") // A-серия бюджетная
        override fun getInitDelayMs() = if (needsExtraDelay()) 300L else 200L
        override fun supportsFullHardwareLevel() = !model.contains("a5") && !model.contains("a3")
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = true
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(1920, 1080)
        // Oppo/Realme могут иметь проблемы на бюджетных моделях
        override fun hasOisIssues() = model.contains("a")
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = model.contains("a")
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class VivoCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = true
        override fun getInitDelayMs() = 300L
        override fun supportsFullHardwareLevel() = model.contains("x") || model.contains("nex")
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = true
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(1920, 1080)
        // Vivo может иметь проблемы со стабилизацией на некоторых моделях
        override fun hasOisIssues() = !model.contains("x") && !model.contains("nex")
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = false
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class MotorolaCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = model.contains("moto e") || model.contains("moto g")
        override fun getInitDelayMs() = if (needsExtraDelay()) 300L else 150L
        override fun supportsFullHardwareLevel() = !model.contains("moto e")
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = false
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = Size(2560, 1440)
        // Motorola обычно хорошо работает, но бюджетные E-серии могут не иметь OIS
        override fun hasOisIssues() = model.contains("moto e") || model.contains("moto g")
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = false
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }

    class DefaultCameraQuirks : CameraQuirks {
        override fun needsExtraDelay() = false
        override fun getInitDelayMs() = 100L
        override fun supportsFullHardwareLevel() = true
        override fun hasAutofocusIssues() = false
        override fun needsManualExposureWorkaround() = false
        override fun getRecommendedPreviewSize() = null
        override fun getMaxRecommendedResolution() = null
        // По умолчанию предполагаем, что устройство поддерживает стабилизацию
        override fun hasOisIssues() = false
        override fun hasEisIssues() = false
        override fun preferEisOverOis() = false
        override fun getEvCompensationBoost() = 0f
        override fun getPreferredIsoSensitivity() = null
        override fun getMinIsoSensitivity() = null
        override fun shouldDisableSceneModes() = false
        override fun useHardcodedAspectRatio() = false
        override fun disableHardwareLevel() = false
        override fun useManualZoom() = false
        override fun limitEvRange() = false
        override fun getEvRangeMin() = -2.0f
        override fun getEvRangeMax() = 2.0f
        override fun forceLegacyCamera() = false
        override fun getMaxZoomRatio() = null
        override fun requiresZoomStabilizationDelay() = false
        override fun getZoomStabilizationDelayMs() = 0L
    }
}

/**
 * XIAOMI BUG FIXES: Comprehensive logging and diagnostics
 */
object XiaomiCameraLogger {
    private const val TAG = "XiaomiCameraDebug"

    /**
     * Log camera configuration for Xiaomi debugging
     */
    fun logCameraConfig(camera: androidx.camera.core.Camera) {
        if (!ManufacturerCompatibility.isXiaomiDevice()) return

        android.util.Log.d(TAG, """
            ╔════════════════════════════════════════════════
            ║ XIAOMI CAMERA CONFIGURATION
            ╠════════════════════════════════════════════════
            ║ Device: ${Build.MODEL}
            ║ MIUI: ${ManufacturerCompatibility.getMiuiVersion() ?: "Unknown"}
            ║ Zoom range: ${camera.cameraInfo.zoomState.value?.minZoomRatio} - ${camera.cameraInfo.zoomState.value?.maxZoomRatio}
            ║ Current zoom: ${camera.cameraInfo.zoomState.value?.zoomRatio}
            ║ Exposure range: ${camera.cameraInfo.exposureState.exposureCompensationRange}
            ║ Exposure step: ${camera.cameraInfo.exposureState.exposureCompensationStep}
            ║ Flash available: ${camera.cameraInfo.hasFlashUnit()}
            ║ Torch state: ${camera.cameraInfo.torchState.value}
            ╚════════════════════════════════════════════════
        """.trimIndent())
    }

    /**
     * Log capture settings before taking photo
     */
    fun logCaptureSettings(zoomRatio: Float, evCompensation: Float) {
        if (!ManufacturerCompatibility.isXiaomiDevice()) return

        android.util.Log.d(TAG, """
            ║ CAPTURE SETTINGS
            ║ Zoom: ${String.format("%.2f", zoomRatio)}x
            ║ EV: ${String.format("%.2f", evCompensation)}
        """.trimIndent())
    }

    /**
     * Log image metadata after capture
     */
    fun logImageMetadata(file: java.io.File) {
        if (!ManufacturerCompatibility.isXiaomiDevice()) return

        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight
            val ratio = width.toFloat() / height.toFloat()

            android.util.Log.d(TAG, """
                ╔════════════════════════════════════════════════
                ║ IMAGE METADATA
                ╠════════════════════════════════════════════════
                ║ File: ${file.name}
                ║ Resolution: ${width}x${height}
                ║ Aspect ratio: ${String.format("%.3f", ratio)} (target: 1.333 for 4:3)
                ║ File size: ${file.length() / 1024} KB
                ╚════════════════════════════════════════════════
            """.trimIndent())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to log image metadata", e)
        }
    }

    /**
     * Log diagnostic summary for bug reports
     */
    fun logDiagnosticSummary() {
        if (!ManufacturerCompatibility.isXiaomiDevice()) return

        val quirks = ManufacturerCompatibility.getCameraQuirks()

        android.util.Log.i(TAG, """
            ╔════════════════════════════════════════════════
            ║ XIAOMI DIAGNOSTIC SUMMARY
            ╠════════════════════════════════════════════════
            ║ Device: ${Build.MANUFACTURER} ${Build.MODEL}
            ║ MIUI: ${ManufacturerCompatibility.getMiuiVersion() ?: "Unknown"}
            ║ Android: ${Build.VERSION.SDK_INT}
            ╠════════════════════════════════════════════════
            ║ ACTIVE WORKAROUNDS:
            ║ • Hardcoded 4:3 aspect ratio: ${quirks.useHardcodedAspectRatio()}
            ║ • Manual zoom processing: ${quirks.useManualZoom()}
            ║ • Limited EV range: ${quirks.limitEvRange()} (${quirks.getEvRangeMin()} to ${quirks.getEvRangeMax()})
            ║ • Zoom stabilization delay: ${quirks.requiresZoomStabilizationDelay()} (${quirks.getZoomStabilizationDelayMs()}ms)
            ║ • Max zoom ratio: ${quirks.getMaxZoomRatio() ?: "unlimited"}
            ║ • Init delay: ${quirks.getInitDelayMs()}ms
            ╚════════════════════════════════════════════════
        """.trimIndent())
    }
}
