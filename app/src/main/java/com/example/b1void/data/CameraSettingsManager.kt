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
}
