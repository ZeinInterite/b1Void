package com.example.b1void.utils

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
    }

    class XiaomiCameraQuirks(private val model: String) : CameraQuirks {
        override fun needsExtraDelay() = model.contains("redmi")
        override fun getInitDelayMs() = when {
            model.contains("redmi 9") -> 500L
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
            model.contains("redmi note 10") -> 0.7f  // Redmi Note 10 особенно темный
            model.contains("redmi note") -> 0.5f     // Другие модели Note
            model.contains("redmi") -> 0.5f          // Все бюджетные Redmi
            else -> 0.3f                             // Флагманы Mi менее проблемные
        }

        override fun getPreferredIsoSensitivity() = when {
            model.contains("redmi note 10") -> 400   // Повышенный ISO для Note 10
            model.contains("redmi") -> 300           // Умеренный ISO для других Redmi
            else -> null                             // Auto ISO для флагманов
        }

        override fun getMinIsoSensitivity() = when {
            model.contains("redmi") -> 200           // Минимум ISO 200 для бюджетных
            else -> null
        }

        // Отключаем сценарные режимы, которые могут принудительно затемнять
        override fun shouldDisableSceneModes() = model.contains("redmi")
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
    }
}
