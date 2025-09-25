package com.example.b1void.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

class CameraSettingsManager(private val context: Context) {

    companion object {
        val FLASH_ENABLED_KEY = intPreferencesKey("flash_mode")
        val TIMESTAMP_ENABLED_KEY = booleanPreferencesKey("timestamp_enabled")
        val RESOLUTION_KEY = stringPreferencesKey("resolution")
        
        // Orientation layout preferences
        val THUMBNAIL_POSITION_LANDSCAPE_KEY = stringPreferencesKey("thumbnail_position_landscape")
        val AUTO_HIDE_DELAY_KEY = intPreferencesKey("auto_hide_delay")
        val TRANSITION_SPEED_KEY = intPreferencesKey("transition_speed")
        val CAPTURE_BUTTON_SIZE_LANDSCAPE_KEY = booleanPreferencesKey("capture_button_size_landscape")
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

    fun isTimestampEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[TIMESTAMP_ENABLED_KEY] ?: true // Default to ON
        }
    }

    suspend fun setTimestampEnabled(isEnabled: Boolean) {
        context.dataStore.edit {
            it[TIMESTAMP_ENABLED_KEY] = isEnabled
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
}
