package com.example.b1void.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

class CameraSettingsManager(private val context: Context) {

    companion object {
        // Flash and torch settings
        val FLASH_ENABLED_KEY = intPreferencesKey("flash_mode")
        val TORCH_ENABLED_KEY = booleanPreferencesKey("torch_enabled")

        // Resolution and quality settings
        val RESOLUTION_KEY = stringPreferencesKey("resolution")
        val VIDEO_QUALITY_KEY = intPreferencesKey("video_quality")

        // Manual camera controls
        val ISO_VALUE_KEY = intPreferencesKey("iso_value")
        val SHUTTER_SPEED_KEY = longPreferencesKey("shutter_speed")
        val FOCUS_MODE_KEY = stringPreferencesKey("focus_mode")

        // Stabilization settings
        val OIS_ENABLED_KEY = booleanPreferencesKey("ois_enabled")
        val EIS_ENABLED_KEY = booleanPreferencesKey("eis_enabled")

        // Photo quality settings
        val PHOTO_QUALITY_KEY = intPreferencesKey("photo_quality")

        // Exposure compensation
        val EV_COMPENSATION_KEY = floatPreferencesKey("ev_compensation")

        // Xiaomi brightness boost settings
        val XIAOMI_BRIGHTNESS_BOOST_ENABLED_KEY = booleanPreferencesKey("xiaomi_brightness_boost_enabled")
        val CUSTOM_EV_COMPENSATION_KEY = floatPreferencesKey("custom_ev_compensation")

        // Orientation layout preferences
        val THUMBNAIL_POSITION_LANDSCAPE_KEY = stringPreferencesKey("thumbnail_position_landscape")
        val AUTO_HIDE_DELAY_KEY = intPreferencesKey("auto_hide_delay")
        val TRANSITION_SPEED_KEY = intPreferencesKey("transition_speed")
        val CAPTURE_BUTTON_SIZE_LANDSCAPE_KEY = booleanPreferencesKey("capture_button_size_landscape")

        // Video recording delay preference
        val VIDEO_RECORD_DELAY_KEY = intPreferencesKey("video_record_delay")

        // Orientation lock (true = force landscape; false = allow auto-rotate)
        val ORIENTATION_LOCK_ENABLED_KEY = booleanPreferencesKey("orientation_lock_enabled")

        // Default values
        const val DEFAULT_ISO = 100
        const val DEFAULT_SHUTTER_SPEED = 1000000L // 1/1000 sec in nanoseconds
        const val DEFAULT_FOCUS_MODE = "auto"
        const val DEFAULT_PHOTO_QUALITY = 95

        // Camera aspect ratio - фиксированное соотношение 4:3 для альбомной ориентации
        const val CAMERA_ASPECT_RATIO = androidx.camera.core.AspectRatio.RATIO_4_3
    }

    fun getFlashMode(): Flow<Int> {
        return context.dataStore.data.map {
            it[FLASH_ENABLED_KEY] ?: 0 // Default to OFF
        }
    }

    suspend fun setFlashMode(mode: Int) {
        context.dataStore.edit {
            it[FLASH_ENABLED_KEY] = mode
        }
    }

    fun getResolution(): Flow<String?> {
        return context.dataStore.data.map {
            it[RESOLUTION_KEY]
        }
    }

    suspend fun setResolution(resolution: String) {
        context.dataStore.edit {
            it[RESOLUTION_KEY] = resolution
        }
    }

    fun getTorchEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[TORCH_ENABLED_KEY] ?: false // Default to OFF
        }
    }

    suspend fun setTorchEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[TORCH_ENABLED_KEY] = enabled
        }
    }

    // Orientation layout preference methods
    
    /**
     * Get thumbnail position preference for landscape mode
     * @return "center" or "right" (default: "center")
     */
    fun getThumbnailPositionLandscape(): Flow<String> {
        return context.dataStore.data.map {
            it[THUMBNAIL_POSITION_LANDSCAPE_KEY] ?: "center"
        }
    }
    
    suspend fun setThumbnailPositionLandscape(position: String) {
        context.dataStore.edit {
            it[THUMBNAIL_POSITION_LANDSCAPE_KEY] = position
        }
    }
    
    /**
     * Get auto-hide delay in milliseconds for landscape mode
     * @return delay in ms (default: 3000)
     */
    fun getAutoHideDelay(): Flow<Int> {
        return context.dataStore.data.map {
            it[AUTO_HIDE_DELAY_KEY] ?: 3000
        }
    }
    
    suspend fun setAutoHideDelay(delayMs: Int) {
        context.dataStore.edit {
            it[AUTO_HIDE_DELAY_KEY] = delayMs
        }
    }
    
    /**
     * Get transition speed in milliseconds
     * @return speed in ms (default: 300)
     */
    fun getTransitionSpeed(): Flow<Int> {
        return context.dataStore.data.map {
            it[TRANSITION_SPEED_KEY] ?: 300
        }
    }
    
    suspend fun setTransitionSpeed(speedMs: Int) {
        context.dataStore.edit {
            it[TRANSITION_SPEED_KEY] = speedMs
        }
    }
    
    /**
     * Get capture button size enhancement preference for landscape
     * @return true if enhanced size enabled (default: true)
     */
    fun isCaptureButtonSizeLandscapeEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[CAPTURE_BUTTON_SIZE_LANDSCAPE_KEY] ?: true
        }
    }
    
    suspend fun setCaptureButtonSizeLandscapeEnabled(isEnabled: Boolean) {
        context.dataStore.edit {
            it[CAPTURE_BUTTON_SIZE_LANDSCAPE_KEY] = isEnabled
        }
    }

    /**
     * Get video recording delay in milliseconds
     * @return delay in ms (default: 800)
     */
    fun getVideoRecordDelay(): Flow<Int> {
        return context.dataStore.data.map {
            it[VIDEO_RECORD_DELAY_KEY] ?: 800
        }
    }

    suspend fun setVideoRecordDelay(delayMs: Int) {
        context.dataStore.edit {
            it[VIDEO_RECORD_DELAY_KEY] = delayMs
        }
    }

    /**
     * Get preferred video quality (one of 2160, 1080, 720, 480)
     * default: 720 (HD)
     */
    fun getVideoQuality(): Flow<Int> {
        return context.dataStore.data.map {
            it[VIDEO_QUALITY_KEY] ?: 720
        }
    }

    /**
     * Orientation lock preference
     * true  -> force landscape (default)
     * false -> allow system auto-rotate (portrait + landscape)
     */
    fun isOrientationLockEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[ORIENTATION_LOCK_ENABLED_KEY] ?: true
        }
    }

    suspend fun setOrientationLockEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[ORIENTATION_LOCK_ENABLED_KEY] = enabled
        }
    }

    suspend fun setVideoQuality(quality: Int) {
        context.dataStore.edit {
            it[VIDEO_QUALITY_KEY] = quality
        }
    }

    // ==================== Manual Camera Controls ====================

    /**
     * Get ISO value
     * @return ISO value (default: 100)
     */
    fun getIsoValue(): Flow<Int> {
        return context.dataStore.data.map {
            it[ISO_VALUE_KEY] ?: DEFAULT_ISO
        }
    }

    /**
     * Set ISO value
     * @param iso ISO value to save
     */
    suspend fun setIsoValue(iso: Int) {
        try {
            context.dataStore.edit {
                it[ISO_VALUE_KEY] = iso
            }
            android.util.Log.d("CameraSettingsManager", "ISO saved: $iso")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save ISO", e)
        }
    }

    /**
     * Get shutter speed in nanoseconds
     * @return Shutter speed in nanoseconds (default: 1/1000 sec)
     */
    fun getShutterSpeed(): Flow<Long> {
        return context.dataStore.data.map {
            it[SHUTTER_SPEED_KEY] ?: DEFAULT_SHUTTER_SPEED
        }
    }

    /**
     * Set shutter speed in nanoseconds
     * @param shutterSpeed Shutter speed in nanoseconds
     */
    suspend fun setShutterSpeed(shutterSpeed: Long) {
        try {
            context.dataStore.edit {
                it[SHUTTER_SPEED_KEY] = shutterSpeed
            }
            android.util.Log.d("CameraSettingsManager", "Shutter speed saved: $shutterSpeed ns")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save shutter speed", e)
        }
    }

    /**
     * Get focus mode
     * @return Focus mode: "auto", "manual", "continuous" (default: "auto")
     */
    fun getFocusMode(): Flow<String> {
        return context.dataStore.data.map {
            it[FOCUS_MODE_KEY] ?: DEFAULT_FOCUS_MODE
        }
    }

    /**
     * Set focus mode
     * @param mode Focus mode: "auto", "manual", "continuous"
     */
    suspend fun setFocusMode(mode: String) {
        try {
            context.dataStore.edit {
                it[FOCUS_MODE_KEY] = mode
            }
            android.util.Log.d("CameraSettingsManager", "Focus mode saved: $mode")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save focus mode", e)
        }
    }

    // ==================== Stabilization Settings ====================

    /**
     * Get OIS (Optical Image Stabilization) enabled state
     * @return true if OIS enabled (default: false)
     */
    fun getOisEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[OIS_ENABLED_KEY] ?: false
        }
    }

    /**
     * Set OIS enabled state
     * @param enabled true to enable OIS
     */
    suspend fun setOisEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit {
                it[OIS_ENABLED_KEY] = enabled
            }
            android.util.Log.d("CameraSettingsManager", "OIS enabled: $enabled")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save OIS state", e)
        }
    }

    /**
     * Get EIS (Electronic Image Stabilization) enabled state
     * @return true if EIS enabled (default: false)
     */
    fun getEisEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[EIS_ENABLED_KEY] ?: false
        }
    }

    /**
     * Set EIS enabled state
     * @param enabled true to enable EIS
     */
    suspend fun setEisEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit {
                it[EIS_ENABLED_KEY] = enabled
            }
            android.util.Log.d("CameraSettingsManager", "EIS enabled: $enabled")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save EIS state", e)
        }
    }

    // ==================== Photo Quality ====================

    /**
     * Get photo quality (JPEG compression quality)
     * @return Quality 0-100 (default: 95)
     */
    fun getPhotoQuality(): Flow<Int> {
        return context.dataStore.data.map {
            it[PHOTO_QUALITY_KEY] ?: DEFAULT_PHOTO_QUALITY
        }
    }

    /**
     * Set photo quality (JPEG compression quality)
     * @param quality Quality 0-100
     */
    suspend fun setPhotoQuality(quality: Int) {
        try {
            context.dataStore.edit {
                it[PHOTO_QUALITY_KEY] = quality.coerceIn(0, 100)
            }
            android.util.Log.d("CameraSettingsManager", "Photo quality saved: $quality")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save photo quality", e)
        }
    }

    // ==================== Exposure Compensation ====================

    /**
     * Get exposure compensation value in EV stops
     * @return EV value from -2.0 to +2.0 (default: 0.0)
     */
    fun getEvCompensation(): Flow<Float> {
        return context.dataStore.data.map {
            it[EV_COMPENSATION_KEY] ?: 0.0f
        }
    }

    /**
     * Set exposure compensation value in EV stops
     * @param evValue EV value from -2.0 to +2.0
     */
    suspend fun setEvCompensation(evValue: Float) {
        try {
            context.dataStore.edit {
                it[EV_COMPENSATION_KEY] = evValue.coerceIn(-2.0f, 2.0f)
            }
            android.util.Log.d("CameraSettingsManager", "EV compensation saved: $evValue")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save EV compensation", e)
        }
    }

    /**
     * Per-camera EV getters/setters using a dynamic key composed from
     * manufacturer_model_cameraId_lensFacing. Falls back to global EV if absent.
     */
    fun getEvCompensationFor(cameraKey: String): Flow<Float> {
        val key = floatPreferencesKey("ev_compensation_" + cameraKey)
        return context.dataStore.data.map { prefs ->
            prefs[key] ?: prefs[EV_COMPENSATION_KEY] ?: 0.0f
        }
    }

    suspend fun setEvCompensationFor(cameraKey: String, evValue: Float) {
        val key = floatPreferencesKey("ev_compensation_" + cameraKey)
        try {
            context.dataStore.edit {
                it[key] = evValue.coerceIn(-2.0f, 2.0f)
            }
            android.util.Log.d(
                "CameraSettingsManager",
                "EV compensation for $cameraKey saved: $evValue"
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "CameraSettingsManager",
                "Failed to save EV compensation for $cameraKey",
                e
            )
        }
    }

    // ==================== Xiaomi Brightness Boost Settings ====================

    /**
     * Get Xiaomi brightness boost enabled state
     * @return true if brightness boost is enabled (default: true for Xiaomi devices)
     */
    fun getXiaomiBrightnessBoostEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[Companion.XIAOMI_BRIGHTNESS_BOOST_ENABLED_KEY] ?: true
        }
    }

    /**
     * Set Xiaomi brightness boost enabled state
     * @param enabled true to enable automatic brightness boost for Xiaomi devices
     */
    suspend fun setXiaomiBrightnessBoostEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit {
                it[Companion.XIAOMI_BRIGHTNESS_BOOST_ENABLED_KEY] = enabled
            }
            android.util.Log.d("CameraSettingsManager", "Xiaomi brightness boost enabled: $enabled")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save Xiaomi brightness boost state", e)
        }
    }

    /**
     * Get custom EV compensation value for manual brightness adjustment
     * @return Custom EV value from 0.0 to +2.0 (default: 0.0 = use manufacturer defaults)
     */
    fun getCustomEvCompensation(): Flow<Float> {
        return context.dataStore.data.map {
            it[Companion.CUSTOM_EV_COMPENSATION_KEY] ?: 0.0f
        }
    }

    /**
     * Set custom EV compensation value for manual brightness adjustment
     * @param value Custom EV value from 0.0 to +2.0 (0.0 = use manufacturer defaults)
     */
    suspend fun setCustomEvCompensation(value: Float) {
        try {
            context.dataStore.edit {
                it[Companion.CUSTOM_EV_COMPENSATION_KEY] = value.coerceIn(0.0f, 2.0f)
            }
            android.util.Log.d("CameraSettingsManager", "Custom EV compensation saved: $value")
        } catch (e: Exception) {
            android.util.Log.e("CameraSettingsManager", "Failed to save custom EV compensation", e)
        }
    }
}
