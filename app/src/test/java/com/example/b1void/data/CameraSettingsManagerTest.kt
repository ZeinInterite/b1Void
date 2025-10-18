package com.example.b1void.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit тесты для CameraSettingsManager
 * Проверяет константы и значения по умолчанию
 */
class CameraSettingsManagerTest {

    @Test
    fun `test default ISO value constant`() {
        assertEquals("Default ISO should be 100", 100, CameraSettingsManager.DEFAULT_ISO)
    }

    @Test
    fun `test default shutter speed constant`() {
        assertEquals("Default shutter speed should be 1/1000 sec (1000000 nanoseconds)",
            1000000L, CameraSettingsManager.DEFAULT_SHUTTER_SPEED)
    }

    @Test
    fun `test default focus mode constant`() {
        assertEquals("Default focus mode should be 'auto'",
            "auto", CameraSettingsManager.DEFAULT_FOCUS_MODE)
    }

    @Test
    fun `test default photo quality constant`() {
        assertEquals("Default photo quality should be 95",
            95, CameraSettingsManager.DEFAULT_PHOTO_QUALITY)
    }

    @Test
    fun `test camera aspect ratio is 4_3`() {
        assertEquals("Camera aspect ratio should be fixed to 4:3 for landscape mode",
            androidx.camera.core.AspectRatio.RATIO_4_3,
            CameraSettingsManager.CAMERA_ASPECT_RATIO)
    }

    @Test
    fun `test preference keys are unique`() {
        val keys = setOf(
            CameraSettingsManager.FLASH_ENABLED_KEY.name,
            CameraSettingsManager.TORCH_ENABLED_KEY.name,
            CameraSettingsManager.RESOLUTION_KEY.name,
            CameraSettingsManager.VIDEO_QUALITY_KEY.name,
            CameraSettingsManager.ISO_VALUE_KEY.name,
            CameraSettingsManager.SHUTTER_SPEED_KEY.name,
            CameraSettingsManager.FOCUS_MODE_KEY.name,
            CameraSettingsManager.OIS_ENABLED_KEY.name,
            CameraSettingsManager.EIS_ENABLED_KEY.name,
            CameraSettingsManager.PHOTO_QUALITY_KEY.name
        )

        assertEquals("All preference keys should be unique", 10, keys.size)
    }

    @Test
    fun `test preference key naming convention`() {
        // Проверяем, что ключи используют snake_case
        val keysToCheck = listOf(
            CameraSettingsManager.FLASH_ENABLED_KEY.name to "flash_mode",
            CameraSettingsManager.TORCH_ENABLED_KEY.name to "torch_enabled",
            CameraSettingsManager.ISO_VALUE_KEY.name to "iso_value",
            CameraSettingsManager.SHUTTER_SPEED_KEY.name to "shutter_speed",
            CameraSettingsManager.FOCUS_MODE_KEY.name to "focus_mode",
            CameraSettingsManager.OIS_ENABLED_KEY.name to "ois_enabled",
            CameraSettingsManager.EIS_ENABLED_KEY.name to "eis_enabled",
            CameraSettingsManager.PHOTO_QUALITY_KEY.name to "photo_quality"
        )

        keysToCheck.forEach { (actual, expected) ->
            assertEquals("Key should follow snake_case naming convention", expected, actual)
        }
    }

    @Test
    fun `test default values are reasonable`() {
        // ISO should be in reasonable range (100-6400)
        assertTrue("Default ISO should be in reasonable range",
            CameraSettingsManager.DEFAULT_ISO in 100..6400)

        // Photo quality should be 0-100
        assertTrue("Default photo quality should be between 0 and 100",
            CameraSettingsManager.DEFAULT_PHOTO_QUALITY in 0..100)

        // Shutter speed should be positive
        assertTrue("Default shutter speed should be positive",
            CameraSettingsManager.DEFAULT_SHUTTER_SPEED > 0)
    }
}
