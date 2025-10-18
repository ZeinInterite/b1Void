package com.example.b1void.data

import android.content.Context
import android.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first

object AppSettingsBootstrap {
    fun init(context: Context) {
        // First-launch defaults
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val firstRunDone = prefs.getBoolean("first_run_done", false)
            if (!firstRunDone) {
                // Initialize DataStore defaults
                val mgr = CameraSettingsManager(context)
                runBlocking {
                    runCatching { mgr.setFlashMode(0) }
                    runCatching { mgr.setTorchEnabled(false) }
                    runCatching { mgr.setVideoQuality(720) }
                    runCatching { mgr.setVideoRecordDelay(800) }
                    // Resolution default (let CameraActivity ensure default if missing)
                }
                // Initialize SharedPreferences defaults
                prefs.edit()
                    .putInt("sort_mode", 0) // DATE_ASC ordinal
                    .putBoolean("first_run_done", true)
                    .apply()
            }
        } catch (_: Exception) { /* ignore */ }
        // Restore camera settings from DataStore
        try {
            val mgr = CameraSettingsManager(context)
            runBlocking {
                withTimeout(500L) {
                    // Read once to warm up cache
                    AppSettingsCache.flashMode = runCatching { mgr.getFlashMode().first() }.getOrElse { 0 }
                    AppSettingsCache.torchEnabled = runCatching { mgr.getTorchEnabled().first() }.getOrElse { false }
                    AppSettingsCache.resolution = runCatching { mgr.getResolution().first() }.getOrNull()
                    AppSettingsCache.videoQuality = runCatching { mgr.getVideoQuality().first() }.getOrElse { 720 }
                    AppSettingsCache.videoRecordDelayMs = runCatching { mgr.getVideoRecordDelay().first() }.getOrElse { 800 }
                }
            }
        } catch (_: Exception) { /* ignore */ }

        // Restore file manager sorting from SharedPreferences
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            AppSettingsCache.sortModeOrdinal = prefs.getInt("sort_mode", 0)
        } catch (_: Exception) { /* ignore */ }
    }
}
