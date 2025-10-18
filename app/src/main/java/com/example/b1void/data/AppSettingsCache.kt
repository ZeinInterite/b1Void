package com.example.b1void.data

/**
 * In-memory cache of user settings restored on app startup.
 * This helps initialize screens with persisted values immediately.
 */
object AppSettingsCache {
    // Camera settings
    @Volatile var flashMode: Int = 0 // 0=OFF, 1=ON, 2=AUTO
    @Volatile var torchEnabled: Boolean = false
    @Volatile var resolution: String? = null // e.g., "1920x1080"
    @Volatile var videoQuality: Int = 720 // 2160/1080/720/480
    @Volatile var videoRecordDelayMs: Int = 800

    // File manager settings
    @Volatile var sortModeOrdinal: Int = 0 // mirrors FileManagerActivity.SortMode ordinal
}

