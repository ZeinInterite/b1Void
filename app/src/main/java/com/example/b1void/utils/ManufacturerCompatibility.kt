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
    }
}
